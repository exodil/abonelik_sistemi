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
import com.example.abonekaptanmobile.model.SubscriptionItem
import com.example.abonekaptanmobile.model.SubscriptionStatus
import com.example.abonekaptanmobile.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

// Turkish: Abonelikleri listeleyen ekran.
// English: Screen that lists subscriptions.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubscriptionListScreen(viewModel: MainViewModel) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var selectedSubscriptionItem by remember { mutableStateOf<SubscriptionItem?>(null) }

    val groupedSubscriptions = subscriptions.groupBy { it.status }
    val activeSubscriptions = groupedSubscriptions[SubscriptionStatus.ACTIVE] ?: emptyList()
    val forgottenSubscriptions = groupedSubscriptions[SubscriptionStatus.FORGOTTEN] ?: emptyList()
    val cancelledSubscriptions = groupedSubscriptions[SubscriptionStatus.CANCELLED] ?: emptyList()
    val unknownSubscriptions = groupedSubscriptions[SubscriptionStatus.UNKNOWN] ?: emptyList()


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
                if (activeSubscriptions.isNotEmpty()) {
                    stickyHeader { SubscriptionHeader(title = stringResource(R.string.active_subscriptions)) }
                    items(activeSubscriptions, key = { it.serviceName + it.lastEmailDate }) { item ->
                        SubscriptionCard(item = item, onFeedbackClick = {
                            selectedSubscriptionItem = it
                            showFeedbackDialog = true
                        })
                    }
                }

                if (forgottenSubscriptions.isNotEmpty()) {
                    stickyHeader { SubscriptionHeader(title = stringResource(R.string.forgotten_subscriptions)) }
                    items(forgottenSubscriptions, key = { it.serviceName + it.lastEmailDate }) { item ->
                        SubscriptionCard(item = item, onFeedbackClick = {
                            selectedSubscriptionItem = it
                            showFeedbackDialog = true
                        })
                    }
                }

                if (cancelledSubscriptions.isNotEmpty()) {
                    stickyHeader { SubscriptionHeader(title = stringResource(R.string.cancelled_subscriptions)) }
                    items(cancelledSubscriptions, key = { it.serviceName + it.lastEmailDate }) { item ->
                        SubscriptionCard(item = item, onFeedbackClick = {
                            selectedSubscriptionItem = it
                            showFeedbackDialog = true
                        })
                    }
                }
                if (unknownSubscriptions.isNotEmpty()) {
                    stickyHeader { SubscriptionHeader(title = stringResource(R.string.unknown_subscriptions)) }
                    items(unknownSubscriptions, key = { it.serviceName + it.lastEmailDate }) { item ->
                        SubscriptionCard(item = item, onFeedbackClick = {
                            selectedSubscriptionItem = it
                            showFeedbackDialog = true
                        })
                    }
                }
            }
        }
    }

    if (showFeedbackDialog && selectedSubscriptionItem != null) {
        FeedbackDialog(
            item = selectedSubscriptionItem!!,
            onDismiss = { showFeedbackDialog = false },
            onSubmit = { serviceName, originalStatus, feedbackLabel, note ->
                viewModel.submitFeedback(serviceName, originalStatus, feedbackLabel, note)
                showFeedbackDialog = false
            }
        )
    }
}

@Composable
fun SubscriptionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            .padding(vertical = 8.dp, horizontal = 16.dp)
    )
}

@Composable
fun SubscriptionCard(item: SubscriptionItem, onFeedbackClick: (SubscriptionItem) -> Unit) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.serviceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("${stringResource(R.string.email_count)}: ${item.emailCount}", style = MaterialTheme.typography.bodyMedium)
            Text("${stringResource(R.string.last_email_date)}: ${dateFormatter.format(Date(item.lastEmailDate))}", style = MaterialTheme.typography.bodyMedium)
            item.cancellationDate?.let {
                Text("${stringResource(R.string.cancellation_date)}: ${dateFormatter.format(Date(it))}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
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