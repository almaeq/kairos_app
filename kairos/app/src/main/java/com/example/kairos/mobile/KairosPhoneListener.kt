package com.example.kairos.mobile

import android.content.Intent
import android.util.Log
import com.example.kairos.mobile.data.db.BaselineStats
import com.example.kairos.mobile.data.db.KairosDatabase
import com.example.kairos.mobile.techniques.BreathingState
import com.example.kairos.mobile.techniques.GroundingState
import com.example.kairos.ui.BreathingActivity
import com.example.kairos.ui.CrisisAlertActivity
import com.example.kairos.ui.GroundingActivity
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

            "/kairos/baseline" -> {
                val data    = String(messageEvent.data)
                val hrMean  = data.substringAfter("hrMean=").substringBefore(",").toDoubleOrNull() ?: 0.0
                val hrM2    = data.substringAfter("hrM2=").substringBefore(",").toDoubleOrNull() ?: 0.0
                val hrCount = data.substringAfter("hrCount=").substringBefore(",").toIntOrNull() ?: 0
                val hrvMean = data.substringAfter("hrvMean=").substringBefore(",").toDoubleOrNull() ?: 0.0
                val hrvM2   = data.substringAfter("hrvM2=").substringBefore(",").toDoubleOrNull() ?: 0.0
                val hrvCount = data.substringAfter("hrvCount=").substringBefore(",").toIntOrNull() ?: 0
                val cal     = data.substringAfter("cal=").toIntOrNull() ?: 0

                Log.d("KairosPhone", "Baseline recibido — HR=$hrMean RMSSD=$hrvMean cal=$cal")

                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    KairosDatabase.getInstance(applicationContext).kairosDao().saveBaseline(
                        BaselineStats(
                            hrCount = hrCount,
                            hrMean = hrMean,
                            hrM2 = hrM2,
                            hrvCount = hrvCount,
                            hrvMean = hrvMean,
                            hrvM2 = hrvM2,
                            calibrationWindows = cal,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    MonitorState.preloadCalibration(cal)
                }
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
            "/kairos/grounding/paso" -> {
                val paso = String(messageEvent.data).toIntOrNull() ?: return
                GroundingState.updateStep(paso)
                if (paso in 1..5) GroundingActivity.launch(applicationContext)
                Log.d("KairosPhone", "Grounding paso $paso")
            }
            "/kairos/breathing/update" -> {
                val data  = String(messageEvent.data)
                val phase = data.substringAfter("phase=").substringBefore(",")
                val cycle = data.substringAfter("cycle=").toIntOrNull() ?: 0
                BreathingState.updatePhase(phase, cycle)
                // Abrir la pantalla la primera vez que llega un update (cycle == 1, phase == "Inhalá")
                if (cycle == 1 && phase == "Inhalá") {
                    BreathingActivity.launch(applicationContext)
                }
                Log.d("KairosPhone", "Breathing update — phase=$phase cycle=$cycle")
            }

            "/kairos/breathing/done" -> {
                Log.d("KairosPhone", "Breathing completado")
                BreathingState.markDone()
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