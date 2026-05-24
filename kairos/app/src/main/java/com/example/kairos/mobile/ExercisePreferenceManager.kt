package com.example.kairos.mobile

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

enum class ExercisePreference(val key: String, val label: String, val description: String) {
    BREATHING_ONLY(
        key         = "breathing_only",
        label       = "Solo respiración",
        description = "Técnica 4-4-4: inhalá, retené y exhalá en ciclos de 4 segundos"
    ),
    GROUNDING_ONLY(
        key         = "grounding_only",
        label       = "Solo grounding",
        description = "Técnica 5-4-3-2-1: anclate en el presente usando tus sentidos"
    ),
    BOTH(
        key         = "both",
        label       = "Ambas (recomendado)",
        description = "Primero respiración 4-4-4, luego grounding 5-4-3-2-1"
    )
}

object ExercisePreferenceManager {

    private const val TAG   = "ExercisePrefs"
    private const val PREFS = "kairos_exercise_prefs"
    private const val KEY   = "exercise_preference"
    private const val WEARABLE_PATH = "/kairos/exercise_preference"

    fun save(context: Context, preference: ExercisePreference) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, preference.key)
            .apply()
        Log.d(TAG, "Preferencia guardada: ${preference.key}")
    }

    fun load(context: Context): ExercisePreference {
        val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, ExercisePreference.BOTH.key)
        return ExercisePreference.entries.find { it.key == key } ?: ExercisePreference.BOTH
    }

    /**
     * Sincroniza la preferencia al reloj via Wearable DataClient.
     * Llamar después de guardar desde el teléfono.
     */
    suspend fun syncToWatch(context: Context, preference: ExercisePreference) {
        try {
            val request = PutDataMapRequest.create(WEARABLE_PATH).apply {
                dataMap.putString(KEY, preference.key)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }
            Wearable.getDataClient(context)
                .putDataItem(request.asPutDataRequest().setUrgent())
                .await()
            Log.d(TAG, "Preferencia sincronizada al reloj: ${preference.key}")
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando al reloj: ${e.message}")
        }
    }
}