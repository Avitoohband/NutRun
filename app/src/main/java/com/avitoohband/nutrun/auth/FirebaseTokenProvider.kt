package com.avitoohband.nutrun.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class FirebaseTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun currentUserId(): String? = auth()?.currentUser?.uid

    suspend fun idToken(forceRefresh: Boolean = false): String? {
        val user = auth()?.currentUser ?: return null
        return suspendCancellableCoroutine { continuation ->
            user.getIdToken(forceRefresh).addOnCompleteListener { task ->
                continuation.resume(task.result?.token.takeIf { task.isSuccessful })
            }
        }
    }

    private fun auth(): FirebaseAuth? {
        val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
        return app?.let(FirebaseAuth::getInstance)
    }
}
