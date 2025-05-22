// file: app/java/com/example/abonekaptanmobile/data/local/entity/FeedbackEntity.kt
package com.example.abonekaptanmobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Turkish: Kullanıcı geri bildirimlerini temsil eden Room varlığı.
// English: Room entity representing user feedback.
@Entity(tableName = "feedback")
data class FeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serviceName: String,          // The service name the feedback is for
    val originalStatus: String,       // "Active", "Forgotten", "Cancelled"
    val feedbackLabel: String,        // User's correction: "IsSubscription", "NotSubscription", "IsCancelled"
    val feedbackNote: String?,        // Optional user note
    val createdAt: Long = System.currentTimeMillis(),
    var processed: Boolean = false    // To track if worker has processed this feedback
)