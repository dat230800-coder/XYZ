package com.example.data.remote

import com.example.BuildConfig
import com.example.data.model.Transaction
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Gemini API Request/Response Models ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null,
    val imageConfig: ImageConfig? = null,
    val responseModalities: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class ImageConfig(
    val aspectRatio: String, // "1:1" | "16:9" etc.
    val imageSize: String // "1K" | "512" etc.
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

// --- Retrofit Interface ---

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

// --- Retrofit Client ---

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

// --- Helper Operations & Prompts ---

object GeminiService {

    private val apiKey: String
        get() = BuildConfig.GEMINI_API_KEY

    /**
     * Generates custom Vietnamese budget & habits feedback based on user transactions in the app.
     */
    suspend fun generateFinancialInsights(transactions: List<Transaction>): String = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Chưa cấu hình API Key. Vui lòng thêm GEMINI_API_KEY vào tệp .env hoặc Bảng điều khiển Secrets của bạn."
        }

        // Prepare data representation
        val transactionSummary = if (transactions.isEmpty()) {
            "Hiện tại chưa có giao dịch nào được ghi lại."
        } else {
            transactions.take(15).joinToString("\n") { t ->
                "- ${if (t.type == "CHI_TIEU") "Chi tiêu" else "Thu nhập"}: ${t.amount} VNĐ - Danh mục: ${t.category} - Ghi chú: ${t.note}"
            }
        }

        val prompt = """
            Dưới đây là danh sách các giao dịch tài chính gần đây của tôi:
            $transactionSummary
            
            Hãy đưa ra:
            1. Nhận xét ngắn gọn về thói quen chi tiêu của tôi (bằng tiếng Việt).
            2. 2 lời khuyên tiết kiệm tài chính thiết thực nhất cho riêng tôi.
            3. Đề xuất phát triển 1 thói quen thông minh (ví dụ: bớt uống cafe, chuẩn bị cơm trưa).
            
            Đảm bảo câu trả lời thân thiện, lạc quan, súc tích (dưới 200 từ) và sử dụng định dạng Markdown rõ ràng.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = "Bạn là trợ lý tài chính cá nhân thông minh FinTrack Pro. Bạn luôn đưa ra lời khuyên tài chính ngắn gọn, sâu sắc và hữu ích bằng tiếng Việt."))
            )
        )

        try {
            // Using gemini-3.5-flash for base content generation
            val response = RetrofitClient.service.generateContent("gemini-3.5-flash", apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "Không nhận được phản hồi từ AI."
        } catch (e: Exception) {
            "Không thể tải nhận xét từ AI. Lỗi: ${e.message}"
        }
    }

    /**
     * Generates a base64 encoded JPEG image using gemini-3.1-flash-image-preview.
     */
    suspend fun generateImageFromPrompt(prompt: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext null
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                imageConfig = ImageConfig(aspectRatio = "1:1", imageSize = "512"),
                responseModalities = listOf("TEXT", "IMAGE")
            )
        )

        try {
            // Using gemini-3.1-flash-image-preview for high-quality image generation matching user requests
            val response = RetrofitClient.service.generateContent("gemini-3.1-flash-image-preview", apiKey, request)
            
            // Extract the generated image as a base64 string
            val imagePart = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull {
                it.inlineData != null && it.inlineData.mimeType.startsWith("image/")
            }
            imagePart?.inlineData?.data
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
