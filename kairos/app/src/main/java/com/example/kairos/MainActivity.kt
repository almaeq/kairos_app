package com.example.kairos

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.records.HeartRateRecord
import androidx.lifecycle.lifecycleScope
import com.example.kairos.mobile.data.BaselineRepository
import com.example.kairos.mobile.data.HealthConnectManager
import com.example.kairos.mobile.data.db.KairosDatabase
import com.example.kairos.mobile.detection.CrisisDetector
import com.example.kairos.mobile.detection.KairosMonitorService
import kotlinx.coroutines.launch
import java.time.Instant

class MainActivity : ComponentActivity() {

    private lateinit var healthConnect: HealthConnectManager
    private lateinit var baselineRepo: BaselineRepository
    private val detector = CrisisDetector()

    private val requestPermissions =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            if (grants.values.all { it }) {
                Log.d("KAIROS", "✅ Permisos otorgados")
                startMonitoring()
            } else {
                Log.e("KAIROS", "❌ Permisos denegados")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cargar baseline guardado por CalibrationActivity
        val db = KairosDatabase.getInstance(this)
        baselineRepo = BaselineRepository(db.kairosDao())

        lifecycleScope.launch {
            detector.loadBaseline(baselineRepo)
            Log.d("KAIROS", "Baseline cargado: ${detector.getCalibrationStatus()}")
        }

        if (!HealthConnectManager.isAvailable(this)) {
            Log.e("KAIROS", "❌ Health Connect no disponible")
            return
        }

        healthConnect = HealthConnectManager(this)

        lifecycleScope.launch {
            if (healthConnect.hasAllPermissions()) {
                startMonitoring()
            } else {
                requestPermissions.launch(arrayOf(
                    "android.permission.health.READ_HEART_RATE",
                    "android.permission.health.WRITE_HEART_RATE",
                    "android.permission.health.READ_STEPS"
                ))
            }
        }
    }

    private fun startMonitoring() {
        KairosMonitorService.start(this)
        Log.d("KAIROS", "✅ Monitoreo iniciado")
    }
}