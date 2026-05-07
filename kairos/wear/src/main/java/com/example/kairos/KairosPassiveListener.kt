package com.example.kairos

import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class KairosPassiveListener : PassiveListenerService() {

    init {
        Log.d("KairosWatch", "KairosPassiveListener instanciado")
    }

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        Log.d("KairosWatch", "onNewDataPointsReceived llamado")
        val hrData = dataPoints.getData(DataType.HEART_RATE_BPM)
        Log.d("KairosWatch", "HR data size: ${hrData.size}")
        hrData.lastOrNull()?.value?.let { bpm ->
            Log.d("KairosWatch", "❤️ HR pasivo: $bpm BPM")
            sendToPhone(bpm)
        }
    }

    private fun sendToPhone(bpm: Double) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val nodes = Wearable.getNodeClient(this@KairosPassiveListener)
                    .connectedNodes.await()
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@KairosPassiveListener)
                        .sendMessage(node.id, "/kairos/hr", bpm.toString().toByteArray())
                        .await()
                    Log.d("KairosWatch", "✅ HR enviada: $bpm → ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e("KairosWatch", "Error enviando HR: ${e.message}")
            }
        }
    }
}