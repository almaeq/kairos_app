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

enum class BreathPhase(val label: String, val durationMs: Long) {
    INHALE("Inhalá", 4_000L),
    HOLD1("Retené",  4_000L),
    EXHALE("Exhalá", 4_000L),
    HOLD2("Retené",  4_000L)
}

@Composable
fun BreathingScreen(
    totalCycles: Int       = 6,
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

    LaunchedEffect(ttsReady) {
        if (!ttsReady) return@LaunchedEffect
        delay(500L)
        sendBreathingUpdate(context, "", 0)  // fase vacía = iniciando
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
                while (secondsLeft > 0) { delay(1_000L); secondsLeft-- }
            }
        }

        tts.value?.speak("Muy bien. Tu respiración se está normalizando.", TextToSpeech.QUEUE_FLUSH, null, "outro")
        sendBreathingDone(context)
        delay(3_500L)
        isFinished = true
        onFinished()
    }

    val targetScale = when (currentPhase) {
        BreathPhase.INHALE, BreathPhase.HOLD1 -> 1.2f
        BreathPhase.EXHALE, BreathPhase.HOLD2 -> 0.75f
        null -> 1.0f
    }
    val animatedScale by animateFloatAsState(
        targetValue   = targetScale,
        animationSpec = tween(
            durationMillis = if (currentPhase == BreathPhase.INHALE || currentPhase == BreathPhase.EXHALE) 3_800 else 200,
            easing         = EaseInOut
        ),
        label = "breathScale"
    )
    val phaseColor = when (currentPhase) {
        BreathPhase.INHALE -> KairosBlue
        BreathPhase.EXHALE -> KairTeal
        else               -> KairosBlue.copy(alpha = 0.7f)
    }

    Box(modifier = Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            when {
                currentPhase == null && !isFinished -> {
                    Text("Respiración", fontSize = 13.sp, color = KairosBlue, fontWeight = FontWeight.SemiBold)
                    Text("4 - 4 - 4 - 4", fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                currentPhase != null -> {
                    Text("Ciclo $currentCycle / $totalCycles", fontSize = 10.sp, color = TextSecondary)
                    Box(
                        modifier = Modifier.size(90.dp).scale(animatedScale)
                            .background(phaseColor.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(currentPhase!!.label, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = phaseColor, textAlign = TextAlign.Center)
                            Text("${secondsLeft}s", fontSize = 11.sp, color = phaseColor.copy(alpha = 0.7f))
                        }
                    }
                    val progress = secondsLeft.toFloat() / (currentPhase!!.durationMs / 1000).toFloat()
                    Box(modifier = Modifier.fillMaxWidth(0.8f).height(3.dp)
                        .background(phaseColor.copy(alpha = 0.2f), CircleShape)) {
                        Box(modifier = Modifier.fillMaxWidth(progress).height(3.dp)
                            .background(phaseColor, CircleShape))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(totalCycles) { i ->
                            Box(modifier = Modifier.size(if (i == currentCycle - 1) 8.dp else 6.dp)
                                .background(when {
                                    i < currentCycle - 1  -> KairosBlue
                                    i == currentCycle - 1 -> phaseColor
                                    else                  -> TextSecondary.copy(alpha = 0.3f)
                                }, CircleShape))
                        }
                    }
                }
                else -> {
                    Text("✓ Completado", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = KairosBlue)
                    Button(onClick = onFinished, modifier = Modifier.fillMaxWidth(0.7f).height(36.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = KairosBlue.copy(alpha = 0.2f))) {
                        Text("Continuar", fontSize = 12.sp, color = KairosBlue)
                    }
                }
            }
        }
    }
}

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
            Log.e("BreathingScreen", "Error: ${e.message}")
        }
    }
}
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
            Log.e("BreathingScreen", "Error update: ${e.message}")
        }
    }
}
