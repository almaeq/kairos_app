package com.example.kairos.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kairos.mobile.techniques.BREATHING_PHASES
import com.example.kairos.mobile.techniques.BREATHING_TOTAL_CYCLES
import com.example.kairos.mobile.techniques.BreathingState

class BreathingActivity : ComponentActivity() {

    companion object {
        fun launch(context: Context) {
            context.startActivity(
                Intent(context, BreathingActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val currentPhase by BreathingState.currentPhase.collectAsState()
            val currentCycle by BreathingState.currentCycle.collectAsState()
            BreathingPhoneScreen(
                currentPhase = currentPhase,
                currentCycle = currentCycle,
                onDismiss    = { finish() }
            )
        }
    }
}

@Composable
fun BreathingPhoneScreen(
    currentPhase: String,  // "Inhalá", "Retené", "Exhalá" | "" = esperando | "done" = terminado
    currentCycle: Int,
    onDismiss:    () -> Unit
) {
    val KairosBlue    = Color(0xFF3B82F6)
    val KairTeal      = Color(0xFF06B6D4)
    val KairosGreen   = Color(0xFF00E5A0)
    val Background    = Color(0xFF0A0E1A)
    val CardDark      = Color(0xFF111827)
    val TextPrimary   = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFF94A3B8)

    val isFinished = currentPhase == "done"
    val isWaiting  = currentPhase == ""

    // Color según la fase
    val phaseColor = when (currentPhase) {
        "Inhalá"  -> KairosBlue
        "Exhalá"  -> KairTeal
        else      -> KairosBlue.copy(alpha = 0.7f)
    }

    // Escala del círculo — se expande en Inhalá, se contrae en Exhalá
    val targetScale = when (currentPhase) {
        "Inhalá"  -> 1.15f
        "Retené"  -> if (currentCycle > 0) 1.15f else 0.85f // mantiene el estado anterior
        "Exhalá"  -> 0.75f
        else      -> 1.0f
    }
    val animatedScale by animateFloatAsState(
        targetValue   = targetScale,
        animationSpec = tween(
            durationMillis = when (currentPhase) {
                "Inhalá", "Exhalá" -> 3_800
                else               -> 300
            },
            easing = EaseInOut
        ),
        label = "breathScale"
    )

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text       = "Respiración 4-4-4-4",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = KairosBlue
            )
            Text(
                text     = "Seguí las instrucciones del reloj",
                fontSize = 13.sp,
                color    = TextSecondary
            )

            AnimatedContent(
                targetState    = currentPhase,
                transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
                label          = "phaseTransition"
            ) { phase ->
                when {
                    isFinished -> {
                        // ── Finalizado ────────────────────────────────────
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .background(KairosBlue.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✓", fontSize = 48.sp, color = KairosBlue)
                            }
                            Text(
                                text       = "Respiración completada",
                                fontSize   = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color      = KairosBlue,
                                textAlign  = TextAlign.Center
                            )
                            Text(
                                text      = "Tu ritmo cardíaco se está estabilizando.\nRespiración: normalizada.",
                                fontSize  = 14.sp,
                                color     = TextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    isWaiting -> {
                        // ── Esperando inicio ──────────────────────────────
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .scale(animatedScale)
                                    .background(KairosBlue.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text       = "4-4-4-4",
                                    fontSize   = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = KairosBlue
                                )
                            }
                            Text(
                                text      = "Esperando que el reloj\ninicie el ejercicio...",
                                fontSize  = 14.sp,
                                color     = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    else -> {
                        // ── Fase activa ───────────────────────────────────
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Contador de ciclos
                            Text(
                                text     = "Ciclo $currentCycle / $BREATHING_TOTAL_CYCLES",
                                fontSize = 13.sp,
                                color    = TextSecondary
                            )

                            // Círculo animado con la fase
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .scale(animatedScale)
                                    .background(phaseColor.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text       = phase,
                                        fontSize   = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = phaseColor,
                                        textAlign  = TextAlign.Center
                                    )
                                    Text(
                                        text     = "4 segundos",
                                        fontSize = 13.sp,
                                        color    = phaseColor.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            // Instrucción contextual según la fase
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CardDark)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = when (phase) {
                                        "Inhalá"  -> "Inhalá lento por la nariz contando hasta 4. Sentí cómo se expande tu pecho."
                                        "Exhalá"  -> "Exhalá despacio por la boca contando hasta 4. Soltá toda la tensión."
                                        else      -> "Mantené el aire. Relajá los hombros. Quedate quieta."
                                    },
                                    fontSize  = 15.sp,
                                    color     = TextPrimary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp
                                )
                            }

                            // Indicadores de ciclos
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                repeat(BREATHING_TOTAL_CYCLES) { i ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (i == currentCycle - 1) 10.dp else 8.dp)
                                            .background(
                                                color = when {
                                                    i < currentCycle - 1  -> KairosBlue
                                                    i == currentCycle - 1 -> phaseColor
                                                    else                  -> TextSecondary.copy(alpha = 0.3f)
                                                },
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            // Secuencia de fases del ciclo
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                BREATHING_PHASES.forEachIndexed { i, breathPhase ->
                                    val isCurrentPhase = breathPhase.label == phase
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isCurrentPhase) phaseColor.copy(alpha = 0.2f)
                                                else Color.Transparent
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text       = breathPhase.label,
                                            fontSize   = 11.sp,
                                            color      = if (isCurrentPhase) phaseColor else TextSecondary.copy(alpha = 0.5f),
                                            fontWeight = if (isCurrentPhase) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                    if (i < BREATHING_PHASES.size - 1) {
                                        Text("→", fontSize = 10.sp, color = TextSecondary.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text     = if (isFinished) "Cerrar" else "Omitir ejercicio",
                    fontSize = 13.sp,
                    color    = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}