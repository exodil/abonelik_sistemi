// file: app/java/com/example/abonekaptanmobile/model/CancellationInfo.kt
package com.example.abonekaptanmobile.model

// Turkish: Tespit edilen bir iptal e-postası hakkındaki bilgileri içerir.
// English: Contains information about a detected cancellation email.
data class CancellationInfo(
    val serviceName: String, // İptal edilen servisin adı (tahmin edilen)
    val emailId: String,
    val cancellationDate: Long,
    val matchedKeyword: String // Hangi anahtar kelimeyle eşleştiği
)