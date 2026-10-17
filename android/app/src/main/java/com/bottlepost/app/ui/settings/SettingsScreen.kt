package com.bottlepost.app.ui.settings

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.bottlepost.app.ui.common.ChoiceChips
import com.bottlepost.app.ui.common.Links
import com.bottlepost.app.ui.common.SUPPORTED_LANGS
import com.bottlepost.app.ui.common.emailSupport
import com.bottlepost.app.ui.common.findActivity
import com.bottlepost.app.ui.common.genderLabel
import com.bottlepost.app.ui.common.langLabel
import com.bottlepost.app.ui.common.openUrl
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(container: AppContainer, onAccountDeleted: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container, app) }
    val profile by vm.profile.collectAsStateWithLifecycle(initialValue = null)
    val lastBackup by vm.lastBackupAt.collectAsStateWithLifecycle(initialValue = null)
    val ui by vm.ui.collectAsStateWithLifecycle()
    var consentForRestore by remember { mutableStateOf(false) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.onDriveConsent(it.data, consentForRestore)
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var showAppeal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is SettingsEvent.NeedsDriveConsent -> {
                    consentForRestore = e.forRestore
                    consent.launch(IntentSenderRequest.Builder(e.sender).build())
                }
                SettingsEvent.AccountDeleted -> onAccountDeleted()
            }
        }
    }

    val p = profile ?: return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ui.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.secondary) }

        Header(R.string.settings_profile)
        Text(stringResource(R.string.settings_my_gender, stringResource(genderLabel(p.gender))) + " · " + p.birthYear)
        var nickname by remember(p.nickname) { mutableStateOf(p.nickname) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(20) },
                label = { Text(stringResource(R.string.onboarding_nickname)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.setNickname(nickname) }, enabled = nickname != p.nickname && !ui.busy) {
                Text(stringResource(R.string.action_save))
            }
        }
        Text(stringResource(R.string.onboarding_receive_from), style = MaterialTheme.typography.titleSmall)
        ChoiceChips(listOf("ANY", "M", "F"), p.receiveFrom, { stringResource(genderLabel(it)) }, vm::setReceiveFrom)
        Text(stringResource(R.string.onboarding_language), style = MaterialTheme.typography.titleSmall)
        ChoiceChips(SUPPORTED_LANGS, p.lang, { stringResource(langLabel(it)) }, vm::setLanguage)

        HorizontalDivider()
        Header(R.string.settings_account)
        if (p.linked) {
            Text(stringResource(R.string.settings_google_linked))
            Text(
                lastBackup?.let { stringResource(R.string.backup_last, DateFormat.getDateTimeInstance().format(Date(it))) }
                    ?: stringResource(R.string.backup_never),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::backupNow, enabled = !ui.busy) { Text(stringResource(R.string.action_backup_now)) }
                OutlinedButton(onClick = vm::restore, enabled = !ui.busy) { Text(stringResource(R.string.action_restore)) }
            }
        } else {
            Text(stringResource(R.string.settings_google_explain), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { vm.linkGoogle(context.findActivity()) }, enabled = !ui.busy) {
                Text(stringResource(R.string.action_link_google))
            }
        }

        HorizontalDivider()
        Header(R.string.settings_help)
        TextButton(onClick = { context.openUrl(Links.TERMS) }) { Text(stringResource(R.string.settings_terms)) }
        TextButton(onClick = { context.openUrl(Links.PRIVACY) }) { Text(stringResource(R.string.settings_privacy)) }
        TextButton(onClick = { context.openUrl(Links.CHILD_SAFETY) }) { Text(stringResource(R.string.settings_child_safety)) }
        TextButton(onClick = { context.emailSupport("Bottlepost support") }) { Text(stringResource(R.string.settings_contact)) }
        if (p.status != "active") {
            TextButton(onClick = { showAppeal = true }) { Text(stringResource(R.string.settings_appeal)) }
        }

        HorizontalDivider()
        Button(
            onClick = { confirmDelete = true },
            enabled = !ui.busy,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_delete_account)) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteAccount() }) {
                    Text(stringResource(R.string.action_delete_account), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showAppeal) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAppeal = false },
            title = { Text(stringResource(R.string.settings_appeal)) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it.take(1000) }, minLines = 4, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { showAppeal = false; vm.appeal(text) }, enabled = text.trim().length >= 5) {
                    Text(stringResource(R.string.action_send))
                }
            },
            dismissButton = { TextButton(onClick = { showAppeal = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Header(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}
