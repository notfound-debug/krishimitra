package com.krishi.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// --- Digest ---

data class ArticleLink(
    val title: String = "",
    val link: String = "",
    val source: String = "",
)

data class DigestResponse(
    val date: String,
    val kannada: String,
    val english: String,
    val article_count: Int,
    val sources: List<String>,
    val article_links: List<ArticleLink> = emptyList(),
    val generated_at: String,
)

// --- Profile ---

data class ProfileRequest(
    val device_id: String,
    val district: String = "",
    val taluk: String = "",
    val crops: List<String> = emptyList(),
    val land_acres: Double = 0.0,
    val farmer_category: String = "",
    val language: String = "kn",
)

data class ProfileResponse(
    val device_id: String,
    val district: String,
    val taluk: String,
    val crops: List<String>,
    val land_acres: Double,
    val farmer_category: String,
    val language: String,
)

// --- Prices ---

data class PriceEntry(
    val apmc: String = "",
    val min_price: Int = 0,
    val max_price: Int = 0,
    val modal_price: Int = 0,
    val trend: String = "",
    val trend_direction: String = "",  // "up", "down", "stable"
)

data class PricesResponse(
    val crop: String = "",
    val crop_kn: String = "",
    val date: String = "",
    val best_apmc: String = "",
    val prices: List<PriceEntry> = emptyList(),
    val recommendation_kn: String = "",
    val recommendation_en: String = "",
)

data class CropInfo(
    val name: String = "",
    val name_kn: String = "",
)

data class CropsListResponse(
    val crops: List<CropInfo> = emptyList(),
)

// --- Schemes ---

data class SchemeEligibility(
    val max_land_acres: Double = 0.0,
    val districts: List<String> = emptyList(),
    val crops: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
)

data class SchemeResponse(
    val id: String = "",
    val name_kn: String = "",
    val name_en: String = "",
    val benefit_kn: String = "",
    val benefit_en: String = "",
    val description_kn: String = "",
    val description_en: String = "",
    val steps_kn: List<String> = emptyList(),
    val steps_en: List<String> = emptyList(),
    val eligibility: SchemeEligibility = SchemeEligibility(),
    val documents_kn: String = "",
    val documents_en: String = "",
    val deadline: String = "",
    val apply_url: String = "",
    val helpline: String = "",
)

data class SchemesListResponse(
    val count: Int = 0,
    val schemes: List<SchemeResponse> = emptyList(),
)

// --- Ask KrishiMitra (Q&A) ---

data class AskRequest(
    val question: String,
    val language: String = "kn",
    val device_id: String = "",
    val district: String = "",
    val crops: List<String> = emptyList(),
)

data class AskResponse(
    val question: String = "",
    val answer: String = "",
    val sources: List<String> = emptyList(),
    val language: String = "kn",
)

data class HealthResponse(
    val app: String,
    val status: String,
    val version: String,
)

// --- API interface ---

interface ApiService {

    @GET("api/digest")
    suspend fun getDigest(): DigestResponse

    @POST("api/profile")
    suspend fun createProfile(@Body profile: ProfileRequest): Map<String, String>

    @GET("api/profile/{deviceId}")
    suspend fun getProfile(@Path("deviceId") deviceId: String): ProfileResponse

    @GET("api/prices/crops")
    suspend fun getCrops(): CropsListResponse

    @GET("api/prices/{crop}")
    suspend fun getPrices(@Path("crop") crop: String): PricesResponse

    @POST("api/ask")
    suspend fun askQuestion(@Body request: AskRequest): AskResponse

    @GET("api/schemes")
    suspend fun getSchemes(): SchemesListResponse

    @GET("api/schemes/eligible")
    suspend fun getEligibleSchemes(
        @Query("district") district: String? = null,
        @Query("crops") crops: String? = null,
        @Query("land_acres") landAcres: Double? = null,
        @Query("category") category: String? = null,
    ): SchemesListResponse

    @GET("/")
    suspend fun healthCheck(): HealthResponse
}
