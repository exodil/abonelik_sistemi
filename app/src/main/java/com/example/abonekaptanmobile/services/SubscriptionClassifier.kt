// file: app/java/com/example/abonekaptanmobile/services/SubscriptionClassifier.kt
package com.example.abonekaptanmobile.services

import android.util.Log
import com.example.abonekaptanmobile.data.local.entity.PatternType
import com.example.abonekaptanmobile.data.local.entity.SubscriptionPatternEntity
import com.example.abonekaptanmobile.data.remote.model.ClassificationResult
import com.example.abonekaptanmobile.data.repository.CommunityPatternRepository
import com.example.abonekaptanmobile.data.repository.HuggingFaceRepository
import com.example.abonekaptanmobile.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit
import java.util.regex.PatternSyntaxException
import javax.inject.Inject

/**
 * Turkish: E-postaları abonelik durumuna göre sınıflandıran servis.
 * English: Service that classifies emails based on subscription status.
 */
class SubscriptionClassifier @Inject constructor(
    private val communityPatternRepo: CommunityPatternRepository,
    private val huggingFaceRepository: HuggingFaceRepository
) {
    private val inactivityThresholdDays = 90L
    private val inactivityThresholdMillis = TimeUnit.DAYS.toMillis(inactivityThresholdDays)

    // İptal kalıpları - Geliştirilmiş
    private val cancelPatterns = listOf(
        Regex("aboneliğiniz iptal edildi", RegexOption.IGNORE_CASE),
        Regex("üyeliğiniz sonlandırıldı", RegexOption.IGNORE_CASE),
        Regex("subscription cancelled", RegexOption.IGNORE_CASE),
        Regex("membership ended", RegexOption.IGNORE_CASE),
        Regex("your subscription has been canceled", RegexOption.IGNORE_CASE),
        Regex("hesabınız kapatıldı", RegexOption.IGNORE_CASE),
        Regex("account closed", RegexOption.IGNORE_CASE),
        Regex("iptal onaylandı", RegexOption.IGNORE_CASE),
        Regex("cancellation confirmed", RegexOption.IGNORE_CASE),
        Regex("üyeliğiniz sona erdi", RegexOption.IGNORE_CASE),
        Regex("aboneliğiniz sona ermiştir", RegexOption.IGNORE_CASE),
        Regex("subscription has been terminated", RegexOption.IGNORE_CASE),
        Regex("membership has been cancelled", RegexOption.IGNORE_CASE),
        Regex("we've processed your cancellation", RegexOption.IGNORE_CASE),
        Regex("your cancellation request", RegexOption.IGNORE_CASE),
        Regex("iptal talebiniz", RegexOption.IGNORE_CASE),
        Regex("aboneliğinizi iptal ettiniz", RegexOption.IGNORE_CASE)
    )

    // Abonelik başlangıç kalıpları
    private val startPatterns = listOf(
        Regex("aboneliğiniz başladı", RegexOption.IGNORE_CASE),
        Regex("üyeliğiniz aktifleştirildi", RegexOption.IGNORE_CASE),
        Regex("subscription activated", RegexOption.IGNORE_CASE),
        Regex("welcome to your .* subscription", RegexOption.IGNORE_CASE),
        Regex("your subscription has started", RegexOption.IGNORE_CASE),
        Regex("your membership has begun", RegexOption.IGNORE_CASE),
        Regex("aboneliğiniz başarıyla oluşturuldu", RegexOption.IGNORE_CASE),
        Regex("üyeliğiniz başarıyla başlatıldı", RegexOption.IGNORE_CASE),
        Regex("subscription confirmed", RegexOption.IGNORE_CASE),
        Regex("thank you for subscribing", RegexOption.IGNORE_CASE),
        Regex("abone olduğunuz için teşekkürler", RegexOption.IGNORE_CASE),
        Regex("hoş geldiniz", RegexOption.IGNORE_CASE),
        Regex("welcome to", RegexOption.IGNORE_CASE)
    )

    // Ödeme kalıpları
    private val paymentPatterns = listOf(
        Regex("aylık ödeme planı", RegexOption.IGNORE_CASE),
        Regex("yıllık ödeme planı", RegexOption.IGNORE_CASE),
        Regex("monthly subscription payment", RegexOption.IGNORE_CASE),
        Regex("annual subscription payment", RegexOption.IGNORE_CASE),
        Regex("faturanız", RegexOption.IGNORE_CASE),
        Regex("makbuzunuz", RegexOption.IGNORE_CASE),
        Regex("üyelik ücreti", RegexOption.IGNORE_CASE),
        Regex("payment receipt", RegexOption.IGNORE_CASE),
        Regex("invoice for your subscription", RegexOption.IGNORE_CASE),
        Regex("payment confirmation", RegexOption.IGNORE_CASE),
        Regex("ödeme onayı", RegexOption.IGNORE_CASE),
        Regex("ödemeniz alındı", RegexOption.IGNORE_CASE),
        Regex("payment received", RegexOption.IGNORE_CASE),
        Regex("\\d+[.,]\\d{2} (TL|USD|EUR|GBP)", RegexOption.IGNORE_CASE),
        Regex("\\$\\d+[.,]\\d{2}", RegexOption.IGNORE_CASE),
        Regex("€\\d+[.,]\\d{2}", RegexOption.IGNORE_CASE),
        Regex("£\\d+[.,]\\d{2}", RegexOption.IGNORE_CASE),
        Regex("\\d+[.,]\\d{2} ₺", RegexOption.IGNORE_CASE)
    )

    // Reklam kalıpları
    private val promotionalPatterns = listOf(
        Regex("özel teklif", RegexOption.IGNORE_CASE),
        Regex("special offer", RegexOption.IGNORE_CASE),
        Regex("limited time offer", RegexOption.IGNORE_CASE),
        Regex("sınırlı süre teklifi", RegexOption.IGNORE_CASE),
        Regex("discount", RegexOption.IGNORE_CASE),
        Regex("indirim", RegexOption.IGNORE_CASE),
        Regex("kampanya", RegexOption.IGNORE_CASE),
        Regex("promotion", RegexOption.IGNORE_CASE),
        Regex("deal", RegexOption.IGNORE_CASE),
        Regex("fırsat", RegexOption.IGNORE_CASE),
        Regex("kaçırma", RegexOption.IGNORE_CASE),
        Regex("don't miss", RegexOption.IGNORE_CASE),
        Regex("ücretsiz deneme", RegexOption.IGNORE_CASE),
        Regex("free trial", RegexOption.IGNORE_CASE)
    )

    /**
     * Turkish: E-postaları sınıflandırır ve abonelik öğelerini oluşturur.
     * English: Classifies emails and creates subscription items.
     */
    suspend fun classifyEmails(allRawEmails: List<RawEmail>): List<SubscriptionItem> = coroutineScope {
        Log.i("SubscriptionClassifier", "ClassifyEmails - Input: ${allRawEmails.size} emails.")

        // E-postaları tarih sırasına göre sırala (en yeniden en eskiye)
        val sortedEmails = allRawEmails.sortedByDescending { it.date }
        val classifiedDetailsCollector = mutableMapOf<String, MutableList<ClassifiedEmail>>()

        // Adım 1: Kesinlikle abonelik olmayanları ele
        val nonSubscriptionPatterns = communityPatternRepo.getNonSubscriptionPatterns().sortedByDescending { it.priority }
        Log.d("SubscriptionClassifier", "Fetched ${nonSubscriptionPatterns.size} non-subscription patterns. Examples: ${nonSubscriptionPatterns.take(3).joinToString { it.serviceName }}")

        // Adım 2: Tüm güvenilir abonelik kalıplarını çek ve önceliğe göre sırala
        val allReliableSubPatterns = communityPatternRepo.getReliableSubscriptionPatterns()
            .filter { it.isSubscription } // Sadece abonelik olanları al
            .sortedByDescending { it.priority }
        Log.i("SubscriptionClassifier", "Fetched ${allReliableSubPatterns.size} reliable subscription patterns (isSubscription=true, sorted by priority). Examples: ${allReliableSubPatterns.take(5).joinToString { p -> "${p.serviceName}(${p.priority}, ${p.source})" }}")

        if (allReliableSubPatterns.isEmpty()) {
            Log.w("SubscriptionClassifier", "No reliable subscription patterns found in the database! Seeding might have failed or patterns are not marked correctly.")
        }

        // Adım 3: Geliştirilmiş AI sınıflandırması ile e-postaları işle
        val classificationJobs = sortedEmails.map { email ->
            async {
                try {
                    // E-posta içeriğini hazırla
                    val emailContent = prepareEmailContentForClassification(email)

                    // Detaylı sınıflandırma yap
                    val emailTypeResult = huggingFaceRepository.classifyEmailType(emailContent)
                    val subscriptionResult = huggingFaceRepository.classifySubscription(emailContent)
                    val paidSubscriptionResult = huggingFaceRepository.classifyPaidSubscription(emailContent)

                    // Servis adını belirle
                    val serviceName = determineServiceName(email, allReliableSubPatterns)

                    // Abonelik türünü belirle
                    val subscriptionType = determineSubscriptionType(subscriptionResult, paidSubscriptionResult)

                    // E-posta türünü belirle
                    val emailType = determineEmailType(emailTypeResult, email)

                    // Ücretli abonelik mi?
                    val isPaidSubscription = paidSubscriptionResult.label == "paid_subscription" &&
                                            paidSubscriptionResult.score > 0.65f

                    // Abonelik olma olasılığını kontrol et
                    val isLikelySubscription = when (subscriptionResult.label) {
                        "paid_subscription", "free_subscription" -> true
                        "promotional" -> false
                        "not_subscription" -> false
                        else -> subscriptionType != SubscriptionType.NOT_SUBSCRIPTION &&
                               subscriptionType != SubscriptionType.UNKNOWN
                    }

                    // Eğer abonelik olma olasılığı varsa veya ödeme/başlangıç/iptal kalıplarıyla eşleşiyorsa
                    if (isLikelySubscription ||
                        isPaidSubscription ||
                        emailType == EmailType.SUBSCRIPTION_START ||
                        emailType == EmailType.SUBSCRIPTION_CANCEL ||
                        emailType == EmailType.PAYMENT_CONFIRMATION) {

                        // Sınıflandırma sonucunu kaydet
                        val classifiedEmail = ClassifiedEmail(
                            rawEmail = email,
                            identifiedService = serviceName,
                            isLikelySubscription = isLikelySubscription,
                            matchedPatternId = null,
                            subscriptionType = subscriptionType,
                            emailType = emailType,
                            classificationResults = emailTypeResult.allResults,
                            isPaidSubscription = isPaidSubscription
                        )

                        synchronized(classifiedDetailsCollector) {
                            classifiedDetailsCollector.getOrPut(serviceName) { mutableListOf() }.add(classifiedEmail)
                        }

                        Log.d("SubscriptionClassifier", "AI classified email: ${email.subject.take(30)} for $serviceName, " +
                                "type: ${emailType.name}, subType: ${subscriptionType.name}, isPaid: $isPaidSubscription")
                    } else {
                        Log.d("SubscriptionClassifier", "AI classified email as NOT subscription: ${email.subject.take(30)}, " +
                                "score: ${subscriptionResult.score}, label: ${subscriptionResult.label}")
                    }
                } catch (e: Exception) {
                    Log.e("SubscriptionClassifier", "Error classifying email: ${e.message}", e)
                }
            }
        }

        // Tüm sınıflandırma işlemlerinin tamamlanmasını bekle
        classificationJobs.awaitAll()

        // Adım 4: Kalıplarla eşleşmeyen e-postaları işle
        val processedEmailIds = classifiedDetailsCollector.values.flatten().map { it.rawEmail.id }.toSet()
        val remainingEmails = sortedEmails.filter { it.id !in processedEmailIds }

        if (remainingEmails.isNotEmpty()) {
            Log.i("SubscriptionClassifier", "Processing ${remainingEmails.size} remaining emails with patterns")

            // Kalan e-postaları kalıplarla eşleştir
            val remainingEmailsToProcess = remainingEmails.toMutableList()
            remainingEmailsToProcess.forEach { email ->
                // Önce abonelik olmayan kalıplarla kontrol et
                var matched = false
                for (pattern in nonSubscriptionPatterns) {
                    if (!pattern.isSubscription && matchesPattern(email, pattern)) {
                        Log.v("SubscriptionClassifier", "Email ID ${email.id} (Sub: ${email.subject.take(20)}) matched NON-SUB pattern: '${pattern.serviceName}'")
                        matched = true
                        break
                    }
                }

                // Eğer abonelik olmayan kalıplarla eşleşmediyse, abonelik kalıplarıyla kontrol et
                if (!matched) {
                    for (pattern in allReliableSubPatterns) {
                        if (matchesPattern(email, pattern)) {
                            // E-posta içeriğini hazırla
                            val emailContent = prepareEmailContentForClassification(email)

                            // Ek sınıflandırma yap
                            val emailTypeResult = try {
                                huggingFaceRepository.classifyEmailType(emailContent)
                            } catch (e: Exception) {
                                null
                            }

                            val paidSubscriptionResult = try {
                                huggingFaceRepository.classifyPaidSubscription(emailContent)
                            } catch (e: Exception) {
                                null
                            }

                            // E-posta türünü belirle
                            val emailType = if (emailTypeResult != null) {
                                determineEmailType(emailTypeResult, email)
                            } else {
                                determineEmailTypeFromPatterns(email)
                            }

                            // Ücretli abonelik mi?
                            val isPaidSubscription = paidSubscriptionResult?.let {
                                it.label == "paid_subscription" && it.score > 0.65f
                            } ?: false

                            val detail = ClassifiedEmail(
                                rawEmail = email,
                                identifiedService = pattern.serviceName,
                                isLikelySubscription = true,
                                matchedPatternId = pattern.id,
                                subscriptionType = if (isPaidSubscription) SubscriptionType.PAID else SubscriptionType.UNKNOWN,
                                emailType = emailType,
                                isPaidSubscription = isPaidSubscription
                            )
                            classifiedDetailsCollector.getOrPut(pattern.serviceName) { mutableListOf() }.add(detail)
                            Log.d("SubscriptionClassifier", "Email ID ${email.id} (Sub: ${email.subject.take(20)}) matched SUB pattern: '${pattern.serviceName}'")
                            matched = true
                            break
                        }
                    }
                }
            }
        }

        Log.i("SubscriptionClassifier", "Total services identified before creating items: ${classifiedDetailsCollector.keys.size}. Services: ${classifiedDetailsCollector.keys}")
        return@coroutineScope createSubscriptionItemsFromDetails(classifiedDetailsCollector)
    }

    /**
     * Turkish: E-posta türünü belirler.
     * English: Determines the email type.
     */
    private fun determineEmailType(emailTypeResult: com.example.abonekaptanmobile.data.remote.model.DetailedClassificationResult, email: RawEmail): EmailType {
        // Önce AI sonucuna göre belirle
        val primaryLabel = emailTypeResult.primaryLabel
        val primaryScore = emailTypeResult.primaryScore

        if (primaryScore > 0.7f) {
            return when (primaryLabel) {
                "paid_subscription_confirmation" -> EmailType.SUBSCRIPTION_START
                "subscription_welcome" -> EmailType.WELCOME_MESSAGE
                "subscription_cancellation" -> EmailType.SUBSCRIPTION_CANCEL
                "subscription_renewal" -> EmailType.SUBSCRIPTION_RENEWAL
                "payment_receipt" -> EmailType.PAYMENT_CONFIRMATION
                "promotional_offer" -> EmailType.PROMOTIONAL_MESSAGE
                "general_notification" -> EmailType.GENERAL_NOTIFICATION
                else -> determineEmailTypeFromPatterns(email)
            }
        }

        // AI sonucu yeterince güvenilir değilse, kalıplarla kontrol et
        return determineEmailTypeFromPatterns(email)
    }

    /**
     * Turkish: Kalıplara göre e-posta türünü belirler.
     * English: Determines the email type based on patterns.
     */
    private fun determineEmailTypeFromPatterns(email: RawEmail): EmailType {
        val bodyText = email.bodySnippet ?: email.snippet ?: ""
        val content = "${email.subject} $bodyText".lowercase()

        // İptal kalıplarıyla kontrol et
        for (pattern in cancelPatterns) {
            if (pattern.containsMatchIn(content)) {
                return EmailType.SUBSCRIPTION_CANCEL
            }
        }

        // Başlangıç kalıplarıyla kontrol et
        for (pattern in startPatterns) {
            if (pattern.containsMatchIn(content)) {
                return EmailType.SUBSCRIPTION_START
            }
        }

        // Ödeme kalıplarıyla kontrol et
        for (pattern in paymentPatterns) {
            if (pattern.containsMatchIn(content)) {
                return EmailType.PAYMENT_CONFIRMATION
            }
        }

        // Reklam kalıplarıyla kontrol et
        for (pattern in promotionalPatterns) {
            if (pattern.containsMatchIn(content)) {
                return EmailType.PROMOTIONAL_MESSAGE
            }
        }

        return EmailType.UNKNOWN
    }

    /**
     * Turkish: Abonelik türünü belirler.
     * English: Determines the subscription type.
     */
    private fun determineSubscriptionType(
        subscriptionResult: ClassificationResult,
        paidSubscriptionResult: ClassificationResult
    ): SubscriptionType {
        // Önce ücretli abonelik kontrolü
        if (paidSubscriptionResult.label == "paid_subscription" && paidSubscriptionResult.score > 0.65f) {
            return SubscriptionType.PAID
        }

        // Sonra genel abonelik türü kontrolü
        return when (subscriptionResult.label) {
            "paid_subscription" -> SubscriptionType.PAID
            "free_subscription" -> SubscriptionType.FREE
            "promotional" -> SubscriptionType.PROMOTIONAL
            "not_subscription" -> SubscriptionType.NOT_SUBSCRIPTION
            else -> SubscriptionType.UNKNOWN
        }
    }

    /**
     * E-posta içeriğini sınıflandırma için hazırlar
     */
    private fun prepareEmailContentForClassification(email: RawEmail): String {
        val bodyContent = when {
            email.bodySnippet != null -> email.bodySnippet
            email.snippet != null -> email.snippet
            else -> email.bodyPlainText.take(500)
        }
        return "Subject: ${email.subject}\nFrom: ${email.from}\nContent: $bodyContent"
    }

    /**
     * E-posta için servis adını belirler
     */
    private fun determineServiceName(email: RawEmail, patterns: List<SubscriptionPatternEntity>): String {
        // Önce kalıplarla eşleşmeyi dene
        for (pattern in patterns) {
            if (matchesPattern(email, pattern)) {
                return pattern.serviceName
            }
        }

        // Kalıplarla eşleşmediyse, domain adından tahmin et
        return extractGeneralServiceName(email.from, email.subject, email.bodySnippet)
    }

    /**
     * E-posta adresinden domain adını çıkarır
     */
    private fun extractDomain(emailAddress: String): String? {
        val domainMatch = Regex("@([a-zA-Z0-9.-]+)").find(emailAddress)
        return domainMatch?.groupValues?.get(1)
    }

    private fun matchesPattern(email: RawEmail, pattern: SubscriptionPatternEntity): Boolean {
        val bodyText = when {
            email.bodySnippet != null -> email.bodySnippet
            email.snippet != null -> email.snippet
            else -> ""
        }

        val contentToSearch = when (pattern.patternType) {
            PatternType.DOMAIN -> email.from.lowercase()
            PatternType.SENDER_EMAIL -> email.from.lowercase()
            PatternType.SUBJECT_KEYWORD -> email.subject.lowercase()

            PatternType.BODY_KEYWORD -> bodyText!!.lowercase()
            PatternType.COMBINED, PatternType.UNKNOWN -> "${email.from} ${email.subject} $bodyText".lowercase()
            else -> {
                Log.w("SubscriptionClassifier", "Unknown pattern type: ${pattern.patternType} for pattern ID: ${pattern.id}. Defaulting to full content search.")
                "${email.from} ${email.subject} $bodyText".lowercase()
            }
        }
        return try {
            // Regex'in başında ve sonunda .* ekleyerek kısmi eşleşmelere izin verelim (eğer pattern zaten bunu içermiyorsa)
            // Ancak bu, regex'lerin nasıl yazıldığına bağlı. Eğer regex'ler zaten tam eşleşme içinse bu gereksiz.
            // Şimdilik orijinal haliyle bırakıyorum.
            val regex = Regex(pattern.regexPattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            regex.containsMatchIn(contentToSearch)
        } catch (e: PatternSyntaxException) {
            Log.e("SubscriptionClassifier", "Invalid regex: '${pattern.regexPattern}' for service '${pattern.serviceName}' (ID: ${pattern.id}). Error: ${e.message}")
            false
        }
    }

    // Bu fonksiyon, verilen kalıpların hepsi abonelik kalıbıysa (isSubscription=true) çağrılmalı.
    private fun applyAndCollectSubscriptionPatterns(
        emails: List<RawEmail>,
        subscriptionPatterns: List<SubscriptionPatternEntity>,
        collector: MutableMap<String, MutableList<ClassifiedEmail>>
    ): MutableList<RawEmail> {
        val remaining = mutableListOf<RawEmail>()
        emails.forEach { email ->
            var matched = false
            for (pattern in subscriptionPatterns) {
                // Bu fonksiyona gelen tüm pattern'ların isSubscription = true olduğunu varsayıyoruz.
                if (matchesPattern(email, pattern)) {
                    val detail = ClassifiedEmail(
                        rawEmail = email,
                        identifiedService = pattern.serviceName,
                        isLikelySubscription = true, // Çünkü bunlar abonelik kalıpları
                        matchedPatternId = pattern.id
                    )
                    collector.getOrPut(pattern.serviceName) { mutableListOf() }.add(detail)
                    Log.d("SubscriptionClassifier", "Email ID ${email.id} (Sub: ${email.subject.take(20)}) matched SUB pattern: '${pattern.serviceName}'. Added to collector.")
                    matched = true
                    break // Bu e-posta için kalıp bulundu
                }
            }
            if (!matched) {
                remaining.add(email)
            }
        }
        return remaining
    }

    /**
     * Turkish: Sınıflandırılmış e-postalardan abonelik öğelerini oluşturur.
     * English: Creates subscription items from classified emails.
     */
    private suspend fun createSubscriptionItemsFromDetails(
        classifiedEmailDetails: Map<String, List<ClassifiedEmail>>
    ): List<SubscriptionItem> = coroutineScope {
        val resultList = mutableListOf<SubscriptionItem>()
        Log.d("SubscriptionClassifier", "Creating items from ${classifiedEmailDetails.size} identified services.")

        classifiedEmailDetails.forEach { (serviceName, detailsForService) ->
            // Bu servise ait tüm ham e-postaları al.
            val subscriptionRelatedRawEmails = detailsForService.map { it.rawEmail }.distinctBy { it.id }

            if (subscriptionRelatedRawEmails.isEmpty()) {
                Log.w("SubscriptionClassifier", "No raw emails for service: $serviceName after map. This shouldn't happen if detailsForService is not empty.")
                return@forEach
            }

            // E-postaları tarih sırasına göre sırala (en yeniden en eskiye)
            val sortedRawEmails = subscriptionRelatedRawEmails.sortedByDescending { it.date }
            val lastEmailDate = sortedRawEmails.first().date
            val emailCount = sortedRawEmails.size
            val relatedEmailIds = sortedRawEmails.map { it.id }

            // Sınıflandırılmış e-postaları tarih sırasına göre sırala (en yeniden en eskiye)
            val sortedClassifiedEmails = detailsForService.sortedByDescending { it.rawEmail.date }

            var subscriptionStartDate: Long? = null
            var latestCancellationDate: Long? = null
            var latestRenewalDate: Long? = null
            var latestPaymentDate: Long? = null
            var latestActivityDate = 0L
            var isPaidSubscription = false

            // Her e-posta için abonelik durumunu kontrol et
            for (classifiedEmail in sortedClassifiedEmails) {
                val email = classifiedEmail.rawEmail
                val emailType = classifiedEmail.emailType

                // E-posta türüne göre işlem yap
                when (emailType) {
                    EmailType.SUBSCRIPTION_START -> {
                        // En eski başlangıç tarihini bul
                        if (subscriptionStartDate == null || email.date < subscriptionStartDate) {
                            subscriptionStartDate = email.date
                            Log.d("SubscriptionClassifier", "Found subscription start date for $serviceName: ${email.date}")
                        }

                        // Aktivite tarihini güncelle
                        if (email.date > latestActivityDate) {
                            latestActivityDate = email.date
                        }
                    }
                    EmailType.SUBSCRIPTION_CANCEL -> {
                        // En yeni iptal tarihini bul
                        if (latestCancellationDate == null || email.date > latestCancellationDate) {
                            latestCancellationDate = email.date
                            Log.d("SubscriptionClassifier", "Found subscription cancellation date for $serviceName: ${email.date}")
                        }
                    }
                    EmailType.SUBSCRIPTION_RENEWAL -> {
                        // En yeni yenileme tarihini bul
                        if (latestRenewalDate == null || email.date > latestRenewalDate) {
                            latestRenewalDate = email.date
                            Log.d("SubscriptionClassifier", "Found subscription renewal date for $serviceName: ${email.date}")
                        }

                        // Aktivite tarihini güncelle
                        if (email.date > latestActivityDate) {
                            latestActivityDate = email.date
                        }
                    }
                    EmailType.PAYMENT_CONFIRMATION -> {
                        // En yeni ödeme tarihini bul
                        if (latestPaymentDate == null || email.date > latestPaymentDate) {
                            latestPaymentDate = email.date
                            Log.d("SubscriptionClassifier", "Found payment confirmation date for $serviceName: ${email.date}")
                        }

                        // Aktivite tarihini güncelle
                        if (email.date > latestActivityDate) {
                            latestActivityDate = email.date
                        }

                        // Ücretli abonelik olarak işaretle
                        isPaidSubscription = true
                    }
                    EmailType.WELCOME_MESSAGE -> {
                        // Başlangıç tarihi yoksa, hoşgeldiniz mesajını başlangıç tarihi olarak kullan
                        if (subscriptionStartDate == null) {
                            subscriptionStartDate = email.date
                            Log.d("SubscriptionClassifier", "Using welcome message as subscription start date for $serviceName: ${email.date}")
                        }

                        // Aktivite tarihini güncelle
                        if (email.date > latestActivityDate) {
                            latestActivityDate = email.date
                        }
                    }
                    else -> {
                        // Diğer e-posta türleri için aktivite tarihini güncelle
                        if (email.date > latestActivityDate) {
                            latestActivityDate = email.date
                        }
                    }
                }

                // Ücretli abonelik kontrolü
                if (classifiedEmail.isPaidSubscription) {
                    isPaidSubscription = true
                }
            }

            // Fallback: Eğer hiçbir e-posta türü belirlenemezse, eski yöntemi kullan
            if (latestActivityDate == 0L) {
                for (email in sortedRawEmails) {
                    try {
                        val emailContent = prepareEmailContentForClassification(email)
                        val statusResult = huggingFaceRepository.classifySubscriptionStatus(emailContent)

                        when (statusResult.label) {
                            "subscription_start" -> {
                                if (subscriptionStartDate == null || email.date < subscriptionStartDate) {
                                    subscriptionStartDate = email.date
                                }
                            }
                            "subscription_cancel" -> {
                                if (latestCancellationDate == null || email.date > latestCancellationDate) {
                                    latestCancellationDate = email.date
                                }
                            }
                            "subscription_renewal" -> {
                                if (latestRenewalDate == null || email.date > latestRenewalDate) {
                                    latestRenewalDate = email.date
                                }
                                if (email.date > latestActivityDate) {
                                    latestActivityDate = email.date
                                }
                            }
                            "payment_confirmation" -> {
                                if (latestPaymentDate == null || email.date > latestPaymentDate) {
                                    latestPaymentDate = email.date
                                }
                                if (email.date > latestActivityDate) {
                                    latestActivityDate = email.date
                                }
                                isPaidSubscription = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SubscriptionClassifier", "Error in fallback classification: ${e.message}", e)
                    }
                }
            }

            // En son aktivite tarihini belirle
            if (latestRenewalDate != null && (latestActivityDate == 0L || latestRenewalDate > latestActivityDate)) {
                latestActivityDate = latestRenewalDate
            }

            if (latestPaymentDate != null && (latestActivityDate == 0L || latestPaymentDate > latestActivityDate)) {
                latestActivityDate = latestPaymentDate
            }

            if (latestActivityDate == 0L && sortedRawEmails.isNotEmpty()) {
                latestActivityDate = lastEmailDate
            }

            // Abonelik durumunu belirle
            val status: SubscriptionStatus
            if (latestCancellationDate != null) {
                // İptal tarihi varsa, son aktivite tarihine göre kontrol et
                if (latestActivityDate > latestCancellationDate ||
                    (latestRenewalDate != null && latestRenewalDate > latestCancellationDate) ||
                    (latestPaymentDate != null && latestPaymentDate > latestCancellationDate)) {
                    // İptal sonrası aktivite varsa, aktif olarak işaretle
                    status = SubscriptionStatus.ACTIVE
                } else {
                    // İptal sonrası aktivite yoksa, iptal edilmiş olarak işaretle
                    status = SubscriptionStatus.CANCELLED
                }
            } else {
                // İptal tarihi yoksa, son aktivite tarihine göre kontrol et
                val timeSinceLastActivity = System.currentTimeMillis() - latestActivityDate
                status = if (latestActivityDate > 0 && timeSinceLastActivity > inactivityThresholdMillis) {
                    // Son aktivite üzerinden belirli bir süre geçmişse, unutulmuş olarak işaretle
                    SubscriptionStatus.FORGOTTEN
                } else {
                    // Son aktivite yakın zamandaysa, aktif olarak işaretle
                    SubscriptionStatus.ACTIVE
                }
            }

            Log.i("SubscriptionClassifier", "FINAL ITEM for '$serviceName': Status=$status, Count=$emailCount, " +
                    "LastEmailDate=$lastEmailDate, StartDate=$subscriptionStartDate, CancelDate=$latestCancellationDate, " +
                    "LastActivity=$latestActivityDate, IsPaid=$isPaidSubscription")

            resultList.add(
                SubscriptionItem(
                    serviceName = serviceName,
                    emailCount = emailCount,
                    lastEmailDate = lastEmailDate,
                    status = status,
                    cancellationDate = if (status == SubscriptionStatus.CANCELLED) latestCancellationDate else null,
                    relatedEmailIds = relatedEmailIds,
                    subscriptionStartDate = subscriptionStartDate
                )
            )
        }

        // Önce iptal edilmemiş abonelikleri, sonra en son e-posta tarihine göre sırala
        return@coroutineScope resultList.sortedWith(
            compareByDescending<SubscriptionItem> { it.status != SubscriptionStatus.CANCELLED }
                .thenByDescending { it.lastEmailDate }
        )
    }

    private fun extractGeneralServiceName(from: String, subject: String, bodySnippet: String?): String {
        val domainMatch = Regex("@([a-zA-Z0-9.-]+)").find(from)
        var serviceNameFromDomainPart = domainMatch?.groupValues?.get(1)?.substringBeforeLast(".")?.substringAfterLast(".")
        if (serviceNameFromDomainPart != null && serviceNameFromDomainPart.length < 3 && domainMatch?.groupValues?.get(1)?.contains(".") == true) {
            serviceNameFromDomainPart = domainMatch.groupValues[1].substringBeforeLast(".")
        }

        var serviceNameFromSenderDisplayName = from.substringBefore('<').trim().removeSurrounding("\"")
        if (serviceNameFromSenderDisplayName.contains("@") || serviceNameFromSenderDisplayName.length > 30 || serviceNameFromSenderDisplayName.isEmpty() || serviceNameFromSenderDisplayName.length < 3) {
            serviceNameFromSenderDisplayName = ""
        }

        val subjectKeywords = subject.split(Regex("[^a-zA-Z0-9İıÖöÜüÇçŞşĞğ]+"))
            .filter { it.length in 4..20 && it.firstOrNull()?.isLetter() == true && it.first().isUpperCase() && !it.matches(Regex("^[A-ZİÖÜüÇŞĞ]+$")) }
            .distinct()

        val commonDomainsToAvoidAsName = listOf("google", "googlemail", "gmail", "facebook", "microsoft", "apple", "amazon", "yahoo", "outlook", "hotmail", "support", "info", "noreply", "service", "team", "mail", "email", "com", "newsletter", "update", "alert", "bildirim", "duyuru", "haber", "mailchimp", "sendgrid")

        if (serviceNameFromSenderDisplayName.isNotBlank() && !commonDomainsToAvoidAsName.any { serviceNameFromSenderDisplayName.lowercase().contains(it) }) {
            return serviceNameFromSenderDisplayName.capitalizeWords()
        }

        if (subjectKeywords.isNotEmpty()) {
            val bestSubjectKeyword = subjectKeywords.firstOrNull { keyword -> !commonDomainsToAvoidAsName.any { keyword.lowercase().contains(it) } }
            if (bestSubjectKeyword != null) {
                return bestSubjectKeyword.capitalizeWords()
            }
        }

        val fullDomain = domainMatch?.groupValues?.get(1)
        if (fullDomain != null) {
            val parts = fullDomain.split('.')
            if (parts.isNotEmpty()) {
                val potentialNameFromDomain = if (parts.size > 1 && commonDomainsToAvoidAsName.contains(parts.getOrNull(parts.size - 2)?.lowercase())) {
                    if (parts.size > 2 && !commonDomainsToAvoidAsName.contains(parts.getOrNull(parts.size - 3)?.lowercase())) parts.getOrNull(parts.size - 3) else parts.firstOrNull()
                } else {
                    parts.firstOrNull()
                }
                if (potentialNameFromDomain != null && potentialNameFromDomain.length > 2 && !commonDomainsToAvoidAsName.contains(potentialNameFromDomain.lowercase()))
                    return potentialNameFromDomain.capitalizeWords()
            }
        }
        return serviceNameFromDomainPart?.capitalizeWords()?.takeIf { it.isNotBlank() && !commonDomainsToAvoidAsName.contains(it.lowercase()) } ?: "Unknown Service"
    }

    private fun isPotentiallyReliableSenderForHeuristics(from: String): Boolean {
        val lowerFrom = from.lowercase()
        if (listOf("gmail.com", "outlook.com", "hotmail.com", "yahoo.com", "icloud.com", "yandex.com").any { lowerFrom.contains(it) }) {
            return listOf("no-reply", "noreply", "support", "billing", "account", "service", "team", "info", "do_not_reply", "customer").any { lowerFrom.substringBefore('@').contains(it) }
        }
        return true
    }
}

fun String.capitalizeWords(): String = this.split(Regex("\\s+")).joinToString(" ") { word ->
    if (word.isEmpty()) {
        ""
    } else {
        word.replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase()
            } else {
                char.toString()
            }
        }
    }
}