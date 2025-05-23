// file: app/java/com/example/abonekaptanmobile/data/remote/HuggingFaceApi.kt
package com.example.abonekaptanmobile.data.remote

import com.example.abonekaptanmobile.data.remote.model.TextGenerationRequest
import com.example.abonekaptanmobile.data.remote.model.TextGenerationResponseItem
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Turkish: Hugging Face API için Retrofit arayüzü.
 * English: Retrofit interface for Hugging Face API.
 */
interface HuggingFaceApi {

    /**
     * Turkish: Metin üretimi için Hugging Face API'sine istek gönderir.
     * English: Sends a request to Hugging Face API for text generation.
     *
     * @param authToken The authorization token for the API.
     * @param request The request body containing the input prompt and generation parameters.
     * @return A list of text generation results.
     */
    @POST("models/google/flan-t5-large")
    suspend fun generateText(
        @Header("Authorization") authToken: String,
        @Body request: TextGenerationRequest
    ): List<TextGenerationResponseItem>
}
