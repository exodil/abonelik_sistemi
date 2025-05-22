package com.example.abonekaptanmobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.abonekaptanmobile.data.local.entity.UserSubscriptionEntity

@Dao
interface UserSubscriptionDao {
    /**
     * Inserts a new subscription record into the database. If a subscription with the same
     * primary key already exists, it will be replaced due to the `OnConflictStrategy.REPLACE`.
     * @param subscription The [UserSubscriptionEntity] to insert.
     * @return The row ID of the newly inserted subscription.
     *
     * Turkish: Veritabanına yeni bir abonelik kaydı ekler. Aynı birincil anahtara sahip bir
     * abonelik zaten mevcutsa, `OnConflictStrategy.REPLACE` nedeniyle değiştirilecektir.
     * @param subscription Eklenecek [UserSubscriptionEntity].
     * @return Yeni eklenen aboneliğin satır kimliği.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: UserSubscriptionEntity): Long

    /**
     * Updates an existing subscription record in the database.
     * @param subscription The [UserSubscriptionEntity] to update, identified by its primary key.
     *
     * Turkish: Veritabanındaki mevcut bir abonelik kaydını günceller.
     * @param subscription Güncellenecek [UserSubscriptionEntity], birincil anahtarıyla tanımlanır.
     */
    @Update
    suspend fun update(subscription: UserSubscriptionEntity)

    /**
     * Retrieves the most recent subscription record for a given service name and user ID.
     * The query handles nullable `userId` by comparing `IFNULL(userId, '')`.
     * Ordered by `subscriptionStartDate` descending to get the latest.
     * @param serviceName The name of the service.
     * @param userId The optional user ID. Pass null if not applicable.
     * @return The latest [UserSubscriptionEntity] for the service and user, or null if not found.
     *
     * Turkish: Belirli bir servis adı ve kullanıcı kimliği için en son abonelik kaydını alır.
     * Sorgu, `IFNULL(userId, '')` karşılaştırması yaparak null olabilen `userId`'yi işler.
     * En sonuncuyu almak için `subscriptionStartDate`'e göre azalan sırada sıralanır.
     * @param serviceName Servisin adı.
     * @param userId İsteğe bağlı kullanıcı kimliği. Uygulanabilir değilse null geçin.
     * @return Servis ve kullanıcı için en son [UserSubscriptionEntity] veya bulunamazsa null.
     */
    @Query("SELECT * FROM user_subscriptions WHERE serviceName = :serviceName AND IFNULL(userId, '') = IFNULL(:userId, '') ORDER BY subscriptionStartDate DESC LIMIT 1")
    suspend fun getLatestSubscriptionByServiceNameAndUserId(serviceName: String, userId: String?): UserSubscriptionEntity?

    /**
     * Retrieves all active subscriptions for a given user ID.
     * Filters by `status = 'ACTIVE'` and handles nullable `userId`.
     * Ordered by `lastActiveConfirmationDate` descending.
     * @param userId The optional user ID. Pass null to fetch active subscriptions not tied to a specific user (if any).
     * @return A list of active [UserSubscriptionEntity] objects.
     *
     * Turkish: Belirli bir kullanıcı kimliği için tüm aktif abonelikleri alır.
     * `status = 'ACTIVE'`'e göre filtreler ve null olabilen `userId`'yi işler.
     * `lastActiveConfirmationDate`'e göre azalan sırada sıralanır.
     * @param userId İsteğe bağlı kullanıcı kimliği. Belirli bir kullanıcıya bağlı olmayan aktif abonelikleri
     * (varsa) almak için null geçin.
     * @return Aktif [UserSubscriptionEntity] nesnelerinin bir listesi.
     */
    @Query("SELECT * FROM user_subscriptions WHERE status = 'ACTIVE' AND IFNULL(userId, '') = IFNULL(:userId, '') ORDER BY lastActiveConfirmationDate DESC")
    suspend fun getActiveSubscriptionsByUserId(userId: String?): List<UserSubscriptionEntity>

    /**
     * Retrieves all subscriptions (active or not) for a given user ID.
     * Handles nullable `userId`.
     * Ordered by service name and then by subscription start date descending.
     * @param userId The optional user ID. Pass null to fetch all subscriptions not tied to a specific user (if any).
     * @return A list of all [UserSubscriptionEntity] objects for the user.
     *
     * Turkish: Belirli bir kullanıcı kimliği için tüm abonelikleri (aktif veya değil) alır.
     * Null olabilen `userId`'yi işler.
     * Servis adına ve ardından abonelik başlangıç tarihine göre azalan sırada sıralanır.
     * @param userId İsteğe bağlı kullanıcı kimliği. Belirli bir kullanıcıya bağlı olmayan tüm abonelikleri
     * (varsa) almak için null geçin.
     * @return Kullanıcı için tüm [UserSubscriptionEntity] nesnelerinin bir listesi.
     */
    @Query("SELECT * FROM user_subscriptions WHERE IFNULL(userId, '') = IFNULL(:userId, '') ORDER BY serviceName, subscriptionStartDate DESC")
    suspend fun getAllSubscriptionsByUserId(userId: String?): List<UserSubscriptionEntity>
    
    /**
     * Retrieves a specific subscription by its unique ID.
     * @param subscriptionId The primary key ID of the subscription.
     * @return The [UserSubscriptionEntity] if found, or null otherwise.
     *
     * Turkish: Benzersiz kimliğine göre belirli bir aboneliği alır.
     * @param subscriptionId Aboneliğin birincil anahtar kimliği.
     * @return Bulunursa [UserSubscriptionEntity], aksi takdirde null.
     */
    @Query("SELECT * FROM user_subscriptions WHERE id = :subscriptionId")
    suspend fun getSubscriptionById(subscriptionId: Long): UserSubscriptionEntity?
}
