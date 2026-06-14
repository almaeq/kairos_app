package com.example.kairos.mobile.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Entidad Room que representa un episodio de crisis de ansiedad detectado por KAIROS.
 *
 * Cada registro corresponde a un evento donde el algoritmo de detección superó
 * el umbral de crisis. Se almacenan tanto los episodios confirmados por el usuario
 * como los descartados, para permitir análisis posterior y generación de reportes.
 *
 * La base de datos mantiene un máximo de 50 episodios mediante [keepLatestEpisodes()],
 * eliminando los más antiguos cuando se supera ese límite.
 *
 * @property id Clave primaria autogenerada por Room.
 * @property timestamp Momento en que se detectó la crisis, en milisegundos (epoch).
 * @property hrBpm Frecuencia cardíaca media en BPM registrada durante el episodio.
 * @property rmssdMs Variabilidad cardíaca (RMSSD) en milisegundos registrada durante el episodio.
 * @property durationSeconds Duración total del episodio en segundos,
 *           desde la detección hasta que el usuario finalizó el modo crisis.
 * @property wasConfirmed Indica si el usuario confirmó el episodio como crisis real.
 *           `true` = crisis confirmada. `false` = descartada como falso positivo
 *           (el usuario canceló en la pantalla de pre-alerta).
 * @property notes Anotaciones opcionales ingresadas por el usuario al finalizar el episodio.
 *           Puede incluir contexto, desencadenantes o estado emocional posterior.
 */
@Entity(tableName = "crisis_episodes")
data class CrisisEpisode(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = Instant.now().toEpochMilli(),
    val hrBpm: Double,
    val rmssdMs: Double,
    val durationSeconds: Int = 0,
    val wasConfirmed: Boolean = false,
    val notes: String = ""
)