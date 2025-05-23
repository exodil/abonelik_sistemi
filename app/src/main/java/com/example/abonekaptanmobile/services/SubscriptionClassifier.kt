// file: app/java/com/example/abonekaptanmobile/services/SubscriptionClassifier.kt
package com.example.abonekaptanmobile.services

import android.util.Log
import com.example.abonekaptanmobile.data.repository.HuggingFaceRepository
import com.example.abonekaptanmobile.model.CompanySubscriptionInfo
import com.example.abonekaptanmobile.model.EmailAnalysisResult
import com.example.abonekaptanmobile.model.RawEmail
import com.example.abonekaptanmobile.util.CompanyListProvider
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import java.text.Normalizer

/**
 * Turkish: E-postaları abonelik durumuna göre sınıflandıran servis.
 * English: Service that classifies emails based on subscription status using a two-stage LLM approach.
 */
@Singleton
class SubscriptionClassifier @Inject constructor(
    private val huggingFaceRepository: HuggingFaceRepository,
    private val companyListProvider: CompanyListProvider
) {
    companion object {
        private const val TAG = "SubscriptionClassifier"

        /**
         * Prompt template for the first stage LLM call: Company Detection.
         * Aims to identify the company name and assess if the email is likely subscription-related.
         */
        private const val PROMPT_TEMPLATE_COMPANY_DETECTION = """
Analyze the following email content to identify the primary company or service mentioned and determine if it's likely related to a subscription.
Email From: "{email_from}"
Email Subject: "{email_subject}"
Email Body Snippet (first 500 characters):
---
{email_body_snippet}
---
Based ONLY on the text provided, extract the following information in JSON format:
- "company_name": (string) The name of the company or service. If not clearly identifiable, use "Unknown".
- "is_likely_subscription_related": (boolean) True if the email content (subject, snippet) suggests an ongoing subscription, service agreement, account notification, or renewal. False if it seems like a one-time purchase, marketing newsletter for a product, spam, or completely unrelated.
- "confidence_company_detection": (float) Your confidence in this company detection and likelihood assessment, from 0.0 (low) to 1.0 (high).

Example JSON output:
{
  "company_name": "Spotify",
  "is_likely_subscription_related": true,
  "confidence_company_detection": 0.85
}

Return ONLY the JSON object.
"""

        /**
         * Prompt template for the second stage LLM call: Detail Analysis.
         * Used if a known company is detected and the email is likely subscription-related.
         * Aims to extract specific details about the subscription action.
         */
        private const val PROMPT_TEMPLATE_DETAIL_ANALYSIS = """
Analyze the following email content from "{company_name}" (service hint: {service_hint}) for specific subscription details.
Email From: "{email_from}"
Email Subject: "{email_subject}"
Email Body (first 1500 characters):
---
{email_body_full}
---
Based ONLY on the text provided, extract the following information in JSON format:
- "subscription": (boolean) True if this email explicitly confirms or modifies a subscription (paid or free) with "{company_name}". False otherwise.
- "action": (string) The primary action related to the subscription with "{company_name}". Choose one: "start", "renew", "cancel", "payment_reminder", "payment_issue", "trial_start", "trial_ending", "upgrade", "downgrade", "service_update", "none". If no specific action, use "none".
- "date": (string) If a specific date for an event (e.g., start date, cancellation date, renewal date, payment date for "{company_name}") is mentioned, provide it in YYYY-MM-DD format. If not mentioned or not applicable, use "unknown".
- "confidence_analysis": (float) Your confidence in this detailed analysis for "{company_name}", from 0.0 (low) to 1.0 (high).

Example JSON output:
{
  "subscription": true,
  "action": "renew",
  "date": "2024-03-15",
  "confidence_analysis": 0.92
}

Return ONLY the JSON object.
"""

    /**
     * Data class for parsing the JSON output from the first stage LLM call (Company Detection).
     */
    private data class LlmCompanyDetectionOutput(
        val company_name: String?,
        val is_likely_subscription_related: Boolean?,
        val confidence_company_detection: Float?
    )

    /**
     * Data class for parsing the JSON output from the second stage LLM call (Detail Analysis).
     */
    private data class LlmDetailAnalysisOutput(
        val subscription: Boolean?,
        val action: String?,
        val date: String?,
        val confidence_analysis: Float?
    )

    private lateinit var knownCompanies: List<CompanySubscriptionInfo>
    private var knownCompaniesInitialized: Boolean = false


    /**
     * Processes a list of raw emails using a two-stage Large Language Model (LLM) approach
     * to analyze and extract subscription-related information.
     *
     * Stage 1: Company Detection - Identifies the company and likelihood of subscription.
     * Stage 2: Detail Analysis - If a known company is likely, extracts specific subscription details.
     *
     * @param allRawEmails A list of [RawEmail] objects to be processed.
     * @return A list of [EmailAnalysisResult] objects, sorted by the original email index.
     */
    suspend fun processEmailsWithLLM(allRawEmails: List<RawEmail>): List<EmailAnalysisResult> {
        if (!knownCompaniesInitialized || !::knownCompanies.isInitialized) {
            initializeKnownCompanies()
        }

        val resultsList = mutableListOf<EmailAnalysisResult>()
        val companyActionHistory = mutableMapOf<String, String>() 

        Log.i(TAG, "Starting new two-stage LLM processing for ${allRawEmails.size} emails.")

        val sortedEmails = allRawEmails.sortedByDescending { it.date }

        sortedEmails.forEach { email ->
            val originalEmailIndex = allRawEmails.indexOf(email)
            if (originalEmailIndex == -1) {
                Log.e(TAG, "Critical error: Could not find original index for email ID ${email.id}. Skipping.")
                return@forEach
            }

            // Stage 1: Company Detection
            val companyDetectionPrompt = buildPromptForCompanyDetection(email)
            val stage1ResponseString = try {
                huggingFaceRepository.analyzeEmailWithInstructionModel(companyDetectionPrompt)
            } catch (e: Exception) {
                Log.e(TAG, "Stage 1 LLM API call failed for email ID ${email.id}: ${e.message}", e)
                null
            }

            val llmCompanyOutput = parseLlmCompanyDetectionOutput(stage1ResponseString)
            val detectedCompanyName = llmCompanyOutput.company_name ?: "Unknown"
            val isLikelySubscription = llmCompanyOutput.is_likely_subscription_related ?: false
            val companyDetectionConfidence = llmCompanyOutput.confidence_company_detection ?: 0.0f

            Log.d(TAG, "Email ID ${email.id} (Index: $originalEmailIndex) - Stage 1 Output: Company='${detectedCompanyName}', LikelySub='${isLikelySubscription}', Confidence=${companyDetectionConfidence}")

            val knownCompanyMatch = findMatchingKnownCompany(detectedCompanyName)

            if (!isLikelySubscription || (knownCompanyMatch == null && detectedCompanyName != "Unknown" && detectedCompanyName.isNotBlank())) {
                val result = EmailAnalysisResult(
                    email_index = originalEmailIndex,
                    company = detectedCompanyName.takeIf { it.isNotBlank() } ?: "Unknown",
                    subscription = isLikelySubscription,
                    action = "none",
                    date = "unknown",
                    confidence = companyDetectionConfidence,
                    database_op = "NONE"
                )
                resultsList.add(result)
                Log.i(TAG, "Email ID ${email.id} - Stage 1 decision: Not a known/likely subscription or unknown company not matching. Result: $result")
                return@forEach 
            } else if (knownCompanyMatch == null && (detectedCompanyName == "Unknown" || detectedCompanyName.isBlank())) {
                 val result = EmailAnalysisResult(
                    email_index = originalEmailIndex,
                    company = "Unknown",
                    subscription = false, 
                    action = "none",
                    date = "unknown",
                    confidence = companyDetectionConfidence,
                    database_op = "NONE"
                )
                resultsList.add(result)
                Log.i(TAG, "Email ID ${email.id} - Stage 1 decision: Unknown company and not likely subscription. Result: $result")
                return@forEach
            }
            
            if (knownCompanyMatch == null) {
                 Log.e(TAG, "Email ID ${email.id} - Critical logic error: knownCompanyMatch is null after Stage 1 checks (isLikelySubscription was true and detectedCompanyName was 'Unknown' or matched no known company but was blank). Skipping to avoid crash.")
                 resultsList.add(EmailAnalysisResult(originalEmailIndex, detectedCompanyName, isLikelySubscription, "error_logic_s2", "unknown", companyDetectionConfidence, "NONE"))
                 return@forEach
            }

            // Stage 2: Detail Analysis
            val actualCompanyName = knownCompanyMatch.companyName 
            val serviceHint = knownCompanyMatch.serviceType

            val detailAnalysisPrompt = buildPromptForDetailAnalysis(email, actualCompanyName, serviceHint)
            val stage2ResponseString = try {
                huggingFaceRepository.analyzeEmailWithInstructionModel(detailAnalysisPrompt)
            } catch (e: Exception) {
                Log.e(TAG, "Stage 2 LLM API call failed for email ID ${email.id}, Company='${actualCompanyName}': ${e.message}", e)
                null
            }

            val llmDetailOutput = parseLlmDetailAnalysisOutput(stage2ResponseString)
            Log.d(TAG, "Email ID ${email.id} - Stage 2 Output for '${actualCompanyName}': $llmDetailOutput")

            val finalSubscriptionStatus = llmDetailOutput.subscription ?: false
            val finalAction = llmDetailOutput.action ?: "none"
            val finalDate = llmDetailOutput.date ?: "unknown"
            val stage2Confidence = llmDetailOutput.confidence_analysis ?: 0.0f
            
            val finalConfidence = if (stage2Confidence > 0.01f) { 
                (companyDetectionConfidence + stage2Confidence) / 2.0f
            } else {
                companyDetectionConfidence 
            }

            val databaseOp = determineDatabaseOp(finalAction, actualCompanyName, companyActionHistory)

            val analysisResult = EmailAnalysisResult(
                email_index = originalEmailIndex,
                company = actualCompanyName,
                subscription = finalSubscriptionStatus,
                action = finalAction,
                date = finalDate,
                confidence = String.format("%.2f", finalConfidence).toFloat(),
                database_op = databaseOp
            )
            resultsList.add(analysisResult)
            Log.i(TAG, "Email ID ${email.id} - Stage 2 Result for '${actualCompanyName}': $analysisResult")
        }

        Log.i(TAG, "New LLM processing completed. ${resultsList.size} results generated.")
        return resultsList.sortedBy { it.email_index }
    }

    private suspend fun initializeKnownCompanies() {
        if (knownCompaniesInitialized && ::knownCompanies.isInitialized && knownCompanies.isNotEmpty()) {
            Log.d(TAG, "Known companies already initialized and not empty. Size: ${knownCompanies.size}")
            return
        }
        if (knownCompaniesInitialized && ::knownCompanies.isInitialized && knownCompanies.isEmpty()){
            Log.w(TAG, "Known companies was marked initialized but list is empty. Attempting to re-initialize.")
        }

        Log.i(TAG, "Initializing known companies list...")
        try {
            val companies = companyListProvider.loadCompanyList()
            knownCompanies = companies 
            if (knownCompanies.isEmpty()) {
                Log.w(TAG, "Known companies list is empty after loading from provider.")
            } else {
                Log.i(TAG, "Successfully initialized ${knownCompanies.size} known companies.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing known companies: ${e.message}", e)
            knownCompanies = emptyList() 
        } finally {
            knownCompaniesInitialized = true 
        }
    }

    private fun buildPromptForCompanyDetection(email: RawEmail): String {
        val bodySnippet = (email.bodySnippet ?: email.snippet ?: email.bodyPlainText ?: "").take(500)
        return PROMPT_TEMPLATE_COMPANY_DETECTION
            .replace("{email_from}", email.from.replace("\"", "\\\""))
            .replace("{email_subject}", email.subject.replace("\"", "\\\""))
            .replace("{email_body_snippet}", bodySnippet.replace("\"", "\\\""))
    }

    private fun buildPromptForDetailAnalysis(email: RawEmail, companyName: String, serviceHint: String): String {
        val bodyFull = (email.bodyPlainText ?: email.bodySnippet ?: email.snippet ?: "").take(1500)
        return PROMPT_TEMPLATE_DETAIL_ANALYSIS
            .replace("{company_name}", companyName.replace("\"", "\\\""))
            .replace("{service_hint}", serviceHint.ifBlank { "N/A" }.replace("\"", "\\\""))
            .replace("{email_from}", email.from.replace("\"", "\\\""))
            .replace("{email_subject}", email.subject.replace("\"", "\\\""))
            .replace("{email_body_full}", bodyFull.replace("\"", "\\\""))
    }

    private fun parseLlmCompanyDetectionOutput(jsonString: String?): LlmCompanyDetectionOutput {
        if (jsonString.isNullOrBlank()) {
            Log.w(TAG, "Company detection LLM response was null or blank.")
            return LlmCompanyDetectionOutput("Unknown", false, 0.0f)
        }
        return try {
            val cleanedResponse = jsonString.substringAfter("```json\n", jsonString)
                                           .substringBeforeLast("\n```", jsonString)
                                           .trim()
            val jsonObject = JSONObject(cleanedResponse)
            LlmCompanyDetectionOutput(
                company_name = jsonObject.optString("company_name", "Unknown").ifBlank { "Unknown" },
                is_likely_subscription_related = jsonObject.optBoolean("is_likely_subscription_related", false),
                confidence_company_detection = jsonObject.optDouble("confidence_company_detection", 0.0).toFloat()
            )
        } catch (e: org.json.JSONException) {
            Log.e(TAG, "Failed to parse company detection LLM JSON: $jsonString Error: ${e.message}", e)
            LlmCompanyDetectionOutput("Unknown", false, 0.0f)
        }
    }

    private fun parseLlmDetailAnalysisOutput(jsonString: String?): LlmDetailAnalysisOutput {
        if (jsonString.isNullOrBlank()) {
            Log.w(TAG, "Detail analysis LLM response was null or blank.")
            return LlmDetailAnalysisOutput(false, "none", "unknown", 0.0f)
        }
        return try {
            val cleanedResponse = jsonString.substringAfter("```json\n", jsonString)
                                           .substringBeforeLast("\n```", jsonString)
                                           .trim()
            val jsonObject = JSONObject(cleanedResponse)
            LlmDetailAnalysisOutput(
                subscription = jsonObject.optBoolean("subscription", false),
                action = jsonObject.optString("action", "none").ifBlank { "none" },
                date = jsonObject.optString("date", "unknown").ifBlank { "unknown" },
                confidence_analysis = jsonObject.optDouble("confidence_analysis", 0.0).toFloat()
            )
        } catch (e: org.json.JSONException) {
            Log.e(TAG, "Failed to parse detail analysis LLM JSON: $jsonString Error: ${e.message}", e)
            LlmDetailAnalysisOutput(false, "none", "unknown", 0.0f)
        }
    }
    
    private fun normalizeString(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        val noDiacritics = Regex("\\p{Mn}+").replace(normalized, "")
        return noDiacritics.lowercase()
            .replace(" inc.", "")
            .replace(" ltd.", "")
            .replace(" llc", "")
            .replace(".com", "")
            .replace(" com tr", "")
            .replace("www.", "")
            .replace(Regex("[^a-z0-9\\s-]"), "") 
            .replace(Regex("\\s+"), " ") 
            .trim()
    }

    private fun findMatchingKnownCompany(detectedName: String?): CompanySubscriptionInfo? {
        if (detectedName.isNullOrBlank() || detectedName.equals("Unknown", ignoreCase = true)) {
            return null
        }
         if (!::knownCompanies.isInitialized || knownCompanies.isEmpty()){
            Log.w(TAG, "Known company list is not initialized or empty when trying to match: $detectedName.")
            return null
        }

        val normalizedDetectedName = normalizeString(detectedName)

        knownCompanies.firstOrNull { company ->
            normalizeString(company.companyName) == normalizedDetectedName
        }?.let {
            Log.d(TAG, "Exact match found for '$detectedName' (normalized: $normalizedDetectedName) -> '${it.companyName}'")
            return it
        }

        knownCompanies.firstOrNull { company ->
            val normalizedKnownName = normalizeString(company.companyName)
            normalizedKnownName.contains(normalizedDetectedName) || normalizedDetectedName.contains(normalizedKnownName)
        }?.let {
            Log.d(TAG, "Contains match found for '$detectedName' (normalized: $normalizedDetectedName) -> '${it.companyName}'")
            return it
        }
        
        knownCompanies.firstOrNull { company ->
            val normalizedKnownName = normalizeString(company.companyName)
            normalizedDetectedName.startsWith(normalizedKnownName)
        }?.let {
            Log.d(TAG, "Starts with match for '$detectedName' (normalized: $normalizedDetectedName) -> '${it.companyName}'")
            return it
        }

        Log.d(TAG, "No match found in known companies for: $detectedName (normalized: $normalizedDetectedName)")
        return null
    }

    private fun determineDatabaseOp(action: String?, companyName: String, history: MutableMap<String, String>): String {
        val companyKey = companyName.lowercase().trim() 
        if (companyKey.isBlank() || companyKey == "unknown" || action.isNullOrBlank() || action == "none") {
            return "NONE"
        }

        return when (action) {
            "start", "trial_start" -> {
                if (history[companyKey] != "start") { 
                    history[companyKey] = "start"
                    "CREATE_EVENT"
                } else {
                    Log.d(TAG, "Duplicate 'start' or 'trial_start' action for $companyKey, op: NONE.")
                    "NONE"
                }
            }
            "cancel" -> {
                if (history[companyKey] == "start" || history[companyKey] == "renew") {
                    history[companyKey] = "cancel"
                    "UPDATE_EVENT"
                } else {
                    Log.d(TAG, "Cancel action for $companyKey without prior 'start' or 'renew', op: NONE. Current history: ${history[companyKey]}")
                    "NONE"
                }
            }
            "renew" -> {
                if (history[companyKey] == "start" || history[companyKey] == "renew") {
                    history[companyKey] = "renew" 
                    "NONE"
                } else if (history[companyKey] == null || history[companyKey] == "cancel") {
                    history[companyKey] = "start" 
                    Log.d(TAG, "Renewal for $companyKey with no/cancelled prior history, treating as CREATE_EVENT.")
                    "CREATE_EVENT"
                } else {
                    Log.d(TAG, "Renewal for $companyKey with unexpected history '${history[companyKey]}', op: NONE.")
                    "NONE"
                }
            }
            else -> "NONE"
        }
    }
}
