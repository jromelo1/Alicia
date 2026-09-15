package com.gutigu.alicia.feature.medications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.gutigu.alicia.data.AppDatabase
import com.gutigu.alicia.data.CircleSessionRepository
import com.gutigu.alicia.data.FirestoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MedicationResponseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MEDICATION_TAKEN) return
        val id = intent.getIntExtra(MedicationWorker.KEY_MED_ID, -1)
        if (id < 0) return

        // Cancelar la notificación
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(MED_NOTIFICATION_BASE + id)

        // Registrar la hora en que se tomó (para el Dashboard Familiar)
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getInstance(context).medicationDao()
            dao.markTaken(id, System.currentTimeMillis())

            // Reflejar en Firestore para que el Familiar lo vea — un BroadcastReceiver
            // no participa de Hilt, así que se construye a mano igual que en
            // CheckInTimeoutWorker (mismo patrón ya establecido en el codebase).
            val circleId = CircleSessionRepository(context).getCircleId()
            if (circleId != null) {
                val firestoreRepository = FirestoreRepository(Firebase.firestore, Firebase.auth, FirebaseMessaging.getInstance())
                dao.getById(id)?.let { updated -> firestoreRepository.publishMedication(circleId, updated) }
            }
        }
    }
}
