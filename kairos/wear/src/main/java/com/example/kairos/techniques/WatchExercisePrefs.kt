package com.example.kairos.techniques

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Preferencia de ejercicio de intervención configurada por el usuario desde el teléfono
 * y sincronizada al reloj via Wearable DataClient.
 *
 * @property key Identificador string usado en SharedPreferences y en el payload
 *           del DataClient para sincronización entre dispositivos.
 */
enum class ExercisePreference(val key: String) {
    /** Ejecuta únicamente el ejercicio de respiración box (4-4-4-4). */
    BREATHING_ONLY("breathing_only"),

    /** Ejecuta únicamente el ejercicio de grounding 5-4-3-2-1. */
    GROUNDING_ONLY("grounding_only"),

    /**
     * Ejecuta ambas técnicas en secuencia: respiración 4-4-4-4 seguida de grounding 5-4-3-2-1.
     * Es la opción por defecto y la recomendada.
     */
    BOTH("both")
}

/**
 * Gestiona la preferencia de ejercicio de intervención en el reloj.
 *
 * Persiste el valor en SharedPreferences y lo expone como [StateFlow] para que
 * [MainActivity] del reloj pueda observar cambios en tiempo real sin polling.
 *
 * **Flujo de sincronización:**
 * 1. El usuario cambia la preferencia en el teléfono ([ExercisePreferenceManager.syncToWatch]).
 * 2. [KairosDataListener] recibe el cambio via Wearable DataClient.
 * 3. [KairosDataListener] llama a [save] con la nueva clave.
 * 4. [save] persiste en SharedPreferences y actualiza [preference] para notificar a la UI.
 */
object WatchExercisePrefs {

    private const val PREFS = "kairos_exercise_prefs"
    private const val KEY   = "exercise_preference"

    /**
     * Estado observable de la preferencia actual.
     * Se inicializa con [ExercisePreference.BOTH] y se actualiza al cargar o recibir
     * una sincronización desde el teléfono.
     */
    private val _preference = MutableStateFlow(ExercisePreference.BOTH)

    /**
     * Preferencia actual como Flow de solo lectura.
     * La UI del reloj observa este Flow para saber qué ejercicio ejecutar
     * cuando se activa el Modo Crisis.
     */
    val preference: StateFlow<ExercisePreference> = _preference

    /**
     * Carga la preferencia desde SharedPreferences y actualiza el [StateFlow].
     *
     * Se invoca al iniciar [MainActivity] del reloj para restaurar la preferencia
     * guardada en la sesión anterior. Si no hay valor guardado o el valor no es válido,
     * usa [ExercisePreference.BOTH] como fallback.
     *
     * @param context Contexto para acceder a SharedPreferences.
     * @return Preferencia cargada.
     */
    fun load(context: Context): ExercisePreference {
        val key  = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, ExercisePreference.BOTH.key)
        val pref = ExercisePreference.entries.find { it.key == key } ?: ExercisePreference.BOTH
        _preference.value = pref
        return pref
    }

    /**
     * Persiste la preferencia recibida desde el teléfono y actualiza el [StateFlow].
     *
     * Se invoca desde [KairosDataListener] cuando llega una sincronización via
     * Wearable DataClient. Si la clave no corresponde a ningún valor conocido de
     * [ExercisePreference], usa [ExercisePreference.BOTH] como fallback seguro.
     *
     * @param context Contexto para acceder a SharedPreferences.
     * @param key Clave string de la preferencia (por ejemplo `"breathing_only"`).
     */
    fun save(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, key).apply()
        val pref = ExercisePreference.entries.find { it.key == key } ?: ExercisePreference.BOTH
        _preference.value = pref
        Log.d("WatchExercisePrefs", "Preferencia actualizada: $pref")
    }
}