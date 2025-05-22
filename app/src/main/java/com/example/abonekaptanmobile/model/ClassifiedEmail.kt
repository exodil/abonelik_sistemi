// file: app/java/com/example/abonekaptanmobile/model/ClassifiedEmail.kt
package com.example.abonekaptanmobile.model

import com.example.abonekaptanmobile.data.remote.model.ClassificationResult

/**
 * Turkish: Sınıflandırılmış bir e-postayı temsil eder.
 * English: Represents a classified email.
 */
data class ClassifiedEmail(
    val rawEmail: RawEmail,
    val identifiedService: String,      // Tanımlanmış servis adı, örn: "Netflix", "Unknown Service"
    val isLikelySubscription: Boolean,
    val matchedPatternId: Long? = null, // Opsiyonel: Hangi kalıpla eşleştiğini tutmak için eklenebilir
    val subscriptionType: SubscriptionType = SubscriptionType.UNKNOWN, // Abonelik türü
    val emailType: EmailType = EmailType.UNKNOWN, // E-posta türü
    val classificationResults: List<ClassificationResult> = emptyList(), // Sınıflandırma sonuçları
    val isPaidSubscription: Boolean = false // Ücretli abonelik mi?
)

/**
 * Turkish: Abonelik türlerini temsil eden enum.
 * English: Enum representing subscription types.
 */
enum class SubscriptionType {
    PAID,           // Ücretli abonelik
    FREE,           // Ücretsiz abonelik
    PROMOTIONAL,    // Tanıtım/reklam
    NOT_SUBSCRIPTION, // Abonelik değil
    UNKNOWN         // Bilinmiyor
}

/**
 * Turkish: E-posta türlerini temsil eden enum.
 * English: Enum representing email types.
 */
enum class EmailType {
    SUBSCRIPTION_START,     // Abonelik başlangıcı
    SUBSCRIPTION_CANCEL,    // Abonelik iptali
    SUBSCRIPTION_RENEWAL,   // Abonelik yenilemesi
    PAYMENT_CONFIRMATION,   // Ödeme onayı
    WELCOME_MESSAGE,        // Hoşgeldiniz mesajı
    PROMOTIONAL_MESSAGE,    // Tanıtım/reklam mesajı
    GENERAL_NOTIFICATION,   // Genel bildirim
    UNKNOWN                 // Bilinmiyor
}