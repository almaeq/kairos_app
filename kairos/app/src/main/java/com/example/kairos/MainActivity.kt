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
    private val detector    = CrisisDetector()
    private val detectorSim = CrisisDetector()

    private val requestPermissions =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            if (grants.values.all { it }) {
                Log.d("KAIROS", "✅ Permisos otorgados — iniciando prueba")
                runTest()
            } else {
                Log.e("KAIROS", "❌ Permisos denegados")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(android.R.layout.simple_list_item_1)

        // ── Inicializar Room y cargar baseline ────────────────────────────────
        val db = KairosDatabase.getInstance(this)
        baselineRepo = BaselineRepository(db.kairosDao())

        lifecycleScope.launch {
            detector.loadBaseline(baselineRepo)
            Log.d("KAIROS", "Baseline cargado: ${detector.getCalibrationStatus()}")
        }

        if (isEmulator()) {
            Log.d("KAIROS", "📱 Emulador detectado — corriendo test simulado")
            lifecycleScope.launch { runSimulatedTests() }
            return
        }

        if (!HealthConnectManager.isAvailable(this)) {
            Log.e("KAIROS", "❌ Health Connect no disponible")
            lifecycleScope.launch { runSimulatedTests() }
            return
        }

        healthConnect = HealthConnectManager(this)

        lifecycleScope.launch {
            if (healthConnect.hasAllPermissions()) {
                Log.d("KAIROS", "✅ Ya tiene permisos — iniciando prueba")
                runTest()
            } else {
                requestPermissions.launch(arrayOf(
                    "android.permission.health.READ_HEART_RATE",
                    "android.permission.health.WRITE_HEART_RATE",
                    "android.permission.health.READ_STEPS"
                ))
            }
        }
    }

    // ── Test con sensor real (Pixel Watch) ───────────────────────────────────

    private fun runTest() {
        lifecycleScope.launch {
            Log.d("KAIROS", "--- Iniciando ciclo de prueba ---")

            val steps   = healthConnect.readStepsInWindow(windowSeconds = 3600L)
            val samples = healthConnect.readHeartRateSamples(windowSeconds = 3600L)

            Log.d("KAIROS", "Pasos reales del reloj: $steps")
            Log.d("KAIROS", "Muestras HR obtenidas: ${samples.size}")

            if (samples.isNotEmpty()) {
                Log.d("KAIROS", "── Test con datos REALES ──")
                val result = detector.analyze(
                    hrSamples              = samples,
                    stepsInWindow          = steps,
                    accelerometerMagnitude = 0.0
                )
                if (result != null) {
                    Log.d("KAIROS", "Resultado: ${result.toLogString()}")
                    Log.d("KAIROS", "  HR superado (Z>2.5σ): ${result.hrThresholdExceeded}")
                    Log.d("KAIROS", "  HRV superado (Z<-2.5σ): ${result.hrvThresholdExceeded}")
                    Log.d("KAIROS", "  En reposo: ${result.movementFilterPassed}")
                    Log.d("KAIROS", "  Calibración: ${detector.getCalibrationStatus()}")
                    Log.d("KAIROS", "  CRISIS DETECTADA: ${result.isCrisisDetected}")
                }
            }

            // ── Tests simulados Z-Score ───────────────────────────────────────
            Log.d("KAIROS", "")
            Log.d("KAIROS", "── Tests simulados Z-Score (pasos reales: $steps) ──")

            // Fase 1: calibración — usa detectorSim separado
            Log.d("KAIROS", "── Fase 1: Calibración ──")
            repeat(3) { v ->
                val r = detectorSim.analyze(buildSamples(70L), stepsInWindow = 5L)
                Log.d("KAIROS", "  Calibración $v → ${detectorSim.getCalibrationStatus()}")
                Log.d("KAIROS", "  HR=${r?.averageHrBpm?.let { "%.1f".format(it) }} | RMSSD=${r?.rmssdMs?.let { "%.1f".format(it) }}")
            }

            // Guardar baseline del test simulado en Room para validar persistencia
            detectorSim.saveBaseline(baselineRepo)
            Log.d("KAIROS", "✅ Baseline simulado guardado: ${detectorSim.getCalibrationStatus()}")

            // Fase 2: ejercicio
            Log.d("KAIROS", "── Fase 2: Ejercicio ──")
            repeat(2) { v ->
                val r = detectorSim.analyze(
                    hrSamples              = buildSamples(95L),
                    stepsInWindow          = 120L,
                    accelerometerMagnitude = 0.20
                )
                Log.d("KAIROS", "  Ventana $v → Crisis=${r?.isCrisisDetected} (esperado: false)")
                Log.d("KAIROS", "  En reposo: ${r?.movementFilterPassed} ← filtrado por ACC ✅")
            }

            detectorSim.resetConsecutiveCount()

            // Fase 3: crisis
            Log.d("KAIROS", "── Fase 3: Crisis ──")
            repeat(3) { v ->
                val r = detectorSim.analyze(
                    hrSamples              = buildSamples(90L),
                    stepsInWindow          = 2L,
                    accelerometerMagnitude = 0.03
                )
                Log.d("KAIROS", "  Ventana $v → ${r?.toLogString()}")
                Log.d("KAIROS", "  HR superado (Z>2.5σ): ${r?.hrThresholdExceeded}")
                Log.d("KAIROS", "  HRV superado (Z<-2.5σ): ${r?.hrvThresholdExceeded}")
                Log.d("KAIROS", "  En reposo: ${r?.movementFilterPassed}")
                Log.d("KAIROS", "  >>> CRISIS: ${r?.isCrisisDetected} ${if (v >= 1) "(esperado: true ✅)" else "(esperado: false)"}")
            }

            Log.d("KAIROS", "========================================")
            Log.d("KAIROS", "  Tests finalizados")
            Log.d("KAIROS", "========================================")

            KairosMonitorService.start(this@MainActivity)
            Log.d("KAIROS", "✅ Servicio iniciado")
        }
    }

    // ── Test simulado (emulador o sin Health Connect) ────────────────────────

    private fun runSimulatedTests() {
        Log.d("KAIROS", "========================================")
        Log.d("KAIROS", "  TEST Z-SCORE — sin sensor real")
        Log.d("KAIROS", "========================================")

        // Fase 1: calibración
        Log.d("KAIROS", "── Fase 1: Calibración baseline ──")
        repeat(3) { v ->
            val r = detectorSim.analyze(buildSamples(70L), stepsInWindow = 5L)
            Log.d("KAIROS", "  Calibración $v → ${detectorSim.getCalibrationStatus()}")
            Log.d("KAIROS", "  HR=${r?.averageHrBpm?.let { "%.1f".format(it) }} | RMSSD=${r?.rmssdMs?.let { "%.1f".format(it) }}")
        }

        // Guardar baseline del test simulado
        lifecycleScope.launch {
            detectorSim.saveBaseline(baselineRepo)
            Log.d("KAIROS", "✅ Baseline simulado guardado: ${detectorSim.getCalibrationStatus()}")
        }

        // Fase 2: ejercicio
        Log.d("KAIROS", "── Fase 2: Ejercicio (falso positivo) ──")
        repeat(2) { v ->
            val r = detectorSim.analyze(
                hrSamples              = buildSamples(95L),
                stepsInWindow          = 120L,
                accelerometerMagnitude = 0.20
            )
            Log.d("KAIROS", "  Ventana $v → Crisis=${r?.isCrisisDetected} (esperado: false)")
            Log.d("KAIROS", "  En reposo: ${r?.movementFilterPassed} ← filtrado por ACC ✅")
        }

        detectorSim.resetConsecutiveCount()

        // Fase 3: crisis
        Log.d("KAIROS", "── Fase 3: Crisis real ──")
        repeat(3) { v ->
            val r = detectorSim.analyze(
                hrSamples              = buildSamples(90L),
                stepsInWindow          = 2L,
                accelerometerMagnitude = 0.03
            )
            Log.d("KAIROS", "  Ventana $v → ${r?.toLogString()}")
            Log.d("KAIROS", "  HR superado (Z>2.5σ): ${r?.hrThresholdExceeded}")
            Log.d("KAIROS", "  HRV superado (Z<-2.5σ): ${r?.hrvThresholdExceeded}")
            Log.d("KAIROS", "  En reposo: ${r?.movementFilterPassed}")
            Log.d("KAIROS", "  >>> CRISIS: ${r?.isCrisisDetected} ${if (v >= 1) "(esperado: true ✅)" else "(esperado: false)"}")
        }

        Log.d("KAIROS", "========================================")
        Log.d("KAIROS", "  Tests finalizados")
        Log.d("KAIROS", "========================================")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildSamples(bpm: Long, count: Int = 10): List<HeartRateRecord.Sample> {
        val now    = Instant.now()
        val random = java.util.Random()
        return (0 until count).map { i ->
            val variation = (random.nextInt(11) - 5).toLong()
            HeartRateRecord.Sample(
                time           = now.minusSeconds((count - i) * 6L),
                beatsPerMinute = (bpm + variation).coerceIn(50L, 200L)
            )
        }
    }

    private fun isEmulator(): Boolean =
        android.os.Build.BRAND.startsWith("generic")       ||
                android.os.Build.DEVICE.startsWith("generic")      ||
                android.os.Build.FINGERPRINT.startsWith("generic") ||
                android.os.Build.FINGERPRINT.startsWith("unknown") ||
                android.os.Build.HARDWARE.contains("goldfish")     ||
                android.os.Build.HARDWARE.contains("ranchu")       ||
                android.os.Build.MODEL.contains("Emulator")        ||
                android.os.Build.MODEL.contains("Android SDK")     ||
                android.os.Build.PRODUCT.contains("sdk")           ||
                android.os.Build.PRODUCT.contains("emulator")
}