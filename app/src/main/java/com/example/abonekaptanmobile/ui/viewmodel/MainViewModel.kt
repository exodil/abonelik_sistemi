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
import com.example.abonekaptanmobile.data.local.entity.FeedbackEntity
import com.example.abonekaptanmobile.data.repository.FeedbackRepository
import com.example.abonekaptanmobile.data.repository.GmailRepository
import com.example.abonekaptanmobile.model.EmailAnalysisResult
import com.example.abonekaptanmobile.model.RawEmail
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
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _isSignedIn = MutableStateFlow(googleAuthManager.getCurrentAccount() != null)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<EmailAnalysisResult>>(emptyList())
    val subscriptions: StateFlow<List<EmailAnalysisResult>> = _subscriptions.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        Log.d("MainViewModel", "ViewModel init block started. Is signed in: ${_isSignedIn.value}") // YENİ LOG
        if (_isSignedIn.value) {
            Log.d("MainViewModel", "User already signed in on init, loading subscriptions.") // YENİ LOG
            loadSubscriptions()
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
                Log.i("MainViewModel", "Google Sign-In successful for account: ${account.email}") // YENİ LOG
                googleAuthManager.updateCurrentAccount(account)
                _isSignedIn.value = true
                loadSubscriptions()
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

    fun loadSubscriptions() {
        if (!_isSignedIn.value) {
            Log.d("MainViewModel", "loadSubscriptions called but user not signed in. Skipping.") // YENİ LOG
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            Log.i("MainViewModel", "Loading subscriptions...") // YENİ LOG
            try {
                val rawEmails = gmailRepository.fetchEmails(maxTotalEmails = 300)
                Log.d("MainViewModel", "Fetched ${rawEmails.size} raw emails from GmailRepository.") // YENİ LOG

                val emailsWithSnippets = rawEmails.map { email ->
                    email.copy(
                        bodySnippet = email.bodySnippet ?: (email.snippet.takeIf { it.isNotBlank() } ?: email.bodyPlainText).take(250)
                    )
                }

                val finalProcessedEmails = subscriptionClassifier.processEmailsWithLLM(emailsWithSnippets)
                Log.d("MainViewModel", "LLM Classifier returned ${finalProcessedEmails.size} processed email results.")
                _subscriptions.value = finalProcessedEmails

                if (finalProcessedEmails.isEmpty()) {
                    if (rawEmails.isNotEmpty()) {
                        _error.value = "E-postalarınız analiz edildi ancak uygun bir abonelik veya ilgili e-posta bulunamadı."
                        Log.i("MainViewModel", "Emails analyzed by LLM, but no relevant email results classified.")
                    } else {
                        _error.value = "Abonelik bilgisi bulunamadı veya e-postalar yüklenemedi. Lütfen yenileyin veya daha sonra tekrar deneyin."
                        Log.w("MainViewModel", "No raw emails fetched, so no email analysis results found.")
                    }
                }

            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to load subscriptions", e) // YENİ LOG
                e.printStackTrace()
                _error.value = "Abonelikler yüklenirken bir hata oluştu: ${e.localizedMessage ?: "Bilinmeyen hata"}"
                _subscriptions.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun submitFeedback(serviceName: String, originalStatus: SubscriptionStatus, feedbackLabel: String, note: String?) {
        Log.d("MainViewModel", "submitFeedback called for $serviceName, label: $feedbackLabel") // YENİ LOG
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