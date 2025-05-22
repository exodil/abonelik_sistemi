// file: app/java/com/example/abonekaptanmobile/services/SubscriptionClassifier.kt
package com.example.abonekaptanmobile.services

import android.util.Log // Single import for Log
import com.example.abonekaptanmobile.data.local.dao.UserSubscriptionDao
import com.example.abonekaptanmobile.data.local.entity.PatternType
import com.example.abonekaptanmobile.data.local.entity.SubscriptionPatternEntity
import com.example.abonekaptanmobile.data.local.entity.UserSubscriptionEntity
import com.example.abonekaptanmobile.data.repository.CommunityPatternRepository // Kept for determineServiceName
import com.example.abonekaptanmobile.data.repository.HuggingFaceRepository
import com.example.abonekaptanmobile.model.RawEmail
import java.util.regex.PatternSyntaxException // Kept for matchesPattern
import javax.inject.Inject

/**
 * Service responsible for classifying emails to identify subscription-related lifecycle events
 * (like new subscriptions or cancellations) using AI models and persisting these findings
 * into the local database. It leverages [HuggingFaceRepository] for AI classification and
 * [UserSubscriptionDao] for database interactions. Service name identification may still use
 * [CommunityPatternRepository] as a fallback or supplement.
 *
 * Turkish: E-postaları abonelik yaşam döngüsü olaylarına göre (örneğin yeni abonelikler veya
 * iptaller) AI modelleri kullanarak sınıflandıran ve bu bulguları yerel veritabanına
 * kaydeden servistir. AI sınıflandırması için [HuggingFaceRepository]'yi ve veritabanı
 * etkileşimleri için [UserSubscriptionDao]'yu kullanır. Servis adı tanımlama, bir geri
 * dönüş veya ek olarak [CommunityPatternRepository]'yi kullanabilir.
 */
class SubscriptionClassifier @Inject constructor(
    private val communityPatternRepo: CommunityPatternRepository,
    private val huggingFaceRepository: HuggingFaceRepository,
    private val userSubscriptionDao: UserSubscriptionDao
) {

    companion object {
        // Confidence threshold for AI model's predictions to be considered valid.
        private const val CONFIDENCE_THRESHOLD = 0.75f
        // Constant for active subscription status in the database.
        private const val STATUS_ACTIVE = "ACTIVE"
        // Constant for cancelled subscription status in the database.
        private const val STATUS_CANCELLED = "CANCELLED"
    }

    /**
     * Classifies a list of raw emails to identify subscription lifecycle events and updates the database accordingly.
     * Emails are processed in descending order of their date (newest first).
     * For each email, it attempts to determine the service name, then uses an AI model
     * ([HuggingFaceRepository.classifySubscriptionLifecycle]) to detect if it's a paid subscription event
     * or a cancellation event. Based on the classification, it calls helper methods
     * [handlePaidSubscriptionEvent] or [handlePaidSubscriptionCancellation] to process the event.
     *
     * @param allRawEmails A list of [RawEmail] objects to be classified.
     *
     * Turkish: Ham e-postaların bir listesini sınıflandırarak abonelik yaşam döngüsü olaylarını
     * tanımlar ve veritabanını buna göre günceller. E-postalar tarihlerine göre azalan sırada
     * (en yeniden en eskiye) işlenir. Her e-posta için servis adını belirlemeye çalışır, ardından
     * ücretli bir abonelik olayı mı yoksa bir iptal olayı mı olduğunu tespit etmek için bir AI modeli
     * ([HuggingFaceRepository.classifySubscriptionLifecycle]) kullanır. Sınıflandırmaya bağlı olarak,
     * olayı işlemek için [handlePaidSubscriptionEvent] veya [handlePaidSubscriptionCancellation] yardımcı
     * metotlarını çağırır.
     */
    suspend fun classifyEmails(
        allRawEmails: List<RawEmail>,
        onProgress: suspend (processedCount: Int, totalToProcess: Int) -> Unit // New callback
    ) {
        Log.i("SubscriptionClassifier", "classifyEmails - Input: ${allRawEmails.size} emails.")

        // Sort emails by date, newest first, to process events in chronological order.
        val sortedEmails = allRawEmails.sortedByDescending { it.date }
        val totalToProcess = sortedEmails.size
        var processedCount = 0

        // Fetch patterns for service name determination, used as a fallback or supplement to other methods.
        val allReliableSubPatterns = communityPatternRepo.getReliableSubscriptionPatterns()
            .filter { it.isSubscription } // Ensure only actual subscription patterns are used.
            .sortedByDescending { it.priority } // Process higher priority patterns first for service name.
        if (allReliableSubPatterns.isEmpty()) {
            Log.w("SubscriptionClassifier", "No reliable subscription patterns found for determineServiceName. Service name identification might be less accurate.")
        }

        try {
            for (email in sortedEmails) {
                try {
                    val emailContent = prepareEmailContentForClassification(email)
                    val lifecycleResult = huggingFaceRepository.classifySubscriptionLifecycle(emailContent)

                    val serviceName = determineServiceName(email, allReliableSubPatterns)

                // userId is currently null. Future enhancements could allow per-user classification.
                val userId: String? = null

                // Get scores for relevant lifecycle events.
                val paidEventScore = lifecycleResult.getScoreForLabel("paid_subscription_event")
                val cancellationScore = lifecycleResult.getScoreForLabel("paid_subscription_cancellation")

                // Determine action based on scores and confidence threshold.
                // Prioritize paid events if both scores are high but paid is higher.
                if (paidEventScore >= CONFIDENCE_THRESHOLD && paidEventScore > cancellationScore) {
                    handlePaidSubscriptionEvent(email, serviceName, userId)
                } else if (cancellationScore >= CONFIDENCE_THRESHOLD) {
                    handlePaidSubscriptionCancellation(email, serviceName, userId)
                } else {
                    // Log if the email classification is ambiguous or below threshold.
                    Log.d("SubscriptionClassifier", "Email ${email.id} for $serviceName (user: $userId) did not meet classification thresholds or was ambiguous. PaidScore: $paidEventScore, CancelScore: $cancellationScore. Labels: ${lifecycleResult.allResults.joinToString { it.label + ": " + it.score }}")
                }

                } catch (e: Exception) {
                    Log.e("SubscriptionClassifier", "Error processing email ${email.id} (Subject: ${email.subject.take(50)}) in classifyEmails: ${e.message}", e)
                } finally {
                    processedCount++
                    onProgress(processedCount, totalToProcess)
                }
            }
        } finally {
            // Ensure final progress is reported, especially if loop terminated unexpectedly or completed.
            // This also handles the case where sortedEmails is empty.
            onProgress(processedCount, totalToProcess)
        }
        Log.i("SubscriptionClassifier", "Finished processing $processedCount of $totalToProcess emails.")
    }

    /**
     * Handles the logic for when a "paid_subscription_event" is detected for an email.
     * This can mean a new subscription starting, or a re-activation of a previously cancelled one.
     * It checks the existing subscription status in the database and either inserts a new
     * [UserSubscriptionEntity] or updates an existing one.
     *
     * @param email The [RawEmail] identified as a paid subscription event.
     * @param serviceName The name of the service identified for this subscription.
     * @param userId The identifier for the user; currently nullable and typically null.
     *
     * Turkish: Bir e-posta için "paid_subscription_event" algılandığında mantığı işler.
     * Bu, yeni bir aboneliğin başlaması veya daha önce iptal edilmiş bir aboneliğin yeniden
     * etkinleştirilmesi anlamına gelebilir. Veritabanındaki mevcut abonelik durumunu kontrol eder ve
     * ya yeni bir [UserSubscriptionEntity] ekler ya da mevcut olanı günceller.
     */
    private suspend fun handlePaidSubscriptionEvent(email: RawEmail, serviceName: String, userId: String?) {
        val existingSubscription = userSubscriptionDao.getLatestSubscriptionByServiceNameAndUserId(serviceName, userId)
        val endDate = existingSubscription?.subscriptionEndDate // Store in a local variable

        // Scenario 1: No existing subscription found, or it was cancelled and this email is newer than the cancellation end date.
        // This indicates a new subscription or a re-subscription.
        if (existingSubscription == null ||
            (existingSubscription.status == STATUS_CANCELLED && endDate != null && email.date > endDate)) {
            val newSub = UserSubscriptionEntity(
                serviceName = serviceName,
                userId = userId,
                subscriptionStartDate = email.date, // Start date is the date of this email.
                status = STATUS_ACTIVE,
                lastEmailIdProcessed = email.id,
                lastActiveConfirmationDate = email.date, // Confirmation date is also this email's date.
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            userSubscriptionDao.insert(newSub)
            Log.i("SubscriptionClassifier", "New subscription started for $serviceName (user: $userId) from email ${email.id} dated ${email.date}.")
        // Scenario 2: An active subscription already exists. Update its last active confirmation.
        } else if (existingSubscription.status == STATUS_ACTIVE) {
            existingSubscription.lastActiveConfirmationDate = email.date
            existingSubscription.lastEmailIdProcessed = email.id
            existingSubscription.updatedAt = System.currentTimeMillis()
            userSubscriptionDao.update(existingSubscription)
            Log.i("SubscriptionClassifier", "Subscription activity updated for $serviceName (user: $userId) from email ${email.id} dated ${email.date}.")
        // Scenario 3: Paid event received for an already cancelled subscription, but the event date is not after the cancellation.
        // This might be a late-arriving confirmation for a period already covered by the previous subscription term.
        } else {
             Log.i("SubscriptionClassifier", "Paid event for $serviceName (user: $userId, email ${email.id} dated ${email.date}) but current status is ${existingSubscription.status} and email date is not after recorded end date ${existingSubscription.subscriptionEndDate}. No state change action taken.")
        }
    }

    /**
     * Handles the logic for when a "paid_subscription_cancellation" event is detected.
     * If an active subscription exists for the service, it's updated to "CANCELLED",
     * and the cancellation date is recorded.
     *
     * @param email The [RawEmail] identified as a cancellation event.
     * @param serviceName The name of the service for which the cancellation was detected.
     * @param userId The identifier for the user; currently nullable and typically null.
     *
     * Turkish: "paid_subscription_cancellation" olayı algılandığında mantığı işler.
     * Servis için aktif bir abonelik mevcutsa, "CANCELLED" olarak güncellenir ve iptal tarihi
     * kaydedilir.
     */
    private suspend fun handlePaidSubscriptionCancellation(email: RawEmail, serviceName: String, userId: String?) {
        val existingSubscription = userSubscriptionDao.getLatestSubscriptionByServiceNameAndUserId(serviceName, userId)

        // Only proceed if there's an active subscription to cancel.
        if (existingSubscription != null && existingSubscription.status == STATUS_ACTIVE) {
            // Sanity check: A cancellation email should not be dated before the subscription started.
            if (email.date < existingSubscription.subscriptionStartDate) {
                Log.w("SubscriptionClassifier", "Cancellation email for $serviceName (user: $userId, email ${email.id} dated ${email.date}) is dated before subscription start date (${existingSubscription.subscriptionStartDate}). Ignoring cancellation event.")
                return
            }
            existingSubscription.subscriptionEndDate = email.date // Cancellation date is the email's date.
            existingSubscription.status = STATUS_CANCELLED
            existingSubscription.lastEmailIdProcessed = email.id
            existingSubscription.updatedAt = System.currentTimeMillis()
            userSubscriptionDao.update(existingSubscription)
            Log.i("SubscriptionClassifier", "Subscription cancelled for $serviceName (user: $userId) from email ${email.id} dated ${email.date}.")
        // Scenario: No active subscription found, or it was already cancelled. Log this for information.
        } else {
            Log.i("SubscriptionClassifier", "Cancellation event for $serviceName (user: $userId, email ${email.id} dated ${email.date}) but no active subscription found or it was already not active. Current state: $existingSubscription")
        }
    }

    // Note: prepareEmailContentForClassification, determineServiceName, extractDomain, matchesPattern,
    // and capitalizeWords are kept as they support the core logic (especially service name determination).
    // Their own KDocs are assumed to be sufficient unless specific changes were requested for them.

    /**
     * Prepares the email content for AI classification by combining subject, sender, and body/snippet.
     *
     * Turkish: E-posta içeriğini, konu, gönderen ve gövde/kısa içerik bilgilerini birleştirerek
     * AI sınıflandırması için hazırlar.
     */
    private fun prepareEmailContentForClassification(email: RawEmail): String {
        val bodyContent = when {
            email.bodySnippet != null -> email.bodySnippet
            email.snippet != null -> email.snippet
            else -> email.bodyPlainText.take(500) // Use a portion of plain text body as a fallback.
        }
        return "Subject: ${email.subject}\nFrom: ${email.from}\nContent: $bodyContent"
    }

    /**
     * Determines the service name for a given email.
     * It first tries to match the email against a list of reliable [SubscriptionPatternEntity]s.
     * If no pattern matches, it falls back to [extractGeneralServiceName] for heuristic-based extraction.
     *
     * Turkish: Verilen bir e-posta için servis adını belirler.
     * Önce e-postayı güvenilir [SubscriptionPatternEntity] listesiyle eşleştirmeye çalışır.
     * Eğer hiçbir kalıp eşleşmezse, sezgisel tabanlı çıkarma için [extractGeneralServiceName]'e geri döner.
     */
    private fun determineServiceName(email: RawEmail, patterns: List<SubscriptionPatternEntity>): String {
        // Attempt to match with predefined patterns first.
        for (pattern in patterns) {
            if (matchesPattern(email, pattern)) {
                return pattern.serviceName
            }
        }
        // Fallback to general service name extraction if no pattern matches.
        return extractGeneralServiceName(email.from, email.subject, email.bodySnippet)
    }

    /**
     * Extracts the domain from an email address.
     * Helper for [extractGeneralServiceName].
     *
     * Turkish: Bir e-posta adresinden alan adını çıkarır.
     * [extractGeneralServiceName] için yardımcıdır.
     */
    private fun extractDomain(emailAddress: String): String? {
        val domainMatch = Regex("@([a-zA-Z0-9.-]+)").find(emailAddress)
        return domainMatch?.groupValues?.get(1)
    }

    /**
     * Checks if an email matches a given [SubscriptionPatternEntity].
     * The matching logic depends on the [PatternType] defined in the pattern.
     *
     * Turkish: Bir e-postanın verilen bir [SubscriptionPatternEntity] ile eşleşip eşleşmediğini kontrol eder.
     * Eşleştirme mantığı, kalıpta tanımlanan [PatternType]'a bağlıdır.
     */
    private fun matchesPattern(email: RawEmail, pattern: SubscriptionPatternEntity): Boolean {
        val bodyText = when {
            email.bodySnippet != null -> email.bodySnippet
            email.snippet != null -> email.snippet
            else -> "" // Ensure bodyText is not null for safe lowercasing.
        }

        // Determine which part of the email to search based on the pattern type.
        val contentToSearch = when (pattern.patternType) {
            PatternType.DOMAIN -> email.from.lowercase()
            PatternType.SENDER_EMAIL -> email.from.lowercase()
            PatternType.SUBJECT_KEYWORD -> email.subject.lowercase()
            PatternType.BODY_KEYWORD -> bodyText.lowercase()
            PatternType.COMBINED, PatternType.UNKNOWN -> "${email.from} ${email.subject} $bodyText".lowercase()
            else -> {
                Log.w("SubscriptionClassifier", "Unknown pattern type: ${pattern.patternType}, defaulting to combined search.")
                "${email.from} ${email.subject} $bodyText".lowercase()
            }
        }
        return try {
            val regex = Regex(pattern.regexPattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            regex.containsMatchIn(contentToSearch)
        } catch (e: PatternSyntaxException) {
            Log.e("SubscriptionClassifier", "Invalid regex: '${pattern.regexPattern}' for service '${pattern.serviceName}' (ID: ${pattern.id}). Error: ${e.message}")
            false
        }
    }
    
    /**
     * Extracts a general service name from email fields (sender, subject, snippet) using heuristics.
     * This is a fallback mechanism if pattern-based matching in [determineServiceName] fails.
     * It tries to find a plausible service name from the sender's display name, subject keywords, or domain parts.
     *
     * Turkish: E-posta alanlarından (gönderen, konu, kısa içerik) sezgisel yöntemler kullanarak genel bir servis adı çıkarır.
     * Bu, [determineServiceName]'deki kalıp tabanlı eşleştirme başarısız olursa kullanılan bir geri dönüş mekanizmasıdır.
     * Gönderenin görünen adından, konu anahtar kelimelerinden veya alan adı bölümlerinden makul bir servis adı bulmaya çalışır.
     */
    private fun extractGeneralServiceName(from: String, subject: String, bodySnippet: String?): String {
        // Attempt to extract from sender's display name (part before '<').
        var serviceNameFromSenderDisplayName = from.substringBefore('<').trim().removeSurrounding("\"")
        if (serviceNameFromSenderDisplayName.contains("@") || serviceNameFromSenderDisplayName.length > 30 || serviceNameFromSenderDisplayName.isEmpty() || serviceNameFromSenderDisplayName.length < 3) {
            serviceNameFromSenderDisplayName = "" // Invalidate if it looks like an email or is too long/short.
        }

        // Common words/domains to avoid mistaking as service names.
        val commonDomainsToAvoidAsName = listOf("google", "googlemail", "gmail", "facebook", "microsoft", "apple", "amazon", "yahoo", "outlook", "hotmail", "support", "info", "noreply", "service", "team", "mail", "email", "com", "newsletter", "update", "alert", "bildirim", "duyuru", "haber", "mailchimp", "sendgrid")

        if (serviceNameFromSenderDisplayName.isNotBlank() && !commonDomainsToAvoidAsName.any { serviceNameFromSenderDisplayName.lowercase().contains(it) }) {
            return serviceNameFromSenderDisplayName.capitalizeWords()
        }
        
        // Attempt to extract from capitalized keywords in the subject.
        val subjectKeywords = subject.split(Regex("[^a-zA-Z0-9İıÖöÜüÇçŞşĞğ]+"))
            .filter { it.length in 4..20 && it.firstOrNull()?.isLetter() == true && it.first().isUpperCase() && !it.matches(Regex("^[A-ZİÖÜüÇŞĞ]+$")) }
            .distinct()
        if (subjectKeywords.isNotEmpty()) {
            val bestSubjectKeyword = subjectKeywords.firstOrNull { keyword -> !commonDomainsToAvoidAsName.any { keyword.lowercase().contains(it) } }
            if (bestSubjectKeyword != null) {
                return bestSubjectKeyword.capitalizeWords()
            }
        }
        
        // Attempt to extract from email domain parts.
        val domainMatch = Regex("@([a-zA-Z0-9.-]+)").find(from)
        val fullDomain = domainMatch?.groupValues?.get(1)
        if (fullDomain != null) {
            val parts = fullDomain.split('.')
            if (parts.isNotEmpty()) {
                // Try to get a meaningful part of the domain, avoiding generic TLDs or common service provider domains.
                val potentialNameFromDomain = if (parts.size > 1 && commonDomainsToAvoidAsName.contains(parts.getOrNull(parts.size - 2)?.lowercase())) {
                    if (parts.size > 2 && !commonDomainsToAvoidAsName.contains(parts.getOrNull(parts.size - 3)?.lowercase())) parts.getOrNull(parts.size - 3) else parts.firstOrNull()
                } else {
                    parts.firstOrNull()
                }
                if (potentialNameFromDomain != null && potentialNameFromDomain.length > 2 && !commonDomainsToAvoidAsName.contains(potentialNameFromDomain.lowercase()))
                    return potentialNameFromDomain.capitalizeWords()
            }
        }
        
        // Fallback to a less specific part of the domain if still nothing.
        var serviceNameFromDomainPart = domainMatch?.groupValues?.get(1)?.substringBeforeLast(".")?.substringAfterLast(".")
        if (serviceNameFromDomainPart != null && serviceNameFromDomainPart.length < 3 && fullDomain?.contains(".") == true) { // if domain is like "example.co.uk", try "example"
            serviceNameFromDomainPart = fullDomain.substringBeforeLast(".") 
            if(serviceNameFromDomainPart.contains(".")) serviceNameFromDomainPart = serviceNameFromDomainPart.substringAfterLast(".") // if "news.example", take "example"
        }
        return serviceNameFromDomainPart?.capitalizeWords()?.takeIf { it.isNotBlank() && !commonDomainsToAvoidAsName.contains(it.lowercase()) } ?: "Unknown Service" // Default if all else fails.
    }
}

/**
 * Extension function to capitalize each word in a string.
 * E.g., "hello world" becomes "Hello World".
 *
 * Turkish: Bir stringteki her kelimenin ilk harfini büyük yapar.
 * Örneğin, "hello world" -> "Hello World".
 */
fun String.capitalizeWords(): String = this.split(Regex("\\s+")).joinToString(" ") { word ->
    if (word.isEmpty()) {
        ""
    } else {
        word.replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase() // More locale-aware than toUpperCase() for the first char.
            } else {
                char.toString()
            }
        }
    }
}