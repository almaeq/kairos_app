package com.example.kairos.services

import android.util.Log
import com.example.kairos.techniques.WatchExercisePrefs
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class KairosDataListener : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == "/kairos/exercise_preference") {

                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val key     = dataMap.getString("exercise_preference") ?: return@forEach

                WatchExercisePrefs.save(this, key)
                Log.d("KairosDataListener", "Preferencia recibida del teléfono: $key")
            }
        }
    }
}