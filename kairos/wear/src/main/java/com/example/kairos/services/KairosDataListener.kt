package com.example.kairos.services

import android.util.Log
import com.example.kairos.techniques.WatchExercisePrefs
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Servicio que escucha cambios en el DataLayer de Wearable para sincronizar
 * preferencias del teléfono al reloj.
 *
 * El teléfono actualiza la preferencia de ejercicio via [ExercisePreferenceManager.syncToWatch],
 * que escribe en el path `/kairos/exercise_preference` del Wearable DataClient.
 * Este servicio detecta ese cambio y persiste el nuevo valor en [WatchExercisePrefs]
 * (SharedPreferences del reloj) para que [KairosWatchService] lo use en el próximo
 * Modo Crisis sin necesidad de conexión al teléfono.
 *
 * A diferencia de [KairosPassiveListener] (que recibe mensajes puntuales via Message API),
 * este servicio escucha el DataLayer — adecuado para preferencias porque el DataClient
 * garantiza entrega eventual incluso si el reloj no está conectado en el momento del cambio.
 */
class KairosDataListener : WearableListenerService() {

    /**
     * Callback invocado cuando cambia algún item del DataLayer compartido entre
     * el teléfono y el reloj.
     *
     * Filtra los eventos por path `/kairos/exercise_preference` y tipo
     * [DataEvent.TYPE_CHANGED], ignorando cualquier otro cambio en el DataLayer.
     * Al recibir la preferencia, la persiste localmente en [WatchExercisePrefs].
     *
     * @param dataEvents Buffer de eventos del DataLayer recibidos desde el teléfono.
     */
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == "/kairos/exercise_preference") {

                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val key     = dataMap.getString("exercise_preference") ?: return@forEach

                // Persistimos la preferencia localmente para que esté disponible
                // durante el Modo Crisis incluso sin conexión al teléfono
                WatchExercisePrefs.save(this, key)
                Log.d("KairosDataListener", "Preferencia recibida del teléfono: $key")
            }
        }
    }
}