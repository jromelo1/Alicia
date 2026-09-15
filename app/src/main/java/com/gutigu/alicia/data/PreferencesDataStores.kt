package com.gutigu.alicia.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Instancias singleton de Preferences DataStore.
 *
 * Al declararse como extensiones de nivel superior la librería garantiza
 * que sólo existe UNA instancia por proceso para cada nombre.
 *
 * Archivos generados:
 *   files/datastore/profile_prefs.preferences_pb
 *   files/datastore/checkin_config.preferences_pb
 */

/** Almacena el perfil de usuario (rol + nombre). */
val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "profile_prefs"
)

/** Almacena la configuración del check-in diario (activado, hora, timeout, nombre). */
val Context.checkInDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "checkin_config"
)

/** Almacena el circleId de este dispositivo — quién es "mi círculo" en Firestore. */
val Context.circleSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "circle_session"
)
