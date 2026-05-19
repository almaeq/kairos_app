package com.example.kairos.mobile

import android.content.Intent
import android.util.Log
import com.example.kairos.ui.CrisisAlertActivity
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KairosPhoneListener : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {

            "/kairos/ping" -> {
                Log.d("KairosPhone", "Ping recibido del reloj")
                MonitorState.setWatchConnected(true)
            }

            // Heartbeat periódico (cada 60s): estado normal del reloj
            "/kairos/heartbeat" -> {
                val data = String(messageEvent.data)
                val bpm   = data.substringAfter("hr=").substringBefore(",").toDoubleOrNull() ?: 0.0
                val rmssd = data.substringAfter("rmssd=").substringBefore(",").toDoubleOrNull() ?: 0.0
                val cal   = data.substringAfter("cal=").toIntOrNull()
                    ?: MonitorState.data.value.calibrationWindows

                Log.d("KairosPhone", "Heartbeat — HR=$bpm RMSSD=$rmssd cal=$cal")
                MonitorState.updateFromWear(
                    bpm                = bpm,
                    rmssd              = rmssd,
                    state              = CrisisState.NORMAL,
                    calibrationWindows = cal
                )
            }

            "/kairos/hr" -> {
                val bpm = String(messageEvent.data).toDoubleOrNull() ?: return
                Log.d("KairosPhone", "HR recibida: $bpm BPM")
                MonitorState.updateHr(bpm)
            }

            "/kairos/prealerta" -> {
                val data = String(messageEvent.data)
                val bpm  = data.substringAfter("hr=").toDoubleOrNull() ?: 0.0
                Log.d("KairosPhone", "Pre-alerta recibida: $data")
                MonitorState.updateFromWear(
                    bpm                = bpm,
                    rmssd              = MonitorState.data.value.rmssd,
                    state              = CrisisState.PRE_ALERT,
                    calibrationWindows = MonitorState.data.value.calibrationWindows
                )
            }

            "/kairos/crisis" -> {
                val data  = String(messageEvent.data)
                val bpm   = data.substringAfter("hr=").substringBefore(",").toDoubleOrNull() ?: 0.0
                val rmssd = data.substringAfter("rmssd=").toDoubleOrNull() ?: 0.0
                Log.d("KairosPhone", "CRISIS recibida: $data")
                MonitorState.updateFromWear(
                    bpm                = bpm,
                    rmssd              = rmssd,
                    state              = CrisisState.CRISIS,
                    calibrationWindows = MonitorState.data.value.calibrationWindows
                )
            }
            // El usuario no respondió en 30s → enviar SMS
            "/kairos/crisis/confirmada" -> {
                Log.d("KairosPhone", "Crisis confirmada — abriendo pantalla de alerta")
                CrisisAlertActivity.launch(applicationContext)
            }

// El usuario tocó "Estoy bien" → cancelar sin SMS
            "/kairos/crisis/cancelada" -> {
                Log.d("KairosPhone", "Crisis cancelada por usuario — sin SMS")
                MonitorState.updateFromWear(
                    bpm                = MonitorState.data.value.heartRate,
                    rmssd              = MonitorState.data.value.rmssd,
                    state              = CrisisState.NORMAL,
                    calibrationWindows = MonitorState.data.value.calibrationWindows
                )
            }
        }
    }

    override fun onPeerConnected(peer: com.google.android.gms.wearable.Node) {
        Log.d("KairosPhone", "Reloj conectado: ${peer.displayName}")
        MonitorState.setWatchConnected(true)
    }

    override fun onPeerDisconnected(peer: com.google.android.gms.wearable.Node) {
        Log.d("KairosPhone", "Reloj desconectado: ${peer.displayName}")
        MonitorState.setWatchConnected(false)
    }
}