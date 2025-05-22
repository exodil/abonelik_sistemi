// file: app/java/com/example/abonekaptanmobile/data/remote/GmailApi.kt
package com.example.abonekaptanmobile.data.remote

import com.example.abonekaptanmobile.data.remote.model.GmailMessage
import com.example.abonekaptanmobile.data.remote.model.GmailMessageListResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// Turkish: Gmail REST API çağrıları için Retrofit arayüzü.
// English: Retrofit interface for Gmail REST API calls.
interface GmailApi {

    // Turkish: Kullanıcının e-posta mesajlarını listeler.
    // English: Lists the email messages in the user's mailbox.
    @GET("gmail/v1/users/me/messages")
    suspend fun listMessages(
        @Query("q") query: String? = null, // Optional query string (e.g., "from:newsletter@example.com")
        @Query("labelIds") labelIds: List<String>? = null, // Optional list of label IDs
        @Query("maxResults") maxResults: Int = 100, // Max 500
        @Query("pageToken") pageToken: String? = null
    ): GmailMessageListResponse

    // Turkish: Belirli bir e-posta mesajının tam içeriğini alır.
    // English: Gets the full content of a specific email message.
    @GET("gmail/v1/users/me/messages/{id}")
    suspend fun getMessage(
        @Path("id") messageId: String,
        @Query("format") format: String = "full" // "full", "metadata", "raw", "minimal"
    ): GmailMessage
}