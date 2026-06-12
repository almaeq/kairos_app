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

// ── Datos de cada paso del grounding 5-4-3-2-1 ───────────────────────────────

data class GroundingStep(
    val number:     Int,
    val emoji:      String,
    val sense:      String,
    val audioText:  String,
    val durationMs: Long
)

val GROUNDING_STEPS = listOf(
    GroundingStep(
        number     = 5,
        emoji      = "👁️",
        sense      = "cosas que ves",
        audioText  = "Buscá 5 cosas que podés ver ahora mismo. Mirá a tu alrededor y nombralas en tu mente.",
        durationMs = 25_000L
    ),
    GroundingStep(
        number     = 4,
        emoji      = "✋",
        sense      = "cosas que tocás",
        audioText  = "Ahora buscá 4 cosas que podés tocar. Sentí su textura. Fijate si son frías, calientes, suaves o rugosas.",
        durationMs = 25_000L
    ),
    GroundingStep(
        number     = 3,
        emoji      = "👂",
        sense      = "cosas que escuchás",
        audioText  = "Escuchá con atención. ¿Qué 3 sonidos podés identificar ahora mismo?",
        durationMs = 20_000L
    ),
    GroundingStep(
        number     = 2,
        emoji      = "👃",
        sense      = "cosas que olés",
        audioText  = "Respirá profundo. ¿Podés identificar 2 olores distintos a tu alrededor?",
        durationMs = 20_000L
    ),
    GroundingStep(
        number     = 1,
        emoji      = "👅",
        sense      = "cosa que saboreás",
        audioText  = "Por último, prestá atención a tu boca. ¿Qué sabor tenés ahora mismo?",
        durationMs = 15_000L
    )
)

// ── Pantalla de grounding en el reloj ────────────────────────────────────────

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

    var currentStepIndex by remember { mutableIntStateOf(-1) }
    var secondsLeft      by remember { mutableIntStateOf(5) }
    var isFinished       by remember { mutableStateOf(false) }

    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        val t = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.value?.language = Locale("es", "AR")
                ttsReady = true  // ← señal de que está listo
                Log.d("GroundingScreen", "TTS inicializado ✅")
            } else {
                Log.e("GroundingScreen", "TTS falló: $status")
            }
        }
        tts.value = t
        onDispose { t.stop(); t.shutdown() }
    }

// La secuencia espera a que ttsReady sea true
    LaunchedEffect(ttsReady) {
        if (!ttsReady) return@LaunchedEffect  // esperar

        delay(500L)
        tts.value?.speak(
            "Vamos a hacer un ejercicio de grounding para ayudarte a calmarte.",
            TextToSpeech.QUEUE_FLUSH, null, "intro"
        )
        delay(4_000L)

        GROUNDING_STEPS.forEachIndexed { index, step ->
            currentStepIndex = index
            secondsLeft = (step.durationMs / 1000).toInt()
            sendGroundingStep(context, step.number)
            tts.value?.speak(step.audioText, TextToSpeech.QUEUE_FLUSH, null, "step_$index")
            while (secondsLeft > 0) {
                delay(1_000L)
                secondsLeft--
            }
        }

        tts.value?.speak(
            "Muy bien. Respirá profundo. Estás en un lugar seguro.",
            TextToSpeech.QUEUE_FLUSH, null, "outro"
        )
        sendGroundingStep(context, 0)
        delay(4_000L)
        isFinished = true
        onFinished()
    }

    val currentStep = if (currentStepIndex >= 0) GROUNDING_STEPS[currentStepIndex] else null

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
        modifier = Modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {

            if (currentStep == null && !isFinished) {
                // ── Intro ──────────────────────────────────────────────────
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
            } else if (currentStep != null) {
                // ── Paso activo ────────────────────────────────────────────
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
                    text      = currentStep.sense,
                    fontSize  = 12.sp,
                    color     = TextPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Text(
                    text     = "${secondsLeft}s",
                    fontSize = 11.sp,
                    color    = TextSecondary
                )

                // Barra de progreso
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
            } else {
                // ── Finalizado ─────────────────────────────────────────────
                Text(
                    text       = "✓ Completado",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = KairosGreen,
                    textAlign  = TextAlign.Center
                )
                Button(
                    onClick  = onFinished,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = KairosGreen.copy(alpha = 0.2f)
                    )
                ) {
                    Text("Cerrar", fontSize = 12.sp, color = KairosGreen)
                }
            }
        }
    }
}

// ── Enviar paso al teléfono ───────────────────────────────────────────────────

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