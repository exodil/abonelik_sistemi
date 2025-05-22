// file: app/java/com/example/abonekaptanmobile/workers/ProcessFeedbackWorker.kt
package com.example.abonekaptanmobile.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.abonekaptanmobile.data.local.AppDatabase
import com.example.abonekaptanmobile.data.local.entity.PatternType
import com.example.abonekaptanmobile.data.local.entity.SubscriptionPatternEntity
import com.example.abonekaptanmobile.data.repository.CommunityPatternRepository
import com.example.abonekaptanmobile.data.repository.FeedbackRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

@HiltWorker
class ProcessFeedbackWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val feedbackRepo: FeedbackRepository,
    private val patternRepo: CommunityPatternRepository,
    private val appDatabase: AppDatabase
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "ProcessFeedbackWorker"
        private const val MIN_VOTES_FOR_COMMUNITY_ACTION = 3 // Topluluk kararı için daha düşük bir eşik
        private const val COMMUNITY_CONFIDENCE_RATE = 0.67f // ~2/3 çoğunluk

        private const val DEFAULT_PATTERN_PRIORITY = 30
        private const val USER_FEEDBACK_PRIORITY_BOOST = 10
        private const val COMMUNITY_APPROVED_PRIORITY = 70
        private const val COMMUNITY_REJECTED_PRIORITY = 80 // Negatif teyitler daha öncelikli
        private const val ADMIN_VERIFIED_PRIORITY = 100
    }

    override suspend fun doWork(): Result {
        return try {
            val pendingFeedbackList = feedbackRepo.getPendingFeedback()
            if (pendingFeedbackList.isEmpty()) {
                Log.d("ProcessFeedbackWorker", "No pending feedback.")
                return Result.success()
            }
            Log.i("ProcessFeedbackWorker", "Processing ${pendingFeedbackList.size} feedback items.")

            appDatabase.withTransaction {
                pendingFeedbackList.forEach { feedback ->
                    Log.d("ProcessFeedbackWorker", "Feedback for: ${feedback.serviceName}, Label: ${feedback.feedbackLabel}")

                    // TODO: feedback.serviceName yerine, e-postanın gerçek göndericisini/domain'ini kullanmak daha iyi.
                    // Bu bilgi FeedbackEntity'ye eklenebilir.
                    var pattern = patternRepo.getPatternByServiceName(feedback.serviceName)
                    var isNewPattern = false

                    if (pattern == null) {
                        isNewPattern = true
                        val initialRegex = Pattern.quote(feedback.serviceName.lowercase()) // Basit, domain/sender'a göre iyileştirilmeli
                        pattern = SubscriptionPatternEntity(
                            serviceName = feedback.serviceName,
                            regexPattern = initialRegex,
                            source = "user_feedback_new",
                            isSubscription = feedback.feedbackLabel != "NotSubscription",
                            patternType = PatternType.UNKNOWN, // TODO: Daha iyi tahmin et (eğer feedback'de sender info varsa)
                            priority = DEFAULT_PATTERN_PRIORITY,
                            isTrustedSenderDomain = false
                        )
                        Log.d("ProcessFeedbackWorker", "New pattern for ${feedback.serviceName}, isSub: ${pattern.isSubscription}")
                    }

                    var tempPattern = pattern.copy()

                    when (feedback.feedbackLabel) {
                        "IsActive", "IsForgotten", "IsCancelled" -> { // Kullanıcı bunun bir abonelik olduğunu söylüyor
                            tempPattern = tempPattern.copy(
                                approvedCount = tempPattern.approvedCount + 1,
                                isSubscription = true, // Kesinleştir
                                // Eğer daha önce güçlü bir şekilde "abonelik değil" denmişse, rejectedCount'ı azalt
                                rejectedCount = if (tempPattern.source == "community_rejected") (tempPattern.rejectedCount - 1).coerceAtLeast(0) else tempPattern.rejectedCount
                            )
                        }
                        "NotSubscription" -> { // Kullanıcı bunun bir abonelik olmadığını söylüyor
                            tempPattern = tempPattern.copy(
                                rejectedCount = tempPattern.rejectedCount + 1,
                                isSubscription = false, // Kesinleştir
                                approvedCount = if (tempPattern.source == "community_approved") (tempPattern.approvedCount - 1).coerceAtLeast(0) else tempPattern.approvedCount
                            )
                        }
                        // "NeverSubscribed" gibi etiketler için özel mantık eklenebilir.
                    }
                    tempPattern = tempPattern.copy(updatedAt = System.currentTimeMillis())

                    val totalVotes = tempPattern.approvedCount + tempPattern.rejectedCount

                    if (isNewPattern) {
                        tempPattern = tempPattern.copy(priority = DEFAULT_PATTERN_PRIORITY + USER_FEEDBACK_PRIORITY_BOOST)
                    }

                    if (totalVotes >= MIN_VOTES_FOR_COMMUNITY_ACTION) {
                        if (tempPattern.isSubscription) { // Şu anki pattern "abonelik" diyor
                            val approvalRate = tempPattern.approvedCount.toFloat() / totalVotes
                            if (approvalRate >= COMMUNITY_CONFIDENCE_RATE) {
                                tempPattern = tempPattern.copy(source = "community_approved", priority = COMMUNITY_APPROVED_PRIORITY)
                                Log.i("ProcessFeedbackWorker", "Pattern ${tempPattern.serviceName} confirmed as SUBSCRIPTION by community.")
                            } else if (tempPattern.rejectedCount.toFloat() / totalVotes >= COMMUNITY_CONFIDENCE_RATE) {
                                // Çoğunluk artık abonelik olmadığını söylüyor
                                tempPattern = tempPattern.copy(isSubscription = false, source = "community_rejected", priority = COMMUNITY_REJECTED_PRIORITY)
                                Log.i("ProcessFeedbackWorker", "Pattern ${tempPattern.serviceName} FLIPPED to NON-SUBSCRIPTION by community.")
                            }
                        } else { // Şu anki pattern "abonelik değil" diyor
                            val rejectionRate = tempPattern.rejectedCount.toFloat() / totalVotes
                            if (rejectionRate >= COMMUNITY_CONFIDENCE_RATE) {
                                tempPattern = tempPattern.copy(source = "community_rejected", priority = COMMUNITY_REJECTED_PRIORITY)
                                Log.i("ProcessFeedbackWorker", "Pattern ${tempPattern.serviceName} confirmed as NON-SUBSCRIPTION by community.")
                            } else if (tempPattern.approvedCount.toFloat() / totalVotes >= COMMUNITY_CONFIDENCE_RATE) {
                                // Çoğunluk artık abonelik olduğunu söylüyor
                                tempPattern = tempPattern.copy(isSubscription = true, source = "community_approved", priority = COMMUNITY_APPROVED_PRIORITY)
                                Log.i("ProcessFeedbackWorker", "Pattern ${tempPattern.serviceName} FLIPPED to SUBSCRIPTION by community.")
                            }
                        }
                    }
                    patternRepo.upsertPattern(tempPattern)
                    feedbackRepo.markFeedbackAsProcessed(feedback.id)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("ProcessFeedbackWorker", "Error in doWork: ${e.message}", e)
            return if (e is PatternSyntaxException) Result.failure() else Result.retry()
        }
    }
}