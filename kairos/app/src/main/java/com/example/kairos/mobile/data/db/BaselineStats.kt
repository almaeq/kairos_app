package com.example.kairos.mobile.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Entidad Room que almacena los parámetros estadísticos de la línea de base personal del usuario.
 *
 * Implementa el algoritmo de Welford para calcular media y varianza de forma incremental,
 * sin necesidad de guardar el historial completo de ventanas. Esto permite actualizar
 * la calibración en tiempo real con cada nueva ventana de baseline.
 *
 * La tabla siempre contiene exactamente un registro (id = 1), que se sobreescribe
 * en cada actualización. No se acumulan filas históricas.
 *
 * @property id Clave primaria fija (siempre 1). La tabla es un singleton.
 * @property hrCount Cantidad de muestras de HR procesadas hasta el momento.
 * @property hrMean Media acumulada de HR calculada con el algoritmo de Welford.
 * @property hrM2 Suma de diferencias al cuadrado de HR (necesaria para calcular la varianza).
 * @property hrvCount Cantidad de muestras de HRV (RMSSD) procesadas hasta el momento.
 * @property hrvMean Media acumulada de HRV calculada con el algoritmo de Welford.
 * @property hrvM2 Suma de diferencias al cuadrado de HRV (necesaria para calcular la varianza).
 * @property calibrationWindows Número de ventanas de baseline completadas.
 *           El sistema considera la calibración lista cuando este valor alcanza el mínimo requerido.
 * @property updatedAt Timestamp de la última actualización en milisegundos (epoch).
 */
@Entity(tableName = "baseline_stats")
data class BaselineStats(
    @PrimaryKey val id: Int = 1,
    val hrCount: Int = 0,
    val hrMean: Double = 0.0,
    val hrM2: Double = 0.0,
    val hrvCount: Int = 0,
    val hrvMean: Double = 0.0,
    val hrvM2: Double = 0.0,
    val calibrationWindows: Int = 0,
    val updatedAt: Long = Instant.now().toEpochMilli()
)