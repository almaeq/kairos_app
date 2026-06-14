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
import com.example.kairos.mobile.techniques.GROUNDING_STEPS
import com.example.kairos.mobile.techniques.GroundingState

/**
 * Activity que muestra el ejercicio de grounding 5-4-3-2-1 en el teléfono
 * durante el Modo Crisis.
 *
 * Al igual que [BreathingActivity], esta pantalla es un espejo visual del ejercicio
 * que controla el reloj. Observa [GroundingState] y renderiza el paso actual
 * sin gestionar el timing ni el avance de pasos.
 *
 * Se lanza desde [KairosPhoneListener] cuando llega un mensaje `/kairos/grounding/paso`
 * con un valor entre 1 y 5.
 */
class GroundingActivity : ComponentActivity() {

    companion object {
        /**
         * Lanza la Activity desde cualquier contexto.
         *
         * [Intent.FLAG_ACTIVITY_CLEAR_TOP] evita apilar múltiples instancias
         * si el reloj envía actualizaciones de paso mientras la Activity ya está abierta.
         *
         * @param context Contexto desde el que se lanza la Activity.
         */
        fun launch(context: Context) {
            context.startActivity(
                Intent(context, GroundingActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // El paso actual se actualiza desde KairosPhoneListener
            // cuando llega el mensaje /kairos/grounding/paso desde el reloj
            val currentStep by GroundingState.currentStep.collectAsState()
            GroundingPhoneScreen(
                currentStep = currentStep,
                onDismiss   = { finish() }
            )
        }
    }
}

/**
 * Pantalla del ejercicio de grounding 5-4-3-2-1 en el teléfono.
 *
 * Muestra tres estados según [currentStep]:
 * - **Esperando** (`-1`): el reloj aún no inició el ejercicio.
 * - **Paso activo** (`5`, `4`, `3`, `2`, `1`): círculo con número, emoji e instrucción.
 * - **Completado** (`0`): mensaje de cierre con checkmark.
 *
 * **Indicadores de progreso:**
 * Los 5 puntos en la parte inferior muestran el avance:
 * - Azul → paso activo.
 * - Verde → pasos ya completados (número mayor al actual, ya que la secuencia es descendente).
 * - Gris → pasos pendientes.
 *
 * @param currentStep Número del paso activo recibido desde [GroundingState].
 *        Valores posibles: -1 (esperando), 5–1 (paso activo), 0 (completado).
 * @param onDismiss Callback para cerrar la Activity (botón "Cerrar" u "Omitir").
 */
@Composable
fun GroundingPhoneScreen(
    currentStep: Int,
    onDismiss:   () -> Unit
) {
    val KairosBlue    = Color(0xFF3B82F6)
    val KairosGreen   = Color(0xFF00E5A0)
    val Background    = Color(0xFF0A0E1A)
    val CardDark      = Color(0xFF111827)
    val TextPrimary   = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFF94A3B8)

    // Buscamos el paso actual en la lista de pasos definidos
    val step       = GROUNDING_STEPS.find { it.number == currentStep }
    val isFinished = currentStep == 0

    // Animación de respiración suave del círculo principal — independiente del paso
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    val circleScale by infiniteTransition.animateFloat(
        initialValue  = 0.9f,
        targetValue   = 1.05f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circleScale"
    )

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text       = "Ejercicio de Grounding",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = KairosBlue
            )
            Text(
                text     = "Seguí las instrucciones del reloj",
                fontSize = 13.sp,
                color    = TextSecondary
            )

            // AnimatedContent hace crossfade entre los tres estados de la pantalla
            AnimatedContent(
                targetState    = currentStep,
                transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(400)) },
                label          = "stepTransition"
            ) { stepNum ->
                val s = GROUNDING_STEPS.find { it.number == stepNum }

                when {
                    isFinished -> {
                        // ── Estado: ejercicio completado ──────────────────
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier            = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier         = Modifier
                                    .size(120.dp)
                                    .background(KairosGreen.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✓", fontSize = 48.sp, color = KairosGreen)
                            }
                            Text(
                                text       = "Ejercicio completado",
                                fontSize   = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color      = KairosGreen,
                                textAlign  = TextAlign.Center
                            )
                            Text(
                                text       = "Respirá profundo. Estás en un lugar seguro.\nTu sistema nervioso se está calmando.",
                                fontSize   = 14.sp,
                                color      = TextSecondary,
                                textAlign  = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    s != null -> {
                        // ── Estado: paso activo ───────────────────────────
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier            = Modifier.fillMaxWidth()
                        ) {
                            // Círculo principal con número y emoji del sentido activo
                            Box(
                                modifier         = Modifier
                                    .size(160.dp)
                                    .scale(circleScale)
                                    .background(KairosBlue.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text       = s.number.toString(),
                                        fontSize   = 56.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = KairosBlue
                                    )
                                    Text(text = s.emoji, fontSize = 24.sp)
                                }
                            }

                            Text(
                                text          = s.sense.uppercase(),
                                fontSize      = 13.sp,
                                fontWeight    = FontWeight.SemiBold,
                                color         = KairosBlue,
                                letterSpacing = 2.sp
                            )

                            // Instrucción completa del paso actual
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CardDark)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text       = s.audioText,
                                    fontSize   = 15.sp,
                                    color      = TextPrimary,
                                    textAlign  = TextAlign.Center,
                                    lineHeight = 22.sp
                                )
                            }

                            // Indicadores de progreso: 5 puntos (uno por paso)
                            // Azul = activo, verde = completado, gris = pendiente
                            // La secuencia es descendente (5→1), por eso isDone = gs.number > s.number
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                GROUNDING_STEPS.forEach { gs ->
                                    val isActive = gs.number == s.number
                                    val isDone   = gs.number > s.number
                                    Box(
                                        modifier = Modifier
                                            .size(if (isActive) 10.dp else 8.dp)
                                            .background(
                                                color = when {
                                                    isActive -> KairosBlue
                                                    isDone   -> KairosGreen
                                                    else     -> TextSecondary.copy(alpha = 0.3f)
                                                },
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        // ── Estado: esperando inicio desde el reloj ───────
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier         = Modifier
                                    .size(100.dp)
                                    .scale(circleScale)
                                    .background(KairosBlue.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "5-4-3-2-1",
                                    fontSize   = 16.sp,
                                    color      = KairosBlue,
                                    fontWeight = FontWeight.Bold
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
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick  = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
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