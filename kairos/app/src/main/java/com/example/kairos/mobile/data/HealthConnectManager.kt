package com.example.kairos.mobile.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.kairos.mobile.detection.WesadThresholds
import java.time.Instant

/**
 * Acceso a Health Connect para lectura de señales biomédicas.
 *
 * Cubre US#3895 — la app detecta variaciones en métricas biomédicas
 * leyendo HeartRateRecord y StepsRecord desde el repositorio compartido.
 *
 * Nota de arquitectura:
 *   Health Connect es el repositorio compartido entre la app móvil y
 *   la app Wear OS. El smartwatch escribe los datos; esta clase los lee.
 *   Para monitoreo en tiempo real (< 2s latencia) ver:
 *   wearos/sensors/PassiveMonitorClient.kt
 */
class HealthConnectManager(private val context: Context) {

    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    companion object {
        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class)
        )

        fun isAvailable(context: Context): Boolean =
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasAllPermissions(): Boolean =
        client.permissionController
            .getGrantedPermissions()
            .containsAll(REQUIRED_PERMISSIONS)

    /**
     * Lee muestras de HeartRateRecord en la ventana WESAD (60s por defecto).
     * Retorna lista plana de samples para calcular HR y RMSSD.
     */
    suspend fun readHeartRateSamples(
        windowSeconds: Long = 3600L
    ): List<HeartRateRecord.Sample> {
        val end   = Instant.now()
        val start = end.minusSeconds(windowSeconds)

        Log.d("KAIROS_HC", "Ahora UTC: $end")
        Log.d("KAIROS_HC", "Buscando desde: $start")

        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType      = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            Log.d("KAIROS_HC", "Records encontrados: ${response.records.size}")
            response.records.flatMap { it.samples }
        } catch (e: Exception) {
            Log.e("KAIROS_HC", "Error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Lee pasos en la ventana como proxy de actividad física.
     * Se usa en Capa 3 del detector cuando el ACC del reloj no está disponible.
     */
    suspend fun readStepsInWindow(
        windowSeconds: Long = 3600L
    ): Long {
        val end   = Instant.now()
        val start = end.minusSeconds(windowSeconds)
        return try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType      = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            ).records.sumOf { it.count }
        } catch (e: Exception) {
            Log.e("KAIROS_HC", "Error pasos: ${e.message}")
            0L
        }
    }
}