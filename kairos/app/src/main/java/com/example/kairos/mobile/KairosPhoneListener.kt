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
            "/kairos/hr" -> {
                val bpm = String(messageEvent.data).toDoubleOrNull() ?: return
                Log.d("KairosPhone", "HR recibida del reloj: $bpm BPM")

                val db = KairosDatabase.getInstance(this)
                val baselineRepo = BaselineRepository(db.kairosDao())

                scope.launch {
                    val detector = CrisisDetector()
                    detector.loadBaseline(baselineRepo)

                    val sample = HeartRateRecord.Sample(
                        time = Instant.now(),
                        beatsPerMinute = bpm.toLong()
                    )

                    val result = detector.analyze(
                        hrSamples = listOf(sample),
                        stepsInWindow = 0L,
                        accelerometerMagnitude = 0.0
                    )

                    if (result?.isCrisisDetected == true) {
                        Log.d("KairosPhone", "🚨 CRISIS DETECTADA — HR: $bpm BPM")
                    } else {
                        Log.d("KairosPhone", "Estado normal — HR: $bpm BPM | HR threshold: ${result?.hrThresholdExceeded}")
                    }
                }
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