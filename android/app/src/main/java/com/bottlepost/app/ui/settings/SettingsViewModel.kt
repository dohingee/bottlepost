package com.bottlepost.app.ui.settings

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bottlepost.app.AppContainer
import com.bottlepost.app.R
import com.bottlepost.app.auth.GoogleResult
import com.bottlepost.app.backup.BackupWorker
import com.bottlepost.app.backup.DriveAccess
import com.bottlepost.app.ui.common.applyAppLanguage
import com.bottlepost.app.ui.common.errorMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUi(
    val busy: Boolean = false,
    @StringRes val message: Int? = null,
)

sealed interface SettingsEvent {
    data class NeedsDriveConsent(val sender: IntentSender, val forRestore: Boolean) : SettingsEvent
    data object AccountDeleted : SettingsEvent
}

class SettingsViewModel(private val c: AppContainer, private val app: android.app.Application) : ViewModel() {
    val profile = c.profiles.profile
    val lastBackupAt = c.profiles.lastBackupAt

    private val _ui = MutableStateFlow(SettingsUi())
    val ui: StateFlow<SettingsUi> = _ui
    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SettingsEvent> = _events

    private fun action(@StringRes success: Int? = null, block: suspend () -> Unit) {
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _ui.update { it.copy(busy = false, message = success ?: it.message) } }
                .onFailure { e -> _ui.update { it.copy(busy = false, message = errorMessage(e)) } }
        }
    }

    fun setNickname(v: String) = action(R.string.settings_saved) { c.profiles.update(mapOf("nickname" to v.trim())) }
    fun setReceiveFrom(v: String) = action(R.string.settings_saved) { c.profiles.update(mapOf("receiveFrom" to v)) }
    fun setLanguage(v: String) = action {
        c.profiles.update(mapOf("lang" to v))
        applyAppLanguage(v)
    }

    fun linkGoogle(activity: Activity) = action {
        when (c.auth.connectGoogle(activity, discardCurrent = { c.api.deleteAccount() })) {
            GoogleResult.Cancelled -> Unit
            GoogleResult.Linked -> {
                c.profiles.confirmGoogleLink()
                BackupWorker.schedule(app)
                withDrive(forRestore = false, ::doBackup)
            }
            // This Google account already had a Bottlepost account; this install now uses it.
            GoogleResult.SwitchedToExisting -> {
                c.wipeLocal()
                c.profiles.refresh()
                c.onReady()
                withDrive(forRestore = true, ::doRestore)
            }
        }
    }

    fun backupNow() = action { withDrive(forRestore = false, ::doBackup) }

    fun restore() = action { withDrive(forRestore = true, ::doRestore) }

    private suspend fun doBackup(token: String) = c.profiles.markBackedUp(c.backup.backup(token))

    private suspend fun doRestore(token: String) {
        val n = c.backup.restore(token)
        _ui.update { it.copy(message = if (n == null) R.string.backup_none else R.string.backup_restored) }
    }

    private suspend fun withDrive(forRestore: Boolean, onToken: suspend (String) -> Unit) {
        when (val access = c.backup.requestAccess()) {
            is DriveAccess.Granted -> {
                onToken(access.accessToken)
                if (!forRestore) _ui.update { it.copy(message = R.string.backup_done) }
            }
            is DriveAccess.NeedsConsent -> _events.emit(SettingsEvent.NeedsDriveConsent(access.pendingIntent.intentSender, forRestore))
        }
    }

    fun onDriveConsent(data: Intent?, forRestore: Boolean) {
        if (runCatching { c.backup.tokenFromConsent(data) }.getOrNull() == null) return
        if (forRestore) restore() else backupNow()
    }

    fun appeal(message: String) = action(R.string.appeal_sent) { c.api.submitAppeal(message, "appeal") }

    fun deleteAccount() = action {
        c.api.deleteAccount()
        c.wipeLocal()
        c.auth.signOut()
        _events.emit(SettingsEvent.AccountDeleted)
    }
}
