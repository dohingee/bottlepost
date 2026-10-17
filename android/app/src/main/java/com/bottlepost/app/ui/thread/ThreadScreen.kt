package com.bottlepost.app.ui.thread

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bottlepost.app.AppContainer
import com.bottlepost.app.R
import com.bottlepost.app.data.local.LetterEntity
import com.bottlepost.app.ui.common.genderText
import com.bottlepost.app.ui.theme.LetterTextStyle
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(container: AppContainer, threadId: String, onBack: () -> Unit) {
    val vm: ThreadViewModel = viewModel(key = threadId) { ThreadViewModel(container, threadId) }
    val thread by vm.thread.collectAsStateWithLifecycle()
    val letters by vm.letters.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(letters.size) { if (letters.isNotEmpty()) listState.animateScrollToItem(letters.lastIndex) }
    LaunchedEffect(ui.toast) {
        ui.toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.toastShown()
        }
        if (ui.leave) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val t = thread
                    val name = t?.partnerNickname?.ifBlank { null } ?: stringResource(R.string.stranger)
                    val gender = t?.partnerGender?.takeIf { it.isNotBlank() }?.let { " · ${genderText(it)}" }.orEmpty()
                    Text(name + gender)
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_report)) }, onClick = { menu = false; showReport = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_block)) }, onClick = { menu = false; confirmBlock = true })
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(letters, key = { it.deliveryId }) { letter ->
                    LetterCard(letter, ui.translations[letter.deliveryId]) { vm.toggleTranslation(letter) }
                }
            }
            HorizontalDivider()
            if (thread?.closed == true) {
                Text(stringResource(R.string.thread_closed), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            } else {
                ui.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = ui.reply,
                        onValueChange = vm::onReplyChange,
                        placeholder = { Text(stringResource(R.string.reply_placeholder)) },
                        textStyle = LetterTextStyle,
                        maxLines = 6,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = vm::sendReply, enabled = !ui.sending) {
                        Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.compose_send))
                    }
                }
            }
        }
    }

    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text(stringResource(R.string.block_title)) },
            text = { Text(stringResource(R.string.block_body)) },
            confirmButton = { TextButton(onClick = { confirmBlock = false; vm.block() }) { Text(stringResource(R.string.action_block)) } },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showReport) ReportDialog(onDismiss = { showReport = false }) { reason, note ->
        showReport = false
        vm.report(reason, note)
    }
}

@Composable
private fun LetterCard(letter: LetterEntity, translation: Translation?, onTranslate: () -> Unit) {
    val colors = if (letter.outgoing) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    Card(
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = if (letter.outgoing) 0.dp else 2.dp),
        modifier = Modifier.fillMaxWidth().padding(start = if (letter.outgoing) 32.dp else 0.dp, end = if (letter.outgoing) 0.dp else 32.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (letter.outgoing) R.string.letter_from_me else R.string.letter_from_them) + " · " +
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(letter.sentAt)),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(letter.body, style = LetterTextStyle)
            when (translation) {
                is Translation.Shown -> Text(translation.text, style = LetterTextStyle, color = MaterialTheme.colorScheme.secondary)
                is Translation.Error -> Text(stringResource(translation.message), style = MaterialTheme.typography.bodySmall)
                Translation.Loading -> Text(stringResource(R.string.translate_loading), style = MaterialTheme.typography.bodySmall)
                null -> {}
            }
            if (!letter.outgoing) {
                TextButton(onClick = onTranslate, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(if (translation == null) R.string.action_translate else R.string.action_show_original))
                }
            }
        }
    }
}

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var reason by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                REPORT_REASONS.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = reason == r, onClick = { reason = r }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(stringResource(reasonLabel(r)), modifier = Modifier.clickable { reason = r })
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    placeholder = { Text(stringResource(R.string.report_note_hint)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                // Consent: reporting is the only way letter text ever reaches our server.
                Text(stringResource(R.string.report_consent), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { reason?.let { onSubmit(it, note) } }, enabled = reason != null) { Text(stringResource(R.string.action_report)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun reasonLabel(reason: String): Int = when (reason) {
    "spam" -> R.string.reason_spam
    "scam" -> R.string.reason_scam
    "sexual" -> R.string.reason_sexual
    "harassment" -> R.string.reason_harassment
    "minor" -> R.string.reason_minor
    "hate" -> R.string.reason_hate
    "self_harm" -> R.string.reason_self_harm
    else -> R.string.reason_other
}
