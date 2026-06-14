package com.example.kairos.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.kairos.mobile.data.db.KairosDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gestiona el envío de alertas SMS de emergencia a los contactos de confianza del usuario.
 *
 * Cuando KAIROS confirma una crisis, este objeto abre la app de mensajes nativa del
 * dispositivo con el número y el texto de alerta prellenados. El usuario solo necesita
 * presionar "Enviar", lo que garantiza compatibilidad universal sin requerir permisos
 * especiales de SMS (`SEND_SMS`).
 *
 * **Decisión de diseño — app de mensajes vs envío directo:**
 * Se eligió `Intent.ACTION_SENDTO` en lugar de `SmsManager.sendTextMessage()` porque:
 * - No requiere el permiso `SEND_SMS` (sensible y rechazado frecuentemente por usuarios).
 * - Funciona en todos los operadores y configuraciones de dispositivo.
 * - Le da al usuario control final sobre el envío, evitando envíos accidentales.
 * - Es más confiable en dispositivos con apps de mensajes de terceros (WhatsApp, etc.).
 *
 * **Mecanismo de cooldown:**
 * Para evitar abrir la app de SMS múltiples veces durante el mismo episodio
 * (por ejemplo, si llegan múltiples mensajes desde el reloj), se implementa
 * un cooldown de 5 minutos entre alertas consecutivas.
 */
object SmsAlertManager {

    private const val TAG = "SmsAlertManager"

    /**
     * Mensaje de alerta que se prerrellena en la app de SMS.
     *
     * Incluye instrucciones concretas para el contacto de confianza,
     * basadas en principios de acompañamiento en crisis de pánico
     * (Teoría Polivagal — estimulación del sistema parasimpático mediante calma).
     */
    private const val SMS_MESSAGE =
        "🚨 KAIROS - Crisis de ansiedad detectada\n\n" +
                "Tu contacto de confianza en KAIROS necesita ayuda ahora.\n\n" +
                "Qué hacer:\n" +
                "1. Llamale o escribile ya\n" +
                "2. Hablale con calma, no la apures\n" +
                "3. Quedáte en línea hasta que se sienta mejor\n" +
                "Una crisis de pánico no es peligrosa pero necesita acompañamiento. " +
                "Tu presencia ayuda muchísimo."

    /**
     * Timestamp de la última alerta enviada, en milisegundos (epoch).
     * Se usa para calcular si el cooldown sigue activo.
     */
    private var lastAlertTimestamp = 0L

    /**
     * Tiempo mínimo entre alertas consecutivas: 5 minutos.
     * Evita abrir la app de SMS repetidamente durante el mismo episodio de crisis.
     */
    private const val COOLDOWN_MS = 5 * 60 * 1000L

    /**
     * Prepara y abre la app de SMS nativa para enviar alertas a los contactos activos.
     *
     * Se ejecuta en [Dispatchers.Main] porque `startActivity` debe llamarse
     * desde el hilo principal. La lectura de contactos desde Room se hace
     * dentro del mismo contexto para simplificar el flujo.
     *
     * **Normalización de número argentino:**
     * Los números en formato WhatsApp incluyen el "9" después de +54 (`+549...`),
     * que no es válido para SMS en Argentina. Se elimina automáticamente: `+549... → +54...`
     *
     * **Manejo de múltiples contactos:**
     * Si hay más de un contacto activo, abre la app de SMS una vez por cada uno.
     * El cooldown aplica al conjunto completo, no por contacto individual.
     *
     * @param context Contexto necesario para acceder a Room y lanzar el Intent.
     * @return [Result.success] con la cantidad de apps de SMS abiertas (0 si cooldown activo),
     *         o [Result.failure] si no hay contactos configurados o si ocurre un error.
     */
    suspend fun sendEmergencyAlert(context: Context): Result<Int> = withContext(Dispatchers.Main) {
        val now = System.currentTimeMillis()

        // Verificamos el cooldown antes de hacer cualquier operación
        if (now - lastAlertTimestamp < COOLDOWN_MS) {
            val remainingSecs = (COOLDOWN_MS - (now - lastAlertTimestamp)) / 1000
            Log.d(TAG, "Alerta ignorada — cooldown activo ($remainingSecs s restantes)")
            return@withContext Result.success(0)
        }
        lastAlertTimestamp = now

        try {
            val contacts = KairosDatabase.getInstance(context).kairosDao().getActiveContacts()

            if (contacts.isEmpty()) {
                Log.w(TAG, "No hay contactos de confianza configurados")
                return@withContext Result.failure(Exception("Sin contactos configurados"))
            }

            var sentCount = 0

            contacts.forEach { contact ->
                // Normalización del número: eliminamos el "9" del formato WhatsApp argentino
                // +549XXXXXXXXXX → +54XXXXXXXXXX (formato válido para SMS)
                val phone = contact.phoneNumber.let { num ->
                    if (num.startsWith("+549")) "+54${num.substring(4)}" else num
                }

                // ACTION_SENDTO con "smsto:" abre directamente la app de SMS
                // con el número y el cuerpo del mensaje prellenados
                // FLAG_ACTIVITY_NEW_TASK es obligatorio cuando se lanza desde un contexto
                // que no es una Activity (por ejemplo, desde un Service o BroadcastReceiver)
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