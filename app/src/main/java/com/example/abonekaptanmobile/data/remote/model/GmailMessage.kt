// file: app/java/com/example/abonekaptanmobile/data/remote/model/GmailMessage.kt
package com.example.abonekaptanmobile.data.remote.model

import com.google.gson.annotations.SerializedName

// Turkish: Gmail API'sinden gelen mesaj listesi yanıtını temsil eder.
// English: Represents the message list response from Gmail API.
data class GmailMessageListResponse(
    @SerializedName("messages") val messages: List<MessageId>?,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("resultSizeEstimate") val resultSizeEstimate: Int
)

data class MessageId(
    @SerializedName("id") val id: String,
    @SerializedName("threadId") val threadId: String
)

// Turkish: Gmail API'sinden gelen tek bir e-posta mesajını temsil eder.
// English: Represents a single email message from Gmail API.
data class GmailMessage(
    @SerializedName("id") val id: String,
    @SerializedName("threadId") val threadId: String,
    @SerializedName("labelIds") val labelIds: List<String>?,
    @SerializedName("snippet") val snippet: String?,
    @SerializedName("payload") val payload: MessagePayload?,
    @SerializedName("internalDate") val internalDate: String?, // Unix timestamp in milliseconds as a String
    @SerializedName("historyId") val historyId: String?,
    @SerializedName("sizeEstimate") val sizeEstimate: Int?
)

data class MessagePayload(
    @SerializedName("partId") val partId: String?,
    @SerializedName("mimeType") val mimeType: String?,
    @SerializedName("filename") val filename: String?,
    @SerializedName("headers") val headers: List<MessageHeader>?,
    @SerializedName("body") val body: MessagePartBody?,
    @SerializedName("parts") val parts: List<MessagePayload>?
)

data class MessageHeader(
    @SerializedName("name") val name: String,
    @SerializedName("value") val value: String
)

data class MessagePartBody(
    @SerializedName("attachmentId") val attachmentId: String?,
    @SerializedName("size") val size: Int?,
    @SerializedName("data") val data: String? // Base64 encoded string
)