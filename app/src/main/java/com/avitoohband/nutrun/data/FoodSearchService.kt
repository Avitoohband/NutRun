package com.avitoohband.nutrun.data

import com.avitoohband.nutrun.BuildConfig
import com.avitoohband.nutrun.auth.FirebaseTokenProvider
import com.avitoohband.nutrun.domain.FoodCatalogItem
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface FoodSearchService {
    suspend fun search(query: String): List<FoodCatalogItem>
}

@Singleton
class BackendFoodSearchService @Inject constructor(
    private val tokenProvider: FirebaseTokenProvider
) : FoodSearchService {
    override suspend fun search(query: String): List<FoodCatalogItem> {
        val normalized = query.trim()
        if (normalized.length < 2) return emptyList()
        if (BuildConfig.BACKEND_BASE_URL.isBlank()) return sampleFoods(normalized)
        val token = tokenProvider.idToken()
            ?: throw SecurityException("Sign in again before searching the food catalog.")

        return withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(normalized, StandardCharsets.UTF_8.name())
            val connection = URI("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/v1/foods/search?q=$encoded")
                .toURL()
                .openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            try {
                if (connection.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    throw SecurityException("Your session expired. Sign in again.")
                }
                if (connection.responseCode !in 200..299) return@withContext sampleFoods(normalized)
                val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val items = root.optJSONArray("items") ?: return@withContext emptyList()
                buildList {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        val name = item.optString("name").trim()
                        if (name.isEmpty()) continue
                        add(
                            FoodCatalogItem(
                                id = item.optString("id", "remote-$index"),
                                name = name,
                                brand = item.optString("brand").takeIf(String::isNotBlank),
                                servingGrams = item.optDouble("servingGrams", 100.0),
                                calories = item.optInt("calories"),
                                proteinGrams = item.optDouble("proteinGrams"),
                                carbohydrateGrams = item.optDouble("carbohydrateGrams"),
                                fatGrams = item.optDouble("fatGrams")
                            )
                        )
                    }
                }
            } catch (error: SecurityException) {
                throw error
            } catch (_: Exception) {
                sampleFoods(normalized)
            } finally {
                connection.disconnect()
            }
        }
    }
}

private val fallbackCatalog = listOf(
    FoodCatalogItem("sample-oats", "Rolled oats", null, 100.0, 379, 13.2, 67.7, 6.5),
    FoodCatalogItem("sample-banana", "Banana", null, 100.0, 89, 1.1, 22.8, 0.3),
    FoodCatalogItem("sample-chicken", "Chicken breast, cooked", null, 100.0, 165, 31.0, 0.0, 3.6),
    FoodCatalogItem("sample-rice", "White rice, cooked", null, 100.0, 130, 2.7, 28.2, 0.3),
    FoodCatalogItem("sample-yogurt", "Plain Greek yogurt", null, 100.0, 97, 9.0, 3.9, 5.0)
)

private fun sampleFoods(query: String): List<FoodCatalogItem> =
    fallbackCatalog.filter { it.name.contains(query, ignoreCase = true) }
