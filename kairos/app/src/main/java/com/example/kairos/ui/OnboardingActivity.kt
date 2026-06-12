package com.example.kairos.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class OnboardingActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME    = "kairos_onboarding"
        private const val KEY_COMPLETED = "onboarding_completed"

        fun hasCompleted(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_COMPLETED, false)

        fun markCompleted(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_COMPLETED, true).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnboardingScreen(
                onFinish = {
                    markCompleted(this)
                    // Ir a términos de uso
                    TermsActivity.launchForOnboarding(this)
                    finish()
                }
            )
        }
    }
}

// ── Modelo de datos de cada página ────────────────────────────────────────────

data class OnboardingPage(
    val emoji:       String,
    val title:       String,
    val description: String,
    val detail:      String,
    val accentColor: Color
)

// ── Pantalla principal ────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {

    val Background    = Color(0xFF0A0E1A)
    val CardDark      = Color(0xFF111827)
    val KairosGreen   = Color(0xFF00E5A0)
    val KairosBlue    = Color(0xFF3B82F6)
    val KairosOrange  = Color(0xFFF59E0B)
    val KairosRed     = Color(0xFFEF4444)
    val TextPrimary   = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFFCBD5E1)

    val pages = listOf(
        OnboardingPage(
            emoji       = "🧠",
            title       = "Bienvenida a KAIROS",
            description = "Tu compañero silencioso durante las crisis de ansiedad.",
            detail      = "KAIROS monitorea tu frecuencia cardíaca de forma continua a través de tu smartwatch y activa soporte automático cuando detecta una posible crisis — sin que tengas que hacer nada.",
            accentColor = KairosBlue
        ),
        OnboardingPage(
            emoji       = "⌚",
            title       = "Detección automática",
            description = "El reloj trabaja, vos descansás.",
            detail      = "Un modelo de Machine Learning analiza tu ritmo cardíaco y variabilidad en tiempo real. Si detecta una desviación sostenida de tu baseline personal durante 3 minutos, activa el modo crisis — con ejercicios guiados de respiración y grounding.",
            accentColor = KairosGreen
        ),
        OnboardingPage(
            emoji       = "👥",
            title       = "Tu red de apoyo",
            description = "No estás sola cuando más lo necesitás.",
            detail      = "Agregá hasta 3 contactos de confianza — familiares, amigos o tu terapeuta. Si no respondés en 30 segundos, KAIROS te asistirá para avisarles con un mensaje claro de cómo ayudarte.",
            accentColor = KairosOrange
        ),
        OnboardingPage(
            emoji       = "📊",
            title       = "Tu perfil fisiológico",
            description = "KAIROS aprende cómo sos vos, no el promedio.",
            detail      = "En los primeros minutos de uso, el sistema calibra tu frecuencia cardíaca basal y variabilidad personal. Cuanto más tiempo uses el reloj en reposo, más precisa es la detección. Podés recalibrar cuando quieras.",
            accentColor = KairosRed
        )
    )

    var currentPage by remember { mutableStateOf(0) }
    val isLast = currentPage == pages.size - 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // ── Indicadores de página ─────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentPage) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (index == currentPage) pages[currentPage].accentColor
                                else TextSecondary.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // ── Contenido animado ─────────────────────────────────────────────
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "onboarding_page"
            ) { page ->
                val data = pages[page]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Emoji grande
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(data.accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(data.emoji, fontSize = 52.sp)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text       = data.title,
                        fontSize   = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color      = TextPrimary,
                        textAlign  = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text      = data.description,
                        fontSize  = 16.sp,
                        color     = data.accentColor,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardDark)
                            .padding(20.dp)
                    ) {
                        Text(
                            text      = data.detail,
                            fontSize  = 14.sp,
                            color     = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 21.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Botones ───────────────────────────────────────────────────────
            Button(
                onClick = {
                    if (isLast) onFinish()
                    else currentPage++
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = pages[currentPage].accentColor
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text       = if (isLast) "Comenzar →" else "Siguiente →",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF0A0E1A)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isLast) {
                TextButton(
                    onClick  = onFinish,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text     = "Saltar",
                        fontSize = 13.sp,
                        color    = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}