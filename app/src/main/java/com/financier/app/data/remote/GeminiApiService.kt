package com.financier.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GeminiApiService {

    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}

// ===== Request Models =====
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiSystemInstruction? = null,
    val generationConfig: GenerationConfig = GenerationConfig()
)

data class GeminiContent(
    val role: String, // "user" | "model"
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiSystemInstruction(
    val parts: List<GeminiPart>
)

data class GenerationConfig(
    val maxOutputTokens: Int = 500,
    val temperature: Double = 0.7
)

// ===== Response Models =====
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?,
    val error: GeminiError?
)

data class GeminiCandidate(
    val content: GeminiContent
)

data class GeminiError(
    val code: Int,
    val message: String
)
