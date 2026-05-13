package com.example.kairos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.*
import androidx.health.services.client.setPassiveListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import com.example.kairos.detection.WatchCrisisDetector
import com.example.kairos.ui.WatchCrisisState
import com.example.kairos.ui.WatchMonitorState
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await as awaitTask

class KairosWatchService : Service() {

    companion object {
        var isRunning = false
        const val ACTION_RESET_WINDOW = "com.example.kairos.RESET_WINDOW"
        private const val CALIBRATION_WINDOW_MS = 60_000L
        private const val MAX_BUFFER_SIZE = 80
        private const val HEARTBEAT_INTERVAL_MS = 60_000L  // update al celular cada 60s
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val hrBuffer = mutableListOf<Double>()
    private lateinit var detector: WatchCrisisDetector
    private var windowStartMs = 0L

    override fun onCreate() {
        super.onCreate()
        if (isRunning) {
            Log.d("KairosWatch", "Servicio ya activo")
            stopSelf()
            return
        }
        isRunning = true
        detector = WatchCrisisDetector.getInstance(this)
        windowStartMs = System.currentTimeMillis()
        Log.d("KairosWatch", "Servicio iniciado")
        startForeground(1, buildNotification())
        startExerciseMonitoring()
        startHeartbeat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESET_WINDOW) {
            windowStartMs = System.currentTimeMillis()
            hrBuffer.clear()
            Log.d("KairosWatch", "Ventana reseteada — recalibrando desde 0s")
        }
        return START_STICKY
    }

    // Cada 60s manda el estado actual al celular aunque no haya crisis
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
                    Log.d("KairosWatch", "Heartbeat enviado al celular")
                }
            }
        }
    }

    private fun startExerciseMonitoring() {
        scope.launch {
            try {
                val exerciseClient = HealthServices.getClient(this@KairosWatchService)
                    .exerciseClient

                val capabilities = exerciseClient.getCapabilitiesAsync().await()
                val exerciseType = when {
                    ExerciseType.WALKING in capabilities.supportedExerciseTypes -> ExerciseType.WALKING
                    else -> ExerciseType.WORKOUT
                }

                try {
                    exerciseClient.endExerciseAsync().await()
                } catch (e: Exception) {
                    Log.d("KairosWatch", "Sin ejercicio previo activo")
                }

                val config = ExerciseConfig(
                    exerciseType = exerciseType,
                    dataTypes = setOf(DataType.HEART_RATE_BPM),
                    isAutoPauseAndResumeEnabled = false,
                    isGpsEnabled = false
                )

                exerciseClient.setUpdateCallback(
                    object : ExerciseUpdateCallback {
                        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                            val hrData = update.latestMetrics.getData(DataType.HEART_RATE_BPM)
                            if (hrData.isNotEmpty()) {
                                hrData.forEach { hrBuffer.add(it.value) }
                                while (hrBuffer.size > MAX_BUFFER_SIZE) hrBuffer.removeAt(0)
                                Log.d("KairosWatch", "HR: ${hrData.last().value} BPM — buffer: ${hrBuffer.size}")
                                processBuffer()
                            }
                        }
                        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
                        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {}
                        override fun onRegistered() {
                            Log.d("KairosWatch", "ExerciseUpdateCallback registrado ✅")
                        }
                        override fun onRegistrationFailed(throwable: Throwable) {
                            Log.e("KairosWatch", "Error registrando callback: ${throwable.message}")
                        }
                    }
                )

                exerciseClient.startExerciseAsync(config).await()
                Log.d("KairosWatch", "ExerciseClient iniciado ✅")

            } catch (e: Exception) {
                Log.e("KairosWatch", "Error iniciando exercise: ${e.message}")
                startPassiveFallback()
            }
        }
    }

    private fun processBuffer() {
        if (hrBuffer.size < 6) return

        val now = System.currentTimeMillis()
        val elapsedMs = now - windowStartMs

        if (!detector.isCalibrated()) {
            val remainingSecs = ((CALIBRATION_WINDOW_MS - elapsedMs) / 1000).coerceAtLeast(0)
            Log.d("KairosWatch", "Calibrando ventana ${detector.calibrationWindows + 1}/3 " +
                    "— faltan ${remainingSecs}s — muestras: ${hrBuffer.size}")
            if (elapsedMs < CALIBRATION_WINDOW_MS) return
            windowStartMs = now
            Log.d("KairosWatch", "Ventana ${detector.calibrationWindows + 1} completada")
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
                Log.d("KairosWatch", "🚨 CRISIS — HR=${result.averageHrBpm} RMSSD=${result.rmssdMs}")
                sendToPhone("/kairos/crisis",
                    "hr=${result.averageHrBpm},rmssd=${result.rmssdMs}")
            }
            result.isPreAlert -> {
                Log.d("KairosWatch", "⚠️ PRE-ALERTA — HR=${result.averageHrBpm}")
                sendToPhone("/kairos/prealerta", "hr=${result.averageHrBpm}")
            }
            else -> {
                Log.d("KairosWatch", "✅ Normal — HR=${"%.1f".format(result.averageHrBpm)} " +
                        "RMSSD=${"%.1f".format(result.rmssdMs)} buffer: ${hrBuffer.size}")
            }
        }
    }

    private fun startPassiveFallback() {
        scope.launch {
            try {
                val passiveClient = HealthServices.getClient(this@KairosWatchService)
                    .passiveMonitoringClient
                val config = androidx.health.services.client.data.PassiveListenerConfig.builder()
                    .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                    .build()
                passiveClient.setPassiveListenerService(
                    KairosPassiveListener::class.java, config
                )
                Log.d("KairosWatch", "Fallback a PassiveMonitoring ✅")
            } catch (e: Exception) {
                Log.e("KairosWatch", "Error en fallback: ${e.message}")
            }
        }
    }

    private fun sendToPhone(path: String, data: String) {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@KairosWatchService)
                    .connectedNodes.awaitTask()
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@KairosWatchService)
                        .sendMessage(node.id, path, data.toByteArray())
                        .awaitTask()
                    Log.d("KairosWatch", "Enviado $path → ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e("KairosWatch", "Error enviando: ${e.message}")
            }
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "kairos_watch"
        val channel = NotificationChannel(channelId, "KAIROS Monitor", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return Notification.Builder(this, channelId)
            .setContentTitle("KAIROS activo")
            .setContentText("Monitoreando en segundo plano")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        scope.cancel()
    }
}