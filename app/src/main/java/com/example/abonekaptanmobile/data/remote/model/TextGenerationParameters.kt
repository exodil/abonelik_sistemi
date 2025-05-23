package com.example.abonekaptanmobile.data.remote.model

/**
 * Data class representing the parameters for a text generation request.
 *
 * @property max_new_tokens The maximum number of new tokens to generate.
 * @property temperature The temperature for sampling, controls randomness. Higher values mean more random.
 * @property return_full_text Whether to return the full text (prompt + generated) or just the generated part.
 */
data class TextGenerationParameters(
    val max_new_tokens: Int,
    val temperature: Double,
    val return_full_text: Boolean = false
)
