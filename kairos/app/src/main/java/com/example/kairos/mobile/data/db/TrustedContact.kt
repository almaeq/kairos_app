package com.example.kairos.mobile.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Room que representa un contacto de confianza del usuario.
 *
 * Los contactos activos reciben un SMS de alerta automático cuando KAIROS
 * detecta y confirma una crisis de ansiedad. El mensaje incluye la ubicación
 * del usuario y pasos de acción para que el contacto pueda asistirlo.
 *
 * El usuario puede tener múltiples contactos registrados y activar o desactivar
 * cada uno individualmente sin necesidad de eliminarlo.
 *
 * @property id Clave primaria autogenerada por Room.
 * @property name Nombre del contacto tal como lo ingresó el usuario.
 *           Se muestra en la pantalla de configuración y en el preview del SMS.
 * @property phoneNumber Número de teléfono al que se envía la alerta SMS.
 *           Debe incluir el código de país para garantizar la entrega
 *           en escenarios sin conexión a internet (SMS nativo).
 * @property isActive Indica si este contacto recibirá alertas SMS.
 *           `true` = activo (recibe alertas). `false` = desactivado temporalmente.
 *           Los contactos inactivos se conservan en la base de datos
 *           para poder reactivarlos sin tener que reingresarlos.
 */
@Entity(tableName = "trusted_contacts")
data class TrustedContact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phoneNumber: String,
    val isActive: Boolean = true
)