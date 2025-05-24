// file: app/java/com/example/abonekaptanmobile/ui/screens/SubscriptionListScreen.kt
package com.example.abonekaptanmobile.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.abonekaptanmobile.R
import com.example.abonekaptanmobile.model.EmailAnalysisResult // Updated import
import com.example.abonekaptanmobile.model.SubscriptionStatus // Kept for FeedbackDialog
import com.example.abonekaptanmobile.ui.viewmodel.MainViewModel
//SimpleDateFormat and Date are no longer needed as item.date is a String
//import java.text.SimpleDateFormat
//import java.util.*

// Turkish: Abonelikleri listeleyen ekran.
// English: Screen that lists subscriptions.
@OptIn(ExperimentalFoundationApi::class) // ExperimentalFoundationApi might not be needed anymore
@Composable
fun SubscriptionListScreen(viewModel: MainViewModel) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var selectedSubscriptionItem by remember { mutableStateOf<EmailAnalysisResult?>(null) } // Updated type

    // Grouping logic removed
    // val groupedSubscriptions = subscriptions.groupBy { it.status }
    // val activeSubscriptions = groupedSubscriptions[SubscriptionStatus.ACTIVE] ?: emptyList()
    // val forgottenSubscriptions = groupedSubscriptions[SubscriptionStatus.FORGOTTEN] ?: emptyList()
    // val cancelledSubscriptions = groupedSubscriptions[SubscriptionStatus.CANCELLED] ?: emptyList()
    // val unknownSubscriptions = groupedSubscriptions[SubscriptionStatus.UNKNOWN] ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { viewModel.loadSubscriptions() }) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh_subscriptions))
            }
            Button(onClick = { viewModel.signOut() }) {
                Icon(Icons.Filled.ExitToApp, contentDescription = stringResource(R.string.sign_out))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.sign_out))
            }
        }

        if (subscriptions.isEmpty() && !viewModel.isLoading.collectAsState().value) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_subscriptions_found), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Sticky headers removed, using a single list
                items(subscriptions, key = { item -> item.company + item.date + item.email_index }) { item ->
                    SubscriptionCard(item = item, onFeedbackClick = {
                        selectedSubscriptionItem = it
                        showFeedbackDialog = true
                    })
                }
            }
        }
    }

    if (showFeedbackDialog && selectedSubscriptionItem != null) {
        // Assuming FeedbackDialog.kt will be updated separately to accept EmailAnalysisResult
        // For now, we adjust the call as per instructions.
        FeedbackDialog(
            item = selectedSubscriptionItem!!, // This is now EmailAnalysisResult
            onDismiss = { showFeedbackDialog = false },
            onSubmit = { _, _, feedbackLabel, note -> // serviceName and originalStatus from dialog are ignored
                viewModel.submitFeedback(
                    selectedSubscriptionItem!!.company, // Use company from EmailAnalysisResult
                    SubscriptionStatus.UNKNOWN, // Use fixed UNKNOWN status
                    feedbackLabel,
                    note
                )
                showFeedbackDialog = false
            }
        )
    }
}

// SubscriptionHeader is no longer needed as sticky headers are removed.
// @Composable
// fun SubscriptionHeader(title: String) { ... }

@Composable
fun SubscriptionCard(item: EmailAnalysisResult, onFeedbackClick: (EmailAnalysisResult) -> Unit) {
    // dateFormatter removed as item.date is a String

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.company, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            // Email count removed as it's not in EmailAnalysisResult
            // Text("${stringResource(R.string.email_count)}: ${item.emailCount}", style = MaterialTheme.typography.bodyMedium)
            
            // Using hardcoded string "Olay Tarihi:" as R.string.event_date might not be available
            Text("Olay Tarihi: ${item.date}", style = MaterialTheme.typography.bodyMedium)
            
            if (item.action == "cancel") {
                // Using hardcoded string "İptal Tarihi:" as R.string.cancellation_date_label might not be available
                Text("İptal Tarihi: ${item.date}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Text("Aksiyon: ${item.action}", style = MaterialTheme.typography.bodySmall)
            Text("Veritabanı Op: ${item.database_op}", style = MaterialTheme.typography.bodySmall)
            Text("Güven Skoru: ${String.format("%.2f", item.confidence)}", style = MaterialTheme.typography.bodySmall)


            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onFeedbackClick(item) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.is_this_wrong))
            }
        }
    }
}