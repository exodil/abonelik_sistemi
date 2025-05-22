// file: app/java/com/example/abonekaptanmobile/data/repository/CommunityPatternRepository.kt
package com.example.abonekaptanmobile.data.repository

import android.util.Log
import com.example.abonekaptanmobile.data.local.dao.CommunityPatternDao
import com.example.abonekaptanmobile.data.local.entity.PatternType
import com.example.abonekaptanmobile.data.local.entity.SubscriptionPatternEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunityPatternRepository @Inject constructor(
    private val communityPatternDao: CommunityPatternDao
) {

    suspend fun getReliableSubscriptionPatterns(): List<SubscriptionPatternEntity> {
        return communityPatternDao.getReliableSubscriptionPatterns()
    }

    suspend fun getNonSubscriptionPatterns(minRejectionVotes: Int = 3): List<SubscriptionPatternEntity> {
        return communityPatternDao.getNonSubscriptionPatterns(minRejectionVotes)
    }

    suspend fun getPatternByServiceName(serviceName: String): SubscriptionPatternEntity? {
        return communityPatternDao.getPatternByServiceName(serviceName)
    }

    suspend fun upsertPattern(pattern: SubscriptionPatternEntity) {
        communityPatternDao.upsertPattern(pattern)
    }

    suspend fun seedInitialPatternsIfEmpty() {
        val netflixDomainPattern = communityPatternDao.getPatternByServiceName("Netflix")
        val shouldSeed = netflixDomainPattern == null ||
                !(netflixDomainPattern.source == "default_verified" &&
                        netflixDomainPattern.patternType == PatternType.DOMAIN &&
                        netflixDomainPattern.regexPattern == "netflix\\.com") // Daha spesifik kontrol

        if (shouldSeed) {
            Log.i("PatternRepository", "Seeding initial patterns with named arguments...")
            val initialPatterns = listOf(
                // Netflix
                SubscriptionPatternEntity(serviceName = "Netflix", regexPattern = "(?i)netflix.*(abonelik|üyelik|üyeliğiniz|yenile|fatura|ödeme|hesap|subscription|membership|renew|bill|payment|invoice|account|iptal|cancel|ücret)", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 85),
                SubscriptionPatternEntity(serviceName = "Netflix", regexPattern = "netflix\\.com", source = "default_verified", approvedCount = 5, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 100),

                // YouTube Premium & YouTube
                SubscriptionPatternEntity(serviceName = "YouTube Premium", regexPattern = "(?i)youtube.*premium.*(abonelik|üyelik|üyeliğiniz|yenile|başla|fatura|ödeme|subscription|membership|renew|bill|payment|invoice|iptal|cancel)", source = "default_verified", approvedCount = 3, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 95),
                SubscriptionPatternEntity(serviceName = "YouTube", regexPattern = "youtube\\.com", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 70),

                // Spotify
                SubscriptionPatternEntity(serviceName = "Spotify", regexPattern = "(?i)spotify.*(abonelik|üyelik|üyeliğiniz|yenile|fatura|ödeme|hesap|subscription|membership|renew|bill|payment|invoice|account|iptal|cancel|ücret)", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 85),
                SubscriptionPatternEntity(serviceName = "Spotify", regexPattern = "spotify\\.com", source = "default_verified", approvedCount = 5, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 100),

                // Amazon Prime
                SubscriptionPatternEntity(serviceName = "Amazon Prime", regexPattern = "(?i)amazon.*prime.*(abonelik|üyelik|üyeliğiniz|yenile|fatura|ödeme|subscription|membership|renew|bill|payment|invoice|iptal|cancel)", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 85),
                SubscriptionPatternEntity(serviceName = "Amazon Prime", regexPattern = "amazon\\.com", source = "default_verified", approvedCount = 3, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 90),
                SubscriptionPatternEntity(serviceName = "Amazon Prime Video", regexPattern = "primevideo\\.com", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 90),

                // Apple (iTunes, Music, TV+, Arcade, One, iCloud)
                SubscriptionPatternEntity(serviceName = "Apple Services", regexPattern = "(?i)apple.*(music|tv|itunes|arcade|one|icloud).*?(abonelik|üyelik|yenile|fatura|subscription|membership|renew|bill|invoice|payment|ödeme|iptal|cancel)", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 80),
                SubscriptionPatternEntity(serviceName = "Apple Services", regexPattern = "apple\\.com", source = "default_verified", approvedCount = 3, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 90),

                // Disney+
                SubscriptionPatternEntity(serviceName = "Disney+", regexPattern = "(?i)disney.*plus.*(abonelik|üyelik|üyeliğiniz|yenile|fatura|ödeme|subscription|membership|renew|bill|payment|invoice|iptal|cancel)", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 85),
                SubscriptionPatternEntity(serviceName = "Disney+", regexPattern = "disneyplus\\.com", source = "default_verified", approvedCount = 3, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 100),

                // HBO Max (veya sadece Max)
                SubscriptionPatternEntity(serviceName = "HBO Max", regexPattern = "(?i)(hbo|max).*?(abonelik|üyelik|yenile|fatura|subscription|membership|renew|bill|invoice|payment|ödeme|iptal|cancel)", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 80),
                SubscriptionPatternEntity(serviceName = "HBO Max", regexPattern = "hbomax\\.com", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 90),

                // Microsoft 365 / Office 365
                SubscriptionPatternEntity(serviceName = "Microsoft 365", regexPattern = "(?i)microsoft.*(365|office|yenile|abonelik|subscription|payment|ödeme|fatura|invoice|iptal|cancel)", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 80),
                SubscriptionPatternEntity(serviceName = "Microsoft", regexPattern = "microsoft\\.com", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 70),

                // Google One / Drive
                SubscriptionPatternEntity(serviceName = "Google One", regexPattern = "(?i)google.*(one|drive|storage|depolama).*?(yenile|abonelik|subscription|payment|ödeme|fatura|invoice|iptal|cancel)", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 80),
                SubscriptionPatternEntity(serviceName = "Google Services", regexPattern = "google\\.com", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.DOMAIN, priority = 60),

                // SoundCloud
                SubscriptionPatternEntity(serviceName = "SoundCloud", regexPattern = "(?i)soundcloud.*(abonelik|yenile|renew|subscription|go\\+|payment|ödeme|fatura|invoice)", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 75),
                SubscriptionPatternEntity(serviceName = "SoundCloud", regexPattern = "soundcloud\\.com", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 90),

                // Audible
                SubscriptionPatternEntity(serviceName = "Audible", regexPattern = "(?i)audible.*(abonelik|üyelik|yenile|renew|subscription|membership|payment|ödeme|fatura|invoice)", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 75),
                SubscriptionPatternEntity(serviceName = "Audible", regexPattern = "audible\\.com", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 90),

                // Dropbox
                SubscriptionPatternEntity(serviceName = "Dropbox", regexPattern = "(?i)dropbox.*(plus|professional|abonelik|yenile|renew|subscription|payment|ödeme|fatura|invoice)", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 75),
                SubscriptionPatternEntity(serviceName = "Dropbox", regexPattern = "dropbox\\.com", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 90),

                // LinkedIn Premium
                SubscriptionPatternEntity(serviceName = "LinkedIn Premium", regexPattern = "(?i)linkedin.*(premium|abonelik|üyelik|yenile|renew|subscription|payment|ödeme|fatura|invoice)", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 80),
                SubscriptionPatternEntity(serviceName = "LinkedIn", regexPattern = "linkedin\\.com", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 70),

                // Tidal
                SubscriptionPatternEntity(serviceName = "Tidal", regexPattern = "(?i)tidal.*(abonelik|üyelik|yenile|renew|subscription|hifi|payment|ödeme|fatura|invoice)", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 75),
                SubscriptionPatternEntity(serviceName = "Tidal", regexPattern = "tidal\\.com", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 90),

                // GitHub
                SubscriptionPatternEntity(serviceName = "GitHub", regexPattern = "(?i)github.*(pro|sponsors|abonelik|subscription|billing|fatura|yenile|renew)", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 70),
                SubscriptionPatternEntity(serviceName = "GitHub", regexPattern = "github\\.com", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 80),

                // GitLab
                SubscriptionPatternEntity(serviceName = "GitLab", regexPattern = "(?i)gitlab.*(premium|ultimate|abonelik|subscription|billing|fatura|yenile|renew)", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 70),
                SubscriptionPatternEntity(serviceName = "GitLab", regexPattern = "gitlab\\.com", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 80),

                // Twitch
                SubscriptionPatternEntity(serviceName = "Twitch", regexPattern = "(?i)twitch.*(sub|turbo|abonelik|subscription|renewal|yenileme|payment|ödeme|fatura|invoice)", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 75),
                SubscriptionPatternEntity(serviceName = "Twitch", regexPattern = "twitch\\.tv", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 90),

                // EA Play
                SubscriptionPatternEntity(serviceName = "EA Play", regexPattern = "(?i)ea.*(play|access|abonelik|subscription|yenile|renew|payment|ödeme|fatura|invoice)", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 75),
                SubscriptionPatternEntity(serviceName = "EA", regexPattern = "ea\\.com", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 90),

                // PlayStation Network
                SubscriptionPatternEntity(serviceName = "PlayStation", regexPattern = "(?i)playstation.*(plus|now|abonelik|subscription|yenile|renew|fatura|payment|ödeme|invoice)", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 80),
                SubscriptionPatternEntity(serviceName = "PlayStation", regexPattern = "playstation\\.com", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 90),

                // Xbox
                SubscriptionPatternEntity(serviceName = "Xbox", regexPattern = "(?i)xbox.*(game pass|live gold|abonelik|subscription|yenile|renew|fatura|payment|ödeme|invoice)", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 80),
                SubscriptionPatternEntity(serviceName = "Xbox", regexPattern = "xbox\\.com", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 90),

                // TV+
                SubscriptionPatternEntity(serviceName = "TV+", regexPattern = "(?i)tvplus.*(abonelik|yenile|subscription|ödeme|fatura|invoice)", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 80),
                SubscriptionPatternEntity(serviceName = "TV+", regexPattern = "tvplus\\.com\\.tr", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 90),

                // Tivibu
                SubscriptionPatternEntity(serviceName = "Tivibu", regexPattern = "(?i)tivibu.*(abonelik|yenile|subscription|ödeme|fatura|invoice)", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 80),
                SubscriptionPatternEntity(serviceName = "Tivibu", regexPattern = "tivibu\\.com\\.tr", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 90),

                // D-Smart
                SubscriptionPatternEntity(serviceName = "D-Smart", regexPattern = "(?i)(d-?smart|dbctv).*(abonelik|yenile|subscription|fatura|ödeme|invoice)", source = "default_verified", approvedCount = 1, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 80),
                SubscriptionPatternEntity(serviceName = "D-Smart", regexPattern = "dsmartgo\\.com\\.tr", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 90),

                // Exxen
                SubscriptionPatternEntity(serviceName = "Exxen", regexPattern = "(?i)exxen.*(abonelik|üyelik|yenile|subscription|ödeme|fatura|invoice)", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 85),
                SubscriptionPatternEntity(serviceName = "Exxen", regexPattern = "exxen\\.com", source = "default_verified", approvedCount = 3, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 100),

                // BluTV
                SubscriptionPatternEntity(serviceName = "BluTV", regexPattern = "(?i)blutv.*(abonelik|üyelik|yenile|subscription|ödeme|fatura|invoice)", source = "default_verified", approvedCount = 2, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 85),
                SubscriptionPatternEntity(serviceName = "BluTV", regexPattern = "blutv\\.com", source = "default_verified", approvedCount = 3, rejectedCount = 0, isSubscription = true, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 100),

                // Negatif Kalıplar
                SubscriptionPatternEntity(serviceName = "Google Security Alert", regexPattern = "Güvenlik Uyarısı|Security Alert|Security notification", source = "default_verified", approvedCount = 0, rejectedCount = 5, isSubscription = false, isTrustedSenderDomain = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 110),
                SubscriptionPatternEntity(serviceName = "Facebook Notification", regexPattern = "facebookmail\\.com", source = "default_verified", approvedCount = 0, rejectedCount = 5, isSubscription = false, isTrustedSenderDomain = true, patternType = PatternType.DOMAIN, priority = 105),
                SubscriptionPatternEntity(serviceName = "Kargo Takip", regexPattern = "kargonuz yola çıktı|siparişiniz gönderildi|delivery update|shipping confirmation", source = "default_verified", approvedCount = 0, rejectedCount = 3, isSubscription = false, patternType = PatternType.SUBJECT_KEYWORD, priority = 110),
                SubscriptionPatternEntity(serviceName = "LinkedIn Jobs", regexPattern = "linkedin\\.com.*(job|iş ilanı)", source = "default_verified", approvedCount = 0, rejectedCount = 3, isSubscription = false, patternType = PatternType.COMBINED, priority = 105)
            )
            initialPatterns.forEach { communityPatternDao.upsertPattern(it) }
            Log.i("PatternRepository", "${initialPatterns.size} initial patterns seeded/updated with named arguments.")
        } else {
            Log.i("PatternRepository", "Initial patterns condition not met. Skipping seed.")
        }
    }
}