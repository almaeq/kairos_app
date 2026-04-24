package com.example.kairos

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.records.HeartRateRecord
import androidx.lifecycle.lifecycleScope
import com.example.kairos.mobile.data.HealthConnectManager
import com.example.kairos.mobile.detection.CrisisDetector
import com.example.kairos.mobile.detection.KairosMonitorService
import kotlinx.coroutines.launch
import java.time.Instant

class MainActivity : ComponentActivity() {

    private lateinit var healthConnect: HealthConnectManager
    private val detector = CrisisDetector()

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

            val steps = healthConnect.readStepsInWindow(windowSeconds = 3600L)
            val samples = healthConnect.readHeartRateSamples(windowSeconds = 3600L)

            Log.d("KAIROS", "Pasos reales del reloj: $steps")
            Log.d("KAIROS", "Muestras HR obtenidas: ${samples.size}")

            // ── Test con datos reales si hay HR ──────────────────────────────
            if (samples.isNotEmpty()) {
                Log.d("KAIROS", "── Test con datos REALES ──")
                val result = detector.analyze(
                    hrSamples              = samples,
                    stepsInWindow          = steps,
                    accelerometerMagnitude = 0.0
                )
                if (result != null) {
                    Log.d("KAIROS", "Resultado: ${result.toLogString()}")
                    Log.d("KAIROS", "  HR threshold: ${result.hrThresholdExceeded}")
                    Log.d("KAIROS", "  HRV threshold: ${result.hrvThresholdExceeded}")
                    Log.d("KAIROS", "  En reposo: ${result.movementFilterPassed}")
                    Log.d("KAIROS", "  CRISIS DETECTADA: ${result.isCrisisDetected}")
                }
            }

            // ── Tests simulados — siempre corren ─────────────────────────────
            Log.d("KAIROS", "")
            Log.d("KAIROS", "── Tests simulados con pasos REALES ($steps pasos) ──")

            detector.resetConsecutiveCount()

            // Escenario 1: calma
            Log.d("KAIROS", "── Escenario 1: Calma ──")
            repeat(3) { v ->
                val r = detector.analyze(buildSamples(70L), stepsInWindow = steps)
                Log.d("KAIROS", "  Ventana $v → ${r?.toLogString()}")
                Log.d("KAIROS", "  Crisis: ${r?.isCrisisDetected} (esperado: false)")
            }

            detector.resetConsecutiveCount()

            // Escenario 2: ejercicio
            Log.d("KAIROS", "── Escenario 2: Ejercicio ──")
            repeat(3) { v ->
                val r = detector.analyze(
                    hrSamples              = buildSamples(95L),
                    stepsInWindow          = steps,
                    accelerometerMagnitude = 0.20
                )
                Log.d("KAIROS", "  Ventana $v → ${r?.toLogString()}")
                Log.d("KAIROS", "  Crisis: ${r?.isCrisisDetected} (esperado: false)")
            }

            detector.resetConsecutiveCount()

            // Escenario 3: crisis
            Log.d("KAIROS", "── Escenario 3: Crisis ──")
            repeat(3) { v ->
                val r = detector.analyze(
                    hrSamples              = buildSamples(90L),
                    stepsInWindow          = 2L,   // forzamos reposo para ver la crisis
                    accelerometerMagnitude = 0.03
                )
                Log.d("KAIROS", "  Ventana $v → ${r?.toLogString()}")
                Log.d("KAIROS", "  HR superado: ${r?.hrThresholdExceeded}")
                Log.d("KAIROS", "  HRV superado: ${r?.hrvThresholdExceeded}")
                Log.d("KAIROS", "  En reposo:    ${r?.movementFilterPassed}")
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
        Log.d("KAIROS", "  TEST SIMULADO — sin sensor real")
        Log.d("KAIROS", "========================================")

        Log.d("KAIROS", "── Escenario 1: Usuario en calma ──")
        repeat(3) { v ->
            val r = detector.analyze(buildSamples(70L), stepsInWindow = 5L)
            Log.d("KAIROS", "  Ventana $v → ${r?.toLogString()}")
            Log.d("KAIROS", "  Crisis: ${r?.isCrisisDetected} (esperado: false)")
        }

        detector.resetConsecutiveCount()

        Log.d("KAIROS", "── Escenario 2: Actividad física ──")
        repeat(3) { v ->
            val r = detector.analyze(
                hrSamples              = buildSamples(95L),
                stepsInWindow          = 120L,
                accelerometerMagnitude = 0.20
            )
            Log.d("KAIROS", "  Ventana $v → ${r?.toLogString()}")
            Log.d("KAIROS", "  Crisis: ${r?.isCrisisDetected} (esperado: false)")
        }

        detector.resetConsecutiveCount()

        Log.d("KAIROS", "── Escenario 3: Crisis real ──")
        repeat(3) { v ->
            val r = detector.analyze(
                hrSamples              = buildSamples(90L),
                stepsInWindow          = 2L,
                accelerometerMagnitude = 0.03
            )
            Log.d("KAIROS", "  Ventana $v → ${r?.toLogString()}")
            Log.d("KAIROS", "  HR superado: ${r?.hrThresholdExceeded}")
            Log.d("KAIROS", "  HRV superado: ${r?.hrvThresholdExceeded}")
            Log.d("KAIROS", "  En reposo:    ${r?.movementFilterPassed}")
            Log.d("KAIROS", "  >>> CRISIS: ${r?.isCrisisDetected} ${if (v >= 1) "(esperado: true ✅)" else "(esperado: false)"}")
        }

        Log.d("KAIROS", "========================================")
        Log.d("KAIROS", "  Tests finalizados — revisá Logcat")
        Log.d("KAIROS", "========================================")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildSamples(bpm: Long, count: Int = 10): List<HeartRateRecord.Sample> {
        val now = Instant.now()
        val random = java.util.Random()
        return (0 until count).map { i ->
            // Variación de ±5 bpm para simular HRV realista
            val variation = (random.nextInt(11) - 5).toLong()
            HeartRateRecord.Sample(
                time           = now.minusSeconds((count - i) * 6L),
                beatsPerMinute = (bpm + variation).coerceIn(50L, 200L)
            )
        }
    }
    private fun isEmulator(): Boolean =
        android.os.Build.BRAND.startsWith("generic") ||
                android.os.Build.DEVICE.startsWith("generic") ||
                android.os.Build.FINGERPRINT.startsWith("generic") ||
                android.os.Build.FINGERPRINT.startsWith("unknown") ||
                android.os.Build.HARDWARE.contains("goldfish") ||
                android.os.Build.HARDWARE.contains("ranchu") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK") ||
                android.os.Build.PRODUCT.contains("sdk") ||
                android.os.Build.PRODUCT.contains("emulator")
}