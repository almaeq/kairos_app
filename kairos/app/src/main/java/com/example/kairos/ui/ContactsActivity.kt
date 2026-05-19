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
import androidx.compose.foundation.shape.RoundedCornerShape
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

            // Abre el selector de contactos del sistema
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
                }
            )
        }
    }

    /**
     * Lee nombre y teléfono del URI devuelto por el picker de contactos.
     * Limpia el número dejando solo dígitos y el + inicial.
     */
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
                    arrayOf(contactId),
                    null
                )?.use { phoneCursor ->
                    if (phoneCursor.moveToFirst()) {
                        val raw = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.NUMBER))
                        phone = raw?.replace(Regex("[^+\\d]"), "")?.let { cleaned ->
                            when {
                                cleaned.startsWith("+") -> cleaned          // ya tiene código de país
                                cleaned.startsWith("0") -> "+54${cleaned.substring(1)}" // saca el 0 inicial
                                cleaned.length == 10    -> "+549$cleaned"    // número local de 10 dígitos
                                else                    -> cleaned          // dejar como está
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
    onDelete:    (TrustedContact) -> Unit
) {
    val Background    = Color(0xFF0A0E1A)
    val CardDark      = Color(0xFF111827)
    val KairosGreen   = Color(0xFF00E5A0)
    val KairosRed     = Color(0xFFEF4444)
    val TextPrimary   = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFF64748B)

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text("Contactos de confianza", fontSize = 20.sp,
                fontWeight = FontWeight.Bold, color = TextPrimary)

            Text(
                text      = "Durante una crisis, estas personas recibirán un SMS automático si no respondés en 30 segundos.",
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(contacts) { contact ->
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

            Spacer(modifier = Modifier.weight(1f))

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