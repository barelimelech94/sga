package com.bar.sga.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bar.sga.R

/** Yes/no dialog. It never hides itself: the caller clears its own state inside [onConfirm] and [onDismiss]. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, dismiss: String = stringResource(R.string.cancel)) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}
