package com.example.abonekaptanmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
// import androidx.compose.runtime.saveable.rememberSaveable // Not used in this change
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.abonekaptanmobile.ui.components.HorizontalProgressBar
import com.example.abonekaptanmobile.ui.screens.SignInScreen
import com.example.abonekaptanmobile.ui.screens.SubscriptionListScreen
import com.example.abonekaptanmobile.ui.theme.AboneKaptanMobileTheme
import com.example.abonekaptanmobile.ui.viewmodel.MainViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AboneKaptanMobileTheme {
                val viewModel: MainViewModel = hiltViewModel()
                val isSignedIn by viewModel.isSignedIn.collectAsState()
                val isLoading by viewModel.isLoading.collectAsState()
                val isSigningIn by viewModel.isSigningIn.collectAsState()
                val error by viewModel.error.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                // States for the HorizontalProgressBar
                val progressPercentage by viewModel.progressPercentage.collectAsState()
                val progressMessage by viewModel.progressMessage.collectAsState()
                val estimatedTimeRemaining by viewModel.estimatedTimeRemaining.collectAsState()


                val signInLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    viewModel.handleSignInResult(task)
                }

                LaunchedEffect(error) {
                    error?.let {
                        snackbarHostState.showSnackbar(
                            message = it,
                            duration = SnackbarDuration.Long
                        )
                        viewModel.clearError() // Hata gösterildikten sonra temizle
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(id = R.string.app_name)) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when {
                            isLoading -> { // Main loading state for fetching/classifying
                                HorizontalProgressBar(
                                    progress = progressPercentage / 100f,
                                    progressText = progressMessage ?: "Yükleniyor...",
                                    estimatedTimeRemainingText = estimatedTimeRemaining
                                )
                            }
                            isSigningIn -> { // Distinct, typically shorter, sign-in phase
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            isSignedIn -> {
                                SubscriptionListScreen(viewModel = viewModel)
                            }
                            else -> { // Not loading, not signing in, not signed in -> Show SignInScreen
                                SignInScreen(
                                    onSignInClick = {
                                        viewModel.startSignIn(signInLauncher)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}