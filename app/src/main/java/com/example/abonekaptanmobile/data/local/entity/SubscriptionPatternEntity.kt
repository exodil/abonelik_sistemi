// file: app/java/com/example/abonekaptanmobile/data/local/entity/SubscriptionPatternEntity.kt
package com.example.abonekaptanmobile.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subscription_patterns",
    indices = [Index(value = ["serviceName"], unique = false), Index(value = ["regexPattern"], unique = false)]
)
data class SubscriptionPatternEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serviceName: String,
    val regexPattern: String,
    var source: String, // Bu alan constructor'da zorunlu, varsayılanı yok
    var approvedCount: Int = 0,
    var rejectedCount: Int = 0,
    var isSubscription: Boolean = true,
    var isTrustedSenderDomain: Boolean = false,
    var patternType: String = PatternType.UNKNOWN,
    var priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

object PatternType {
    const val UNKNOWN = "unknown"
    const val DOMAIN = "domain"
    const val SENDER_EMAIL = "sender_email"
    const val SUBJECT_KEYWORD = "subject_keyword"
    const val BODY_KEYWORD = "body_keyword"
    const val COMBINED = "combined"
}