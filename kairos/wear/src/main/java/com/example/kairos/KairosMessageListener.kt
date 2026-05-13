package com.example.kairos

import android.content.Intent
import android.util.Log
import com.example.kairos.db.WatchBaseline
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class KairosMessageListener : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/kairos/ping" -> {
                Log.d("KairosPhone", "Ping recibido del reloj ✅")
            }
            "/kairos/hr" -> {
                val bpm = String(messageEvent.data).toDoubleOrNull()
                Log.d("KairosPhone", "HR recibida del reloj: $bpm BPM")
            }
            "/kairos/start" -> {
                Log.d("KairosWatch", "Comando recibido: iniciar monitoreo")
                startService(Intent(this, KairosWatchService::class.java))
            }
            "/kairos/stop" -> {
                Log.d("KairosWatch", "Comando recibido: detener monitoreo")
                stopService(Intent(this, KairosWatchService::class.java))
            }
            "/kairos/reset_baseline" -> {
                Log.d("KairosWatch", "Comando recibido: borrar baseline del reloj")
                WatchBaseline.clear(this)
            }
        }
    }

    override fun onPeerConnected(peer: com.google.android.gms.wearable.Node) {
        Log.d("KairosWatch", "Teléfono conectado: ${peer.displayName}")
    }

    override fun onPeerDisconnected(peer: com.google.android.gms.wearable.Node) {
        Log.d("KairosWatch", "Teléfono desconectado: ${peer.displayName}")
    }
}