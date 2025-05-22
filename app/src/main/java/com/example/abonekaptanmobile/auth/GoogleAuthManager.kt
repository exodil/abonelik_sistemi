// file: app/java/com/example/abonekaptanmobile/auth/GoogleAuthManager.kt
package com.example.abonekaptanmobile.auth

import android.content.Context
import android.content.Intent
import android.util.Log // Loglama için eklendi
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.gmail.GmailScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(@ApplicationContext private val context: Context) {

    private var currentAccount: GoogleSignInAccount? = null
    private lateinit var credential: GoogleAccountCredential

    private val gso: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(GmailScopes.GMAIL_READONLY))
        .build()

    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    init {
        currentAccount = GoogleSignIn.getLastSignedInAccount(context)
        Log.d("GoogleAuthManager", "Init - Last signed in account: ${currentAccount?.email}") // YENİ LOG
        initializeCredential()
    }

    private fun initializeCredential() {
        credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(GmailScopes.GMAIL_READONLY)
        )
        currentAccount?.account?.let { account ->
            Log.d("GoogleAuthManager", "Initializing credential with account: ${account.name}") // YENİ LOG
            credential.selectedAccount = account
        }
    }

    fun signIn(signInLauncher: ActivityResultLauncher<Intent>) {
        Log.d("GoogleAuthManager", "signIn called.") // YENİ LOG
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    fun signOut(): com.google.android.gms.tasks.Task<Void> {
        Log.d("GoogleAuthManager", "signOut called.") // YENİ LOG
        val signOutTask = googleSignInClient.signOut()
        signOutTask.addOnCompleteListener {
            Log.d("GoogleAuthManager", "Sign out complete. Clearing current account and credential.") // YENİ LOG
            currentAccount = null
            credential.selectedAccount = null
        }
        return signOutTask
    }

    fun getCurrentAccount(): GoogleSignInAccount? {
        Log.d("GoogleAuthManager", "getCurrentAccount - Returning: ${currentAccount?.email}") // YENİ LOG
        return currentAccount
    }

    fun updateCurrentAccount(account: GoogleSignInAccount?) {
        Log.d("GoogleAuthManager", "updateCurrentAccount - New account: ${account?.email}") // YENİ LOG
        currentAccount = account
        credential.selectedAccount = account?.account
        if (account == null) {
            Log.d("GoogleAuthManager", "Credential account cleared due to null account update.")
        } else {
            Log.d("GoogleAuthManager", "Credential account updated to: ${credential.selectedAccountName}")
        }
    }

    fun getCredential(): GoogleAccountCredential {
        // Eğer currentAccount null ise ve credential'da bir hesap seçili değilse,
        // bu durum bir sorun teşkil edebilir. initializeCredential'ın doğru çalışması önemli.
        if (credential.selectedAccountName == null && currentAccount != null) {
            Log.w("GoogleAuthManager", "getCredential - Credential has no selected account, but currentAccount exists. Re-initializing.")
            initializeCredential() // Güvenlik için tekrar initialize etmeyi dene
        }
        Log.d("GoogleAuthManager", "getCredential - Returning credential for account: ${credential.selectedAccountName}") // YENİ LOG
        return credential
    }
}