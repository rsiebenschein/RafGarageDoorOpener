package com.raf.garagedoor.ui.automation

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raf.garagedoor.R
import com.raf.garagedoor.automation.DiagnosticEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DIAGNOSTIC_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d HH:mm:ss")

@Composable
fun DiagnosticsRoute(viewModel: AutomationViewModel, onBack: () -> Unit) {
    val entries by viewModel.diagnosticEntries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    DiagnosticsScreen(
        entries = entries,
        onBack = onBack,
        onShare = { shareDiagnostics(context, entries) },
        onClear = viewModel::clearLogs,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(entries: List<DiagnosticEntry>, onBack: () -> Unit, onShare: () -> Unit, onClear: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.diagnostics_share)) }
                    ClearLogsAction(onClear)
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.diagnostics_empty))
            }
        } else {
            DiagnosticsList(entries, Modifier.padding(padding))
        }
    }
}

@Composable
private fun DiagnosticsList(entries: List<DiagnosticEntry>, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(entries) { entry ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(formatTime(entry), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(entry.message, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider()
        }
    }
}

private fun formatTime(entry: DiagnosticEntry): String =
    DIAGNOSTIC_TIME_FORMAT.format(Instant.ofEpochMilli(entry.atEpochMillis).atZone(ZoneId.systemDefault()))

private fun shareDiagnostics(context: Context, entries: List<DiagnosticEntry>) {
    val text = entries.reversed().joinToString("\n") { "${formatTime(it)}  ${it.message}" }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}
