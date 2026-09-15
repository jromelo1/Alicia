package com.gutigu.alicia.feature.medications

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gutigu.alicia.ui.theme.AliciaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity de pantalla completa que muestra la foto de la pastilla al llegar
 * la hora del recordatorio. Aparece sobre la pantalla bloqueada y enciende
 * la pantalla del dispositivo.
 *
 * Lanzada por [MedicationWorker] con FLAG_ACTIVITY_NEW_TASK.
 */
@AndroidEntryPoint
class MedicationAlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MED_ID    = "med_id"
        const val EXTRA_MED_NAME  = "med_name"
        const val EXTRA_MED_DOSE  = "med_dose"
        const val EXTRA_PHOTO_URL = "photo_url"
        const val EXTRA_WITH_FOOD = "with_food"
        const val EXTRA_NOTE      = "note"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mostrar sobre la pantalla bloqueada y encender pantalla (API 27+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val medId    = intent.getIntExtra(EXTRA_MED_ID, -1)
        val medName  = intent.getStringExtra(EXTRA_MED_NAME) ?: "Medicamento"
        val medDose  = intent.getStringExtra(EXTRA_MED_DOSE) ?: ""
        val photoUrl = intent.getStringExtra(EXTRA_PHOTO_URL)
        val withFood = intent.getBooleanExtra(EXTRA_WITH_FOOD, false)
        val note     = intent.getStringExtra(EXTRA_NOTE) ?: ""

        enableEdgeToEdge()

        setContent {
            AliciaTheme {
                MedicationAlarmScreen(
                    medId    = medId,
                    medName  = medName,
                    medDose  = medDose,
                    photoUrl = photoUrl,
                    withFood = withFood,
                    note     = note,
                    onFinish = { finish() }
                )
            }
        }
    }

    /**
     * El usuario no puede cerrar la pantalla con Back — debe presionar
     * "Ya la tomé" o esperar que el timeout cierre la pantalla.
     */
    @Suppress("DEPRECATION")
    @Deprecated("Overrides deprecated super method intentionally")
    override fun onBackPressed() {
        // No-op: impedimos la salida fácil por accidente
    }
}
