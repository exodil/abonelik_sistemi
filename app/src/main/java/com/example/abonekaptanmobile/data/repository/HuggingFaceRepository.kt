// file: app/java/com/example/abonekaptanmobile/data/repository/HuggingFaceRepository.kt
package com.example.abonekaptanmobile.data.repository

import android.util.Log
import com.example.abonekaptanmobile.data.remote.HuggingFaceApi
import com.example.abonekaptanmobile.data.remote.model.ClassificationResult
import com.example.abonekaptanmobile.data.remote.model.DetailedClassificationResult
import com.example.abonekaptanmobile.data.remote.model.HuggingFaceParameters
import com.example.abonekaptanmobile.data.remote.model.HuggingFaceRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turkish: Hugging Face API ile etkileşim için repository sınıfı.
 * English: Repository class for interacting with Hugging Face API.
 */
@Singleton
class HuggingFaceRepository @Inject constructor(
    private val huggingFaceApi: HuggingFaceApi
) {
    companion object {
        private const val AUTH_TOKEN = "Bearer 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"
        private const val TAG = "HuggingFaceRepository"

        // Abonelik sınıflandırması için etiketler (geliştirilmiş)
        private val SUBSCRIPTION_LABELS = listOf("paid_subscription", "free_subscription", "not_subscription", "promotional")

        // Abonelik durumu sınıflandırması için etiketler (geliştirilmiş)
        private val SUBSCRIPTION_STATUS_LABELS = listOf(
            "subscription_start",
            "subscription_cancel",
            "subscription_renewal",
            "payment_confirmation",
            "welcome_message",
            "promotional_message",
            "other"
        )

        // Mail türü sınıflandırması için etiketler
        private val EMAIL_TYPE_LABELS = listOf(
            "paid_subscription_confirmation",
            "subscription_welcome",
            "subscription_cancellation",
            "subscription_renewal",
            "payment_receipt",
            "promotional_offer",
            "general_notification"
        )
    }

    /**
     * Turkish: Verilen metni abonelik olup olmadığına göre sınıflandırır.
     * English: Classifies the given text as subscription or not.
     */
    suspend fun classifySubscription(emailContent: String): ClassificationResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Classifying subscription for email content: ${emailContent.take(100)}...")

            val request = HuggingFaceRequest(
                inputs = emailContent,
                parameters = HuggingFaceParameters(candidateLabels = SUBSCRIPTION_LABELS)
            )

            val response = huggingFaceApi.classifyText(AUTH_TOKEN, request)
            val results = ClassificationResult.fromResponse(response)

            Log.d(TAG, "Classification results: $results")

            // En yüksek skora sahip sonucu döndür
            return@withContext results.maxByOrNull { it.score } ?: ClassificationResult("unknown", 0f)
        } catch (e: Exception) {
            Log.e(TAG, "Error classifying subscription: ${e.message}", e)
            return@withContext ClassificationResult("error", 0f)
        }
    }

    /**
     * Turkish: Verilen metni abonelik durumuna göre sınıflandırır (başlangıç, iptal, yenileme).
     * English: Classifies the given text based on subscription status (start, cancel, renewal).
     */
    suspend fun classifySubscriptionStatus(emailContent: String): ClassificationResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Classifying subscription status for email content: ${emailContent.take(100)}...")

            val request = HuggingFaceRequest(
                inputs = emailContent,
                parameters = HuggingFaceParameters(candidateLabels = SUBSCRIPTION_STATUS_LABELS)
            )

            val response = huggingFaceApi.classifyText(AUTH_TOKEN, request)
            val results = ClassificationResult.fromResponse(response)

            Log.d(TAG, "Subscription status results: $results")

            // En yüksek skora sahip sonucu döndür
            return@withContext results.maxByOrNull { it.score } ?: ClassificationResult("unknown", 0f)
        } catch (e: Exception) {
            Log.e(TAG, "Error classifying subscription status: ${e.message}", e)
            return@withContext ClassificationResult("error", 0f)
        }
    }

    /**
     * Turkish: Verilen metni mail türüne göre detaylı olarak sınıflandırır.
     * English: Classifies the given text based on email type in detail.
     */
    suspend fun classifyEmailType(emailContent: String): DetailedClassificationResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Classifying email type for content: ${emailContent.take(100)}...")

            val request = HuggingFaceRequest(
                inputs = emailContent,
                parameters = HuggingFaceParameters(candidateLabels = EMAIL_TYPE_LABELS)
            )

            val response = huggingFaceApi.classifyText(AUTH_TOKEN, request)
            val results = ClassificationResult.fromResponse(response)

            Log.d(TAG, "Email type classification results: $results")

            // Tüm sonuçları döndür
            val topResults = results.sortedByDescending { it.score }.take(3)

            // En yüksek skora sahip sonucu ve tüm sonuçları döndür
            val primaryResult = topResults.firstOrNull() ?: ClassificationResult("unknown", 0f)
            return@withContext DetailedClassificationResult(
                primaryLabel = primaryResult.label,
                primaryScore = primaryResult.score,
                allResults = topResults
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error classifying email type: ${e.message}", e)
            return@withContext DetailedClassificationResult(
                primaryLabel = "error",
                primaryScore = 0f,
                allResults = listOf(ClassificationResult("error", 0f))
            )
        }
    }

    /**
     * Turkish: Verilen metni ücretli abonelik olup olmadığına göre sınıflandırır.
     * English: Classifies the given text as paid subscription or not.
     */
    suspend fun classifyPaidSubscription(emailContent: String): ClassificationResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Classifying paid subscription for email content: ${emailContent.take(100)}...")

            val request = HuggingFaceRequest(
                inputs = emailContent,
                parameters = HuggingFaceParameters(candidateLabels = listOf("paid_subscription", "free_or_not_subscription"))
            )

            val response = huggingFaceApi.classifyText(AUTH_TOKEN, request)
            val results = ClassificationResult.fromResponse(response)

            Log.d(TAG, "Paid subscription classification results: $results")

            // En yüksek skora sahip sonucu döndür
            return@withContext results.maxByOrNull { it.score } ?: ClassificationResult("unknown", 0f)
        } catch (e: Exception) {
            Log.e(TAG, "Error classifying paid subscription: ${e.message}", e)
            return@withContext ClassificationResult("error", 0f)
        }
    }
}
