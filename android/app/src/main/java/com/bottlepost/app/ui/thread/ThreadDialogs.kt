package com.bottlepost.app.ui.thread

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.bottlepost.app.R
import com.bottlepost.app.data.local.ThreadEntity

/**
 * Asks before deleting a conversation. If the other person is still in it, asks a second time,
 * because deleting then also leaves it on their side.
 */
@Composable
fun DeleteThreadDialogs(thread: ThreadEntity, onDismiss: () -> Unit, onDelete: () -> Unit) {
    var partnerStillHere by remember(thread.threadId) { mutableStateOf(false) }
    if (!partnerStillHere) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.delete_thread_title)) },
            text = { Text(stringResource(R.string.delete_thread_body)) },
            confirmButton = {
                TextButton(onClick = { if (thread.closed) onDelete() else partnerStillHere = true }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.delete_active_title)) },
            text = { Text(stringResource(R.string.delete_active_body)) },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun LeaveThreadDialog(onDismiss: () -> Unit, onLeave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.leave_title)) },
        text = { Text(stringResource(R.string.leave_body)) },
        confirmButton = { TextButton(onClick = onLeave) { Text(stringResource(R.string.action_leave)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
