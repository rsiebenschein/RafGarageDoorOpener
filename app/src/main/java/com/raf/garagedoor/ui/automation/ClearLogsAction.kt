package com.raf.garagedoor.ui.automation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.raf.garagedoor.R

/** Top-bar button that clears both logs after a confirmation, so test evidence is never wiped by a stray tap. */
@Composable
fun ClearLogsAction(onConfirm: () -> Unit) {
    var isConfirming by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { isConfirming = true }) {
        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.diagnostics_clear))
    }
    if (isConfirming) {
        ClearLogsDialog(
            onConfirm = {
                isConfirming = false
                onConfirm()
            },
            onDismiss = { isConfirming = false },
        )
    }
}

@Composable
private fun ClearLogsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_logs_title)) },
        text = { Text(stringResource(R.string.clear_logs_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.clear_logs_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.clear_logs_keep)) } },
    )
}
