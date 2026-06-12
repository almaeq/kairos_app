package com.example.kairos.mobile.episodeRegister

import android.content.Context
import android.util.Log
import com.example.kairos.mobile.data.db.CrisisEpisode
import com.example.kairos.mobile.data.db.KairosDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object EpisodeTracker {

    private const val TAG = "EpisodeTracker"

    private var episodeStartMs = 0L
    private var episodeHr      = 0.0
    private var episodeRmssd   = 0.0
    private var isOpen         = false

    fun onCrisisDetected(hr: Double, rmssd: Double) {
        if (isOpen) {
            Log.d(TAG, "Episodio ya abierto — ignorando")
            return
        }
        episodeStartMs = System.currentTimeMillis()
        episodeHr      = hr
        episodeRmssd   = rmssd
        isOpen         = true
        Log.d(TAG, "Episodio abierto — HR=$hr RMSSD=$rmssd timestamp=$episodeStartMs")
    }

    fun onCrisisConfirmed(context: Context) {
        if (!isOpen) {
            // Recuperar de SharedPreferences si el proceso se reinició
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

    fun onCrisisCancelled(context: Context) {
        if (!isOpen) {
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

    private fun save(context: Context, durationSecs: Int, wasConfirmed: Boolean) {
        // Capturar los valores AHORA antes de que reset() los limpie
        val hr        = episodeHr
        val rmssd     = episodeRmssd
        val timestamp = episodeStartMs

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                KairosDatabase.getInstance(context).kairosDao().insertEpisode(
                    CrisisEpisode(
                        timestamp       = timestamp,
                        hrBpm           = hr,
                        rmssdMs         = rmssd,
                        durationSeconds = durationSecs,
                        wasConfirmed    = wasConfirmed
                    )
                )
                KairosDatabase.getInstance(context).kairosDao().keepLatestCancelledEpisodes()
                Log.d(TAG, "Episodio guardado en Room ✅ HR=$hr RMSSD=$rmssd")
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando episodio: ${e.message}")
            }
        }
    }

    private fun reset() {
        episodeStartMs = 0L
        episodeHr      = 0.0
        episodeRmssd   = 0.0
        isOpen         = false
    }
}