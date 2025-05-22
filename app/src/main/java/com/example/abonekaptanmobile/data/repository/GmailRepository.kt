// file: app/java/com/example/abonekaptanmobile/data/repository/GmailRepository.kt
package com.example.abonekaptanmobile.data.repository

import android.util.Base64
import android.util.Log // Loglama için eklendi
import com.example.abonekaptanmobile.data.remote.GmailApi
import com.example.abonekaptanmobile.data.remote.model.GmailMessage
import com.example.abonekaptanmobile.data.remote.model.MessagePayload
// MessageHeader için importunuzu kontrol edin, eğer ayrı bir dosyadaysa:
// import com.example.abonekaptanmobile.data.remote.model.MessageHeader
import com.example.abonekaptanmobile.model.RawEmail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class GmailRepository @Inject constructor(private val gmailApi: GmailApi) {

    private val trustedSenderDomains = listOf(
        "netflix.com", "spotify.com", "youtube.com", "amazon.com", "primevideo.com",
        "disneyplus.com", "apple.com", "google.com", "microsoft.com", "adobe.com",
        "linkedin.com", "dropbox.com", "evernote.com", "medium.com", "patreon.com",
        "zoom.us", "slack.com", "github.com", "digitalocean.com", "vimeo.com",
        "exxen.com", "blutv.com", "gain.tv", "todtv.com.tr", "ssportplus.com", "getir.com",
        "yemeksepeti.com", "trendyol.com", "hepsiburada.com"
    )

    private val positiveKeywords = listOf(
        "abonelik", "subscription", "üyelik", "membership", "fatura", "invoice",
        "makbuz", "receipt", "yenileme", "renewal", "hoş geldiniz", "welcome",
        "hesabınız", "your account", "sipariş", "order confirmation", "kayıt oldunuz",
        "üyeliğiniz başladı", "planınız", "your plan", "ödeme", "payment"
    )

    private val negativeKeywords = listOf(
        "iş ilanı", "job posting", "reklam", "advertisement", "spam",
        "güvenlik uyarısı", "security alert", "parola sıfırlama", "password reset",
        "anket", "survey", "promosyon", "promotion", "davet", "invitation",
        "kargo takip", "shipping update", "teslimat", "delivery", "bildirim", "notification"
    )

    suspend fun fetchEmails(maxResultsPerQuery: Int = 50, maxTotalEmails: Int = 1000): List<RawEmail> = withContext(Dispatchers.IO) {
        val rawEmails = mutableListOf<RawEmail>()
        var nextPageToken: String? = null
        val processedMessageIds = mutableSetOf<String>()

        // TEST İÇİN BASİT SORGUDAN BAŞLAYALIM, SONRA ORİJİNALİNE DÖNEBİLİRİZ
        // val query = "in:inbox -in:spam -in:trash"
        // Log.d("GmailRepository", "Using query: $query")

        // Orijinal, daha karmaşık sorgu (testten sonra bunu açabilirsiniz):
        val domainQueryPart = trustedSenderDomains.joinToString(" OR ") { "from:$it" }
        val keywordQueryPart = positiveKeywords.joinToString(" OR ") // Tırnaksız daha fazla sonuç verir
        val query = "($domainQueryPart OR $keywordQueryPart) -in:spam -in:trash"
        Log.d("GmailRepository", "Using complex query: $query")

        try {
            var limitReachedInOutermostLoop = false
            do {
                if (rawEmails.size >= maxTotalEmails) {
                    Log.d("GmailRepository", "Max total emails limit reached ($maxTotalEmails). Stopping fetch.")
                    limitReachedInOutermostLoop = true
                    break
                }

                Log.d("GmailRepository", "Fetching message list. Page token: $nextPageToken, Current emails: ${rawEmails.size}")
                val response = gmailApi.listMessages(
                    query = query,
                    maxResults = maxResultsPerQuery.coerceAtMost(100),
                    pageToken = nextPageToken
                )
                Log.d("GmailRepository", "ListMessages API response received. Messages in response: ${response.messages?.size ?: 0}, NextPageToken: ${response.nextPageToken}")


                val messageReferences = response.messages
                if (messageReferences.isNullOrEmpty()) {
                    Log.d("GmailRepository", "No more message references found. Ending fetch loop.")
                    nextPageToken = null
                    break
                }

                val detailedMessagesFutures = messageReferences.mapNotNull { messageRef ->
                    if (processedMessageIds.add(messageRef.id)) {
                        async {
                            try {
                                Log.d("GmailRepository", "Fetching details for message ID: ${messageRef.id}")
                                gmailApi.getMessage(messageRef.id, format = "full")
                            } catch (e: Exception) {
                                Log.e("GmailRepository", "Error fetching message details for ${messageRef.id}: ${e.message}", e)
                                null
                            }
                        }
                    } else {
                        Log.d("GmailRepository", "Skipping already processed message ID: ${messageRef.id}")
                        null
                    }
                }

                val detailedMessages = detailedMessagesFutures.awaitAll().filterNotNull()
                Log.d("GmailRepository", "Fetched details for ${detailedMessages.size} messages in this batch.")

                for (detailedMessage in detailedMessages) {
                    if (rawEmails.size < maxTotalEmails) {
                        parseGmailMessage(detailedMessage)?.let { rawEmail ->
                            // Bu ön filtrelemeyi burada yapmak yerine, tüm e-postaları çekip
                            // SubscriptionClassifier'a bırakmak daha iyi olabilir.
                            // Şimdilik basit bir kontrolle devam edelim.
                            // if (isPotentiallySubscriptionRelated(rawEmail)) {
                            rawEmails.add(rawEmail)
                            // } else {
                            //     Log.d("GmailRepository", "Email (Subject: ${rawEmail.subject.take(30)}) not potentially subscription related, skipping.")
                            // }
                        }
                    } else {
                        Log.d("GmailRepository", "Max total emails limit reached within batch processing.")
                        limitReachedInOutermostLoop = true
                        break
                    }
                }

                if (limitReachedInOutermostLoop) {
                    nextPageToken = null
                } else {
                    nextPageToken = response.nextPageToken
                }

            } while (nextPageToken != null && !limitReachedInOutermostLoop)

        } catch (e: Exception) {
            Log.e("GmailRepository", "Error during email fetching process: ${e.message}", e)
            // Hata durumunda boş liste dönmek yerine, ViewModel'in hatayı yakalaması için
            // exception'ı tekrar fırlatmak daha iyi olabilir: throw e
        }
        Log.i("GmailRepository", "Finished fetching emails. Total raw emails collected: ${rawEmails.size}")
        return@withContext rawEmails
    }

    private fun parseGmailMessage(message: GmailMessage): RawEmail? {
        val headers = message.payload?.headers ?: return null

        val from = headers.find { it.name.equals("From", ignoreCase = true) }?.value ?: "Unknown Sender"
        val toHeaders = headers.filter { it.name.equals("To", ignoreCase = true) }.mapNotNull { it.value }
        val subject = headers.find { it.name.equals("Subject", ignoreCase = true) }?.value ?: "No Subject"
        val date = message.internalDate?.toLongOrNull() ?: System.currentTimeMillis()

        val bodyParts = extractTextParts(message.payload)
        val bodyPlainText = bodyParts.first
        val bodyHtml = bodyParts.second

        val snippetFromApi = message.snippet ?: ""
        val effectiveBodySnippet = (snippetFromApi.takeIf { it.isNotBlank() } ?: bodyPlainText).take(250)

        return RawEmail(
            id = message.id,
            threadId = message.threadId ?: message.id,
            from = from,
            to = toHeaders,
            subject = subject,
            snippet = snippetFromApi,
            bodyPlainText = bodyPlainText,
            bodyHtml = bodyHtml,
            date = date,
            labels = message.labelIds ?: emptyList(),
            bodySnippet = effectiveBodySnippet
        )
    }

    // Bu fonksiyon şimdilik fetchEmails içinde kullanılmıyor, tüm e-postalar çekiliyor.
    // Gerekirse ön filtreleme için tekrar aktif edilebilir.
    private fun isPotentiallySubscriptionRelated(email: RawEmail): Boolean {
        val contentToCheck = "${email.subject} ${email.bodySnippet ?: email.snippet ?: ""} ${email.from}".lowercase()
        if (negativeKeywords.any { contentToCheck.contains(it) }) {
            return false
        }
        val fromDomain = extractDomain(email.from)
        return positiveKeywords.any { contentToCheck.contains(it) } ||
                (fromDomain != null && trustedSenderDomains.any { trusted -> fromDomain.contains(trusted) })
    }

    private fun extractDomain(emailAddress: String): String? {
        val domainPart = emailAddress.substringAfter('@', "").substringBefore('>').trim().lowercase()
        return domainPart.ifEmpty { null }
    }

    private fun extractTextParts(payload: MessagePayload?): Pair<String, String?> {
        val plainTextBuilder = StringBuilder()
        var htmlText: String? = null

        fun findPartsRecursive(currentPayload: MessagePayload?) {
            if (currentPayload == null) return

            when (currentPayload.mimeType?.lowercase()) {
                "text/plain" -> {
                    currentPayload.body?.data?.let {
                        plainTextBuilder.append(decodeBase64(it)).append("\n")
                    }
                }
                "text/html" -> {
                    currentPayload.body?.data?.let {
                        if (htmlText == null) htmlText = decodeBase64(it)
                    }
                }
                "multipart/alternative", "multipart/mixed", "multipart/related", "multipart/report" -> {
                    currentPayload.parts?.forEach { part ->
                        findPartsRecursive(part)
                    }
                }
            }
        }

        findPartsRecursive(payload)
        return Pair(plainTextBuilder.toString().trim(), htmlText?.trim())
    }

    private fun decodeBase64(encodedString: String): String {
        return try {
            String(Base64.decode(encodedString, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            Log.e("GmailRepository", "Base64 decoding error: ${e.message}")
            ""
        }
    }
}