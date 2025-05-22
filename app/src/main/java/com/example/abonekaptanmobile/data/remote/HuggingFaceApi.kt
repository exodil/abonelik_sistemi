// file: app/java/com/example/abonekaptanmobile/data/remote/HuggingFaceApi.kt
package com.example.abonekaptanmobile.data.remote

import com.example.abonekaptanmobile.data.remote.model.HuggingFaceRequest
import com.example.abonekaptanmobile.data.remote.model.HuggingFaceResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Turkish: Hugging Face API için Retrofit arayüzü.
 * English: Retrofit interface for Hugging Face API.
 */
interface HuggingFaceApi {
    
    /**
     * Turkish: Zero-shot sınıflandırma için Hugging Face API'sine istek gönderir.
     * English: Sends a request to Hugging Face API for zero-shot classification.
     */
    @POST("models/MoritzLaurer/mDeBERTa-v3-base-mnli-xnli")
    suspend fun classifyText(
        @Header("Authorization") authToken: String,
        @Body request: HuggingFaceRequest
    ): HuggingFaceResponse
}
