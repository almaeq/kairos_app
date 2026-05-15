package com.example.kairos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.*
import androidx.health.services.client.setPassiveListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import com.example.kairos.detection.WatchCrisisDetector
import com.example.kairos.ui.WatchMonitorState

class KairosWatchService : Service() {

    companion object {
        var isRunning = false
        const val ACTION_RESET_WINDOW = "com.example.kairos.RESET_WINDOW"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var detector: WatchCrisisDetector

    override fun onCreate() {
        super.onCreate()
        if (isRunning) {
            stopSelf()
            return
        }
        isRunning = true
        detector = WatchCrisisDetector.getInstance(this)
        Log.d("KairosWatch", "Servicio iniciado — registrando PassiveListener")
        startForeground(1, buildNotification())
        registerPassiveListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESET_WINDOW) {
            // Notificar al PassiveListener via broadcast
            sendBroadcast(Intent("com.example.kairos.RESET_WINDOW"))
            Log.d("KairosWatch", "Reset enviado al PassiveListener")
        }
        return START_STICKY
    }

    private fun registerPassiveListener() {
        scope.launch {
            try {
                val passiveClient = HealthServices.getClient(this@KairosWatchService)
                    .passiveMonitoringClient

                val config = PassiveListenerConfig.builder()
                    .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                    .build()

                // setPassiveListenerService es suspend fun — no necesita .await()
                passiveClient.setPassiveListenerService(
                    KairosPassiveListener::class.java, config
                )

                Log.d("KairosWatch", "PassiveListener registrado ✅")

            } catch (e: Exception) {
                Log.e("KairosWatch", "Error registrando PassiveListener: ${e.message}")
            }
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "kairos_watch"
        val channel = NotificationChannel(
            channelId, "KAIROS Monitor", NotificationManager.IMPORTANCE_LOW
        )
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
        // Al destruirse el servicio, desregistrar el PassiveListener
        scope.launch {
            try {
                HealthServices.getClient(this@KairosWatchService)
                    .passiveMonitoringClient
                    .clearPassiveListenerServiceAsync()
                    .await()
            } catch (e: Exception) {
                Log.e("KairosWatch", "Error limpiando PassiveListener: ${e.message}")
            }
        }
    }
}