package com.example.compass_app

import android.util.Log
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Data class representing a Point of Interest (POI) from OpenStreetMap.
 */
data class PointOfInterest(
    val id: Long,
    val name: String,
    val locationType: String,
    val category: PoiCategory,
    val location: Location,
    val tags: Map<String, String> = emptyMap()
)

/**
 * Enum for filtering POIs by category, mapped from the Python script logic.
 */
enum class PoiCategory(val displayName: String) {
    FOOD_AND_DRINK("🍽️ Food & Drink"),
    ACCOMMODATION("🏨 Accommodation"),
    SIGHTSEEING_AND_CULTURE("🏛️ Sightseeing & Culture"),
    LEISURE_AND_ACTIVITIES("🎭 Leisure & Activities"),
    HEALTH("🏥 Health"),
    MONEY("💳 Money"),
    TRANSPORT("🚌 Transport"),
    GROCERY_AND_FOOD_SHOPS("🛒 Grocery & Food Shops"),
    RETAIL_SHOPPING("🛍️ Retail Shopping"),
    SERVICES("✂️ Services"),
    OTHER("📍 Other")
}

// --- Overpass API Models ---

data class OverpassResponse(
    @SerializedName("elements") val elements: List<OverpassElement>
)

data class OverpassElement(
    @SerializedName("id") val id: Long,
    @SerializedName("lat") val lat: Float,
    @SerializedName("lon") val lon: Float,
    @SerializedName("tags") val tags: Map<String, String>?
)

interface OverpassApi {
    @POST("interpreter")
    suspend fun query(@Body data: okhttp3.RequestBody): OverpassResponse
}

/**
 * Service class to handle data fetching from Overpass API.
 */
class LocationService {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun buildApi(baseUrl: String): OverpassApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OverpassApi::class.java)

    private val apiEndpoints = listOf(
        "https://overpass.private.coffee/api/",
        "https://overpass-api.de/api/",
        "https://lz4.overpass-api.de/api/"
    )

    suspend fun fetchNearbyPOIs(lat: Float, lon: Float, radius: Int = 1000): List<PointOfInterest> {
        val query = """
            [out:json][timeout:60];
            (
              node["amenity"](around:$radius,$lat,$lon);
              node["shop"](around:$radius,$lat,$lon);
              node["tourism"](around:$radius,$lat,$lon);
              node["leisure"](around:$radius,$lat,$lon);
              node["historic"](around:$radius,$lat,$lon);
              node["healthcare"](around:$radius,$lat,$lon);
              node["emergency"](around:$radius,$lat,$lon);
            );
            out body;
        """.trimIndent()

        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val body = "data=$encodedQuery".toRequestBody("application/x-www-form-urlencoded".toMediaType())

        Log.d("OverpassAPI", "Fetching POIs: lat=$lat, lon=$lon, radius=${radius}m")

        var lastException: Exception? = null
        for (endpoint in apiEndpoints) {
            Log.d("OverpassAPI", "Trying endpoint: $endpoint")
            val start = System.currentTimeMillis()
            try {
                val response = buildApi(endpoint).query(body)
                val elapsed = System.currentTimeMillis() - start
                Log.d("OverpassAPI", "✓ $endpoint responded in ${elapsed}ms, elements=${response.elements.size}")

                val pois = response.elements.mapNotNull { element ->
                    val tags = element.tags ?: return@mapNotNull null
                    val name = tags["name"]
                    val amenity = tags["amenity"]
                    if (name.isNullOrBlank() || tags.containsKey("office") ||
                        amenity in listOf("parking_entrance", "bicycle_rental")) return@mapNotNull null

                    PointOfInterest(
                        id = element.id,
                        name = name,
                        locationType = classifyPoi(tags),
                        category = getCategoryFromTags(tags),
                        location = Location(element.lat, element.lon),
                        tags = tags
                    )
                }
                Log.d("OverpassAPI", "  → ${pois.size} named POIs after filtering")
                return pois
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - start
                Log.e("OverpassAPI", "✗ $endpoint failed after ${elapsed}ms: ${e::class.simpleName}: ${e.message}")
                lastException = e
            }
        }
        Log.e("OverpassAPI", "All endpoints failed")
        throw lastException!!
    }

    private fun classifyPoi(tags: Map<String, String>): String {
        for (key in listOf("amenity", "shop", "tourism", "leisure", "historic", "healthcare", "emergency")) {
            tags[key]?.let { return "$key:$it" }
        }
        return "unknown"
    }

    fun getCategoryFromTags(tags: Map<String, String>): PoiCategory {
        val amenity = tags["amenity"]
        val shop = tags["shop"]
        val tourism = tags["tourism"]
        val leisure = tags["leisure"]
        val historic = tags["historic"]

        return when {
            amenity in listOf("restaurant", "fast_food", "cafe", "pub", "bar", "nightclub", "ice_cream", "food_court", "marketplace") -> PoiCategory.FOOD_AND_DRINK
            tourism in listOf("hotel", "hostel", "motel", "guest_house", "apartment") -> PoiCategory.ACCOMMODATION
            tourism in listOf("museum", "gallery", "attraction", "artwork", "viewpoint", "information") || 
            historic != null || 
            amenity in listOf("theatre", "cinema", "arts_centre", "library", "place_of_worship", "monastery", "fountain", "community_centre", "events_venue", "studio") -> PoiCategory.SIGHTSEEING_AND_CULTURE
            leisure != null || tourism == "picnic_site" -> PoiCategory.LEISURE_AND_ACTIVITIES
            amenity in listOf("pharmacy", "hospital", "clinic", "doctors", "dentist") -> PoiCategory.HEALTH
            amenity in listOf("atm", "bank", "bureau_de_change") -> PoiCategory.MONEY
            amenity in listOf("ferry_terminal", "taxi", "fuel") -> PoiCategory.TRANSPORT
            shop in listOf("supermarket", "convenience", "kiosk", "bakery", "deli", "butcher") -> PoiCategory.GROCERY_AND_FOOD_SHOPS
            shop in listOf(
                "hairdresser", "barber", "massage", "beauty", "nail_salon", "tattoo",
                "laundry", "dry_cleaning", "pet_grooming", "travel_agency", "copyshop",
                "photo", "optician", "car_wash", "funeral_directors", "alterations"
            ) -> PoiCategory.SERVICES
            shop != null -> PoiCategory.RETAIL_SHOPPING
            else -> PoiCategory.OTHER
        }
    }
}
