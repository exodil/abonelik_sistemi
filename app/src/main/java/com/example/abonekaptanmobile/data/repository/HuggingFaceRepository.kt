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

        // Abonelik yaşam döngüsü olayları için etiketler
        private val LIFECYCLE_EVENT_LABELS = listOf(
            "paid_subscription_event",
            "paid_subscription_cancellation",
            "promotional_or_advertisement",
            "free_service_related",
            "other_unrelated"
        )
    }

    /**
     * Turkish: Verilen metni abonelik olup olmadığına göre sınıflandırır.
     * English: Classifies the given text as subscription or not.
     * @Deprecated Superseded by classifySubscriptionLifecycle. Consider for removal in future versions.
     */
    @Deprecated(message = "Superseded by classifySubscriptionLifecycle. Consider for removal in future versions.")
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
     * @Deprecated Superseded by classifySubscriptionLifecycle. Consider for removal in future versions.
     */
    @Deprecated(message = "Superseded by classifySubscriptionLifecycle. Consider for removal in future versions.")
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
     * @Deprecated Superseded by classifySubscriptionLifecycle. Consider for removal in future versions.
     */
    @Deprecated(message = "Superseded by classifySubscriptionLifecycle. Consider for removal in future versions.")
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
     * Classifies the given email content to determine subscription lifecycle events.
     * This method uses a zero-shot classification model with predefined labels to identify
     * events such as new paid subscriptions or cancellations.
     *
     * The primary labels used for classification are defined in `LIFECYCLE_EVENT_LABELS`, including:
     * - "paid_subscription_event": Indicates a new or renewed paid subscription.
     * - "paid_subscription_cancellation": Indicates the cancellation of a paid subscription.
     * - "promotional_or_advertisement": Indicates a promotional email.
     * - "free_service_related": Related to free service notifications.
     * - "other_unrelated": For emails not fitting other categories.
     *
     * The method returns a [DetailedClassificationResult], which includes the primary (highest score)
     * label and score, along with a list of all labels and their corresponding scores.
     *
     * @param emailContent The text content of the email to be classified.
     * @return A [DetailedClassificationResult] containing all classification scores.
     *
     * Turkish: Verilen e-posta içeriğini abonelik yaşam döngüsü olaylarını belirlemek için sınıflandırır.
     * Bu yöntem, yeni ücretli abonelikler veya iptaller gibi olayları tanımlamak için önceden
     * tanımlanmış etiketlerle sıfır vuruşlu bir sınıflandırma modeli kullanır.
     *
     * Sınıflandırma için kullanılan birincil etiketler `LIFECYCLE_EVENT_LABELS` içinde tanımlanmıştır, örneğin:
     * - "paid_subscription_event": Yeni veya yenilenmiş bir ücretli aboneliği belirtir.
     * - "paid_subscription_cancellation": Ücretli bir aboneliğin iptalini belirtir.
     * - "promotional_or_advertisement": Promosyon amaçlı bir e-postayı belirtir.
     * - "free_service_related": Ücretsiz hizmet bildirimleriyle ilgilidir.
     * - "other_unrelated": Diğer kategorilere uymayan e-postalar içindir.
     *
     * Yöntem, birincil (en yüksek puanlı) etiketi ve puanı içeren bir [DetailedClassificationResult]
     * ile birlikte tüm etiketlerin ve karşılık gelen puanlarının bir listesini döndürür.
     */
    suspend fun classifySubscriptionLifecycle(emailContent: String): DetailedClassificationResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Classifying subscription lifecycle for content: ${emailContent.take(100)}...")

            val request = HuggingFaceRequest(
                inputs = emailContent,
                parameters = HuggingFaceParameters(candidateLabels = LIFECYCLE_EVENT_LABELS)
            )

            val response = huggingFaceApi.classifyText(AUTH_TOKEN, request)
            val results = ClassificationResult.fromResponse(response)

            Log.d(TAG, "Subscription lifecycle classification results: $results")

            // Tüm sonuçları döndür
            val topResults = results.sortedByDescending { it.score }

            // En yüksek skora sahip sonucu ve tüm sonuçları döndür
            val primaryResult = topResults.firstOrNull() ?: ClassificationResult("unknown", 0f)
            return@withContext DetailedClassificationResult(
                primaryLabel = primaryResult.label,
                primaryScore = primaryResult.score,
                allResults = topResults
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error classifying subscription lifecycle: ${e.message}", e)
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
     * @Deprecated Superseded by classifySubscriptionLifecycle. Consider for removal in future versions.
     */
    @Deprecated(message = "Superseded by classifySubscriptionLifecycle. Consider for removal in future versions.")
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
