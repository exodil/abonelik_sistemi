// file: app/java/com/example/abonekaptanmobile/di/AppModule.kt
package com.example.abonekaptanmobile.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.abonekaptanmobile.auth.GoogleAuthManager
import com.example.abonekaptanmobile.data.local.AppDatabase // AppDatabase importu
import com.example.abonekaptanmobile.data.local.dao.CommunityPatternDao
import com.example.abonekaptanmobile.data.local.dao.FeedbackDao
import com.example.abonekaptanmobile.data.remote.GmailApi
import com.example.abonekaptanmobile.data.remote.HuggingFaceApi
// CommunityPatternRepository import will be removed if not used elsewhere, but let's keep it for now and just change the provider
import com.example.abonekaptanmobile.data.repository.CommunityPatternRepository 
import com.example.abonekaptanmobile.data.repository.FeedbackRepository
import com.example.abonekaptanmobile.data.repository.GmailRepository
import com.example.abonekaptanmobile.data.repository.HuggingFaceRepository
import com.example.abonekaptanmobile.services.SubscriptionClassifier
import com.example.abonekaptanmobile.util.CompanyListProvider 
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ... (provideGoogleAuthManager, provideOkHttpClient, provideGmailApi, provideGmailRepository aynı kalacak) ...

    @Singleton
    @Provides
    fun provideGoogleAuthManager(@ApplicationContext context: Context): GoogleAuthManager {
        return GoogleAuthManager(context)
    }

    @Singleton
    @Provides
    fun provideOkHttpClient(authManager: GoogleAuthManager): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d("OkHttp", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest: Request = chain.request()
                var requestBuilder = originalRequest.newBuilder()
                var newRequest: Request? = null

                try {
                    val token = authManager.getCredential().token
                    if (token != null) {
                        Log.d("AuthInterceptor", "Token obtained, adding Authorization header.")
                        requestBuilder = originalRequest.newBuilder()
                            .header("Authorization", "Bearer $token")
                        newRequest = requestBuilder.build()
                    } else {
                        Log.w("AuthInterceptor", "Token is null. Proceeding without Authorization header.")
                        newRequest = originalRequest
                    }
                } catch (e: Exception) {
                    Log.e("AuthInterceptor", "Error getting token: ${e.message}", e)
                    newRequest = originalRequest
                }
                chain.proceed(newRequest ?: originalRequest)
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Singleton
    @Provides
    fun provideGmailApi(okHttpClient: OkHttpClient): GmailApi {
        return Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GmailApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGmailRepository(gmailApi: GmailApi): GmailRepository {
        return GmailRepository(gmailApi)
    }

    @Singleton
    @Provides
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3) // <<--- MIGRATION_2_3 EKLENDİ
            // .fallbackToDestructiveMigration() // Geliştirme sırasında şemayı silip yeniden oluşturur, dikkatli kullanın
            .build()
    }

    @Singleton
    @Provides
    fun provideCommunityPatternDao(appDatabase: AppDatabase): CommunityPatternDao {
        return appDatabase.communityPatternDao()
    }

    @Singleton
    @Provides
    fun provideFeedbackDao(appDatabase: AppDatabase): FeedbackDao {
        return appDatabase.feedbackDao()
    }

    @Provides
    @Singleton
    fun provideCommunityPatternRepository(dao: CommunityPatternDao): CommunityPatternRepository {
        return CommunityPatternRepository(dao)
    }

    @Provides
    @Singleton
    fun provideFeedbackRepository(dao: FeedbackDao): FeedbackRepository {
        return FeedbackRepository(dao)
    }

    @Provides
    @Singleton
    fun provideHuggingFaceApi(okHttpClient: OkHttpClient): HuggingFaceApi {
        return Retrofit.Builder()
            .baseUrl("https://api-inference.huggingface.co/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HuggingFaceApi::class.java)
    }

    @Provides
    @Singleton
    fun provideHuggingFaceRepository(huggingFaceApi: HuggingFaceApi): HuggingFaceRepository {
        return HuggingFaceRepository(huggingFaceApi)
    }

    @Provides
    @Singleton
    fun provideSubscriptionClassifier(
        huggingFaceRepository: HuggingFaceRepository,
        companyListProvider: CompanyListProvider 
    ): SubscriptionClassifier {
        return SubscriptionClassifier(huggingFaceRepository, companyListProvider)
    }
}