// file: app/java/com/example/abonekaptanmobile/model/RawEmail.kt
package com.example.abonekaptanmobile.model

data class RawEmail(
    val id: String,
    val threadId: String,
    val from: String,
    val to: List<String>,
    val subject: String,
    val snippet: String, // Gmail API'den gelen snippet
    val bodyPlainText: String,
    val bodyHtml: String?,
    val date: Long,
    val labels: List<String> = emptyList(),
    var bodySnippet: String? = null // <<--- BU SATIRI EKLEYİN (veya GmailRepository'de doldurun)
)