// file: app/java/com/example/abonekaptanmobile/data/local/AppDatabase.kt
package com.example.abonekaptanmobile.data.local

import android.util.Log // Loglama için eklendi (Migration içinde kullanılabilir)
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.abonekaptanmobile.data.local.dao.CommunityPatternDao
import com.example.abonekaptanmobile.data.local.dao.FeedbackDao
import com.example.abonekaptanmobile.data.local.entity.FeedbackEntity
import com.example.abonekaptanmobile.data.local.entity.SubscriptionPatternEntity
import com.example.abonekaptanmobile.data.local.entity.PatternType // PatternType importu

@Database(
    entities = [SubscriptionPatternEntity::class, FeedbackEntity::class],
    version = 3, // <<--- VERSİYONU 3 YAPIN
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun communityPatternDao(): CommunityPatternDao
    abstract fun feedbackDao(): FeedbackDao

    companion object {
        const val DATABASE_NAME = "abone_kaptan_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SubscriptionPatternEntity'ye isSubscription sütununu ekle (varsayılan true)
                db.execSQL("ALTER TABLE subscription_patterns ADD COLUMN isSubscription INTEGER NOT NULL DEFAULT 1")
                try {
                    db.execSQL("ALTER TABLE feedback ADD COLUMN processed INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    Log.w("Migration_1_2", "Could not add 'processed' column to feedback, it might already exist: ${e.message}")
                }
            }
        }

        // YENİ MIGRATION (Versiyon 2'den 3'e)
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SubscriptionPatternEntity'ye yeni sütunları ekle
                // Varsayılan değerler önemlidir.
                // SQLite'ta boolean için INTEGER kullanılır (0 = false, 1 = true)
                db.execSQL("ALTER TABLE subscription_patterns ADD COLUMN isTrustedSenderDomain INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscription_patterns ADD COLUMN patternType TEXT NOT NULL DEFAULT '${PatternType.UNKNOWN}'")
                db.execSQL("ALTER TABLE subscription_patterns ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}