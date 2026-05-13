package com.example.kairos

import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import com.example.kairos.detection.WatchCrisisDetector
import com.example.kairos.ui.WatchCrisisState
import com.example.kairos.ui.WatchMonitorState
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class KairosPassiveListener : PassiveListenerService() {

    // Scope propio ligado al ciclo de vida del servicio — no GlobalScope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val hrBuffer = mutableListOf<Double>()
    private lateinit var detector: WatchCrisisDetector

    private var windowStartMs = 0L
    private val CALIBRATION_WINDOW_MS = 60_000L
    private val MAX_BUFFER_SIZE = 80

    override fun onCreate() {
        super.onCreate()
        detector = WatchCrisisDetector.getInstance(this)
        windowStartMs = System.currentTimeMillis()
        Log.d("KairosPassive", "Listener creado — cal: ${detector.calibrationWindows}/3")
    }

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val hrData = dataPoints.getData(DataType.HEART_RATE_BPM)
        if (hrData.isEmpty()) return

        hrData.forEach { hrBuffer.add(it.value) }

        while (hrBuffer.size > MAX_BUFFER_SIZE) hrBuffer.removeAt(0)

        Log.d("KairosPassive", "HR: ${hrData.last().value} BPM — buffer: ${hrBuffer.size}")

        if (hrBuffer.size < 6) return

        val now = System.currentTimeMillis()
        val elapsedMs = now - windowStartMs

        if (!detector.isCalibrated()) {
            val remainingSecs = ((CALIBRATION_WINDOW_MS - elapsedMs) / 1000).coerceAtLeast(0)
            Log.d("KairosPassive", "Calibrando ${detector.calibrationWindows + 1}/3 — faltan ${remainingSecs}s")
            if (elapsedMs < CALIBRATION_WINDOW_MS) return
            windowStartMs = now
        }

        val result = detector.analyze(hrBuffer.toList()) ?: return

        if (!detector.isCalibrated()) {
            hrBuffer.clear()
        } else {
            val keepFrom = hrBuffer.size / 2
            repeat(keepFrom) { if (hrBuffer.isNotEmpty()) hrBuffer.removeAt(0) }
        }

        WatchMonitorState.update(
            heartRate          = result.averageHrBpm,
            rmssd              = result.rmssdMs,
            crisisState        = when {
                result.isCrisisDetected -> WatchCrisisState.CRISIS
                result.isPreAlert       -> WatchCrisisState.PRE_ALERT
                else                    -> WatchCrisisState.NORMAL
            },
            calibrationWindows = result.calibrationWindows
        )

        when {
            result.isCrisisDetected -> {
                Log.d("KairosPassive", "🚨 CRISIS detectada en background")
                sendToPhone("/kairos/crisis",
                    "hr=${result.averageHrBpm},rmssd=${result.rmssdMs}")
            }
            result.isPreAlert -> {
                Log.d("KairosPassive", "⚠️ PRE-ALERTA en background")
                sendToPhone("/kairos/prealerta", "hr=${result.averageHrBpm}")
            }
            else -> {
                // En background el PassiveListener recibe datos con menor frecuencia,
                // así que cada ciclo normal ya funciona como heartbeat al celular
                sendToPhone(
                    "/kairos/heartbeat",
                    "hr=${result.averageHrBpm},rmssd=${result.rmssdMs},cal=${result.calibrationWindows}"
                )
                Log.d("KairosPassive", "✅ Normal — HR=${"%.1f".format(result.averageHrBpm)} " +
                        "RMSSD=${"%.1f".format(result.rmssdMs)}")
            }
        }
    }

    private fun sendToPhone(path: String, data: String) {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@KairosPassiveListener)
                    .connectedNodes.await()
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@KairosPassiveListener)
                        .sendMessage(node.id, path, data.toByteArray())
                        .await()
                    Log.d("KairosPassive", "Enviado $path → ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e("KairosPassive", "Error enviando $path: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}