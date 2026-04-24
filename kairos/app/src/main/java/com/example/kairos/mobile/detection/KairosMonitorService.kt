package com.example.kairos.mobile.detection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.kairos.mobile.data.HealthConnectManager
import kotlinx.coroutines.*

/**
 * Foreground Service que ejecuta el loop de monitoreo biométrico.
 *
 * Cubre US#3895 — monitoreo continuo de métricas biomédicas.
 * Cubre US#3556 — detección automática de crisis en background.
 *
 * Ciclo: cada 60s (ventana WESAD) lee HR + pasos, corre CrisisDetector
 * y emite broadcast si se confirma una crisis.
 */
class KairosMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var healthConnect: HealthConnectManager
    private val detector = CrisisDetector()

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID      = "kairos_monitor"
        const val ACTION_CRISIS   = "com.example.kairos.CRISIS_DETECTED"

        fun start(context: Context) =
            context.startForegroundService(Intent(context, KairosMonitorService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, KairosMonitorService::class.java))
    }

    override fun onCreate() {
        super.onCreate()
        healthConnect = HealthConnectManager(applicationContext)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoringLoop()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Loop principal ────────────────────────────────────────────────────────

    private fun startMonitoringLoop() {
        scope.launch {
            while (isActive) {
                runCycle()
                delay(WesadThresholds.ANALYSIS_WINDOW_SECONDS * 1_000)
            }
        }
    }

    private suspend fun runCycle() {
        if (!healthConnect.hasAllPermissions()) {
            Log.w("KairosMonitor", "Sin permisos, saltando ciclo")
            return
        }

        // TODO: Health Connect requiere foreground para leer HR
        // La lectura se hace desde MainActivity mientras la app está en pantalla
        // Cuando implementemos el DataClient de Wear OS, los datos llegarán
        // directamente desde el smartwatch sin pasar por Health Connect
        Log.d("KairosMonitor", "Ciclo activo — esperando datos del smartwatch")

        // ── Comentado hasta implementar Wear OS DataClient ────────────────────
//        val samples = healthConnect.readHeartRateSamples()
//        if (samples.isEmpty()) return
//
//        val steps  = healthConnect.readStepsInWindow()
//
//        val result = detector.analyze(
//            hrSamples            = samples,
//            stepsInWindow        = steps,
//            accelerometerMagnitude = 0.0
//        ) ?: return
//
//        Log.d("KairosMonitor", result.toLogString())
//
//        if (result.isCrisisDetected) {
//            broadcastCrisis(result)
//        }
    }

    private fun broadcastCrisis(result: DetectionResult) {
        sendBroadcast(Intent(ACTION_CRISIS).apply {
            putExtra("hr_bpm",    result.averageHrBpm)
            putExtra("rmssd_ms",  result.rmssdMs)
            putExtra("timestamp", result.timestamp.toEpochMilli())
        })
    }

    // ── Notificación foreground (requerida por Android) ───────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "KAIROS — Monitoreo activo",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Monitoreo biométrico en segundo plano" }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("KAIROS activo")
            .setContentText("Monitoreando signos vitales")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
}