package com.example.kairos.mobile.data

import android.util.Log
import com.example.kairos.mobile.data.db.BaselineStats
import com.example.kairos.mobile.data.db.KairosDao
import java.time.Instant

/**
 * Repositorio de baseline — persiste el Z-Score personal del usuario.
 * Cubre US#4010 — KAIROS aprende el baseline y lo recuerda entre sesiones.
 */
class BaselineRepository(private val dao: KairosDao) {

    suspend fun save(
        hrCount: Int, hrMean: Double, hrM2: Double,
        hrvCount: Int, hrvMean: Double, hrvM2: Double,
        calibrationWindows: Int
    ) {
        dao.saveBaseline(
            BaselineStats(
                hrCount            = hrCount,
                hrMean             = hrMean,
                hrM2               = hrM2,
                hrvCount           = hrvCount,
                hrvMean            = hrvMean,
                hrvM2              = hrvM2,
                calibrationWindows = calibrationWindows,
                updatedAt          = Instant.now().toEpochMilli()
            )
        )
        Log.d("BaselineRepo", "Baseline guardado — calibración: $calibrationWindows/3")
    }

    suspend fun load(): BaselineStats? = dao.getBaseline()

    suspend fun isCalibrated(): Boolean = (dao.getBaseline()?.calibrationWindows ?: 0) >= 3
}