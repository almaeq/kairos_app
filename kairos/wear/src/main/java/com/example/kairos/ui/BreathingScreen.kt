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
 * Fase individual del ciclo de respiración box (4-4-4-4).
 *
 * El ciclo completo dura 16 segundos: 4 fases × 4 segundos cada una.
 * El orden es fijo e intencional: inhalar → retener → exhalar → retener.
 *
 * @property label Instrucción a mostrar en pantalla y pronunciar via TTS.
 * @property durationMs Duración de la fase en milisegundos (siempre 4000ms).
 */
enum class BreathPhase(val label: String, val durationMs: Long) {
    INHALE("Inhalá", 4_000L),
    HOLD1("Retené",  4_000L),
    EXHALE("Exhalá", 4_000L),
    HOLD2("Retené",  4_000L)
}

/**
 * Pantalla del ejercicio de respiración box (4-4-4-4) en el reloj.
 *
 * Controla el timing completo del ejercicio de forma autónoma en el reloj,
 * sin depender de mensajes del teléfono. Simultáneamente notifica al teléfono
 * cada cambio de fase via `/kairos/breathing/update` para que la app del teléfono
 * muestre un espejo visual sincronizado.
 *
 * **Text-to-Speech:**
 * Usa TTS en español (locale `es_AR`) para pronunciar cada instrucción en el reloj.
 * El ejercicio no comienza hasta que TTS confirma que está listo ([ttsReady]).
 *
 * **Animación del círculo:**
 * - INHALE / HOLD1: el círculo se expande (scale 1.2) — retiene el volumen inhalado.
 * - EXHALE / HOLD2: el círculo se contrae (scale 0.75) — refleja la exhalación.
 * - La transición anima en 3800ms para INHALE/EXHALE (casi la duración completa de 4s)
 *   y en 200ms para HOLD (transición rápida sin movimiento durante la retención).
 *
 * **Sincronización con el teléfono:**
 * - Al inicio: `phase="",cycle=0` señala que el ejercicio está comenzando.
 * - En cada fase: `phase=<label>,cycle=<N>` actualiza la UI del teléfono.
 * - Al terminar: `/kairos/breathing/done` para que el teléfono muestre el estado final.
 *
 * @param totalCycles Número total de ciclos a ejecutar (default: 6 → 96 segundos).
 * @param onFinished Callback invocado cuando todos los ciclos completaron.
 */
@Composable
fun BreathingScreen(
    totalCycles: Int        = 6,
    onFinished:  () -> Unit = {}
) {
    val context = LocalContext.current

    val KairosBlue    = Color(0xFF3B82F6)
    val KairTeal      = Color(0xFF06B6D4)
    val Background    = Color(0xFF0A0E1A)
    val TextPrimary   = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFF94A3B8)

    var currentPhase by remember { mutableStateOf<BreathPhase?>(null) }
    var currentCycle by remember { mutableIntStateOf(0) }
    var secondsLeft  by remember { mutableIntStateOf(4) }
    var isFinished   by remember { mutableStateOf(false) }
    var ttsReady     by remember { mutableStateOf(false) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    // Inicializamos TTS y lo destruimos cuando el composable sale de la composición
    DisposableEffect(Unit) {
        val t = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.value?.language = Locale("es", "AR")
                ttsReady = true
                Log.d("BreathingScreen", "TTS listo ✅")
            } else {
                Log.e("BreathingScreen", "TTS falló: $status")
            }
        }
        tts.value = t
        onDispose { t.stop(); t.shutdown() }
    }

    // El ejercicio arranca solo cuando TTS está listo para evitar instrucciones mudas
    LaunchedEffect(ttsReady) {
        if (!ttsReady) return@LaunchedEffect

        delay(500L)
        // Notificamos al teléfono que el ejercicio está iniciando (fase vacía)
        sendBreathingUpdate(context, "", 0)
        tts.value?.speak("Respiración en caja. Seguí el ritmo.", TextToSpeech.QUEUE_FLUSH, null, "intro")
        delay(4_500L)
        delay(1_000L)

        repeat(totalCycles) { cycle ->
            currentCycle = cycle + 1
            BreathPhase.values().forEach { phase ->
                currentPhase = phase
                secondsLeft  = (phase.durationMs / 1000).toInt()
                sendBreathingUpdate(context, phase.label, cycle + 1)
                tts.value?.speak(phase.label, TextToSpeech.QUEUE_FLUSH, null, phase.name)
                // Countdown de la fase: actualizamos el contador cada segundo
                while (secondsLeft > 0) { delay(1_000L); secondsLeft-- }
            }
        }

        tts.value?.speak("Muy bien. Tu respiración se está normalizando.",
            TextToSpeech.QUEUE_FLUSH, null, "outro")
        sendBreathingDone(context)
        delay(3_500L)
        isFinished = true
        onFinished()
    }

    // El círculo mantiene escala durante HOLD para no generar movimiento innecesario
    val targetScale = when (currentPhase) {
        BreathPhase.INHALE, BreathPhase.HOLD1 -> 1.2f
        BreathPhase.EXHALE, BreathPhase.HOLD2 -> 0.75f
        null -> 1.0f
    }
    val animatedScale by animateFloatAsState(
        targetValue   = targetScale,
        animationSpec = tween(
            durationMillis = if (currentPhase == BreathPhase.INHALE ||
                currentPhase == BreathPhase.EXHALE) 3_800 else 200,
            easing         = EaseInOut
        ),
        label = "breathScale"
    )

    // El color cambia entre azul (inhalar) y teal (exhalar) para retroalimentación visual
    val phaseColor = when (currentPhase) {
        BreathPhase.INHALE -> KairosBlue
        BreathPhase.EXHALE -> KairTeal
        else               -> KairosBlue.copy(alpha = 0.7f)
    }

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
                // Estado: esperando TTS / pantalla de bienvenida
                currentPhase == null && !isFinished -> {
                    Text("Respiración",  fontSize = 13.sp, color = KairosBlue, fontWeight = FontWeight.SemiBold)
                    Text("4 - 4 - 4 - 4", fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                }

                // Estado: fase activa
                currentPhase != null -> {
                    Text("Ciclo $currentCycle / $totalCycles", fontSize = 10.sp, color = TextSecondary)

                    // Círculo animado con la instrucción y el countdown
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .scale(animatedScale)
                            .background(phaseColor.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                currentPhase!!.label,
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color      = phaseColor,
                                textAlign  = TextAlign.Center
                            )
                            Text("${secondsLeft}s", fontSize = 11.sp, color = phaseColor.copy(alpha = 0.7f))
                        }
                    }

                    // Barra de progreso de la fase actual
                    val progress = secondsLeft.toFloat() / (currentPhase!!.durationMs / 1000).toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(3.dp)
                            .background(phaseColor.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(3.dp)
                                .background(phaseColor, CircleShape)
                        )
                    }

                    // Indicadores de ciclos: completados en azul, activo en color de fase, pendientes en gris
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        repeat(totalCycles) { i ->
                            Box(
                                modifier = Modifier
                                    .size(if (i == currentCycle - 1) 8.dp else 6.dp)
                                    .background(
                                        when {
                                            i < currentCycle - 1  -> KairosBlue
                                            i == currentCycle - 1 -> phaseColor
                                            else                  -> TextSecondary.copy(alpha = 0.3f)
                                        },
                                        CircleShape
                                    )
                            )
                        }
                    }
                }

                // Estado: ejercicio completado
                else -> {
                    Text("✓ Completado", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = KairosBlue)
                    Button(
                        onClick   = onFinished,
                        modifier  = Modifier.fillMaxWidth(0.7f).height(36.dp),
                        colors    = ButtonDefaults.buttonColors(backgroundColor = KairosBlue.copy(alpha = 0.2f))
                    ) {
                        Text("Continuar", fontSize = 12.sp, color = KairosBlue)
                    }
                }
            }
        }
    }
}

/**
 * Notifica al teléfono que el ejercicio de respiración finalizó.
 *
 * Envía el mensaje `/kairos/breathing/done` via Wearable Message API para que
 * [KairosPhoneListener] actualice [BreathingState] y la UI del teléfono muestre
 * el estado de completado.
 *
 * @param context Contexto para acceder al cliente Wearable.
 */
private fun sendBreathingDone(context: Context) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.forEach { node ->
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, "/kairos/breathing/done", ByteArray(0)).await()
                Log.d("BreathingScreen", "Done → ${node.displayName}")
            }
        } catch (e: Exception) {
            Log.e("BreathingScreen", "Error enviando done: ${e.message}")
        }
    }
}

/**
 * Envía una actualización de fase al teléfono para mantener la UI sincronizada.
 *
 * Payload: `"phase=<label>,cycle=<N>"`.
 * La fase vacía (`phase=""`) indica que el ejercicio está iniciando pero
 * todavía no comenzó el primer ciclo.
 *
 * @param context Contexto para acceder al cliente Wearable.
 * @param phase Etiqueta de la fase actual ("Inhalá", "Retené", "Exhalá") o cadena vacía.
 * @param cycle Número del ciclo actual (1 a [BreathingScreen.totalCycles]) o 0 al iniciar.
 */
private fun sendBreathingUpdate(context: Context, phase: String, cycle: Int) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.forEach { node ->
                Wearable.getMessageClient(context)
                    .sendMessage(
                        node.id,
                        "/kairos/breathing/update",
                        "phase=$phase,cycle=$cycle".toByteArray()
                    ).await()
                Log.d("BreathingScreen", "Update phase=$phase cycle=$cycle → ${node.displayName}")
            }
        } catch (e: Exception) {
            Log.e("BreathingScreen", "Error enviando update: ${e.message}")
        }
    }
}