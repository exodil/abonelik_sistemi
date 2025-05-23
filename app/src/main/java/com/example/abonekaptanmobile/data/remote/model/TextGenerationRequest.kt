package com.example.abonekaptanmobile.data.remote.model

/**
 * Data class representing the request body for a text generation API call.
 *
 * @property inputs The prompt string for text generation.
 * @property parameters The parameters for controlling the text generation process.
 * @property options Optional parameters for the API request, defaults to waiting for the model.
 */
data class TextGenerationRequest(
    val inputs: String,
    val parameters: TextGenerationParameters,
    val options: Map<String, Any>? = mapOf("wait_for_model" to true)
)
