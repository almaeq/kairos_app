package com.example.kairos.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

/**
 * Activity que muestra los Términos de uso y Política de privacidad de KAIROS.
 *
 * Opera en dos modos según el extra `"mode"` del Intent:
 * - **Onboarding** (`"onboarding"`): el usuario debe scrollear hasta el final y aceptar
 *   explícitamente antes de poder usar la app. El botón de aceptación se habilita
 *   solo cuando el scroll llega al final del documento.
 * - **Lectura** (`"reading"`): modo informativo, accesible desde [ProfileActivity]
 *   via el ícono de información. Solo muestra el botón "Volver".
 *
 * El estado de aceptación se persiste en SharedPreferences via [hasAccepted] y [markAccepted].
 */
class TermsActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME   = "kairos_terms"
        private const val KEY_ACCEPTED = "terms_accepted"

        /**
         * Verifica si el usuario ya aceptó los términos de uso.
         *
         * @param context Contexto para acceder a SharedPreferences.
         * @return `true` si los términos fueron aceptados, `false` en caso contrario.
         */
        fun hasAccepted(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACCEPTED, false)

        /**
         * Marca los términos como aceptados en SharedPreferences.
         * Se invoca cuando el usuario presiona "Acepto y continuar".
         *
         * @param context Contexto para acceder a SharedPreferences.
         */
        fun markAccepted(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ACCEPTED, true).apply()
        }

        /**
         * Lanza la Activity en modo onboarding.
         *
         * El usuario debe leer y aceptar los términos para continuar.
         * [Intent.FLAG_ACTIVITY_NEW_TASK] es necesario porque se lanza
         * desde [OnboardingActivity.onFinish] después de llamar a `finish()`.
         *
         * @param context Contexto desde el que se lanza la Activity.
         */
        fun launchForOnboarding(context: Context) {
            context.startActivity(
                Intent(context, TermsActivity::class.java).apply {
                    putExtra("mode", "onboarding")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        /**
         * Lanza la Activity en modo lectura.
         *
         * Solo muestra el contenido con el botón "Volver", sin requerir aceptación.
         * Se usa desde [ProfileActivity] cuando el usuario toca el ícono de información.
         *
         * @param context Contexto desde el que se lanza la Activity.
         */
        fun launchForReading(context: Context) {
            context.startActivity(
                Intent(context, TermsActivity::class.java).apply {
                    putExtra("mode", "reading")
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "reading"

        setContent {
            TermsScreen(
                isOnboarding = mode == "onboarding",
                onAccept     = {
                    markAccepted(this)
                    finish()
                },
                onBack = { finish() }
            )
        }
    }
}

/**
 * Pantalla de Términos de uso y Política de privacidad.
 *
 * En modo onboarding ([isOnboarding] = `true`):
 * - Muestra un aviso de consentimiento al inicio del documento.
 * - El botón "Acepto y continuar" solo se habilita cuando el usuario
 *   scrolleó hasta 100px del final del documento ([isScrolledToBottom]).
 * - Incluye el botón "No acepto — salir" para rechazar los términos.
 *
 * En modo lectura ([isOnboarding] = `false`):
 * - Solo muestra el botón "← Volver".
 *
 * Los botones están fijos al pie de la pantalla fuera del área scrolleable,
 * para que siempre sean accesibles independientemente de la posición del scroll.
 *
 * @param isOnboarding `true` si se requiere aceptación, `false` para solo lectura.
 * @param onAccept Callback invocado al aceptar los términos (solo en modo onboarding).
 * @param onBack Callback para cerrar la pantalla.
 */
@Composable
fun TermsScreen(
    isOnboarding: Boolean,
    onAccept:     () -> Unit,
    onBack:       () -> Unit
) {
    val Background    = Color(0xFF0A0E1A)
    val CardDark      = Color(0xFF111827)
    val KairosGreen   = Color(0xFF00E5A0)
    val KairosBlue    = Color(0xFF3B82F6)
    val KairosOrange  = Color(0xFFF59E0B)
    val TextPrimary   = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFF64748B)

    val scrollState = rememberScrollState()
    // El botón de aceptación se habilita cuando el scroll está a menos de 100px del final
    val isScrolledToBottom = scrollState.value >= scrollState.maxValue - 100

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Área scrolleable con el contenido de los términos
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text       = "Términos de uso y\nPolítica de privacidad",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary,
                    lineHeight = 26.sp
                )
                Text(
                    text     = "Última actualización: mayo 2026",
                    fontSize = 11.sp,
                    color    = TextSecondary
                )

                // Aviso de consentimiento visible solo en modo onboarding
                if (isOnboarding) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(KairosBlue.copy(alpha = 0.1f))
                            .padding(16.dp)
                    ) {
                        Text(
                            text       = "Leé estos términos antes de activar el monitoreo. Necesitamos tu consentimiento explícito para procesar tus datos biométricos.",
                            fontSize   = 13.sp,
                            color      = KairosBlue,
                            lineHeight = 18.sp
                        )
                    }
                }

                TermsSection(
                    title      = "1. ¿Qué es KAIROS?",
                    content    = "KAIROS es una aplicación de bienestar personal diseñada para asistir a personas con trastornos de ansiedad. El sistema monitorea señales fisiológicas a través de un smartwatch Wear OS para detectar posibles episodios de crisis y activar herramientas de apoyo inmediato.",
                    cardColor  = CardDark,
                    titleColor = KairosBlue,
                    textColor  = TextPrimary
                )

                TermsSection(
                    title      = "2. Datos que recopila KAIROS",
                    content    = "KAIROS recopila y procesa los siguientes datos:\n\n• Frecuencia cardíaca (BPM) — medida continuamente por el sensor del reloj\n• Variabilidad de la frecuencia cardíaca (RMSSD) — calculada a partir de los datos de HR\n• Datos del acelerómetro — para distinguir estrés de actividad física\n• Contactos de confianza — nombre y número de teléfono que vos ingresás\n• Historial de episodios de crisis — fecha, hora, HR, RMSSD y duración\n\nTodos estos datos se almacenan exclusivamente en tu dispositivo. KAIROS no tiene servidores propios ni envía tus datos a internet.",
                    cardColor  = CardDark,
                    titleColor = KairosBlue,
                    textColor  = TextPrimary
                )

                TermsSection(
                    title      = "3. Para qué se usan tus datos",
                    content    = "Tus datos biométricos se usan únicamente para:\n\n• Calibrar tu perfil fisiológico personal (baseline)\n• Detectar desviaciones que puedan indicar una crisis de ansiedad\n• Activar ejercicios de intervención (respiración y grounding)\n• Registrar episodios en la bitácora para tu seguimiento personal\n• Generar reportes que vos podés compartir con tu terapeuta\n\nKAIROS no comparte, vende ni transmite tus datos a terceros bajo ninguna circunstancia.",
                    cardColor  = CardDark,
                    titleColor = KairosBlue,
                    textColor  = TextPrimary
                )

                TermsSection(
                    title      = "4. Consentimiento para alertas SMS",
                    content    = "KAIROS puede asistirte en el envío de SMS de emergencia a tus contactos de confianza cuando se detecta una crisis. El sistema abrirá la app de mensajes de tu teléfono con el mensaje prellenado — vos tenés el control final de enviar o no.\n\nAl agregar un contacto de confianza, confirmás que esa persona ha dado su consentimiento para recibir estas alertas.",
                    cardColor  = CardDark,
                    titleColor = KairosOrange,
                    textColor  = TextPrimary
                )

                // Aviso médico destacado — siempre visible en ambos modos
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KairosOrange.copy(alpha = 0.1f))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text       = "⚠️ Aviso médico importante",
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color      = KairosOrange
                        )
                        Text(
                            text       = "KAIROS es una herramienta de apoyo al bienestar personal. NO es un dispositivo médico certificado, NO reemplaza el diagnóstico ni el tratamiento profesional, y NO debe usarse como sustituto de atención médica de emergencia.\n\nSi estás experimentando una emergencia médica, llamá al número de emergencias de tu país (107 en Argentina).",
                            fontSize   = 13.sp,
                            color      = TextPrimary,
                            lineHeight = 19.sp
                        )
                    }
                }

                TermsSection(
                    title      = "5. Tu privacidad",
                    content    = "Tus datos de salud son sensibles y los tratamos con el máximo cuidado:\n\n• Almacenamiento local: todos los datos permanecen en tu dispositivo\n• Sin sincronización en la nube: KAIROS funciona completamente offline\n• Control total: podés borrar todos tus datos en cualquier momento desde la configuración\n• Sin publicidad: KAIROS no muestra anuncios ni comparte datos con anunciantes",
                    cardColor  = CardDark,
                    titleColor = KairosGreen,
                    textColor  = TextPrimary
                )

                TermsSection(
                    title      = "6. Limitaciones del sistema",
                    content    = "KAIROS usa algoritmos de machine learning para detectar posibles crisis de ansiedad. El sistema puede generar:\n\n• Falsos positivos: detectar una crisis cuando no la hay (ej: durante ejercicio intenso)\n• Falsos negativos: no detectar una crisis real\n\nEl sistema está diseñado para minimizar ambos casos, pero ningún algoritmo es perfecto. Por esta razón existe el paso de confirmación manual antes de activar las alertas.",
                    cardColor  = CardDark,
                    titleColor = KairosBlue,
                    textColor  = TextPrimary
                )

                TermsSection(
                    title      = "7. Tu consentimiento",
                    content    = "Al usar KAIROS, aceptás:\n\n• El procesamiento de tus datos biométricos para los fines descritos\n• Las limitaciones técnicas del sistema de detección\n• Que KAIROS es una herramienta de apoyo y no un dispositivo médico\n• Que sos mayor de 18 años o tenés autorización de un adulto responsable",
                    cardColor  = CardDark,
                    titleColor = KairosBlue,
                    textColor  = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Botones fijos al pie — fuera del área scrolleable para siempre estar visibles
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Background)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isOnboarding) {
                    // El botón se habilita solo cuando el usuario llegó al final del documento
                    Button(
                        onClick  = onAccept,
                        enabled  = isScrolledToBottom,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor         = KairosGreen,
                            disabledContainerColor = KairosGreen.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text       = if (isScrolledToBottom) "Acepto y continuar" else "Scrolleá para leer todo",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (isScrolledToBottom) Color(0xFF0A0E1A)
                            else Color(0xFF0A0E1A).copy(alpha = 0.5f)
                        )
                    }
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("No acepto — salir", fontSize = 13.sp, color = TextSecondary)
                    }
                } else {
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("← Volver", fontSize = 13.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

/**
 * Composable auxiliar que renderiza una sección numerada de los términos de uso.
 *
 * @param title Título de la sección (por ejemplo: "1. ¿Qué es KAIROS?").
 * @param content Texto completo de la sección. Admite saltos de línea con `\n`.
 * @param cardColor Color de fondo de la tarjeta.
 * @param titleColor Color del título, varía por sección para jerarquía visual.
 * @param textColor Color del texto de contenido.
 */
@Composable
fun TermsSection(
    title:      String,
    content:    String,
    cardColor:  Color,
    titleColor: Color,
    textColor:  Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title,   fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
            Text(text = content, fontSize = 13.sp, color = textColor, lineHeight = 19.sp)
        }
    }
}