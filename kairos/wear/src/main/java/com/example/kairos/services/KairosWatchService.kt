package com.example.kairos.services

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.WarmUpConfig
import androidx.health.services.client.setPassiveListenerService
import com.example.kairos.MainActivity
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
import kotlin.math.sqrt

/**
 * Servicio foreground principal del reloj que orquesta el monitoreo continuo de HR
 * y ejecuta el pipeline de detección de crisis.
 *
 * **Estrategia de sensores — ExerciseClient con fallback a PassiveListener:**
 * Se intenta iniciar con `ExerciseClient` porque mantiene acceso continuo al sensor
 * incluso con la pantalla apagada. Si falla (dispositivo no compatible o sesión
 * activa preexistente), hace fallback a [KairosPassiveListener] via `PassiveMonitoringClient`.
 *
 * **Filtro de movimiento via acelerómetro:**
 * El acelerómetro cuenta eventos de movimiento significativo (magnitud > [MOVEMENT_THRESHOLD])
 * en cada ventana de 60s. Este conteo se pasa al detector como proxy de "pasos en la ventana",
 * permitiendo distinguir taquicardia por ejercicio de taquicardia por estrés/ansiedad.
 *
 * **Sincronización de baseline al teléfono:**
 * Cuando se completa la calibración, envía los parámetros de Welford al teléfono via
 * `/kairos/baseline` para que Room los persista y la UI del teléfono refleje el estado correcto.
 *
 * **Prevención de instancias múltiples:**
 * [isRunning] actúa como guardia para que el servicio no se inicie dos veces si
 * el SO intenta relanzarlo antes de que el anterior termine.
 */
class KairosWatchService : Service() {

    companion object {
        /** Flag que indica si el servicio está activo — previene instancias múltiples. */
        var isRunning = false

        /** Action para resetear la ventana actual desde fuera del servicio. */
        const val ACTION_RESET_WINDOW = "com.example.kairos.RESET_WINDOW"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var detector: WatchCrisisDetector
    private lateinit var exerciseClient: ExerciseClient

    /** Buffer circular de muestras de HR de la ventana actual, en BPM. */
    private val hrBuffer = mutableListOf<Double>()
    private val MAX_BUFFER_SIZE = 80
    private val CALIBRATION_WINDOW_MS  = 60_000L
    private val HEARTBEAT_INTERVAL_MS  = 60_000L

    /**
     * Flag que evita enviar múltiples heartbeats de "calibración completa"
     * — se resetea cuando se resetea la ventana.
     */
    private var calibrationHeartbeatSent = false

    /** Conteo de eventos de movimiento significativo en la ventana actual. */
    private var accelerometerSteps = 0L
    private var lastAccelMagnitude = 0f

    /**
     * Umbral de magnitud del acelerómetro para considerar un evento como movimiento significativo.
     * Unidad: m/s². El valor 12.0 filtra movimientos leves del brazo pero detecta caminata activa.
     */
    private val MOVEMENT_THRESHOLD = 12f
    private var movementCount = 0

    /**
     * Timestamp de inicio de la ventana actual, persistido en SharedPreferences
     * para sobrevivir reinicios del servicio.
     */
    private val prefs by lazy { getSharedPreferences("kairos_passive", MODE_PRIVATE) }
    private var windowStartMs: Long
        get()      = prefs.getLong("window_start_ms", 0L)
        set(value) = prefs.edit().putLong("window_start_ms", value).apply()

    /**
     * Callback de ExerciseClient que recibe actualizaciones de HR del sensor.
     *
     * Implementa el mismo pipeline de [KairosPassiveListener] pero vía ExerciseClient,
     * que garantiza entrega continua de datos con la pantalla apagada.
     *
     * Al completar la calibración, sincroniza el baseline completo al teléfono y
     * emite una notificación de alta prioridad en caso de crisis.
     */
    private val exerciseCallback = object : ExerciseUpdateCallback {

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val hrData = update.latestMetrics.getData(DataType.HEART_RATE_BPM)
            if (hrData.isEmpty()) return

            hrData.forEach { hrBuffer.add(it.value) }
            while (hrBuffer.size > MAX_BUFFER_SIZE) hrBuffer.removeAt(0)

            val stepsInWindow = accelerometerSteps
            Log.d("KairosWatch", "HR: ${hrData.last().value} BPM — buffer: ${hrBuffer.size} — mov: $stepsInWindow")

            if (hrBuffer.size < 6) return

            val now       = System.currentTimeMillis()
            val elapsedMs = now - windowStartMs

            if (!detector.isCalibrated()) {
                val remainingSecs = ((CALIBRATION_WINDOW_MS - elapsedMs) / 1000).coerceAtLeast(0)
                Log.d("KairosWatch", "Calibrando ${detector.calibrationWindows + 1}/3 — faltan ${remainingSecs}s")
                if (elapsedMs < CALIBRATION_WINDOW_MS) return
                windowStartMs = now
            }

            val result = detector.analyze(hrBuffer.toList(), stepsInWindow) ?: return

            // Gestión del overlap del buffer según estado de calibración
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

            // Al completar calibración: sincronizamos baseline al teléfono y enviamos heartbeat inmediato
            if (detector.isCalibrated() && !calibrationHeartbeatSent) {
                calibrationHeartbeatSent = true
                Log.d("KairosWatch", "✅ Calibración completa — heartbeat inmediato")

                // Enviamos los parámetros de Welford para que Room los persista en el teléfono
                val bl = WatchBaseline.load(this@KairosWatchService)
                if (bl != null) {
                    sendToPhone("/kairos/baseline",
                        "hrMean=${bl.hrMean},hrM2=${bl.hrM2},hrCount=${bl.hrCount}," +
                                "hrvMean=${bl.hrvMean},hrvM2=${bl.hrvM2},hrvCount=${bl.hrvCount}," +
                                "cal=${bl.calibrationWindows}")
                }
                sendToPhone("/kairos/heartbeat",
                    "hr=${result.averageHrBpm},rmssd=${result.rmssdMs},cal=${result.calibrationWindows}")
            }

            when {
                result.isCrisisDetected -> {
                    Log.d("KairosWatch", "🚨 CRISIS — HR=${result.averageHrBpm}")
                    sendToPhone("/kairos/crisis",
                        "hr=${result.averageHrBpm},rmssd=${result.rmssdMs}")

                    // Notificación de alta prioridad con FullScreenIntent para Wear OS
                    // Android 14+ bloquea startActivity directo desde servicios — usamos PendingIntent
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    val channelId = "kairos_crisis"
                    val channel = NotificationChannel(channelId, "KAIROS Crisis",
                        NotificationManager.IMPORTANCE_HIGH).apply {
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
                        .setSmallIcon(R.drawable.ic_dialog_alert)
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
                    Log.d("KairosWatch", "✅ Normal — HR=${"%.1f".format(result.averageHrBpm)} " +
                            "RMSSD=${"%.1f".format(result.rmssdMs)}")
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
        // Prevenimos instancias múltiples — el SO puede intentar relanzar el servicio
        if (isRunning) { stopSelf(); return }
        isRunning = true

        detector      = WatchCrisisDetector.getInstance(this)
        exerciseClient = HealthServices.getClient(this).exerciseClient

        if (windowStartMs == 0L) windowStartMs = System.currentTimeMillis()

        startForeground(1, buildNotification())
        startExercise()
        startHeartbeat()
        startAccelerometer()

        Log.d("KairosWatch", "Servicio iniciado — cal: ${detector.calibrationWindows}/3")
    }

    /**
     * Maneja comandos entrantes, principalmente [ACTION_RESET_WINDOW] enviado
     * cuando el usuario recalibra desde el teléfono.
     *
     * `START_STICKY` garantiza que el SO relance el servicio si lo mata por memoria,
     * esencial para el monitoreo continuo 24/7.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESET_WINDOW) {
            windowStartMs            = System.currentTimeMillis()
            calibrationHeartbeatSent = false
            hrBuffer.clear()
            Log.d("KairosWatch", "Ventana reseteada")
        }
        return START_STICKY
    }

    /**
     * Inicia la sesión de ExerciseClient para acceso continuo al sensor de HR.
     *
     * El flujo es: limpiar PassiveListener previo → registrar callback →
     * warm-up → start. Si cualquier paso falla, hace fallback a [KairosPassiveListener].
     *
     * El warm-up es necesario para que el sensor se active antes de iniciar
     * la sesión de ejercicio, reduciendo el tiempo hasta el primer dato de HR.
     */
    private fun startExercise() {
        scope.launch {
            try {
                // Limpiamos cualquier PassiveListener previo para evitar conflictos
                try {
                    HealthServices.getClient(this@KairosWatchService)
                        .passiveMonitoringClient
                        .clearPassiveListenerServiceAsync()
                        .await()
                    Log.d("KairosWatch", "PassiveListener previo limpiado")
                } catch (e: Exception) { /* no había ninguno registrado */ }

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

    /**
     * Fallback a [KairosPassiveListener] si ExerciseClient no está disponible.
     *
     * Puede ocurrir si el dispositivo no soporta ExerciseClient para WORKOUT
     * o si ya hay una sesión de ejercicio activa de otra app.
     */
    private suspend fun fallbackToPassiveListener() {
        try {
            val passiveClient = HealthServices.getClient(this@KairosWatchService)
                .passiveMonitoringClient
            val config = PassiveListenerConfig.builder()
                .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                .setShouldUserActivityInfoBeRequested(true)
                .build()
            passiveClient.setPassiveListenerService(KairosPassiveListener::class.java, config)
            Log.d("KairosWatch", "Fallback a PassiveListener ✅")
        } catch (e: Exception) {
            Log.e("KairosWatch", "Fallback también falló: ${e.message}")
        }
    }

    /**
     * Registra el acelerómetro para el filtro de movimiento del detector.
     *
     * Cuenta eventos donde la magnitud del vector de aceleración supera
     * [MOVEMENT_THRESHOLD], convirtiéndolos en "pasos equivalentes" para
     * el umbral de 30 pasos/minuto del [WatchCrisisDetector].
     *
     * El contador se resetea cada 60s para alinear con las ventanas del detector.
     */
    private fun startAccelerometer() {
        val sensorManager = getSystemService(SensorManager::class.java)
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            Log.w("KairosWatch", "Acelerómetro no disponible — filtro de movimiento desactivado")
            return
        }

        sensorManager.registerListener(
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event ?: return
                    val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
                    val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                    if (magnitude > MOVEMENT_THRESHOLD) {
                        movementCount++
                        accelerometerSteps = movementCount.toLong()
                    }
                    lastAccelMagnitude = magnitude
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            },
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        // Reseteo periódico del contador para alinear con las ventanas del detector
        scope.launch {
            while (true) {
                delay(60_000L)
                movementCount      = 0
                accelerometerSteps = 0L
            }
        }
        Log.d("KairosWatch", "Acelerómetro registrado ✅")
    }

    /**
     * Loop periódico que envía el estado actual al teléfono cada [HEARTBEAT_INTERVAL_MS].
     * El heartbeat también funciona como señal de "reloj en estado normal" para que
     * [KairosPhoneListener] resetee el flag `crisisConfirmed`.
     */
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

    /**
     * Envía un ping al teléfono al iniciar el servicio para notificar que el reloj está activo.
     * [KairosPhoneListener] responde actualizando el indicador de conectividad en la UI.
     */
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

    /**
     * Envía un mensaje al teléfono via Wearable Message API.
     *
     * Usa un scope propio para garantizar que el mensaje se despache incluso si
     * [onDestroy] fue llamado antes de que el envío completara.
     *
     * @param path Path del mensaje (por ejemplo `/kairos/crisis`).
     * @param data Payload como string (por ejemplo `"hr=95.3,rmssd=22.1"`).
     */
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

    /**
     * Construye la notificación persistente del servicio foreground.
     *
     * Importance LOW para que no emita sonido — es solo el indicador de que
     * KAIROS está monitoreando en segundo plano.
     */
    private fun buildNotification(): Notification {
        val channelId = "kairos_watch"
        val channel = NotificationChannel(channelId, "KAIROS Monitor",
            NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return Notification.Builder(this, channelId)
            .setContentTitle("KAIROS activo")
            .setContentText("Monitoreando en segundo plano")
            .setSmallIcon(R.drawable.ic_menu_compass)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        // Scope propio para garantizar que endExercise se complete aunque el scope principal
        // ya esté cancelado en este punto
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