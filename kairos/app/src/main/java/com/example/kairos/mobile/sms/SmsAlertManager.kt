package com.example.kairos.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.kairos.mobile.data.db.KairosDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsAlertManager {

    private const val TAG = "SmsAlertManager"

    private const val SMS_MESSAGE =
        "🚨 KAIROS - Crisis de ansiedad detectada\n\n" +
                "Tu contacto de confianza en KAIROS necesita ayuda ahora.\n\n" +
                "Qué hacer:\n" +
                "1. Llamale o escribile ya\n" +
                "2. Hablale con calma, no la apures\n" +
                "3. Quedáte en línea hasta que se sienta mejor\n" +
                "Una crisis de pánico no es peligrosa pero necesita acompañamiento. " +
                "Tu presencia ayuda muchísimo."

    // Cooldown de 5 minutos para no abrir la app de SMS múltiples veces por episodio
    private var lastAlertTimestamp = 0L
    private const val COOLDOWN_MS = 5 * 60 * 1000L

    suspend fun sendEmergencyAlert(context: Context): Result<Int> = withContext(Dispatchers.Main) {
        val now = System.currentTimeMillis()
        if (now - lastAlertTimestamp < COOLDOWN_MS) {
            Log.d(TAG, "Alerta ignorada — cooldown activo (${(COOLDOWN_MS - (now - lastAlertTimestamp)) / 1000}s restantes)")
            return@withContext Result.success(0)
        }
        lastAlertTimestamp = now

        try {
            val dao      = KairosDatabase.getInstance(context).kairosDao()
            val contacts = dao.getActiveContacts()

            if (contacts.isEmpty()) {
                Log.w(TAG, "No hay contactos de confianza configurados")
                return@withContext Result.failure(Exception("Sin contactos configurados"))
            }

            var sentCount = 0

            contacts.forEach { contact ->
                // Normalizar número: sacar el 9 después de +54 (formato WhatsApp → SMS)
                val phone = contact.phoneNumber.let { num ->
                    if (num.startsWith("+549")) "+54${num.substring(4)}" else num
                }

                // Abre la app de mensajes con número y texto prellenados
                // El usuario solo tiene que tocar "Enviar"
                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$phone")
                    putExtra("sms_body", SMS_MESSAGE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(smsIntent)
                sentCount++
                Log.d(TAG, "App de SMS abierta → ${contact.name} ($phone)")
            }

            Log.d(TAG, "Alerta preparada para $sentCount/${contacts.size} contactos")
            Result.success(sentCount)

        } catch (e: Exception) {
            Log.e(TAG, "Error en sendEmergencyAlert: ${e.message}")
            Result.failure(e)
        }
    }
}