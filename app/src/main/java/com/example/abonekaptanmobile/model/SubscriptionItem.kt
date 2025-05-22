// file: app/java/com/example/abonekaptanmobile/model/SubscriptionItem.kt
package com.example.abonekaptanmobile.model

// Turkish: Kullanıcı arayüzünde listelenecek abonelik öğesini temsil eder.
// English: Represents a subscription item to be listed in the UI.
data class SubscriptionItem(
    val serviceName: String,
    val emailCount: Int,
    val lastEmailDate: Long,
    var status: SubscriptionStatus, // "Active", "Forgotten", "Cancelled"
    val cancellationDate: Long? = null, // İptal edilmişse iptal tarihi
    val relatedEmailIds: List<String>, // Bu abonelikle ilişkili e-posta ID'leri
    val subscriptionStartDate: Long? = null // Aboneliğin başlangıç tarihi
)

enum class SubscriptionStatus {
    ACTIVE, FORGOTTEN, CANCELLED, UNKNOWN
}