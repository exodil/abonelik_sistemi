// file: app/java/com/example/abonekaptanmobile/data/local/dao/CommunityPatternDao.kt
package com.example.abonekaptanmobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.abonekaptanmobile.data.local.entity.SubscriptionPatternEntity

@Dao
interface CommunityPatternDao {

    /**
     * Turkish: Güvenilir ve abonelik olarak işaretlenmiş kalıpları getirir.
     * English: Fetches reliable patterns marked as subscriptions.
     * Test amacıyla basitleştirilmiş sorgu.
     */
    // ÖNCEKİ SORGU YORUM SATIRI YAPILDI:
    // @Query("SELECT * FROM subscription_patterns WHERE isSubscription = 1 AND (source = 'community_approved' OR source = 'admin_verified' OR source = 'default_verified') ORDER BY approvedCount DESC, rejectedCount ASC")
    // YENİ TEST SORGUSU:
    @Query("SELECT * FROM subscription_patterns WHERE isSubscription = 1 AND source = 'default_verified' ORDER BY priority DESC, approvedCount DESC")
    suspend fun getReliableSubscriptionPatterns(): List<SubscriptionPatternEntity>

    /**
     * Turkish: Topluluk tarafından "abonelik değil" olarak işaretlenmiş veya yüksek oranda reddedilmiş kalıpları getirir.
     * English: Fetches patterns marked as "not a subscription" by the community or highly rejected ones.
     */
    @Query("SELECT * FROM subscription_patterns WHERE isSubscription = 0 AND (source = 'community_rejected' OR (rejectedCount > :minRejectionVotes AND approvedCount < rejectedCount)) ORDER BY rejectedCount DESC")
    suspend fun getNonSubscriptionPatterns(minRejectionVotes: Int = 3): List<SubscriptionPatternEntity>

    /**
     * Turkish: Belirli bir servis adına göre kalıbı getirir.
     * English: Fetches a pattern by its service name.
     */
    @Query("SELECT * FROM subscription_patterns WHERE serviceName = :serviceName COLLATE NOCASE LIMIT 1")
    suspend fun getPatternByServiceName(serviceName: String): SubscriptionPatternEntity?

    /**
     * Turkish: Bir kalıbı ekler veya günceller.
     * English: Inserts or updates a pattern.
     */
    @Upsert
    suspend fun upsertPattern(pattern: SubscriptionPatternEntity)

}