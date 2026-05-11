package com.example.kairos.mobile

import android.util.Log
import androidx.health.connect.client.records.HeartRateRecord
import com.example.kairos.mobile.data.BaselineRepository
import com.example.kairos.mobile.data.db.KairosDatabase
import com.example.kairos.mobile.detection.CrisisDetector
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

class KairosPhoneListener : WearableListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/kairos/ping" -> {
                Log.d("KairosPhone", "✅ Ping recibido del reloj")
            }
            "/kairos/crisis" -> {
                val data = String(messageEvent.data)
                Log.d("KairosPhone", "🚨 CRISIS recibida del reloj: $data")
                // Acá va el SMS y la notificación
            }
            "/kairos/prealerta" -> {
                val data = String(messageEvent.data)
                Log.d("KairosPhone", "⚠️ Pre-alerta recibida: $data")
                // Acá va la pantalla ¿Estás bien?
            }
            "/kairos/hr" -> {
                // Solo para bitácora, no urgente
                val bpm = String(messageEvent.data).toDoubleOrNull()
                Log.d("KairosPhone", "HR para bitácora: $bpm BPM")
            }
        }
    }

    override fun onPeerConnected(peer: com.google.android.gms.wearable.Node) {
        Log.d("KairosPhone", "Reloj conectado: ${peer.displayName}")
    }

    override fun onPeerDisconnected(peer: com.google.android.gms.wearable.Node) {
        Log.d("KairosPhone", "Reloj desconectado: ${peer.displayName}")
    }
}