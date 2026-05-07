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

class CalibrationActivity : ComponentActivity() {

    private lateinit var detector: CrisisDetector
    private lateinit var baselineRepo: BaselineRepository
    private lateinit var healthConnect: HealthConnectManager

    // Estado observable desde la UI
    private val calibrationWindows = mutableStateOf(0)
    private val currentHr          = mutableStateOf(0.0)
    private val currentRmssd       = mutableStateOf(0.0)
    private val isFinished         = mutableStateOf(false)
    private val statusMessage      = mutableStateOf("Quedate quieta y respirá con calma...")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db     = KairosDatabase.getInstance(this)
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

        lifecycleScope.launch { runCalibration() }
    }

    // ── Lógica de calibración ─────────────────────────────────────────────────

    private suspend fun runCalibration() {

        // Verificar Health Connect
        if (!HealthConnectManager.isAvailable(this) || !healthConnect.hasAllPermissions()) {
            statusMessage.value = "⚠️ Se necesita Health Connect con permisos para calibrar."
            return
        }

        statusMessage.value = "Leyendo tus datos de la última hora..."

        // Una sola lectura de la última hora
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

        // Dividimos las muestras en 3 grupos para mantener la lógica de ventanas
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
            detector.analyze(
                hrSamples              = chunk,
                stepsInWindow          = steps / 3,
                accelerometerMagnitude = 0.01
            )
            calibrationWindows.value = i + 1
            statusMessage.value = "Procesando ventana ${i + 1} de 3..."
            delay(800L)
        }

        detector.saveBaseline(baselineRepo)
        statusMessage.value = "¡Calibración completa!"
        isFinished.value = true
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

// ── Composable de la pantalla ─────────────────────────────────────────────────

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

    // Animación del pulso
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (isFinished) 1f else 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val KairosGreen  = Color(0xFF4CAF7D)
    val KairosBlue   = Color(0xFF2196A8)
    val BackgroundColor = Color(0xFF0D1117)
    val CardColor    = Color(0xFF161B22)
    val TextPrimary  = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B949E)

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

            // Título
            Text(
                text       = "KAIROS",
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = KairosGreen,
                letterSpacing = 4.sp
            )

            Text(
                text     = "Calibración inicial",
                fontSize = 16.sp,
                color    = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Círculo animado con HR
            Box(contentAlignment = Alignment.Center) {
                // Anillo exterior
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pulseScale)
                        .background(
                            color = KairosGreen.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                )
                // Anillo medio
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .background(
                            color = KairosGreen.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                )
                // Círculo central
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(color = CardColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text       = if (isFinished) "✓" else
                                if (currentHr > 0) "%.0f".format(currentHr) else "—",
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

            // Mensaje de estado
            Text(
                text      = statusMessage,
                fontSize  = 15.sp,
                color     = TextPrimary,
                textAlign = TextAlign.Center
            )

            // Barra de progreso
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress        = { progress },
                    modifier        = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color           = KairosGreen,
                    trackColor      = KairosGreen.copy(alpha = 0.2f),
                )
                Text(
                    text     = "$windows / $totalWindows ventanas",
                    fontSize = 13.sp,
                    color    = TextSecondary
                )
            }

            // Card de instrucciones (mientras calibra) o éxito
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

            // Botón continuar (solo cuando termina)
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