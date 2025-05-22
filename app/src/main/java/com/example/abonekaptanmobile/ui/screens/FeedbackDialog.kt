// file: app/java/com/example/abonekaptanmobile/ui/screens/FeedbackDialog.kt
package com.example.abonekaptanmobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.abonekaptanmobile.R
import com.example.abonekaptanmobile.model.SubscriptionItem
import com.example.abonekaptanmobile.model.SubscriptionStatus

// Turkish: Kullanıcı geri bildirimini almak için diyalog.
// English: Dialog for collecting user feedback.
@Composable
fun FeedbackDialog(
    item: SubscriptionItem,
    onDismiss: () -> Unit,
    onSubmit: (serviceName: String, originalStatus: SubscriptionStatus, feedbackLabel: String, note: String?) -> Unit
) {
    var selectedReason by remember { mutableStateOf("") }
    var otherReasonText by remember { mutableStateOf("") }

    val feedbackOptions = listOf(
        stringResource(R.string.feedback_option_is_subscription_active) to "IsActive", // Aslında bir abonelik ve aktif
        stringResource(R.string.feedback_option_is_subscription_forgotten) to "IsForgotten", // Aslında bir abonelik ve unutulmuş
        stringResource(R.string.feedback_option_is_cancelled) to "IsCancelled", // Bu zaten iptal edilmişti
        stringResource(R.string.feedback_option_not_a_subscription) to "NotSubscription", // Bu bir abonelik değil
        stringResource(R.string.feedback_option_other) to "Other"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feedback_dialog_title, item.serviceName)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.current_status_is, item.status.name))
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.why_is_this_wrong))

                feedbackOptions.forEach { (displayText, value) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (selectedReason == value),
                                onClick = { selectedReason = value }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedReason == value),
                            onClick = { selectedReason = value }
                        )
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                if (selectedReason == "Other") {
                    OutlinedTextField(
                        value = otherReasonText,
                        onValueChange = { otherReasonText = it },
                        label = { Text(stringResource(R.string.feedback_note_other)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val feedbackLabel = if (selectedReason == "Other") "Other" else selectedReason
                    val note = if (selectedReason == "Other") otherReasonText.ifBlank { null } else null
                    if (feedbackLabel.isNotBlank()) {
                        onSubmit(item.serviceName, item.status, feedbackLabel, note)
                    }
                },
                enabled = selectedReason.isNotBlank() && (selectedReason != "Other" || otherReasonText.isNotBlank())
            ) {
                Text(stringResource(R.string.submit_feedback))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}