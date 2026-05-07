package com.example.kairos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.setPassiveListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class KairosWatchService : Service() {

    companion object {
        var isRunning = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (isRunning) {
            Log.d("KairosWatch", "Servicio ya activo, ignorando inicio duplicado")
            stopSelf()
            return
        }
        isRunning = true
        Log.d("KairosWatch", "Servicio iniciado")
        startForeground(1, buildNotification())
        registerPassiveListener()
    }

    private fun registerPassiveListener() {
        scope.launch {
            try {
                val passiveClient = HealthServices.getClient(this@KairosWatchService)
                    .passiveMonitoringClient

                val config = PassiveListenerConfig.builder()
                    .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                    .build()

                passiveClient.setPassiveListenerService(
                    KairosPassiveListener::class.java,
                    config
                )

                Log.d("KairosWatch", "Listener pasivo registrado ✅")
            } catch (e: Exception) {
                Log.e("KairosWatch", "Error registrando listener: ${e.message}", e)
            }
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "kairos_watch"
        val channel = NotificationChannel(
            channelId, "KAIROS Monitor",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("KAIROS")
            .setContentText("Activo")
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