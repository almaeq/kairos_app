package com.example.kairos.mobile

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Preferencia del usuario sobre qué técnicas de intervención ejecutar durante una crisis.
 *
 * El usuario puede elegir entre respiración guiada, grounding 5-4-3-2-1, o ambas.
 * Esta preferencia se persiste en SharedPreferences y se sincroniza al reloj
 * para que el Modo Crisis en Wear OS ejecute las técnicas correctas.
 *
 * @property key Identificador interno usado para persistencia en SharedPreferences
 *           y sincronización via Wearable DataClient.
 * @property label Nombre legible para mostrar en la UI de configuración.
 * @property description Descripción breve de la técnica para orientar al usuario
 *           en la pantalla de selección de preferencias.
 */
enum class ExercisePreference(val key: String, val label: String, val description: String) {

    /**
     * Solo ejecuta el ejercicio de respiración box (4-4-4-4) durante la crisis.
     * Recomendado para usuarios que ya conocen la técnica o prefieren intervenciones breves.
     */
    BREATHING_ONLY(
        key         = "breathing_only",
        label       = "Solo respiración",
        description = "Técnica 4-4-4: inhalá, retené y exhalá en ciclos de 4 segundos"
    ),

    /**
     * Solo ejecuta el ejercicio de grounding 5-4-3-2-1 durante la crisis.
     * Recomendado para usuarios que encuentran más útil el anclaje sensorial
     * que la regulación respiratoria.
     */
    GROUNDING_ONLY(
        key         = "grounding_only",
        label       = "Solo grounding",
        description = "Técnica 5-4-3-2-1: anclate en el presente usando tus sentidos"
    ),

    /**
     * Ejecuta ambas técnicas en secuencia: primero respiración, luego grounding.
     * Es la opción por defecto y la recomendada porque combina regulación fisiológica
     * (respiración → sistema parasimpático) con regulación cognitiva (grounding → anclaje presente).
     */
    BOTH(
        key         = "both",
        label       = "Ambas (recomendado)",
        description = "Primero respiración 4-4-4, luego grounding 5-4-3-2-1"
    )
}

/**
 * Gestiona la persistencia y sincronización de la preferencia de ejercicios del usuario.
 *
 * Guarda la preferencia localmente en SharedPreferences y la sincroniza al reloj
 * via Wearable DataClient para que el Modo Crisis en Wear OS ejecute las técnicas
 * correctas sin necesidad de conexión al teléfono en el momento de la crisis.
 */
object ExercisePreferenceManager {

    private const val TAG            = "ExercisePrefs"
    private const val PREFS          = "kairos_exercise_prefs"
    private const val KEY            = "exercise_preference"

    /**
     * Path del DataClient para sincronización de la preferencia al reloj.
     * El reloj escucha cambios en este path via `DataClient.OnDataChangedListener`.
     */
    private const val WEARABLE_PATH  = "/kairos/exercise_preference"

    /**
     * Persiste la preferencia de ejercicios en SharedPreferences.
     *
     * Debe llamarse cada vez que el usuario cambia su preferencia en la pantalla
     * de configuración. Para sincronizar al reloj además de guardar localmente,
     * llamar también a [syncToWatch].
     *
     * @param context Contexto necesario para acceder a SharedPreferences.
     * @param preference Preferencia seleccionada por el usuario.
     */
    fun save(context: Context, preference: ExercisePreference) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, preference.key)
            .apply()
        Log.d(TAG, "Preferencia guardada: ${preference.key}")
    }

    /**
     * Carga la preferencia de ejercicios desde SharedPreferences.
     *
     * Si no hay preferencia guardada o el valor almacenado no corresponde
     * a ningún [ExercisePreference] conocido, retorna [ExercisePreference.BOTH]
     * como valor por defecto.
     *
     * @param context Contexto necesario para acceder a SharedPreferences.
     * @return Preferencia actualmente configurada por el usuario.
     */
    fun load(context: Context): ExercisePreference {
        val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, ExercisePreference.BOTH.key)
        return ExercisePreference.entries.find { it.key == key } ?: ExercisePreference.BOTH
    }

    /**
     * Sincroniza la preferencia de ejercicios al reloj via Wearable DataClient.
     *
     * Usa `setUrgent()` para que el DataClient entregue la actualización
     * con la menor latencia posible, en lugar de diferirla a la próxima
     * sincronización periódica.
     *
     * Incluye un timestamp en el DataMap para forzar la entrega incluso si
     * el valor de la preferencia no cambió — sin el timestamp, el DataClient
     * omite la actualización si el DataItem es idéntico al anterior.
     *
     * Esta función es best-effort: si el reloj no está conectado o falla la
     * sincronización, se registra el error pero no se lanza excepción.
     * El reloj usará la última preferencia sincronizada o el valor por defecto.
     *
     * @param context Contexto necesario para acceder al Wearable DataClient.
     * @param preference Preferencia a sincronizar al reloj.
     */
    suspend fun syncToWatch(context: Context, preference: ExercisePreference) {
        try {
            val request = PutDataMapRequest.create(WEARABLE_PATH).apply {
                dataMap.putString(KEY, preference.key)
                // Timestamp para forzar la entrega aunque el valor no haya cambiado
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }
            Wearable.getDataClient(context)
                .putDataItem(request.asPutDataRequest().setUrgent())
                .await()
            Log.d(TAG, "Preferencia sincronizada al reloj: ${preference.key}")
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando preferencia al reloj: ${e.message}")
        }
    }
}