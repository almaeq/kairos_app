package com.example.kairos.mobile.episodeRegister

import android.content.Context
import android.util.Log
import com.example.kairos.mobile.data.db.CrisisEpisode
import com.example.kairos.mobile.data.db.KairosDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Singleton que gestiona el ciclo de vida completo de un episodio de crisis.
 *
 * Un episodio comienza cuando el detector confirma una crisis ([onCrisisDetected])
 * y termina cuando el usuario lo confirma ([onCrisisConfirmed]) o lo cancela
 * ([onCrisisCancelled]). Al cerrar, persiste el episodio en Room via [KairosDatabase].
 *
 * **Patrón de captura de variables antes del coroutine:**
 * `save()` captura los valores de HR, RMSSD y timestamp en variables locales
 * *antes* de lanzar el coroutine de IO. Esto evita una race condition donde
 * `reset()` limpia los campos del objeto mientras el coroutine aún los está leyendo.
 *
 * **Recuperación ante reinicio de proceso:**
 * Si el proceso de la app se reinicia entre la detección y la confirmación,
 * [onCrisisConfirmed] y [onCrisisCancelled] recuperan los datos del episodio
 * desde SharedPreferences como fallback.
 */
object EpisodeTracker {

    private const val TAG = "EpisodeTracker"

    /** Timestamp de inicio del episodio en milisegundos (epoch). */
    private var episodeStartMs = 0L

    /** HR media registrada al momento de la detección, en BPM. */
    private var episodeHr = 0.0

    /** RMSSD registrado al momento de la detección, en milisegundos. */
    private var episodeRmssd = 0.0

    /** Indica si hay un episodio actualmente abierto (detectado pero no cerrado). */
    private var isOpen = false

    /**
     * Abre un nuevo episodio cuando el detector confirma una crisis.
     *
     * Si ya hay un episodio abierto (por ejemplo, por mensajes duplicados desde
     * el reloj), ignora la llamada para evitar sobreescribir el timestamp original.
     *
     * @param hr HR media en BPM registrada en la ventana de detección.
     * @param rmssd RMSSD en milisegundos registrado en la ventana de detección.
     */
    fun onCrisisDetected(hr: Double, rmssd: Double) {
        if (isOpen) {
            Log.d(TAG, "Episodio ya abierto — ignorando detección duplicada")
            return
        }
        episodeStartMs = System.currentTimeMillis()
        episodeHr      = hr
        episodeRmssd   = rmssd
        isOpen         = true
        Log.d(TAG, "Episodio abierto — HR=$hr RMSSD=$rmssd timestamp=$episodeStartMs")
    }

    /**
     * Cierra el episodio como crisis confirmada y lo persiste en Room.
     *
     * Se invoca cuando el usuario no cancela la pre-alerta en el tiempo límite,
     * indicando que la crisis es real. El episodio se guarda con
     * [CrisisEpisode.wasConfirmed] = `true`.
     *
     * Si el proceso se reinició entre la detección y la confirmación,
     * recupera los datos del episodio desde SharedPreferences antes de guardar.
     *
     * @param context Contexto necesario para acceder a Room y SharedPreferences.
     */
    fun onCrisisConfirmed(context: Context) {
        if (!isOpen) {
            // El proceso se reinició entre la detección y la confirmación
            // Recuperamos los datos del episodio desde SharedPreferences
            val prefs = context.getSharedPreferences("kairos_crisis", Context.MODE_PRIVATE)
            episodeHr      = prefs.getFloat("last_hr", 0f).toDouble()
            episodeRmssd   = prefs.getFloat("last_rmssd", 0f).toDouble()
            episodeStartMs = prefs.getLong("last_timestamp", System.currentTimeMillis())
            isOpen         = true
            Log.w(TAG, "Episodio recuperado de SharedPreferences — HR=$episodeHr")
        }
        val durationSecs = ((System.currentTimeMillis() - episodeStartMs) / 1000).toInt()
        save(context, durationSecs, wasConfirmed = true)
        Log.d(TAG, "Episodio confirmado — HR=$episodeHr RMSSD=$episodeRmssd duración: ${durationSecs}s")
        reset()
    }

    /**
     * Cierra el episodio como falso positivo y lo persiste en Room.
     *
     * Se invoca cuando el usuario cancela la pre-alerta, indicando que no hay crisis.
     * El episodio se guarda con [CrisisEpisode.wasConfirmed] = `false` para
     * permitir análisis posterior de falsos positivos.
     *
     * Si el proceso se reinició entre la detección y la cancelación,
     * recupera los datos del episodio desde SharedPreferences antes de guardar.
     *
     * @param context Contexto necesario para acceder a Room y SharedPreferences.
     */
    fun onCrisisCancelled(context: Context) {
        if (!isOpen) {
            // El proceso se reinició entre la detección y la cancelación
            // Recuperamos los datos del episodio desde SharedPreferences
            val prefs = context.getSharedPreferences("kairos_crisis", Context.MODE_PRIVATE)
            episodeHr      = prefs.getFloat("last_hr", 0f).toDouble()
            episodeRmssd   = prefs.getFloat("last_rmssd", 0f).toDouble()
            episodeStartMs = prefs.getLong("last_timestamp", System.currentTimeMillis())
            isOpen         = true
            Log.w(TAG, "Episodio recuperado de SharedPreferences — HR=$episodeHr")
        }
        val durationSecs = ((System.currentTimeMillis() - episodeStartMs) / 1000).toInt()
        save(context, durationSecs, wasConfirmed = false)
        Log.d(TAG, "Episodio cancelado — HR=$episodeHr RMSSD=$episodeRmssd duración: ${durationSecs}s")
        reset()
    }

    /**
     * Persiste el episodio en Room en un coroutine de IO.
     *
     * **Importante:** los valores de HR, RMSSD y timestamp se capturan en variables
     * locales (`hr`, `rmssd`, `timestamp`) *antes* de lanzar el coroutine.
     * Esto evita una race condition donde [reset] limpia los campos del objeto
     * mientras el coroutine de IO aún los está leyendo.
     *
     * Después de insertar el episodio, invoca [KairosDao.keepLatestCancelledEpisodes]
     * para mantener acotado el tamaño de la base de datos.
     *
     * @param context Contexto necesario para obtener la instancia de [KairosDatabase].
     * @param durationSecs Duración del episodio en segundos.
     * @param wasConfirmed `true` si el usuario confirmó la crisis, `false` si la canceló.
     */
    private fun save(context: Context, durationSecs: Int, wasConfirmed: Boolean) {
        // Capturamos los valores ANTES de lanzar el coroutine para evitar
        // la race condition con reset() que limpia los campos del objeto
        val hr        = episodeHr
        val rmssd     = episodeRmssd
        val timestamp = episodeStartMs

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = KairosDatabase.getInstance(context).kairosDao()
                dao.insertEpisode(
                    CrisisEpisode(
                        timestamp       = timestamp,
                        hrBpm           = hr,
                        rmssdMs         = rmssd,
                        durationSeconds = durationSecs,
                        wasConfirmed    = wasConfirmed
                    )
                )
                dao.keepLatestCancelledEpisodes()
                Log.d(TAG, "Episodio guardado en Room ✅ HR=$hr RMSSD=$rmssd")
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando episodio: ${e.message}")
            }
        }
    }

    /**
     * Resetea el estado interno del tracker al finalizar un episodio.
     *
     * Debe llamarse siempre después de [save] para que el tracker quede listo
     * para el próximo episodio. Los valores se limpian a sus defaults seguros.
     */
    private fun reset() {
        episodeStartMs = 0L
        episodeHr      = 0.0
        episodeRmssd   = 0.0
        isOpen         = false
    }
}