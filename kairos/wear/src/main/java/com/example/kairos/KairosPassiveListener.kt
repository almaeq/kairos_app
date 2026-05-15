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

    private val CALIBRATION_WINDOW_MS = 60_000L
    private val MAX_BUFFER_SIZE = 80
    private val HEARTBEAT_INTERVAL_MS = 60_000L

    // SharedPreferences para persistir windowStartMs entre reinicios del servicio
    private val prefs by lazy {
        getSharedPreferences("kairos_passive", Context.MODE_PRIVATE)
    }

    private var windowStartMs: Long
        get() = prefs.getLong("window_start_ms", 0L)
        set(value) = prefs.edit().putLong("window_start_ms", value).apply()

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

        // Si no hay windowStartMs guardado (primer arranque o tras reset),
        // inicializarlo ahora. Si ya existe, la ventana continúa desde donde
        // quedó antes del reinicio del servicio.
        if (windowStartMs == 0L) {
            windowStartMs = System.currentTimeMillis()
        }

        registerReceiver(
            resetReceiver,
            IntentFilter("com.example.kairos.RESET_WINDOW"),
            RECEIVER_NOT_EXPORTED
        )
        startHeartbeat()
        Log.d("KairosPassive", "PassiveListener iniciado — cal: ${detector.calibrationWindows}/3 " +
                "— ventana en curso desde hace ${(System.currentTimeMillis() - windowStartMs) / 1000}s")
    }

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
            windowStartMs = now  // persiste automáticamente via setter
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

        // Al completar calibración, mandar heartbeat inmediato al teléfono
        // sin esperar los 60s del timer (que recién arrancó)
        if (result.calibrationWindows == detector.calibrationWindows && detector.isCalibrated()) {
            Log.d("KairosPassive", "✅ Calibración completa — enviando heartbeat inmediato")
            sendToPhone(
                "/kairos/heartbeat",
                "hr=${result.averageHrBpm},rmssd=${result.rmssdMs},cal=${result.calibrationWindows}"
            )
        }

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
        // Scope propio por envío: si el scope principal fue cancelado (onDestroy
        // llegó justo en este momento), el mensaje igual se despacha.
        CoroutineScope(Dispatchers.IO).launch {
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