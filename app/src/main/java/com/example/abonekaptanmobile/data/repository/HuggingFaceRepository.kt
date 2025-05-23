// file: app/java/com/example/abonekaptanmobile/data/repository/HuggingFaceRepository.kt
package com.example.abonekaptanmobile.data.repository

import android.util.Log
import com.example.abonekaptanmobile.data.remote.HuggingFaceApi
import com.example.abonekaptanmobile.data.remote.model.TextGenerationParameters
import com.example.abonekaptanmobile.data.remote.model.TextGenerationRequest
import com.example.abonekaptanmobile.data.remote.model.TextGenerationResponseItem
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
        private const val AUTH_TOKEN = "Bearer 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" // TODO: Consider moving to a secure configuration
        private const val TAG = "HuggingFaceRepository"
    }

    /**
     * Turkish: Verilen bir prompt kullanarak talimat tabanlı model ile e-posta içeriğini analiz eder ve metin üretir.
     * English: Analyzes email content and generates text using an instruction-based model with the given prompt.
     *
     * @param prompt The input prompt for the text generation model.
     * @return The generated text as a String, or null if an error occurs or no text is generated.
     */
    suspend fun analyzeEmailWithInstructionModel(prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Analyzing email with instruction model, prompt: ${prompt.take(150)}...")

            val textGenParams = TextGenerationParameters(
                max_new_tokens = 200,
                temperature = 0.0,
                return_full_text = false
            )

            val request = TextGenerationRequest(
                inputs = prompt,
                parameters = textGenParams,
                options = mapOf("wait_for_model" to true) // Explicitly set, though default in data class
            )

            val response: List<TextGenerationResponseItem> = huggingFaceApi.generateText(AUTH_TOKEN, request)

            if (response.isNotEmpty() && response[0].generated_text.isNotBlank()) {
                val generatedText = response[0].generated_text
                Log.d(TAG, "Successfully generated text: ${generatedText.take(150)}...")
                return@withContext generatedText
            } else {
                Log.w(TAG, "Text generation response was empty or blank for prompt: ${prompt.take(150)}")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing email with instruction model: ${e.message}", e)
            return@withContext null
        }
    }
}
