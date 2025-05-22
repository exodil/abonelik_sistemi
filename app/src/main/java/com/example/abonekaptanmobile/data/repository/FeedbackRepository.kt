// file: app/java/com/example/abonekaptanmobile/data/repository/FeedbackRepository.kt
package com.example.abonekaptanmobile.data.repository

import com.example.abonekaptanmobile.data.local.dao.FeedbackDao
import com.example.abonekaptanmobile.data.local.entity.FeedbackEntity
import javax.inject.Inject

// Turkish: FeedbackDao için Repository katmanı.
// English: Repository layer for FeedbackDao.
class FeedbackRepository @Inject constructor(private val dao: FeedbackDao) {

    // Turkish: Yeni geri bildirim ekler.
    // English: Inserts new feedback.
    suspend fun insertFeedback(feedback: FeedbackEntity) {
        dao.insertFeedback(feedback)
    }

    // Turkish: İşlenmeyi bekleyen geri bildirimleri alır.
    // English: Gets pending feedback.
    suspend fun getPendingFeedback(): List<FeedbackEntity> {
        return dao.getPendingFeedback()
    }

    // Turkish: Geri bildirimi işlendi olarak işaretler.
    // English: Marks feedback as processed.
    suspend fun markFeedbackAsProcessed(feedbackId: Long) {
        dao.markFeedbackAsProcessed(feedbackId)
    }
}