package com.example.abonekaptanmobile

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.abonekaptanmobile.data.repository.CommunityPatternRepository // Repository'yi import edin
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

// Turkish: Hilt için uygulama sınıfı. WorkManager'ın Hilt ile entegrasyonunu ve başlangıç kalıplarının yüklenmesini sağlar.
// English: Application class for Hilt. Provides Hilt integration for WorkManager and loads initial patterns.
@HiltAndroidApp
class AboneKaptanApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject // CommunityPatternRepository'yi inject edin
    lateinit var communityPatternRepository: CommunityPatternRepository

    override fun onCreate() {
        super.onCreate() // Her zaman üst sınıfın onCreate'ini çağırın
        Log.d("AboneKaptanApp", "onCreate - Application starting.")

        // Başlangıç kalıplarını yüklemek için bir coroutine başlatın
        // Bu işlem veritabanına erişeceği için IO Dispatcher kullanılmalı
        Log.d("AboneKaptanApp", "Attempting to seed initial patterns...")
        CoroutineScope(Dispatchers.IO).launch {
            communityPatternRepository.seedInitialPatternsIfEmpty()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}