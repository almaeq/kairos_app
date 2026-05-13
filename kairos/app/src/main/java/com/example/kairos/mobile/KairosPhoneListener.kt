package com.example.kairos.mobile

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class KairosPhoneListener : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/kairos/ping" -> {
                Log.d("KairosPhone", "✅ Ping recibido del reloj")
            }
            "/kairos/hr" -> {
                val bpm = String(messageEvent.data).toDoubleOrNull() ?: return
                Log.d("KairosPhone", "HR para bitácora: $bpm BPM")
                MonitorState.updateHr(bpm)
            }
            "/kairos/prealerta" -> {
                val data = String(messageEvent.data)
                Log.d("KairosPhone", "⚠️ Pre-alerta recibida: $data")
                val bpm = data.substringAfter("hr=").toDoubleOrNull() ?: 0.0
                MonitorState.updateFromWear(
                    bpm = bpm,
                    rmssd = 0.0,
                    state = CrisisState.PRE_ALERT,
                    calibrationWindows = MonitorState.data.value.calibrationWindows
                )
            }
            "/kairos/crisis" -> {
                val data = String(messageEvent.data)
                Log.d("KairosPhone", "🚨 CRISIS recibida del reloj: $data")
                val bpm = data.substringAfter("hr=").substringBefore(",").toDoubleOrNull() ?: 0.0
                val rmssd = data.substringAfter("rmssd=").toDoubleOrNull() ?: 0.0
                MonitorState.updateFromWear(
                    bpm = bpm,
                    rmssd = rmssd,
                    state = CrisisState.CRISIS,
                    calibrationWindows = MonitorState.data.value.calibrationWindows
                )
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