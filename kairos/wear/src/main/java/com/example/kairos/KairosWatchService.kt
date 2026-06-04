package com.example.kairos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.WarmUpConfig
import androidx.health.services.client.setPassiveListenerService
import com.example.kairos.db.WatchBaseline
import com.example.kairos.detection.WatchCrisisDetector
import com.example.kairos.ui.WatchCrisisState
import com.example.kairos.ui.WatchMonitorState
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class KairosWatchService : Service() {

    companion object {
        var isRunning = false
        const val ACTION_RESET_WINDOW = "com.example.kairos.RESET_WINDOW"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var detector: WatchCrisisDetector
    private lateinit var exerciseClient: ExerciseClient

    private val hrBuffer = mutableListOf<Double>()
    private val MAX_BUFFER_SIZE = 80
    private val CALIBRATION_WINDOW_MS = 60_000L
    private val HEARTBEAT_INTERVAL_MS = 60_000L
    private var calibrationHeartbeatSent = false

    // Acelerómetro para el filtro de movimiento
    private var accelerometerSteps = 0L
    private var lastAccelMagnitude = 0f
    private val MOVEMENT_THRESHOLD = 12f // m/s² — umbral para detectar movimiento significativo
    private var movementCount      = 0

    private val prefs by lazy { getSharedPreferences("kairos_passive", MODE_PRIVATE) }
    private var windowStartMs: Long
        get() = prefs.getLong("window_start_ms", 0L)
        set(value) = prefs.edit().putLong("window_start_ms", value).apply()

    private val exerciseCallback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val hrData = update.latestMetrics.getData(DataType.HEART_RATE_BPM)
            if (hrData.isEmpty()) return

            hrData.forEach { hrBuffer.add(it.value) }
            while (hrBuffer.size > MAX_BUFFER_SIZE) hrBuffer.removeAt(0)

            // Filtro de movimiento via acelerómetro — si hay movimiento intenso no es crisis
            val stepsInWindow = accelerometerSteps

            Log.d("KairosWatch", "HR: ${hrData.last().value} BPM — buffer: ${hrBuffer.size} — mov: $stepsInWindow")

            if (hrBuffer.size < 6) return

            val now = System.currentTimeMillis()
            val elapsedMs = now - windowStartMs

            if (!detector.isCalibrated()) {
                val remainingSecs = ((CALIBRATION_WINDOW_MS - elapsedMs) / 1000).coerceAtLeast(0)
                Log.d(
                    "KairosWatch",
                    "Calibrando ${detector.calibrationWindows + 1}/3 — faltan ${remainingSecs}s"
                )
                if (elapsedMs < CALIBRATION_WINDOW_MS) return
                windowStartMs = now
            }

            val result = detector.analyze(hrBuffer.toList(), stepsInWindow) ?: return

            if (!detector.isCalibrated()) {
                hrBuffer.clear()
            } else {
                val keepFrom = hrBuffer.size / 2
                repeat(keepFrom) { if (hrBuffer.isNotEmpty()) hrBuffer.removeAt(0) }
            }

            WatchMonitorState.update(
                heartRate = result.averageHrBpm,
                rmssd = result.rmssdMs,
                crisisState = when {
                    result.isCrisisDetected -> WatchCrisisState.CRISIS
                    result.isPreAlert -> WatchCrisisState.PRE_ALERT
                    else -> WatchCrisisState.NORMAL
                },
                calibrationWindows = result.calibrationWindows
            )

            if (detector.isCalibrated() && !calibrationHeartbeatSent) {
                calibrationHeartbeatSent = true
                Log.d("KairosWatch", "✅ Calibración completa — heartbeat inmediato")

                // Enviar baseline completo al teléfono
                val bl = WatchBaseline.load(this@KairosWatchService)
                if (bl != null) {
                    sendToPhone("/kairos/baseline",
                        "hrMean=${bl.hrMean},hrM2=${bl.hrM2},hrCount=${bl.hrCount}," +
                                "hrvMean=${bl.hrvMean},hrvM2=${bl.hrvM2},hrvCount=${bl.hrvCount}," +
                                "cal=${bl.calibrationWindows}")
                }

                // Heartbeat normal también
                sendToPhone("/kairos/heartbeat",
                    "hr=${result.averageHrBpm},rmssd=${result.rmssdMs},cal=${result.calibrationWindows}")
            }

            when {
                result.isCrisisDetected -> {
                    Log.d("KairosWatch", "🚨 CRISIS — HR=${result.averageHrBpm}")
                    sendToPhone(
                        "/kairos/crisis",
                        "hr=${result.averageHrBpm},rmssd=${result.rmssdMs}"
                    )

                    // Notificación de alta prioridad — Android 14+ bloquea startActivity directo desde servicios
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    val channelId = "kairos_crisis"
                    val channel = NotificationChannel(
                        channelId, "KAIROS Crisis",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 300, 150, 300, 150, 600)
                    }
                    notificationManager.createNotificationChannel(channel)

                    val intent = Intent(this@KairosWatchService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        this@KairosWatchService, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    val notification = Notification.Builder(this@KairosWatchService, channelId)
                        .setContentTitle("⚠️ KAIROS — Crisis detectada")
                        .setContentText("Tocá para iniciar el ejercicio de grounding")
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setFullScreenIntent(pendingIntent, true)
                        .build()

                    notificationManager.notify(2, notification)
                }

                result.isPreAlert -> {
                    Log.d("KairosWatch", "⚠️ PRE-ALERTA — HR=${result.averageHrBpm}")
                    sendToPhone("/kairos/prealerta", "hr=${result.averageHrBpm}")
                }

                else -> {
                    Log.d(
                        "KairosWatch", "✅ Normal — HR=${"%.1f".format(result.averageHrBpm)} " +
                                "RMSSD=${"%.1f".format(result.rmssdMs)}"
                    )
                }
            }
        }

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
            Log.d("KairosWatch", "Sensor $dataType: $availability")
        }
        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
        override fun onRegistered() { Log.d("KairosWatch", "ExerciseCallback registrado ✅") }
        override fun onRegistrationFailed(throwable: Throwable) {
            Log.e("KairosWatch", "ExerciseCallback falló: ${throwable.message}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (isRunning) { stopSelf(); return }
        isRunning = true

        detector = WatchCrisisDetector.getInstance(this)
        exerciseClient = HealthServices.getClient(this).exerciseClient

        if (windowStartMs == 0L) windowStartMs = System.currentTimeMillis()

        startForeground(1, buildNotification())
        startExercise()
        startHeartbeat()
        startAccelerometer()

        Log.d("KairosWatch", "Servicio iniciado — cal: ${detector.calibrationWindows}/3")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESET_WINDOW) {
            windowStartMs = System.currentTimeMillis()
            hrBuffer.clear()
            calibrationHeartbeatSent = false
            Log.d("KairosWatch", "Ventana reseteada")
        }
        return START_STICKY
    }

    private fun startExercise() {
        scope.launch {
            try {
                try {
                    HealthServices.getClient(this@KairosWatchService)
                        .passiveMonitoringClient
                        .clearPassiveListenerServiceAsync()
                        .await()
                    Log.d("KairosWatch", "PassiveListener previo limpiado")
                } catch (e: Exception) { /* no había ninguno, ignorar */ }

                exerciseClient.setUpdateCallback(exerciseCallback)

                val warmUpConfig = WarmUpConfig(
                    exerciseType = ExerciseType.WORKOUT,
                    dataTypes    = setOf(DataType.HEART_RATE_BPM)
                )
                exerciseClient.prepareExerciseAsync(warmUpConfig).await()
                Log.d("KairosWatch", "Warm-up iniciado")

                val config = ExerciseConfig(
                    exerciseType                = ExerciseType.WORKOUT,
                    dataTypes                   = setOf(DataType.HEART_RATE_BPM),
                    isAutoPauseAndResumeEnabled = false,
                    isGpsEnabled                = false
                )
                exerciseClient.startExerciseAsync(config).await()
                Log.d("KairosWatch", "ExerciseClient activo — HR continuo ✅")

                sendPingToPhone()

            } catch (e: Exception) {
                Log.e("KairosWatch", "Error iniciando ExerciseClient: ${e.message}")
                fallbackToPassiveListener()
            }
        }
    }

    private suspend fun fallbackToPassiveListener() {
        try {
            val passiveClient = HealthServices.getClient(this@KairosWatchService)
                .passiveMonitoringClient
            val config = androidx.health.services.client.data.PassiveListenerConfig.builder()
                .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                .setShouldUserActivityInfoBeRequested(true)
                .build()
            passiveClient.setPassiveListenerService(KairosPassiveListener::class.java, config)
            Log.d("KairosWatch", "Fallback a PassiveListener ✅")
        } catch (e: Exception) {
            Log.e("KairosWatch", "Fallback también falló: ${e.message}")
        }
    }

    private fun startAccelerometer() {
        val sensorManager = getSystemService(android.hardware.SensorManager::class.java)
        val accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            Log.w("KairosWatch", "Acelerómetro no disponible — filtro de movimiento desactivado")
            return
        }

        sensorManager.registerListener(
            object : android.hardware.SensorEventListener {
                override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                    event ?: return
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    // Magnitud del vector de aceleración (sin gravedad aproximada)
                    val magnitude = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                    // Contar eventos de movimiento significativo en la ventana
                    if (magnitude > MOVEMENT_THRESHOLD) {
                        movementCount++
                        // Convertir a "pasos equivalentes" para el detector
                        // El detector usa pasos/minuto <= 30 como umbral
                        accelerometerSteps = movementCount.toLong()
                    }
                    // Resetear cada 60s (una ventana de análisis)
                    lastAccelMagnitude = magnitude
                }
                override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
            },
            accelerometer,
            android.hardware.SensorManager.SENSOR_DELAY_NORMAL
        )

        // Resetear el contador cada 60s para que coincida con las ventanas del detector
        scope.launch {
            while (true) {
                delay(60_000L)
                movementCount  = 0
                accelerometerSteps = 0L
            }
        }
        Log.d("KairosWatch", "Acelerómetro registrado ✅")
    }

    private fun startHeartbeat() {
        scope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                val state = WatchMonitorState.state.value
                if (state.heartRate > 0) {
                    sendToPhone("/kairos/heartbeat",
                        "hr=${state.heartRate},rmssd=${state.rmssd},cal=${state.calibrationWindows}")
                    Log.d("KairosWatch", "Heartbeat — HR=${state.heartRate}")
                }
            }
        }
    }

    private suspend fun sendPingToPhone() {
        try {
            val nodes = Wearable.getNodeClient(this@KairosWatchService)
                .connectedNodes.await()
            if (nodes.isEmpty()) { Log.w("KairosWatch", "Ping: sin nodos"); return }
            nodes.forEach { node ->
                Wearable.getMessageClient(this@KairosWatchService)
                    .sendMessage(node.id, "/kairos/ping", ByteArray(0)).await()
                Log.d("KairosWatch", "Ping → ${node.displayName}")
            }
        } catch (e: Exception) {
            Log.e("KairosWatch", "Error en ping: ${e.message}")
        }
    }

    private fun sendToPhone(path: String, data: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = Wearable.getNodeClient(this@KairosWatchService)
                    .connectedNodes.await()
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@KairosWatchService)
                        .sendMessage(node.id, path, data.toByteArray()).await()
                    Log.d("KairosWatch", "Enviado $path → ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e("KairosWatch", "Error enviando $path: ${e.message}")
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                exerciseClient.endExerciseAsync().await()
                Log.d("KairosWatch", "ExerciseClient detenido ✅")
            } catch (e: Exception) {
                Log.e("KairosWatch", "Error deteniendo exercise: ${e.message}")
            }
        }
        scope.cancel()
    }
}