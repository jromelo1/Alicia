package com.gutigu.alicia.feature.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.gutigu.alicia.data.suspendRunCatching
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    val currentUser: FirebaseUser? get() = auth.currentUser
    val uid: String? get() = auth.currentUser?.uid
    val isLoggedIn: Boolean get() = auth.currentUser != null

    suspend fun signIn(email: String, password: String): Result<Unit> = suspendRunCatching {
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    /**
     * Sesión anónima para el Adulto Mayor — no pide correo ni contraseña, es
     * instantánea. No sobrevive una desinstalación o cambio de teléfono por sí sola:
     * cada vez que se llama aquí sin sesión previa, Firebase crea un uid nuevo. Por eso
     * el círculo (los datos que importan: nombre, horario, historial, miembros) vive en
     * Firestore bajo el código de invitación, no bajo este uid — así
     * [com.gutigu.alicia.feature.circle.CircleOnboardingViewModel.recoverCircleAsElder]
     * puede reconectar un uid nuevo al mismo círculo con solo el código, sin perder nada
     * de lo que ya está en Firestore.
     */
    suspend fun signInAnonymously(): Result<Unit> = suspendRunCatching {
        Log.d(TAG, "signInAnonymously: iniciando (currentUser=${auth.currentUser?.uid})")
        if (auth.currentUser == null) auth.signInAnonymously().await()
        Log.d(TAG, "signInAnonymously: listo (uid=${auth.currentUser?.uid})")
        Unit
    }

    suspend fun signUp(email: String, password: String): Result<Unit> = suspendRunCatching {
        auth.createUserWithEmailAndPassword(email, password).await()
        Unit
    }

    fun signOut() {
        auth.signOut()
    }
}
