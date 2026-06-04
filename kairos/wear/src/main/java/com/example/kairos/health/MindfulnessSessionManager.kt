package com.example.kairos.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneOffset

object MindfulnessSessionManager {

    private const val TAG = "MindfulnessSession"

    /**
     * Registra una sesión de intervención en Health Connect.
     *
     * @param context       Contexto de la aplicación
     * @param startTime     Timestamp de inicio (epoch ms)
     * @param endTime       Timestamp de fin (epoch ms)
     * @param sessionType   Tipo de sesión — usar las constantes de MindfulnessSessionRecord
     * @param title         Título descriptivo que aparece en Health Connect
     * @param notes         Notas opcionales (ej: "HR al inicio: 134 BPM")
     */
    @OptIn(ExperimentalMindfulnessSessionApi::class)
    suspend fun record(
        context:     Context,
        startTime:   Long,
        endTime:     Long,
        sessionType: Int,
        title:       String,
        notes:       String = ""
    ) {
        try {
            val client = HealthConnectClient.getOrCreate(context)

            // Verificar que el dispositivo soporta MindfulnessSession antes de insertar
            val featureStatus = client.features.getFeatureStatus(
                HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION
            )
            if (featureStatus != HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) {
                Log.w(TAG, "MindfulnessSession no disponible en este dispositivo")
                return
            }

            val record = MindfulnessSessionRecord(
                startTime       = Instant.ofEpochMilli(startTime),
                startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(Instant.ofEpochMilli(startTime)),
                endTime         = Instant.ofEpochMilli(endTime),
                endZoneOffset   = ZoneOffset.systemDefault().rules.getOffset(Instant.ofEpochMilli(endTime)),
                title           = title,
                notes           = notes.ifBlank { null },
                mindfulnessSessionType = sessionType,
                metadata = Metadata.manualEntry()
            )

            client.insertRecords(listOf(record))
            val durationMin = (endTime - startTime) / 60_000
            Log.d(TAG, "Sesión registrada: '$title' — ${durationMin}min")

        } catch (e: Exception) {
            Log.e(TAG, "Error registrando sesión en Health Connect: ${e.message}")
        }
    }
}