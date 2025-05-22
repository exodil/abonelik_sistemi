// file: app/java/com/example/abonekaptanmobile/ui/viewmodel/MainViewModel.kt
package com.example.abonekaptanmobile.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.abonekaptanmobile.auth.GoogleAuthManager
import com.example.abonekaptanmobile.data.local.dao.UserSubscriptionDao
import com.example.abonekaptanmobile.data.local.entity.FeedbackEntity
import com.example.abonekaptanmobile.data.local.entity.UserSubscriptionEntity
import com.example.abonekaptanmobile.data.repository.FeedbackRepository
import com.example.abonekaptanmobile.data.repository.GmailRepository
import com.example.abonekaptanmobile.model.RawEmail
import com.example.abonekaptanmobile.model.SubscriptionItem
import com.example.abonekaptanmobile.model.SubscriptionStatus
import com.example.abonekaptanmobile.services.SubscriptionClassifier
import com.example.abonekaptanmobile.workers.ProcessFeedbackWorker
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val googleAuthManager: GoogleAuthManager,
    private val gmailRepository: GmailRepository,
    private val subscriptionClassifier: SubscriptionClassifier,
    private val feedbackRepository: FeedbackRepository,
    private val userSubscriptionDao: UserSubscriptionDao // Injected UserSubscriptionDao
) : ViewModel() {

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _isSignedIn = MutableStateFlow(googleAuthManager.getCurrentAccount() != null)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<SubscriptionItem>>(emptyList())
    val subscriptions: StateFlow<List<SubscriptionItem>> = _subscriptions.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Progress reporting StateFlows
    private val _progressPercentage = MutableStateFlow(0)
    val progressPercentage: StateFlow<Int> = _progressPercentage.asStateFlow()

    private val _progressMessage = MutableStateFlow<String?>(null)
    val progressMessage: StateFlow<String?> = _progressMessage.asStateFlow()

    init {
        Log.d("MainViewModel", "ViewModel init block started. Is signed in: ${_isSignedIn.value}")
        if (_isSignedIn.value) {
            Log.d("MainViewModel", "User already signed in on init, calling classifyAndRefreshDb.")
            classifyAndRefreshDb() // Changed to new orchestration method
        }
        scheduleFeedbackWorker()
    }

    fun startSignIn(launcher: ActivityResultLauncher<Intent>) {
        Log.d("MainViewModel", "startSignIn called.") // YENİ LOG
        _isSigningIn.value = true
        googleAuthManager.signIn(launcher)
    }

    fun handleSignInResult(task: Task<GoogleSignInAccount>?) {
        Log.d("MainViewModel", "handleSignInResult called.") // YENİ LOG
        _isSigningIn.value = false
        try {
            val account = task?.getResult(ApiException::class.java)
            if (account != null) {
                Log.i("MainViewModel", "Google Sign-In successful for account: ${account.email}")
                googleAuthManager.updateCurrentAccount(account)
                _isSignedIn.value = true
                classifyAndRefreshDb() // Changed to new orchestration method
            } else {
                _isSignedIn.value = false
                _error.value = "Google Sign-In failed: Account is null."
                Log.w("MainViewModel", "Google Sign-In failed: Account is null after getResult.") // YENİ LOG
            }
        } catch (e: ApiException) {
            _isSignedIn.value = false
            _error.value = "Google Sign-In failed: (code: ${e.statusCode}) ${e.message}"
            Log.e("MainViewModel", "Google Sign-In ApiException: code=${e.statusCode}", e) // YENİ LOG
            e.printStackTrace()
        } catch (e: Exception) {
            _isSignedIn.value = false
            _error.value = "Google Sign-In failed: ${e.message}"
            Log.e("MainViewModel", "Google Sign-In generic exception", e) // YENİ LOG
            e.printStackTrace()
        }
    }

    fun signOut() {
        Log.d("MainViewModel", "signOut called.") // YENİ LOG
        viewModelScope.launch {
            googleAuthManager.signOut().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _isSignedIn.value = false
                    _subscriptions.value = emptyList()
                    _error.value = null
                    Log.i("MainViewModel", "User signed out successfully.") // YENİ LOG
                } else {
                    _error.value = "Sign out failed: ${task.exception?.message}"
                    Log.e("MainViewModel", "Sign out failed", task.exception) // YENİ LOG
                    task.exception?.printStackTrace()
                }
            }
        }
    }

    /**
     * Orchestrates the process of fetching emails, classifying them for subscription lifecycle events,
     * updating the local database with these events, and then refreshing the UI by loading
     * subscription data from the database.
     * This function should be called when a full refresh of subscription data is needed,
     * for example, after initial sign-in or when the user requests a manual refresh.
     * It manages the `isLoading` state for the overall operation.
     *
     * Turkish: E-postaların getirilmesi, abonelik yaşam döngüsü olayları için sınıflandırılması,
     * bu olaylarla yerel veritabanının güncellenmesi ve ardından veritabanından abonelik
     * verilerini yükleyerek kullanıcı arayüzünün yenilenmesi sürecini yönetir.
     * Bu işlev, örneğin ilk oturum açmadan sonra veya kullanıcı manuel yenileme istediğinde,
     * abonelik verilerinin tam olarak yenilenmesi gerektiğinde çağrılmalıdır.
     * Genel işlem için `isLoading` durumunu yönetir.
     */
    fun classifyAndRefreshDb() {
        if (!_isSignedIn.value) {
            Log.d("MainViewModel", "classifyAndRefreshDb called but user not signed in. Skipping.")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _progressPercentage.value = 0
            _progressMessage.value = "İşlem başlıyor: E-postalar taranacak..."
            Log.i("MainViewModel", "Starting email classification and DB refresh...")
            try {
                // Step 1: Fetch emails
                _progressMessage.value = "E-posta tarama başlatılıyor..." // Initial message for fetching
                _progressPercentage.value = 0 // Initial percentage for fetching

                val rawEmails = gmailRepository.fetchEmails(
                    maxTotalEmails = 1000, // Or your configured value
                    onProgress = { fetchedCount, totalToFetch ->
                        val currentFetchingProgress = if (totalToFetch > 0) {
                            (fetchedCount.toFloat() / totalToFetch.toFloat()) * 25f // Fetching is 0-25% of total
                        } else {
                            25f // Assume 25% if totalToFetch is 0 (e.g. no emails at all)
                        }
                        _progressPercentage.value = currentFetchingProgress.toInt()
                        _progressMessage.value = "E-postalar taranıyor: $fetchedCount / $totalToFetch"
                    }
                )
                Log.d("MainViewModel", "Fetched ${rawEmails.size} raw emails from GmailRepository.")
                // After fetching is complete, progress should ideally be at 25% from the callback.
                // Set message for next phase.
                _progressPercentage.value = 25 // Ensure it's at least 25%
                _progressMessage.value = "E-postalar alındı (${rawEmails.size} adet). Sınıflandırma başlıyor..."

                // Step 2: Classify emails (this now writes to the DB)
                if (rawEmails.isNotEmpty()) { // Changed from emailsWithSnippets to rawEmails
                    // _progressPercentage.value is already 25 at this point.
                    _progressMessage.value = "E-postalar alındı (${rawEmails.size} adet). Sınıflandırma başlıyor..." // Changed from emailsWithSnippets to rawEmails
                    subscriptionClassifier.classifyEmails(
                        allRawEmails = rawEmails, // Changed from emailsWithSnippets to rawEmails
                        onProgress = { processedCount, totalToProcess ->
                            // Classification phase is 25% to 90% of total progress
                            val classificationProgress = if (totalToProcess > 0) {
                                (processedCount.toFloat() / totalToProcess.toFloat()) * 65f
                            } else {
                                65f // Assume full 65% if total is 0 (e.g. no emails to classify)
                            }
                            _progressPercentage.value = (25f + classificationProgress).toInt()
                            _progressMessage.value = "E-postalar sınıflandırılıyor: $processedCount / $totalToProcess"
                        }
                    )
                } else {
                    // If no emails to classify, set progress as if classification part is done
                    _progressPercentage.value = 90 // 25% (fetching) + 65% (classification) = 90%
                    _progressMessage.value = "Sınıflandırılacak e-posta bulunmuyor. Sonraki adıma geçiliyor..."
                }

                // After classification is complete (or skipped if no emails)
                _progressPercentage.value = 90 // Ensure it's at 90% before loading from DB
                _progressMessage.value = "Sınıflandırma tamamlandı. Sonuçlar yükleniyor..."

                // Step 3: Load subscriptions from DB to update UI
                loadUserSubscriptionsFromDb() // This function now handles the final progress update

            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed during classifyAndRefreshDb", e)
                e.printStackTrace()
                _error.value = "Abonelikler işlenirken bir hata oluştu: ${e.localizedMessage ?: "Bilinmeyen hata"}"
                _progressMessage.value = "Hata oluştu: ${e.localizedMessage ?: "Bilinmeyen hata"}"
                _progressPercentage.value = 0
                // Optionally, load from DB even if classification fails to show existing data
                // loadUserSubscriptionsFromDb()
            } finally {
                // _isLoading.value = false // isLoading is handled by loadUserSubscriptionsFromDb if called
                // Reset progress message and percentage if not already at 100% (success) or 0 (error)
                if (_progressPercentage.value != 100 && _progressPercentage.value != 0) {
                    _progressPercentage.value = 0
                    _progressMessage.value = null
                }
                if (_isLoading.value && _progressPercentage.value != 100) { // Ensure isLoading is set to false if process didn't complete fully through loadUserSubscriptionsFromDb
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Loads user subscription data directly from the local database via [UserSubscriptionDao]
     * and updates the UI state. This function is typically called after the classification
     * process ([classifyAndRefreshDb]) has updated the database, or if a direct refresh
     * from the database is needed without re-classifying emails.
     * It manages the `isLoading` state specifically for this database loading operation.
     *
     * Turkish: Kullanıcı abonelik verilerini [UserSubscriptionDao] aracılığıyla doğrudan yerel
     * veritabanından yükler ve kullanıcı arayüzü durumunu günceller. Bu işlev genellikle
     * sınıflandırma süreci ([classifyAndRefreshDb]) veritabanını güncelledikten sonra veya
     * e-postaları yeniden sınıflandırmadan doğrudan veritabanından yenileme gerektiğinde çağrılır.
     * Özellikle bu veritabanı yükleme işlemi için `isLoading` durumunu yönetir.
     */
    private fun loadUserSubscriptionsFromDb() {
        viewModelScope.launch {
            _isLoading.value = true // Indicate loading specifically for DB fetch and UI update
            Log.i("MainViewModel", "Loading user subscriptions from database...")
            try {
                // Currently fetching all subscriptions; userId is null. This can be parameterized in the future.
                val subscriptionEntities = userSubscriptionDao.getAllSubscriptionsByUserId(null)
                Log.d("MainViewModel", "Fetched ${subscriptionEntities.size} subscription entities from DAO.")
                
                _subscriptions.value = mapEntitiesToSubscriptionItems(subscriptionEntities)
                Log.d("MainViewModel", "Transformed and updated _subscriptions StateFlow with ${subscriptionEntities.size} items.")

                // If no subscriptions are found in the DB and no other error is present, set a specific message.
                if (subscriptionEntities.isEmpty() && _error.value == null) {
                     _error.value = "Veritabanında abonelik bulunamadı. E-postalarınızı analiz etmek için yenileyin."
                     Log.i("MainViewModel", "No subscriptions found in the database.")
                }

            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to load subscriptions from database", e)
                _error.value = "Veritabanından abonelikler yüklenirken bir hata oluştu: ${e.localizedMessage ?: "Bilinmeyen hata"}"
                _subscriptions.value = emptyList() // Clear subscriptions on error
            } finally {
                _isLoading.value = false // End loading state for this operation
                // Final progress update, assuming loadUserSubscriptionsFromDb is the last step
                _progressPercentage.value = 100
                _progressMessage.value = "İşlem tamamlandı."
            }
        }
    }

    /**
     * Maps a list of [UserSubscriptionEntity] objects from the database to a list of
     * [SubscriptionItem] objects suitable for UI display.
     *
     * Key mapping logic:
     * - `UserSubscriptionEntity.status` ("ACTIVE", "CANCELLED") is mapped to [SubscriptionStatus] enum.
     * - `SubscriptionItem.lastEmailDate` is derived from `entity.lastActiveConfirmationDate` for active
     *   subscriptions, or `entity.subscriptionEndDate` (or `entity.updatedAt` as fallback) for cancelled ones.
     * - Placeholders are used for `emailCount` (set to 0) and `relatedEmailIds` (set to emptyList),
     *   as these are not directly available from `UserSubscriptionEntity` in this context.
     *
     * @param entities The list of [UserSubscriptionEntity] to transform.
     * @return A list of [SubscriptionItem] for UI display.
     *
     * Turkish: Veritabanından alınan [UserSubscriptionEntity] nesnelerinin bir listesini,
     * kullanıcı arayüzü gösterimi için uygun [SubscriptionItem] nesnelerinin bir listesine eşler.
     *
     * Temel eşleme mantığı:
     * - `UserSubscriptionEntity.status` ("ACTIVE", "CANCELLED"), [SubscriptionStatus] enum'una eşlenir.
     * - `SubscriptionItem.lastEmailDate`, aktif abonelikler için `entity.lastActiveConfirmationDate`'ten
     *   veya iptal edilenler için `entity.subscriptionEndDate`'ten (veya geri dönüş olarak `entity.updatedAt`) türetilir.
     * - `emailCount` (0 olarak ayarlanır) ve `relatedEmailIds` (boş liste olarak ayarlanır) için yer tutucular
     *   kullanılır, çünkü bunlar bu bağlamda `UserSubscriptionEntity`'den doğrudan mevcut değildir.
     */
    private fun mapEntitiesToSubscriptionItems(entities: List<UserSubscriptionEntity>): List<SubscriptionItem> {
        return entities.map { entity ->
            val subStatus = when (entity.status) {
                "ACTIVE" -> SubscriptionStatus.ACTIVE
                "CANCELLED" -> SubscriptionStatus.CANCELLED
                else -> {
                    Log.w("MainViewModel", "Unknown status found in UserSubscriptionEntity: ${entity.status} for service ${entity.serviceName}. Defaulting to UNKNOWN.")
                    SubscriptionStatus.UNKNOWN 
                }
            }

            SubscriptionItem(
                serviceName = entity.serviceName,
                emailCount = 0, // Placeholder: Not directly available from UserSubscriptionEntity.
                lastEmailDate = if (subStatus == SubscriptionStatus.CANCELLED) entity.subscriptionEndDate ?: entity.updatedAt else entity.lastActiveConfirmationDate,
                status = subStatus,
                cancellationDate = if (subStatus == SubscriptionStatus.CANCELLED) entity.subscriptionEndDate else null,
                relatedEmailIds = emptyList(), // Placeholder: Not directly available from UserSubscriptionEntity.
                subscriptionStartDate = entity.subscriptionStartDate
            )
        }
    }

    fun submitFeedback(serviceName: String, originalStatus: SubscriptionStatus, feedbackLabel: String, note: String?) {
        Log.d("MainViewModel", "submitFeedback called for $serviceName, label: $feedbackLabel")
        viewModelScope.launch {
            try {
                val feedback = FeedbackEntity(
                    serviceName = serviceName,
                    originalStatus = originalStatus.name,
                    feedbackLabel = feedbackLabel,
                    feedbackNote = note
                )
                feedbackRepository.insertFeedback(feedback)
                Log.i("MainViewModel", "Feedback submitted for $serviceName.") // YENİ LOG
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to submit feedback for $serviceName", e) // YENİ LOG
                e.printStackTrace()
                _error.value = "Geri bildirim gönderilirken hata oluştu: ${e.localizedMessage}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun scheduleFeedbackWorker() {
        Log.d("MainViewModel", "Scheduling ProcessFeedbackWorker.") // YENİ LOG
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val repeatingRequest = PeriodicWorkRequestBuilder<ProcessFeedbackWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ProcessFeedbackWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            repeatingRequest
        )
    }
}