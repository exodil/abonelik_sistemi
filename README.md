# AboneKaptan Mobile - Android Subscription Manager

**AboneKaptan Mobile** is an Android application designed to help users manage their digital subscriptions by intelligently scanning their Gmail inbox. It aims to identify active, inactive (forgotten), and cancelled subscriptions, providing a clear overview and empowering users to take control of their recurring expenses.

## Core Features

*   **Gmail Integration (OAuth 2.0):** Securely connects to the user's Gmail account using OAuth 2.0 with `gmail.readonly` scope to read email content.
*   **Subscription Detection:** Analyzes email subjects, senders, and content snippets to identify potential subscription-related emails (e.g., welcome emails, invoices, renewal notices, cancellation confirmations).
*   **Status Classification:** Categorizes identified subscriptions into:
    *   **Active:** Currently ongoing subscriptions.
    *   **Forgotten (Inactive):** Subscriptions that haven't shown recent activity (e.g., no new emails for 90 days).
    *   **Cancelled:** Subscriptions પાણીcifically identified as terminated.
*   **Pattern-Based Learning:**
    *   Utilizes a local Room database (`SubscriptionPatternEntity`) to store patterns (regex, keywords, sender domains) associated with subscription services.
    *   Initial set of common subscription service patterns provided.
*   **Community-Driven Feedback & Learning (Core Goal - In Progress):**
    *   Users can provide feedback on the app's classifications (e.g., "This is not a subscription," "This is actually active").
    *   `FeedbackEntity` stores user corrections.
    *   A `ProcessFeedbackWorker` (WorkManager) periodically processes this feedback to:
        *   Refine existing patterns (adjusting approval/rejection counts).
        *   Identify new patterns or mark senders as non-subscription based on collective user input.
        *   Promote reliable patterns to "community-approved" status for higher classification accuracy.
*   **User Interface (Jetpack Compose):** A modern UI built with Jetpack Compose to display subscription lists and facilitate feedback.
*   **Local Data Persistence (Room):** Stores subscription patterns and user feedback locally.

## Technical Stack

*   **Language:** Kotlin
*   **Architecture:** MVVM (ViewModel + LiveData/Flow)
*   **UI:** Jetpack Compose
*   **Asynchronous Programming:** Kotlin Coroutines & Flow
*   **Dependency Injection:** Hilt
*   **Networking:** Retrofit, OkHttp (for Gmail API)
*   **Database:** Room (SQLite)
*   **Background Processing:** WorkManager

## Project Goals & Future Enhancements

*   **Improve Classification Accuracy:** Continuously refine the pattern matching logic and the effectiveness of the `SubscriptionClassifier`.
*   **Advanced Heuristics:** Develop more sophisticated heuristics for identifying subscriptions that don't match predefined patterns.
*   **Machine Learning Integration (Optional - Future):**
    *   Explore using local ML models (e.g., TF-IDF + Logistic Regression, or a TFLite model) trained on user feedback and email content to enhance classification.
    *   Implement NER (Named Entity Recognition) to extract service names, prices, and renewal dates more accurately.
*   **Actionable Insights:** Provide users with options to directly visit unsubscribe pages or set reminders for renewals.
*   **Multi-Account Support:** Allow users to connect and manage subscriptions from multiple Gmail accounts.
*   **Dark Theme & UI Polish.**

## Setup & Configuration

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/exodil/AboneKaptanMobile.git
    ```
2.  **Google Cloud Project Setup:**
    *   Create a new project on [Google Cloud Console](https://console.cloud.google.com/).
    *   Enable the **Gmail API** for your project.
    *   Create **OAuth 2.0 Client IDs** for an Android application:
        *   Go to "APIs & Services" > "Credentials".
        *   Click "Create Credentials" > "OAuth client ID".
        *   Select "Android" as the application type.
        *   Enter your application's package name (e.g., `com.example.abonekaptanmobile`).
        *   Generate your **SHA-1 signing certificate fingerprint** for both debug and release builds and add them.
            *   For debug: `./gradlew signingReport`
        *   Download the `credentials.json` file (or note the Client ID, though `credentials.json` is not directly used in this client-side OAuth flow, it's good practice for other Google services).
    *   Configure the **OAuth consent screen**:
        *   Provide an application name, user support email, and developer contact information.
        *   **Add Scopes:** Add the `https://www.googleapis.com/auth/gmail.readonly` scope.
        *   **Test Users:** While in "Testing" publishing status, add Google accounts that will be allowed to use the app.
        *   Provide a link to your app's **Privacy Policy** (required for sensitive scopes).
3.  **Android Studio:**
    *   Open the project in Android Studio.
    *   The project uses Hilt for dependency injection, so no further manual DI setup should be needed.
    *   Build and run the application.

## How It Works (Data Flow)

1.  User signs in with Google via `GoogleAuthManager`.
2.  `MainViewModel` triggers `GmailRepository` to fetch emails.
3.  `GmailRepository` queries the Gmail API for relevant emails based on predefined queries (trusted senders, keywords).
4.  Fetched `RawEmail` data is passed to `SubscriptionClassifier`.
5.  `SubscriptionClassifier`:
    *   Filters out emails matching "non-subscription" patterns.
    *   Applies "reliable subscription" patterns (admin, default, community-approved) to identify likely subscriptions.
    *   (Optionally) Applies heuristics to remaining emails.
    *   Performs chronological analysis on emails grouped by service to determine the final status (Active, Forgotten, Cancelled) by looking for cancellation emails and recency of activity.
6.  The classified `SubscriptionItem` list is displayed in the UI via `MainViewModel`.
7.  User submits feedback (`FeedbackEntity`) on misclassifications.
8.  `ProcessFeedbackWorker` runs periodically:
    *   Reads pending feedback.
    *   Updates `approvedCount` and `rejectedCount` for `SubscriptionPatternEntity`.
    *   Adjusts `isSubscription` status of patterns.
    *   Promotes/demotes patterns to/from "community_approved"/"community_rejected" based on consensus.

## Contribution

Contributions, issues, and feature requests are welcome! Feel free to check [issues page](https://github.com/YOUR_USERNAME/AboneKaptanMobile/issues).

## License

[Specify your license here, e.g., MIT, Apache 2.0]