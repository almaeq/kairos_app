package com.example.kairos.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.kairos.MainActivity
import com.example.kairos.mobile.data.BaselineRepository
import com.example.kairos.mobile.data.HealthConnectManager
import com.example.kairos.mobile.data.db.KairosDatabase
import com.example.kairos.mobile.detection.CrisisDetector
import com.example.kairos.mobile.detection.WesadThresholds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activity que guía al usuario a través del proceso de calibración inicial de KAIROS.
 *
 * Durante la calibración, la app lee el historial de frecuencia cardíaca de la última hora
 * desde Health Connect, lo divide en 3 ventanas y alimenta el algoritmo de Welford
 * para establecer la línea de base personal del usuario.
 *
 * Una vez completadas las 3 ventanas, el baseline queda persistido en Room y
 * la detección de crisis queda habilitada para futuras sesiones.
 *
 * Flujo de la pantalla:
 * 1. Verificar disponibilidad y permisos de Health Connect.
 * 2. Leer muestras de HR de la última hora.
 * 3. Dividir en 3 ventanas y procesar cada una con [CrisisDetector].
 * 4. Guardar el baseline resultante via [BaselineRepository].
 * 5. Habilitar el botón "Empezar a monitorear" para ir a [MainActivity].
 */
class CalibrationActivity : ComponentActivity() {

    private lateinit var detector: CrisisDetector
    private lateinit var baselineRepo: BaselineRepository
    private lateinit var healthConnect: HealthConnectManager

    /**
     * Estados observables que alimentan la UI de Compose.
     * Al ser [MutableState], cualquier cambio dispara una recomposición automática.
     */
    private val calibrationWindows = mutableStateOf(0)
    private val currentHr          = mutableStateOf(0.0)
    private val currentRmssd       = mutableStateOf(0.0)
    private val isFinished         = mutableStateOf(false)
    private val statusMessage      = mutableStateOf("Quedate quieta y respirá con calma...")

    /**
     * Inicializa los componentes y lanza el proceso de calibración en segundo plano.
     *
     * @param savedInstanceState Estado previo de la Activity (no usado en esta pantalla).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db        = KairosDatabase.getInstance(this)
        baselineRepo  = BaselineRepository(db.kairosDao())
        detector      = CrisisDetector()
        healthConnect = HealthConnectManager(this)

        setContent {
            CalibrationScreen(
                windows        = calibrationWindows.value,
                totalWindows   = WesadThresholds.MIN_CALIBRATION_WINDOWS,
                currentHr      = currentHr.value,
                currentRmssd   = currentRmssd.value,
                statusMessage  = statusMessage.value,
                isFinished     = isFinished.value,
                onContinue     = { goToMain() }
            )
        }

        // Lanzamos la calibración en el scope de la Activity para que se cancele
        // automáticamente si el usuario sale de la pantalla antes de terminar
        lifecycleScope.launch { runCalibration() }
    }

    // ── Lógica de calibración ─────────────────────────────────────────────────

    /**
     * Ejecuta el proceso completo de calibración de la línea de base personal.
     *
     * Lee muestras de HR desde Health Connect, las divide en 3 ventanas temporales
     * y procesa cada una con [CrisisDetector] para alimentar el algoritmo de Welford.
     * Al finalizar, persiste el baseline con [BaselineRepository.save].
     *
     * Maneja tres casos de error sin lanzar excepciones:
     * - Health Connect no disponible o sin permisos.
     * - Sin muestras de HR en la última hora.
     * - Pocas muestras para dividir en 3 ventanas (menos de 3 por chunk).
     *
     * En todos los casos de error actualiza [statusMessage] para informar al usuario.
     */
    private suspend fun runCalibration() {

        // Verificamos disponibilidad de Health Connect y permisos antes de intentar leer
        if (!HealthConnectManager.isAvailable(this) || !healthConnect.hasAllPermissions()) {
            statusMessage.value = "⚠️ Se necesita Health Connect con permisos para calibrar."
            return
        }

        statusMessage.value = "Leyendo tus datos de la última hora..."

        // Lectura de HR y pasos de la última hora desde Health Connect
        val samples = healthConnect.readHeartRateSamples(windowSeconds = 3600L)
        val steps   = healthConnect.readStepsInWindow(windowSeconds = 3600L)

        if (samples.isEmpty()) {
            statusMessage.value = "Sin datos del reloj. Usá el smartwatch un rato y volvé a intentar."
            return
        }

        currentHr.value = samples.map { it.beatsPerMinute }.average()
        statusMessage.value = "Analizando ${samples.size} muestras..."
        calibrationWindows.value = 1
        delay(1_000L)

        // Dividimos las muestras en 3 chunks de igual tamaño para simular
        // las 3 ventanas de 60s que el algoritmo de Welford necesita para calibrarse
        val chunkSize = samples.size / 3
        if (chunkSize < 3) {
            statusMessage.value = "Pocos datos. Usá el reloj más tiempo y volvé a intentar."
            return
        }

        listOf(
            samples.subList(0, chunkSize),
            samples.subList(chunkSize, chunkSize * 2),
            samples.subList(chunkSize * 2, samples.size)
        ).forEachIndexed { i, chunk ->
            // Procesamos cada ventana con el detector
            // accelerometerMagnitude = 0.01 indica reposo (calibración sin movimiento)
            // Los pasos se dividen por 3 para distribuirlos uniformemente entre ventanas
            detector.analyze(
                hrSamples              = chunk,
                stepsInWindow          = steps / 3,
                accelerometerMagnitude = 0.01
            )
            calibrationWindows.value = i + 1
            statusMessage.value = "Procesando ventana ${i + 1} de 3..."
            delay(800L)
        }

        // Persistimos el baseline resultante en Room via el repositorio
        detector.saveBaseline(baselineRepo)
        statusMessage.value = "¡Calibración completa!"
        isFinished.value = true
    }

    /**
     * Navega a [MainActivity] y cierra esta Activity para que no quede en el back stack.
     */
    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

// ── Composable de la pantalla ─────────────────────────────────────────────────

/**
 * Pantalla de calibración de KAIROS.
 *
 * Muestra el progreso de las ventanas procesadas, la HR actual,
 * mensajes de estado y una animación de pulso mientras el proceso está activo.
 * Cuando la calibración termina, muestra un mensaje de éxito y habilita
 * el botón para continuar a la pantalla principal.
 *
 * @param windows Número de ventanas de calibración completadas hasta el momento.
 * @param totalWindows Número total de ventanas requeridas para completar la calibración.
 * @param currentHr HR media detectada durante la calibración, en BPM.
 * @param currentRmssd RMSSD detectado durante la calibración, en ms (actualmente no mostrado en UI).
 * @param statusMessage Mensaje de estado a mostrar al usuario.
 * @param isFinished Indica si la calibración finalizó exitosamente.
 * @param onContinue Callback invocado cuando el usuario presiona "Empezar a monitorear".
 */
@Composable
fun CalibrationScreen(
    windows:       Int,
    totalWindows:  Int,
    currentHr:     Double,
    currentRmssd:  Double,
    statusMessage: String,
    isFinished:    Boolean,
    onContinue:    () -> Unit
) {
    val progress = if (totalWindows > 0) windows.toFloat() / totalWindows else 0f

    // Animación de pulso cardiaco: escala entre 1.0 y 1.12 de forma continua
    // Se detiene (queda en 1.0) cuando la calibración finaliza
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (isFinished) 1f else 1.12f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val KairosGreen     = Color(0xFF4CAF7D)
    val KairosBlue      = Color(0xFF2196A8)
    val BackgroundColor = Color(0xFF0D1117)
    val CardColor       = Color(0xFF161B22)
    val TextPrimary     = Color(0xFFE6EDF3)
    val TextSecondary   = Color(0xFF8B949E)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text          = "KAIROS",
                fontSize      = 28.sp,
                fontWeight    = FontWeight.Bold,
                color         = KairosGreen,
                letterSpacing = 4.sp
            )

            Text(
                text     = "Calibración inicial",
                fontSize = 16.sp,
                color    = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Círculo animado que muestra la HR actual
            // Tres capas concéntricas: anillo exterior pulsante, anillo medio, círculo central con valor
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pulseScale)
                        .background(
                            color = KairosGreen.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .background(
                            color = KairosGreen.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(color = CardColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isFinished) "✓"
                            else if (currentHr > 0) "%.0f".format(currentHr)
                            else "—",
                            fontSize   = if (isFinished) 36.sp else 28.sp,
                            fontWeight = FontWeight.Bold,
                            color      = if (isFinished) KairosGreen else TextPrimary
                        )
                        if (!isFinished) {
                            Text(
                                text     = "BPM",
                                fontSize = 11.sp,
                                color    = TextSecondary
                            )
                        }
                    }
                }
            }

            Text(
                text      = statusMessage,
                fontSize  = 15.sp,
                color     = TextPrimary,
                textAlign = TextAlign.Center
            )

            // Barra de progreso de ventanas completadas
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress   = { progress },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color      = KairosGreen,
                    trackColor = KairosGreen.copy(alpha = 0.2f),
                )
                Text(
                    text     = "$windows / $totalWindows ventanas",
                    fontSize = 13.sp,
                    color    = TextSecondary
                )
            }

            // Card de instrucciones durante la calibración,
            // reemplazada por mensaje de éxito al terminar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = CardColor)
            ) {
                if (!isFinished) {
                    Column(
                        modifier            = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text       = "Para una calibración precisa:",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = TextPrimary
                        )
                        listOf(
                            "Sentate o quedate quieta",
                            "Respirá lento y profundo",
                            "No hables ni te muevas",
                            "Dura solo unos minutos"
                        ).forEach { hint ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(KairosBlue, CircleShape)
                                )
                                Text(text = hint, fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                    }
                } else {
                    Column(
                        modifier            = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text       = "Baseline guardado ✓",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = KairosGreen
                        )
                        Text(
                            text      = "KAIROS ya conoce tu frecuencia cardíaca normal. A partir de ahora detectará desviaciones personalizadas.",
                            fontSize  = 13.sp,
                            color     = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Botón visible solo cuando la calibración finalizó exitosamente
            if (isFinished) {
                Button(
                    onClick  = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = KairosGreen)
                ) {
                    Text(
                        text       = "Empezar a monitorear",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White
                    )
                }
            }
        }
    }
}