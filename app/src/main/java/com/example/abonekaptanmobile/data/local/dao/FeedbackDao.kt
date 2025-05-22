// file: app/java/com/example/abonekaptanmobile/data/local/dao/FeedbackDao.kt
package com.example.abonekaptanmobile.data.local.dao

import androidx.room.*
import com.example.abonekaptanmobile.data.local.entity.FeedbackEntity

// Turkish: Kullanıcı geri bildirimlerini yönetmek için DAO.
// English: DAO for managing user feedback.
@Dao
interface FeedbackDao {

    // Turkish: Yeni bir geri bildirim ekler.
    // English: Inserts new feedback.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: FeedbackEntity)

    // Turkish: İşlenmeyi bekleyen geri bildirimleri getirir.
    // English: Fetches pending feedback that hasn't been processed.
    @Query("SELECT * FROM feedback WHERE processed = 0 ORDER BY createdAt ASC")
    suspend fun getPendingFeedback(): List<FeedbackEntity>

    // Turkish: Geri bildirimi işlendi olarak işaretler.
    // English: Marks feedback as processed.
    @Query("UPDATE feedback SET processed = 1 WHERE id = :feedbackId")
    suspend fun markFeedbackAsProcessed(feedbackId: Long)

    // Turkish: Tüm geri bildirimleri siler (test veya sıfırlama için).
    // English: Deletes all feedback (for testing or reset).
    @Query("DELETE FROM feedback")
    suspend fun clearAllFeedback()
}