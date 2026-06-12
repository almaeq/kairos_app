package com.example.kairos.ui

import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.kairos.mobile.data.db.KairosDatabase
import com.example.kairos.mobile.data.db.TrustedContact
import kotlinx.coroutines.launch

class ContactsActivity : ComponentActivity() {

    private val MAX_CONTACTS = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dao = KairosDatabase.getInstance(this).kairosDao()

        setContent {
            var contacts by remember { mutableStateOf<List<TrustedContact>>(emptyList()) }
            var errorMsg by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                contacts = dao.getActiveContacts()
            }

            val contactPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickContact()
            ) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                val (name, phone) = resolveContact(uri)
                if (name == null || phone == null) {
                    errorMsg = "Ese contacto no tiene número de teléfono guardado."
                    return@rememberLauncherForActivityResult
                }
                errorMsg = null
                lifecycleScope.launch {
                    dao.saveContact(TrustedContact(name = name, phoneNumber = phone))
                    contacts = dao.getActiveContacts()
                }
            }

            ContactsScreen(
                contacts    = contacts,
                maxContacts = MAX_CONTACTS,
                errorMsg    = errorMsg,
                onAdd       = { contactPickerLauncher.launch(null) },
                onDelete    = { contact ->
                    lifecycleScope.launch {
                        dao.deleteContact(contact)
                        contacts = dao.getActiveContacts()
                    }
                },
                onBack      = { finish() }   // ← agregar
            )
        }
    }

    private fun resolveContact(uri: Uri): Pair<String?, String?> {
        var name: String? = null
        var phone: String? = null

        contentResolver.query(
            uri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts._ID),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndexOrThrow(
                    ContactsContract.Contacts.DISPLAY_NAME))
                val contactId = cursor.getString(cursor.getColumnIndexOrThrow(
                    ContactsContract.Contacts._ID))

                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId), null
                )?.use { phoneCursor ->
                    if (phoneCursor.moveToFirst()) {
                        val raw = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.NUMBER))
                        phone = raw?.replace(Regex("[^+\\d]"), "")?.let { cleaned ->
                            when {
                                cleaned.startsWith("+") -> cleaned
                                cleaned.startsWith("0") -> "+54${cleaned.substring(1)}"
                                cleaned.length == 10    -> "+549$cleaned"
                                else                    -> cleaned
                            }
                        }
                    }
                }
            }
        }

        Log.d("ContactsActivity", "Contacto resuelto: $name → $phone")
        return Pair(name, phone)
    }
}

// ── Pantalla ──────────────────────────────────────────────────────────────────

@Composable
fun ContactsScreen(
    contacts:    List<TrustedContact>,
    maxContacts: Int,
    errorMsg:    String?,
    onAdd:       () -> Unit,
    onBack:      () -> Unit = {},
    onDelete:    (TrustedContact) -> Unit
) {
    val Background    = Color(0xFF0A0E1A)
    val CardDark      = Color(0xFF111827)
    val KairosGreen   = Color(0xFF00E5A0)
    val KairosBlue    = Color(0xFF3B82F6)
    val KairosOrange  = Color(0xFFF59E0B)
    val KairosRed     = Color(0xFFEF4444)
    val TextPrimary   = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFF94A3B8)

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick  = onBack,
                    modifier = Modifier.align(Alignment.CenterStart).size(36.dp)
                ) {
                    Text("←", fontSize = 20.sp, color = TextSecondary)
                }
                Text(
                    text       = "Contactos de Confianza",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary,
                    modifier   = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 44.dp)  // ← deja espacio para la flecha
                )
            }
            Text(
                text      = "Durante una crisis, estas personas recibirán un SMS si no respondés en 30 segundos.",
                fontSize  = 13.sp, color = TextSecondary, lineHeight = 18.sp
            )

            Text(
                text     = "${contacts.size} / $maxContacts contactos",
                fontSize = 12.sp,
                color    = if (contacts.size >= maxContacts) KairosRed else KairosGreen
            )

            errorMsg?.let {
                Text(text = it, fontSize = 12.sp, color = KairosRed)
            }

            // ── Lista de contactos ────────────────────────────────────────────
            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No hay contactos todavía.\nAgregá al menos uno.",
                        fontSize = 13.sp, color = TextSecondary)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    contacts.forEach { contact ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardDark).padding(16.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(contact.name, fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(contact.phoneNumber, fontSize = 13.sp, color = TextSecondary)
                            }
                            IconButton(onClick = { onDelete(contact) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                                    tint = KairosRed.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }

            // ── SMS que recibirá el contacto ──────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardDark)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📱 Mensaje que recibirán",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = KairosBlue)
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "🚨 KAIROS - Crisis de ansiedad detectada\n\n" +
                                    "Tu contacto de confianza en KAIROS necesita ayuda ahora.\n\n" +
                                    "Qué hacer:\n" +
                                    "1. Llamale o escribile ya\n" +
                                    "2. Hablale con calma, no la apures\n" +
                                    "3. Quedáte en línea hasta que se sienta mejor\n" +
                                    "Una crisis de pánico no es peligrosa pero necesita acompañamiento. " +
                                    "Tu presencia ayuda muchísimo.",
                            fontSize  = 12.sp,
                            color     = TextPrimary,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // ── Instrucciones para el contacto ────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KairosOrange.copy(alpha = 0.08f))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("💡 Qué pedirle a tu contacto que haga",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = KairosOrange)
                    Text(
                        text      = "Compartí estas instrucciones con las personas que agregues como contactos de confianza:",
                        fontSize  = 12.sp,
                        color     = TextSecondary,
                        lineHeight = 17.sp
                    )

                    InstructionItem(
                        numero = "1",
                        texto  = "Llamame o escribime de inmediato cuando recibas el mensaje.",
                        color  = KairosOrange
                    )
                    InstructionItem(
                        numero = "2",
                        texto  = "Hablame con calma y voz tranquila. No me apures ni me digas que me calme.",
                        color  = KairosOrange
                    )
                    InstructionItem(
                        numero = "3",
                        texto  = "Si atiendo, quedate en línea conmigo hasta que me sienta mejor.",
                        color  = KairosOrange
                    )
                    InstructionItem(
                        numero = "4",
                        texto  = "No es necesario que hagas nada heroico — solo estar presente ayuda muchísimo.",
                        color  = KairosOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick  = onAdd,
                enabled  = contacts.size < maxContacts,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = KairosGreen,
                    disabledContainerColor = TextSecondary.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF0A0E1A))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text       = if (contacts.size >= maxContacts) "Límite alcanzado (máx. $maxContacts)"
                    else "Agregar desde mis contactos",
                    color      = Color(0xFF0A0E1A),
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun InstructionItem(numero: String, texto: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(numero, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
        }
        Text(
            text      = texto,
            fontSize  = 12.sp,
            color     = Color(0xFFE2E8F0),
            lineHeight = 17.sp,
            modifier  = Modifier.weight(1f)
        )
    }
}