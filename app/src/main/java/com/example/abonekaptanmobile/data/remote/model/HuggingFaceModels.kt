// file: app/java/com/example/abonekaptanmobile/data/remote/model/HuggingFaceModels.kt
package com.example.abonekaptanmobile.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * Turkish: Hugging Face API'sine gönderilecek istek modeli.
 * English: Request model to be sent to Hugging Face API.
 */
data class HuggingFaceRequest(
    @SerializedName("inputs") val inputs: String,
    @SerializedName("parameters") val parameters: HuggingFaceParameters
)

/**
 * Turkish: Hugging Face API isteği için parametreler.
 * English: Parameters for Hugging Face API request.
 */
data class HuggingFaceParameters(
    @SerializedName("candidate_labels") val candidateLabels: List<String>
)

/**
 * Turkish: Hugging Face API'sinden dönen yanıt modeli.
 * English: Response model returned from Hugging Face API.
 */
data class HuggingFaceResponse(
    @SerializedName("sequence") val sequence: String,
    @SerializedName("labels") val labels: List<String>,
    @SerializedName("scores") val scores: List<Float>
)

/**
 * Turkish: Hugging Face API'sinden dönen sınıflandırma sonucu.
 * English: Classification result returned from Hugging Face API.
 */
data class ClassificationResult(
    val label: String,
    val score: Float
) {
    companion object {
        fun fromResponse(response: HuggingFaceResponse): List<ClassificationResult> {
            return response.labels.zip(response.scores) { label, score ->
                ClassificationResult(label, score)
            }
        }
    }
}

/**
 * Turkish: Hugging Face API'sinden dönen detaylı sınıflandırma sonucu.
 * English: Detailed classification result returned from Hugging Face API.
 */
data class DetailedClassificationResult(
    val primaryLabel: String,
    val primaryScore: Float,
    val allResults: List<ClassificationResult>
) {
    /**
     * Turkish: Belirli bir etiketin skorunu döndürür.
     * English: Returns the score for a specific label.
     */
    fun getScoreForLabel(label: String): Float {
        return allResults.find { it.label == label }?.score ?: 0f
    }

    /**
     * Turkish: Belirli bir eşik değerinin üzerinde olan etiketleri döndürür.
     * English: Returns labels that have a score above a certain threshold.
     */
    fun getLabelsAboveThreshold(threshold: Float): List<String> {
        return allResults.filter { it.score >= threshold }.map { it.label }
    }

    /**
     * Turkish: Belirli bir etiketin belirli bir eşik değerinin üzerinde olup olmadığını kontrol eder.
     * English: Checks if a specific label has a score above a certain threshold.
     */
    fun isLabelAboveThreshold(label: String, threshold: Float): Boolean {
        return getScoreForLabel(label) >= threshold
    }
}
