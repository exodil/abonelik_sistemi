package com.example.abonekaptanmobile.model

/**
 * Represents the result of an email analysis.
 *
 * @property email_index The index of the email.
 * @property company The company associated with the email.
 * @property subscription Indicates whether the email is related to a subscription.
 * @property action The action identified in the email (e.g., "start", "renew", "cancel", "none").
 * @property date The date associated with the action (ISO format YYYY-MM-DD or "unknown").
 * @property confidence The confidence level of the analysis (0.0 to 1.0).
 * @property database_op The database operation to be performed (e.g., "CREATE_EVENT", "UPDATE_EVENT", "NONE").
 */
data class EmailAnalysisResult(
    val email_index: Int,
    val company: String,
    val subscription: Boolean,
    val action: String,
    val date: String,
    val confidence: Float,
    val database_op: String
)
