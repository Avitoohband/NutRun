package com.avitoohband.nutrun.billing

import com.avitoohband.nutrun.BuildConfig
import com.avitoohband.nutrun.auth.FirebaseTokenProvider
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class VerifiedEntitlement(
    val subscriber: Boolean,
    val acknowledged: Boolean = false
)

@Singleton
class BillingVerificationService @Inject constructor(
    private val tokenProvider: FirebaseTokenProvider
) {
    suspend fun verify(
        packageName: String,
        productId: String,
        purchaseToken: String
    ): VerifiedEntitlement {
        val body = JSONObject()
            .put("packageName", packageName)
            .put("productId", productId)
            .put("purchaseToken", purchaseToken)
        return request("/v1/billing/verify", "POST", body)
    }

    suspend fun entitlement(): VerifiedEntitlement =
        request("/v1/entitlement", "GET", null)

    private suspend fun request(
        path: String,
        method: String,
        body: JSONObject?
    ): VerifiedEntitlement {
        check(BuildConfig.BACKEND_BASE_URL.isNotBlank()) {
            "The NutRun billing backend is not configured."
        }
        val token = tokenProvider.idToken()
            ?: throw SecurityException("Sign in again before verifying purchases.")
        return withContext(Dispatchers.IO) {
            val connection = URI("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}$path")
                .toURL()
                .openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
            }
            try {
                body?.let { value ->
                    connection.outputStream.use { it.write(value.toString().toByteArray()) }
                }
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException(
                        "Purchase verification returned ${connection.responseCode}."
                    )
                }
                val response = JSONObject(
                    connection.inputStream.bufferedReader().use { it.readText() }
                )
                VerifiedEntitlement(
                    subscriber = response.optString("kind") == "SUBSCRIBER",
                    acknowledged = response.optBoolean("verified", method == "GET")
                )
            } finally {
                connection.disconnect()
            }
        }
    }
}
