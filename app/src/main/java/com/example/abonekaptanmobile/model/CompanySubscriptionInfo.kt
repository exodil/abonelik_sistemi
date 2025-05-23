package com.example.abonekaptanmobile.model

/**
 * Represents structured information about a company's subscription service.
 *
 * @property companyName The official name of the company or service (e.g., "Netflix").
 * @property category The general category the service falls into (e.g., "Medya/Video", "Eğitim").
 * @property serviceType A brief description of the type of service offered (e.g., "Film ve dizi akış hizmeti").
 */
data class CompanySubscriptionInfo(
    val companyName: String,
    val category: String,
    val serviceType: String
)
