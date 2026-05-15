package com.example.kairos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class KairosPassiveListener : PassiveListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val hrBuffer = mutableListOf<Double>()
    private lateinit var detector: WatchCrisisDetector

    private var windowStartMs = 0L
    private val CALIBRATION_WINDOW_MS = 60_000L
    private val MAX_BUFFER_SIZE = 80
    private val HEARTBEAT_INTERVAL_MS = 60_000L

    // Recibe el reset desde KairosWatchService
    private val resetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            windowStartMs = System.currentTimeMillis()
            hrBuffer.clear()
            Log.d("KairosPassive", "Ventana reseteada via broadcast")
        }
    }

    override fun onCreate() {
        super.onCreate()
        detector = WatchCrisisDetector.getInstance(this)
        windowStartMs = System.currentTimeMillis()
        registerReceiver(resetReceiver, IntentFilter("com.example.kairos.RESET_WINDOW"),
            RECEIVER_NOT_EXPORTED)
        startHeartbeat()
        Log.d("KairosPassive", "PassiveListener iniciado — cal: ${detector.calibrationWindows}/3")
    }

    // Timer propio de heartbeat — independiente del ExerciseClient
    private fun startHeartbeat() {
        scope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                val state = WatchMonitorState.state.value
                if (state.heartRate > 0) {
                    sendToPhone(
                        "/kairos/heartbeat",
                        "hr=${state.heartRate},rmssd=${state.rmssd},cal=${state.calibrationWindows}"
                    )
                    Log.d("KairosPassive", "Heartbeat enviado — HR=${state.heartRate}")
                }
            }
        }
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
            Log.d("KairosPassive", "Calibrando ${detector.calibrationWindows + 1}/3 " +
                    "— faltan ${remainingSecs}s")
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

        // Actualizar estado — el heartbeat timer lo mandará al celular
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

        // Crisis y pre-alerta se mandan inmediatamente, sin esperar el heartbeat
        when {
            result.isCrisisDetected -> {
                Log.d("KairosPassive", "🚨 CRISIS detectada")
                sendToPhone("/kairos/crisis",
                    "hr=${result.averageHrBpm},rmssd=${result.rmssdMs}")
            }
            result.isPreAlert -> {
                Log.d("KairosPassive", "⚠️ PRE-ALERTA")
                sendToPhone("/kairos/prealerta", "hr=${result.averageHrBpm}")
            }
            else -> {
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
        unregisterReceiver(resetReceiver)
        scope.cancel()
    }
}