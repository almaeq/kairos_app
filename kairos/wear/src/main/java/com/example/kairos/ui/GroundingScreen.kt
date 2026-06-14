package com.example.kairos.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Representa un paso individual del ejercicio de grounding 5-4-3-2-1 en el reloj.
 *
 * Versión Wear OS de [GroundingStep] del teléfono. Incluye el campo [audioText]
 * que se pronuncia via TTS durante el paso, ausente en la versión del teléfono
 * porque allí el audio lo maneja el reloj.
 *
 * @property number Número de estímulos a identificar (5, 4, 3, 2, 1).
 * @property emoji Ícono del sentido activado, mostrado en el círculo central.
 * @property sense Descripción breve del sentido mostrada debajo del círculo.
 * @property audioText Instrucción completa pronunciada por TTS al iniciar el paso.
 * @property durationMs Duración del paso en milisegundos.
 */
data class GroundingStep(
    val number:     Int,
    val emoji:      String,
    val sense:      String,
    val audioText:  String,
    val durationMs: Long
)

/**
 * Secuencia completa de pasos del ejercicio de grounding 5-4-3-2-1 en el reloj.
 *
 * Los pasos van en orden descendente (5→1) activando los cinco sentidos.
 * Las duraciones son ligeramente más cortas que en la versión del teléfono
 * porque la pantalla pequeña del reloj requiere instrucciones más concisas.
 *
 * **Duración total:** 80 segundos (~1.3 minutos).
 */
val GROUNDING_STEPS = listOf(
    GroundingStep(
        number     = 5,
        emoji      = "👁️",
        sense      = "cosas que ves",
        audioText  = "Buscá 5 cosas que podés ver ahora mismo. Mirá a tu alrededor y nombralas en tu mente.",
        durationMs = 20_000L
    ),
    GroundingStep(
        number     = 4,
        emoji      = "✋",
        sense      = "cosas que tocás",
        audioText  = "Ahora buscá 4 cosas que podés tocar. Sentí su textura. Fijate si son frías, calientes, suaves o rugosas.",
        durationMs = 20_000L
    ),
    GroundingStep(
        number     = 3,
        emoji      = "👂",
        sense      = "cosas que escuchás",
        audioText  = "Escuchá con atención. ¿Qué 3 sonidos podés identificar ahora mismo?",
        durationMs = 15_000L
    ),
    GroundingStep(
        number     = 2,
        emoji      = "👃",
        sense      = "cosas que olés",
        audioText  = "Respirá profundo. ¿Podés identificar 2 olores distintos a tu alrededor?",
        durationMs = 15_000L
    ),
    GroundingStep(
        number     = 1,
        emoji      = "👅",
        sense      = "cosa que saboreás",
        audioText  = "Por último, prestá atención a tu boca. ¿Qué sabor tenés ahora mismo?",
        durationMs = 10_000L
    )
)

/**
 * Pantalla del ejercicio de grounding 5-4-3-2-1 en el reloj.
 *
 * Controla el timing completo del ejercicio de forma autónoma, pronuncia cada
 * instrucción via TTS y notifica al teléfono cada cambio de paso via
 * `/kairos/grounding/paso` para que la app del teléfono muestre un espejo visual.
 *
 * **Text-to-Speech:**
 * El ejercicio no comienza hasta que TTS confirma que está listo ([ttsReady]).
 * Usa locale `es_AR` para español rioplatense.
 *
 * **Estados de la pantalla según [currentStepIndex]:**
 * - `-1` y no finalizado → pantalla de intro con título.
 * - `0..4` → paso activo con círculo animado, número, emoji y barra de progreso.
 * - finalizado → mensaje de completado con botón "Cerrar".
 *
 * **Sincronización con el teléfono via `/kairos/grounding/paso`:**
 * - `step = 5, 4, 3, 2, 1` → paso activo correspondiente.
 * - `step = 0` → ejercicio completado.
 *
 * @param onFinished Callback invocado cuando todos los pasos completaron.
 */
@Composable
fun GroundingScreen(
    onFinished: () -> Unit = {}
) {
    val context = LocalContext.current

    val KairosGreen   = Color(0xFF00E5A0)
    val KairosBlue    = Color(0xFF3B82F6)
    val Background    = Color(0xFF0A0E1A)
    val TextPrimary   = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFF94A3B8)

    /** Índice del paso actual en [GROUNDING_STEPS]. -1 = intro, 0-4 = paso activo. */
    var currentStepIndex by remember { mutableIntStateOf(-1) }
    var secondsLeft      by remember { mutableIntStateOf(5) }
    var isFinished       by remember { mutableStateOf(false) }
    var ttsReady         by remember { mutableStateOf(false) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    // Inicializamos TTS y lo destruimos cuando el composable sale de la composición
    DisposableEffect(Unit) {
        val t = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.value?.language = Locale("es", "AR")
                ttsReady = true
                Log.d("GroundingScreen", "TTS inicializado ✅")
            } else {
                Log.e("GroundingScreen", "TTS falló: $status")
            }
        }
        tts.value = t
        onDispose { t.stop(); t.shutdown() }
    }

    // El ejercicio arranca solo cuando TTS está listo para evitar instrucciones mudas
    LaunchedEffect(ttsReady) {
        if (!ttsReady) return@LaunchedEffect

        delay(500L)
        tts.value?.speak(
            "Vamos a hacer un ejercicio de grounding para ayudarte a calmarte.",
            TextToSpeech.QUEUE_FLUSH, null, "intro"
        )
        delay(4_000L)

        GROUNDING_STEPS.forEachIndexed { index, step ->
            currentStepIndex = index
            secondsLeft      = (step.durationMs / 1000).toInt()
            sendGroundingStep(context, step.number)
            tts.value?.speak(step.audioText, TextToSpeech.QUEUE_FLUSH, null, "step_$index")
            // Countdown del paso: actualizamos el contador cada segundo
            while (secondsLeft > 0) {
                delay(1_000L)
                secondsLeft--
            }
        }

        tts.value?.speak(
            "Muy bien. Respirá profundo. Estás en un lugar seguro.",
            TextToSpeech.QUEUE_FLUSH, null, "outro"
        )
        // step=0 señala al teléfono que el ejercicio completó
        sendGroundingStep(context, 0)
        delay(4_000L)
        isFinished = true
        onFinished()
    }

    val currentStep = if (currentStepIndex >= 0) GROUNDING_STEPS[currentStepIndex] else null

    // Animación de respiración suave del círculo — independiente del paso activo
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    val circleScale by infiniteTransition.animateFloat(
        initialValue  = 0.85f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circleScale"
    )

    Box(
        modifier         = Modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier            = Modifier.padding(12.dp)
        ) {
            when {
                // Estado: intro — TTS aún pronunciando la introducción
                currentStep == null && !isFinished -> {
                    Text(
                        text          = "KAIROS",
                        fontSize      = 13.sp,
                        color         = KairosGreen,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text       = "Ejercicio de\ngrounding",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = TextPrimary,
                        textAlign  = TextAlign.Center
                    )
                }

                // Estado: paso activo
                currentStep != null -> {
                    // Círculo con número y emoji del sentido activo, con animación de respiración
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .scale(circleScale)
                            .background(KairosBlue.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text       = currentStep.number.toString(),
                                fontSize   = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color      = KairosBlue
                            )
                            Text(text = currentStep.emoji, fontSize = 16.sp)
                        }
                    }

                    Text(
                        text       = currentStep.sense,
                        fontSize   = 12.sp,
                        color      = TextPrimary,
                        textAlign  = TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    Text(text = "${secondsLeft}s", fontSize = 11.sp, color = TextSecondary)

                    // Barra de progreso del paso actual
                    val progress = secondsLeft.toFloat() / (currentStep.durationMs / 1000).toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(4.dp)
                            .background(KairosBlue.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .background(KairosBlue, CircleShape)
                        )
                    }
                }

                // Estado: ejercicio completado
                else -> {
                    Text(
                        text       = "✓ Completado",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = KairosGreen,
                        textAlign  = TextAlign.Center
                    )
                    Button(
                        onClick  = onFinished,
                        modifier = Modifier.fillMaxWidth(0.7f).height(36.dp),
                        colors   = ButtonDefaults.buttonColors(
                            backgroundColor = KairosGreen.copy(alpha = 0.2f)
                        )
                    ) {
                        Text("Cerrar", fontSize = 12.sp, color = KairosGreen)
                    }
                }
            }
        }
    }
}

/**
 * Notifica al teléfono el paso actual del ejercicio de grounding.
 *
 * Envía el número del paso via `/kairos/grounding/paso` para que
 * [KairosPhoneListener] actualice [GroundingState] y la UI del teléfono
 * muestre el paso correspondiente sincronizado con el reloj.
 *
 * @param context Contexto para acceder al cliente Wearable.
 * @param step Número del paso activo (5, 4, 3, 2, 1) o 0 cuando el ejercicio completó.
 */
private fun sendGroundingStep(context: Context, step: Int) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.forEach { node ->
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, "/kairos/grounding/paso", step.toString().toByteArray())
                    .await()
                Log.d("GroundingScreen", "Paso $step → ${node.displayName}")
            }
        } catch (e: Exception) {
            Log.e("GroundingScreen", "Error enviando paso: ${e.message}")
        }
    }
}