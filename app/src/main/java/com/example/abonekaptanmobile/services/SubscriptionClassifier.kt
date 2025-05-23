// file: app/java/com/example/abonekaptanmobile/services/SubscriptionClassifier.kt
package com.example.abonekaptanmobile.services

import android.util.Log
import com.example.abonekaptanmobile.data.repository.HuggingFaceRepository
import com.example.abonekaptanmobile.model.EmailAnalysisResult
import com.example.abonekaptanmobile.model.RawEmail
import org.json.JSONObject
import javax.inject.Inject

/**
 * Turkish: E-postaları abonelik durumuna göre sınıflandıran servis.
 * English: Service that classifies emails based on subscription status.
 */
class SubscriptionClassifier @Inject constructor(
    private val huggingFaceRepository: HuggingFaceRepository
) {
    /**
     * Template for prompting the LLM to analyze email content for subscription information.
     * Includes placeholders for email details (from, subject, body) and specifies the
     * desired JSON output format with fields: company, subscription, action, date, confidence.
     */
    private const val PROMPT_TEMPLATE_V2 = """
Analyze the following email content to identify subscription-related information.
The email is from: "{email_from}"
Subject: "{email_subject}"
Body Snippet:
---
{email_body}
---

Based ONLY on the text provided, extract the following information in JSON format:
- "company": (string) The name of the company or service the subscription is with. If not clearly identifiable, use "Unknown".
- "subscription": (boolean) True if this email explicitly mentions a subscription service (paid or free). False otherwise (e.g. newsletters, promotions for one-time purchases, notifications not about an ongoing subscription).
- "action": (string) The primary action related to the subscription. Choose one: "start", "renew", "cancel", "payment_reminder", "payment_issue", "trial_start", "trial_ending", "upgrade", "downgrade", "service_update", "none". If no specific action, use "none".
- "date": (string) If a specific date for an event (e.g., start date, cancellation date, renewal date, payment date) is mentioned, provide it in YYYY-MM-DD format. If not mentioned or not applicable, use "unknown".
- "confidence": (float) Your confidence in this analysis, from 0.0 (low) to 1.0 (high).

Example JSON output:
{
  "company": "Netflix",
  "subscription": true,
  "action": "start",
  "date": "2023-01-15",
  "confidence": 0.9
}

Return ONLY the JSON object.
"""

    /**
     * Represents the structured output expected from the LLM after analyzing an email.
     * This data class is used for parsing the JSON response from the LLM.
     *
     * @property company The name of the company or service identified in the email. Nullable if not found.
     * @property subscription Boolean indicating if the email pertains to a subscription. Nullable if not determined.
     * @property action The primary action related to the subscription (e.g., "start", "cancel"). Nullable if not determined.
     * @property date The date associated with the action (YYYY-MM-DD format). Nullable if not found or applicable.
     * @property confidence The LLM's confidence score (0.0 to 1.0) in its analysis. Nullable if not provided.
     */
    private data class LlmAnalysisOutput(
        val company: String?,
        val subscription: Boolean?,
        val action: String?,
        val date: String?,
        val confidence: Float?
    )

    /**
     * Constructs the prompt string to be sent to the LLM for analyzing a given email.
     * It injects the email's sender, subject, and body content into the [PROMPT_TEMPLATE_V2].
     *
     * @param email The [RawEmail] object containing the details to be included in the prompt.
     * @return A formatted string ready to be used as a prompt for the LLM.
     */
    private fun buildPrompt(email: RawEmail): String {
        val bodyContent = when {
            !email.bodySnippet.isNullOrBlank() -> email.bodySnippet
            !email.snippet.isNullOrBlank() -> email.snippet
            !email.bodyPlainText.isNullOrBlank() -> email.bodyPlainText.take(1000) // Increased limit for LLM
            else -> ""
        }
        return PROMPT_TEMPLATE_V2
            .replace("{email_from}", email.from)
            .replace("{email_subject}", email.subject)
            .replace("{email_body}", bodyContent.replace("\"", "\\\"")) // Escape quotes in body
    }


    /**
     * Processes a list of raw emails using a Large Language Model (LLM) to analyze and extract
     * subscription-related information.
     *
     * This function iterates through each email, builds a specific prompt, sends it to the LLM,
     * parses the JSON response, and determines a database operation (`database_op`) based on
     * the detected action (e.g., "start", "cancel") and a history of actions for each company.
     * The results are then compiled into a list of [EmailAnalysisResult] objects.
     *
     * Key steps:
     * 1. Sorts emails by date (descending) to process recent emails first, which aids in determining
     *    the correct `database_op` by understanding the sequence of subscription events.
     * 2. For each email:
     *    a. Retrieves its original index to maintain the initial order in the final output.
     *    b. Builds a prompt using [buildPrompt].
     *    c. Calls the LLM via `huggingFaceRepository.analyzeEmailWithInstructionModel`.
     *    d. Parses the LLM's JSON response into an [LlmAnalysisOutput] object, handling potential errors.
     *    e. Determines `database_op` ("CREATE_EVENT", "UPDATE_EVENT", "NONE") based on the
     *       `action` from the LLM and the `companyActionHistory`. This logic aims to identify
     *       new subscriptions or updates to existing ones.
     *    f. Creates an [EmailAnalysisResult] object.
     * 3. Logs progress and any errors encountered during processing.
     * 4. Returns the list of [EmailAnalysisResult]s, sorted by their original email index.
     *
     * @param allRawEmails A list of [RawEmail] objects to be processed.
     * @return A list of [EmailAnalysisResult] objects, each representing the LLM's analysis
     *         for an email, sorted by the original index of the emails in the input list.
     */
    suspend fun processEmailsWithLLM(allRawEmails: List<RawEmail>): List<EmailAnalysisResult> {
        val resultsList = mutableListOf<EmailAnalysisResult>()
        val companyActionHistory = mutableMapOf<String, String>() // Tracks last significant action (start/cancel)

        Log.i("SubscriptionClassifier", "Starting LLM processing for ${allRawEmails.size} emails.")

        // Sort emails by date descending to process most recent first, which helps with action history logic
        val sortedEmails = allRawEmails.sortedByDescending { it.date }

        sortedEmails.forEach { email ->
            val originalEmailIndex = allRawEmails.indexOf(email) // Get original index for later sorting
            if (originalEmailIndex == -1) {
                Log.e("SubscriptionClassifier", "Could not find original index for email ID: ${email.id}. Skipping.")
                return@forEach
            }

            val prompt = buildPrompt(email)
            Log.d("SubscriptionClassifier", "Processing email ID ${email.id} (Original Index: $originalEmailIndex) with LLM. Subject: ${email.subject.take(50)}")

            val llmResponseString = try {
                huggingFaceRepository.analyzeEmailWithInstructionModel(prompt)
            } catch (e: Exception) {
                Log.e("SubscriptionClassifier", "LLM API call failed for email ID ${email.id}: ${e.message}", e)
                null
            }

            var llmOutput: LlmAnalysisOutput
            var extractedCompany = "Unknown"
            var extractedSubscription = false
            var extractedAction = "none"
            var extractedDate = "unknown"
            var extractedConfidence = 0.0f

            if (!llmResponseString.isNullOrBlank()) {
                try {
                    // Clean the response string if it contains markdown or other non-JSON parts
                    val cleanedResponse = llmResponseString.substringAfter("```json\n", llmResponseString)
                                                          .substringBeforeLast("\n```", llmResponseString)
                                                          .trim()

                    val jsonResponse = JSONObject(cleanedResponse)
                    extractedCompany = jsonResponse.optString("company", "Unknown")
                    extractedSubscription = jsonResponse.optBoolean("subscription", false)
                    extractedAction = jsonResponse.optString("action", "none")
                    extractedDate = jsonResponse.optString("date", "unknown")
                    // optDouble can return NaN, so handle it
                    val confidenceDouble = jsonResponse.optDouble("confidence", 0.0)
                    extractedConfidence = if (confidenceDouble.isNaN()) 0.0f else confidenceDouble.toFloat()

                    llmOutput = LlmAnalysisOutput(
                        company = extractedCompany,
                        subscription = extractedSubscription,
                        action = extractedAction,
                        date = extractedDate,
                        confidence = extractedConfidence
                    )
                    Log.d("SubscriptionClassifier", "LLM Response for email ID ${email.id}: $llmOutput")
                } catch (e: org.json.JSONException) {
                    Log.e("SubscriptionClassifier", "Failed to parse LLM JSON response for email ID ${email.id}. Response: $llmResponseString. Error: ${e.message}")
                    llmOutput = LlmAnalysisOutput("Unknown", false, "none", "unknown", 0.0f)
                }
            } else {
                Log.w("SubscriptionClassifier", "LLM response was null or blank for email ID ${email.id}.")
                llmOutput = LlmAnalysisOutput("Unknown", false, "none", "unknown", 0.0f)
                // Keep extracted variables as their default error values
            }

            // Determine database_op
            var currentDatabaseOp = "NONE"
            val companyKey = extractedCompany.lowercase().trim() // Use a normalized key for history

            if (companyKey.isNotBlank() && companyKey != "unknown") {
                when (extractedAction) {
                    "start", "trial_start" -> {
                        if (companyActionHistory[companyKey] != "start") {
                            currentDatabaseOp = "CREATE_EVENT"
                            companyActionHistory[companyKey] = "start"
                        } else {
                             Log.d("SubscriptionClassifier", "Duplicate 'start' action for $companyKey, op: NONE.")
                        }
                    }
                    "cancel" -> {
                        if (companyActionHistory[companyKey] == "start" || companyActionHistory[companyKey] == "renew") { // Allow cancel if started or renewed
                            currentDatabaseOp = "UPDATE_EVENT"
                            companyActionHistory[companyKey] = "cancel"
                        } else {
                            Log.d("SubscriptionClassifier", "Cancel action for $companyKey without prior 'start' or 'renew', op: NONE. History: ${companyActionHistory[companyKey]}")
                        }
                    }
                    "renew" -> {
                         if (companyActionHistory[companyKey] == "start" || companyActionHistory[companyKey] == "renew") {
                             // Renew doesn't typically change the core event in this model, but updates its activity
                             // We can consider an UPDATE_EVENT if renewal implies a change that needs tracking (e.g. new term)
                             // For now, let's assume it doesn't create a new DB event but confirms existing.
                             // If it's the first 'start-like' action, treat as start.
                             currentDatabaseOp = "NONE" // Or UPDATE_EVENT if we want to track renewals explicitly
                             companyActionHistory[companyKey] = "renew" // Update history to show it's active
                         } else if (companyActionHistory[companyKey] == null) {
                             // If no history, and it's a renewal, treat as a start.
                             currentDatabaseOp = "CREATE_EVENT"
                             companyActionHistory[companyKey] = "start" // Treat as start as it's the first positive signal
                             Log.d("SubscriptionClassifier", "Renewal for $companyKey with no prior history, treating as CREATE_EVENT.")
                         }
                    }
                    // Other actions like "payment_reminder", "service_update" usually don't change the subscription state in DB
                    // but might be useful for other analytics or updating last_activity_date for an event.
                }
            }

            val analysisResult = EmailAnalysisResult(
                email_index = originalEmailIndex,
                company = extractedCompany,
                subscription = extractedSubscription,
                action = extractedAction,
                date = extractedDate,
                confidence = extractedConfidence,
                database_op = currentDatabaseOp
            )
            resultsList.add(analysisResult)
            Log.i("SubscriptionClassifier", "Processed email ID ${email.id}: Company='${analysisResult.company}', Action='${analysisResult.action}', DB_Op='${analysisResult.database_op}'")
        }

        Log.i("SubscriptionClassifier", "LLM processing completed. ${resultsList.size} results generated.")
        // Sort results by the original email index to maintain chronological order of input
        return resultsList.sortedBy { it.email_index }
    }
}
// The String.capitalizeWords() extension function is removed as it's part of the old logic.