package com.avitoohband.nutrun.auth

import android.content.Context
import com.avitoohband.nutrun.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AuthenticatedAccount(
    val userId: String,
    val email: String,
    val trialStartedAtMillis: Long,
    val subscriber: Boolean
)

interface AuthenticationGateway {
    suspend fun authenticate(
        email: String,
        password: String,
        createAccount: Boolean
    ): Result<AuthenticatedAccount>
    suspend fun deleteAccount(): Result<Unit>
    fun signOut()
}

@Singleton
class FirebaseAuthenticationGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : AuthenticationGateway {
    private fun firebaseAuth(): FirebaseAuth? {
        val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
        return app?.let(FirebaseAuth::getInstance)
    }

    override suspend fun authenticate(
        email: String,
        password: String,
        createAccount: Boolean
    ): Result<AuthenticatedAccount> {
        val auth = firebaseAuth()
        if (auth == null) {
            return if (BuildConfig.DEBUG) {
                Result.success(
                    AuthenticatedAccount(
                        userId = "debug-${email.trim().lowercase().hashCode().toUInt()}",
                        email = email,
                        trialStartedAtMillis = System.currentTimeMillis(),
                        subscriber = false
                    )
                )
            } else {
                Result.failure(IllegalStateException("Firebase Authentication is not configured."))
            }
        }
        val task = if (createAccount) {
            auth.createUserWithEmailAndPassword(email, password)
        } else {
            auth.signInWithEmailAndPassword(email, password)
        }
        val userResult = suspendCancellableCoroutine<Result<com.google.firebase.auth.FirebaseUser>> { continuation ->
            task.addOnCompleteListener { result ->
                val user = result.result?.user
                if (result.isSuccessful && user?.email != null) {
                    continuation.resume(Result.success(user))
                } else {
                    continuation.resume(
                        Result.failure(
                            result.exception ?: IllegalStateException("Authentication failed.")
                        )
                    )
                }
            }
        }
        val user = userResult.getOrElse { return Result.failure(it) }
        if (BuildConfig.BACKEND_BASE_URL.isBlank()) {
            return if (BuildConfig.DEBUG) {
                Result.success(
                    AuthenticatedAccount(
                        userId = user.uid,
                        email = user.email.orEmpty(),
                        trialStartedAtMillis = System.currentTimeMillis(),
                        subscriber = false
                    )
                )
            } else {
                Result.failure(IllegalStateException("The NutRun backend is not configured."))
            }
        }
        return bootstrapSession(user)
    }

    override fun signOut() {
        firebaseAuth()?.signOut()
    }

    private suspend fun bootstrapSession(
        user: com.google.firebase.auth.FirebaseUser
    ): Result<AuthenticatedAccount> {
        val tokenResult = suspendCancellableCoroutine<Result<String>> { continuation ->
            user.getIdToken(false).addOnCompleteListener { task ->
                val token = task.result?.token
                continuation.resume(
                    if (task.isSuccessful && token != null) Result.success(token)
                    else Result.failure(task.exception ?: IllegalStateException("Could not authorize session."))
                )
            }
        }
        val token = tokenResult.getOrElse { return Result.failure(it) }
        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = URI("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/v1/session")
                    .toURL()
                    .openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 8_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write("{}".toByteArray()) }
                try {
                    if (connection.responseCode !in 200..299) {
                        throw IllegalStateException("The session service returned ${connection.responseCode}.")
                    }
                    val body = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    AuthenticatedAccount(
                        user.uid,
                        user.email.orEmpty(),
                        body.getLong("trialStartedAtMillis"),
                        body.optString("kind") == "SUBSCRIBER"
                    )
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        val auth = firebaseAuth()
        val user = auth?.currentUser
        if (user == null || BuildConfig.BACKEND_BASE_URL.isBlank()) {
            return if (BuildConfig.DEBUG) Result.success(Unit)
            else Result.failure(IllegalStateException("Account deletion service is not configured."))
        }
        val tokenResult = suspendCancellableCoroutine<Result<String>> { continuation ->
            user.getIdToken(false).addOnCompleteListener { task ->
                val token = task.result?.token
                continuation.resume(
                    if (task.isSuccessful && token != null) Result.success(token)
                    else Result.failure(task.exception ?: IllegalStateException("Could not authorize deletion."))
                )
            }
        }
        val token = tokenResult.getOrElse { return Result.failure(it) }
        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = URI("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/v1/account")
                    .toURL()
                    .openConnection() as HttpURLConnection
                connection.requestMethod = "DELETE"
                connection.connectTimeout = 8_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Authorization", "Bearer $token")
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                        throw IllegalStateException("The account deletion service returned ${connection.responseCode}.")
                    }
                    auth.signOut()
                } finally {
                    connection.disconnect()
                }
            }
        }
    }
}
