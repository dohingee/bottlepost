package com.bottlepost.app.ui.thread

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.bottlepost.app.data.local.awaitingReply
import com.bottlepost.app.ui.common.BottleButton
import com.bottlepost.app.ui.common.DriftingBottleScene
import com.bottlepost.app.ui.common.PaperSheet
import com.bottlepost.app.ui.common.Sea
import com.bottlepost.app.ui.common.SeaBackdrop
import com.bottlepost.app.ui.common.SeaTopBar
import com.bottlepost.app.ui.common.ruledLines
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@Composable
fun ThreadScreen(container: AppContainer, threadId: String, onBack: () -> Unit) {
    val vm: ThreadViewModel = viewModel(key = threadId) { ThreadViewModel(container, threadId) }
    val thread by vm.thread.collectAsStateWithLifecycle()
    val letters by vm.letters.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val closed = thread?.closed == true
    // Letters alternate: after writing, the reply box gives way to the drifting bottle.
    val waiting = !closed && letters.awaitingReply()

    LaunchedEffect(letters.size) { if (letters.isNotEmpty()) listState.animateScrollToItem(letters.lastIndex) }
    LaunchedEffect(ui.toast, ui.leave) {
        ui.toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.toastShown()
        }
        if (ui.leave) onBack()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            SeaTopBar(
                title = {
                    val t = thread
                    val name = t?.partnerNickname?.ifBlank { null } ?: stringResource(R.string.stranger)
                    val gender = t?.partnerGender?.takeIf { it.isNotBlank() }?.let { " · ${genderText(it)}" }.orEmpty()
                    Text(name + gender, fontFamily = FontFamily.Serif)
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!closed) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_leave)) }, onClick = { menu = false; confirmLeave = true })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_delete_thread)) }, onClick = { menu = false; confirmDelete = true })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_report)) }, onClick = { menu = false; showReport = true })
                        if (!closed) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_block)) }, onClick = { menu = false; confirmBlock = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        SeaBackdrop(Modifier.fillMaxSize()) {
            Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
                if (waiting) WaitingBanner()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(letters, key = { it.deliveryId }) { letter ->
                        LetterCard(letter, ui.translations[letter.deliveryId]) { vm.toggleTranslation(letter) }
                    }
                }
                ui.error?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                when {
                    closed -> ClosedFooter(partnerLeft = thread?.partnerLeft == true) { confirmDelete = true }
                    waiting -> {}
                    else -> ReplyBox(ui.reply, ui.sending, vm::onReplyChange, vm::sendReply)
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
    if (confirmLeave) LeaveThreadDialog(onDismiss = { confirmLeave = false }) {
        confirmLeave = false
        vm.leave()
    }
    if (confirmDelete) thread?.let { t ->
        DeleteThreadDialogs(t, onDismiss = { confirmDelete = false }) {
            confirmDelete = false
            vm.delete()
        }
    }
    if (showReport) ReportDialog(onDismiss = { showReport = false }) { reason, note ->
        showReport = false
        vm.report(reason, note)
    }
}

@Composable
internal fun WaitingBanner() {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DriftingBottleScene()
        Text(
            stringResource(R.string.waiting_reply),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            stringResource(R.string.waiting_reply_hint),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
internal fun ClosedFooter(partnerLeft: Boolean, onDelete: () -> Unit) {
    PaperSheet(Modifier.fillMaxWidth().padding(12.dp), tinted = true) {
        Text(
            stringResource(if (partnerLeft) R.string.partner_left else R.string.thread_closed),
            fontFamily = FontFamily.Serif,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        BottleButton(
            stringResource(R.string.action_delete_thread),
            onClick = onDelete,
            icon = Icons.Filled.Delete,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
internal fun ReplyBox(text: String, sending: Boolean, onChange: (String) -> Unit, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
        PaperSheet(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
            BasicTextField(
                value = text,
                onValueChange = onChange,
                textStyle = LetterTextStyle.copy(color = Sea.InkText),
                cursorBrush = SolidColor(Sea.Deep),
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().ruledLines(LetterTextStyle.lineHeight),
                decorationBox = { inner ->
                    if (text.isEmpty()) Text(stringResource(R.string.reply_placeholder), style = LetterTextStyle, color = Sea.InkText.copy(alpha = 0.45f))
                    inner()
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = !sending,
            modifier = Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Sea.Deep, Sea.Teal))),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.compose_send), tint = Sea.Paper)
        }
    }
}

@Composable
internal fun LetterCard(letter: LetterEntity, translation: Translation?, onTranslate: () -> Unit) {
    PaperSheet(
        tinted = letter.outgoing,
        modifier = Modifier.fillMaxWidth().padding(start = if (letter.outgoing) 28.dp else 0.dp, end = if (letter.outgoing) 0.dp else 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (letter.outgoing) R.string.letter_from_me else R.string.letter_from_them),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (letter.outgoing) Sea.Teal else Sea.Wax,
                modifier = Modifier.weight(1f),
            )
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(letter.sentAt)),
                style = MaterialTheme.typography.labelSmall,
                color = Sea.InkText.copy(alpha = 0.6f),
            )
        }
        Text(letter.body, style = LetterTextStyle, modifier = Modifier.fillMaxWidth().ruledLines(LetterTextStyle.lineHeight))
        when (translation) {
            is Translation.Shown -> Text(translation.text, style = LetterTextStyle, color = Sea.Teal)
            is Translation.Error -> Text(stringResource(translation.message), style = MaterialTheme.typography.bodySmall)
            Translation.Loading -> Text(stringResource(R.string.translate_loading), style = MaterialTheme.typography.bodySmall)
            null -> {}
        }
        if (!letter.outgoing) {
            TextButton(onClick = onTranslate, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(if (translation == null) R.string.action_translate else R.string.action_show_original), color = Sea.Deep)
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
