package com.example.kairos

import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import com.example.kairos.detection.WatchCrisisDetector
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class KairosPassiveListener : PassiveListenerService() {

    private val hrBuffer = mutableListOf<Double>()
    private lateinit var detector: WatchCrisisDetector

    override fun onCreate() {
        super.onCreate()
        // Usar singleton en vez de nueva instancia
        detector = WatchCrisisDetector.getInstance(this)
        Log.d("KairosWatch", "KairosPassiveListener creado — cal: ${detector.calibrationWindows}/3")
    }

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val hrData = dataPoints.getData(DataType.HEART_RATE_BPM)
        if (hrData.isEmpty()) return

        // Agregar todas las muestras al buffer
        hrData.forEach { hrBuffer.add(it.value) }
        Log.d("KairosWatch", "Buffer HR: ${hrBuffer.size} muestras")

        // Analizar cuando tengamos suficientes muestras (~60s de datos)
        if (hrBuffer.size >= 6) {
            val result = detector.analyze(hrBuffer.toList())

            if (result != null) {
                when {
                    result.isCrisisDetected -> {
                        Log.d("KairosWatch", "🚨 CRISIS DETECTADA en reloj")
                        sendToPhone("/kairos/crisis",
                            "hr=${result.averageHrBpm},rmssd=${result.rmssdMs}")
                    }
                    result.isPreAlert -> {
                        Log.d("KairosWatch", "⚠️ PRE-ALERTA en reloj")
                        sendToPhone("/kairos/prealerta",
                            "hr=${result.averageHrBpm}")
                    }
                    else -> {
                        Log.d("KairosWatch", "✅ Normal — HR=${result.averageHrBpm} RMSSD=${result.rmssdMs}")
                    }
                }

                // Ventana deslizante — descartar primeras 3 muestras
                repeat(3) { if (hrBuffer.isNotEmpty()) hrBuffer.removeFirst() }
            }
        }
    }

    private fun sendToPhone(path: String, data: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val nodes = Wearable.getNodeClient(this@KairosPassiveListener)
                    .connectedNodes.await()
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@KairosPassiveListener)
                        .sendMessage(node.id, path, data.toByteArray())
                        .await()
                    Log.d("KairosWatch", "Enviado $path → ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e("KairosWatch", "Error enviando $path: ${e.message}")
            }
        }
    }
}