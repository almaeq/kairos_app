package com.example.kairos.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
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
import androidx.lifecycle.lifecycleScope
import com.example.kairos.mobile.data.db.KairosDatabase
import com.example.kairos.mobile.data.db.TrustedContact
import kotlinx.coroutines.launch

class CrisisAlertActivity : ComponentActivity() {

    companion object {
        private const val SMS_MESSAGE =
            "🚨 KAIROS: Necesito ayuda. Mi app detectó una crisis de ansiedad y no respondí en 30 segundos. " +
                    "Por favor comunicate conmigo."

        fun launch(context: Context) {
            val intent = Intent(context, CrisisAlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dao = KairosDatabase.getInstance(this).kairosDao()

        setContent {
            var contacts by remember { mutableStateOf<List<TrustedContact>>(emptyList()) }
            val sentSet  = remember { mutableStateSetOf<Int>() }

            LaunchedEffect(Unit) {
                contacts = dao.getActiveContacts()
            }

            CrisisAlertScreen(
                contacts = contacts,
                sentSet  = sentSet,
                onSendTo = { contact ->
                    val phone = contact.phoneNumber.let { num ->
                        if (num.startsWith("+549")) "+54${num.substring(4)}" else num
                    }
                    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:$phone")
                        putExtra("sms_body", SMS_MESSAGE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(smsIntent)
                    sentSet.add(contact.id)
                    Log.d("CrisisAlert", "SMS abierto → ${contact.name} ($phone)")
                },
                onDismiss = { finish() }
            )
        }
    }
}

@Composable
fun CrisisAlertScreen(
    contacts:  List<TrustedContact>,
    sentSet:   Set<Int>,
    onSendTo:  (TrustedContact) -> Unit,
    onDismiss: () -> Unit
) {
    val KairosRed     = Color(0xFFEF4444)
    val KairosGreen   = Color(0xFF00E5A0)
    val Background    = Color(0xFF0A0E1A)
    val CardDark      = Color(0xFF111827)
    val TextPrimary   = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFF94A3B8)

    val allSent = contacts.isNotEmpty() && contacts.all { sentSet.contains(it.id) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.12f,
        animationSpec = infiniteRepeatable(
            animation  = tween(500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Ícono pulsante
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale)
                    .background(KairosRed.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🚨", fontSize = 32.sp)
            }

            Text(
                text       = "Crisis detectada",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = KairosRed,
                textAlign  = TextAlign.Center
            )

            Text(
                text      = "Avisá a tus contactos de confianza.\nTocá cada botón para abrir el mensaje listo para enviar.",
                fontSize  = 13.sp,
                color     = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text      = "No tenés contactos de confianza configurados.\nAndá a Configuración → Contactos para agregar.",
                        fontSize  = 13.sp,
                        color     = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                contacts.forEach { contact ->
                    val yaEnviado = sentSet.contains(contact.id)
                    Button(
                        onClick  = { if (!yaEnviado) onSendTo(contact) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (yaEnviado) KairosGreen.copy(alpha = 0.2f)
                            else KairosRed.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text       = contact.name,
                                    fontSize   = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = if (yaEnviado) KairosGreen else TextPrimary
                                )
                                Text(
                                    text     = contact.phoneNumber,
                                    fontSize = 11.sp,
                                    color    = TextSecondary
                                )
                            }
                            Text(
                                text     = if (yaEnviado) "✓ Listo" else "Enviar →",
                                fontSize = 13.sp,
                                color    = if (yaEnviado) KairosGreen else KairosRed
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (allSent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KairosGreen.copy(alpha = 0.1f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text      = "✓ Todos los contactos fueron avisados",
                        fontSize  = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color     = KairosGreen,
                        textAlign = TextAlign.Center
                    )
                }
            }

            TextButton(
                onClick  = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text  = if (allSent) "Cerrar" else "Omitir por ahora",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}