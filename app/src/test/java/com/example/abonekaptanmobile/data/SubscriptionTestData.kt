package com.example.abonekaptanmobile.data

data class TestEmail(
    val id: String,
    val from: String,
    val subject: String,
    val bodySnippet: String,
    val expectedService: String,
    val expectedLabel: String, // "paid_subscription_event", "paid_subscription_cancellation", "other"
    val date: Long
)

val sampleTestEmails = listOf(
    TestEmail(
        id = "email1",
        from = "Netflix <billing@netflix.com>",
        subject = "Your Netflix bill is available",
        bodySnippet = "Hi, your monthly Netflix bill is now available. Amount: $15.99.",
        expectedService = "Netflix",
        expectedLabel = "paid_subscription_event",
        date = 1672531200000L // Jan 1, 2023
    ),
    TestEmail(
        id = "email2",
        from = "Spotify <noreply@spotify.com>",
        subject = "Confirmation of your Spotify Premium cancellation",
        bodySnippet = "We've processed your request to cancel Spotify Premium. Your subscription will end on Feb 1, 2023.",
        expectedService = "Spotify",
        expectedLabel = "paid_subscription_cancellation",
        date = 1672617600000L // Jan 2, 2023
    ),
    TestEmail(
        id = "email3",
        from = "YouTube Premium <support@youtube.com>",
        subject = "Welcome to YouTube Premium!",
        bodySnippet = "Thanks for subscribing to YouTube Premium! Enjoy ad-free videos and more.",
        expectedService = "YouTube Premium",
        expectedLabel = "paid_subscription_event",
        date = 1672704000000L // Jan 3, 2023
    ),
    TestEmail(
        id = "email4",
        from = "Amazon Prime <cs@amazon.com>",
        subject = "Your Prime Membership Renewal",
        bodySnippet = "Your Amazon Prime membership has been renewed for another year.",
        expectedService = "Amazon Prime",
        expectedLabel = "paid_subscription_event",
        date = 1672790400000L // Jan 4, 2023
    ),
    TestEmail(
        id = "email5",
        from = "Trendyol <kampanya@trendyol.com>",
        subject = "Great deals just for you!",
        bodySnippet = "Don't miss out on our special spring sale. Up to 50% off!",
        expectedService = "Trendyol", // Or "Other" / "Unknown" if not considered a subscription service
        expectedLabel = "other",
        date = 1672876800000L // Jan 5, 2023
    ),
    TestEmail(
        id = "email6",
        from = "LinkedIn <notifications@linkedin.com>",
        subject = "You have new job suggestions",
        bodySnippet = "We found new jobs that match your profile. Apply now!",
        expectedService = "LinkedIn", // Or "Other" / "Unknown"
        expectedLabel = "other",
        date = 1672963200000L // Jan 6, 2023
    ),
    TestEmail(
        id = "email7",
        from = "Adobe Creative Cloud <message@adobe.com>",
        subject = "Your Creative Cloud trial is ending soon",
        bodySnippet = "Your trial for Adobe Photoshop is ending in 3 days. Upgrade to a full membership.",
        expectedService = "Adobe Creative Cloud", // Or "Adobe Photoshop"
        expectedLabel = "other", // Could be ambiguous, might be a pre-subscription email
        date = 1673049600000L // Jan 7, 2023
    ),
    TestEmail(
        id = "email8",
        from = "Disney+ <updates@disneyplus.com>",
        subject = "Regarding your Disney+ account",
        bodySnippet = "We are updating our terms of service. Please review them at your convenience. Your subscription continues as usual.",
        expectedService = "Disney+",
        expectedLabel = "other", // Not a direct lifecycle event, but related to an active subscription
        date = 1673136000000L // Jan 8, 2023
    ),
     TestEmail(
        id = "email9",
        from = "Exxen <destek@exxen.com>",
        subject = "Exxen Üyeliğiniz İptal Edildi",
        bodySnippet = "Merhaba, Exxen spor paketi üyeliğiniz isteğiniz üzerine iptal edilmiştir.",
        expectedService = "Exxen",
        expectedLabel = "paid_subscription_cancellation",
        date = 1673222400000L // Jan 9, 2023
    ),
    TestEmail(
        id = "email10",
        from = "notifications@example-random.com",
        subject = "Payment received for your order #12345",
        bodySnippet = "We have received your payment for order #12345. This could be a subscription.",
        expectedService = "Unknown Service", // Ambiguous
        expectedLabel = "paid_subscription_event", // Ambiguous, but leaning towards payment
        date = 1673308800000L // Jan 10, 2023
    )
)
