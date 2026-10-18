package com.bottlepost.app.ui.onboarding

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bottlepost.app.AppContainer
import com.bottlepost.app.R
import com.bottlepost.app.auth.GoogleResult
import com.bottlepost.app.backup.DriveAccess
import com.bottlepost.app.ui.common.SUPPORTED_LANGS
import com.bottlepost.app.ui.common.errorMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Year
import java.util.Locale

data class OnboardingState(
    val gender: String? = null,
    val birthYear: String = "",
    val lang: String = defaultLang(),
    val receiveFrom: String = "ANY",
    val nickname: String = "",
    val agreeAge: Boolean = false,
    val agreeTerms: Boolean = false,
    val agreePrivacy: Boolean = false,
    val googleLinked: Boolean = false,
    val busy: Boolean = false,
    @StringRes val error: Int? = null,
    @StringRes val info: Int? = null,
) {
    private val year get() = birthYear.toIntOrNull()
    /** Birth year only, so be conservative: assume the birthday hasn't happened yet this year. */
    val isAdult: Boolean get() = year?.let { Year.now().value - it - 1 >= MIN_AGE } == true
    val yearInvalid: Boolean get() = birthYear.length == 4 && (year == null || year!! < 1900 || !isAdult)
    val canSubmit: Boolean
        get() = gender != null && isAdult && agreeAge && agreeTerms && agreePrivacy && !busy

    companion object {
        const val MIN_AGE = 18
        fun defaultLang(): String = Locale.getDefault().language
            .let { if (it == "in") "id" else it } // Older Android reports Indonesian as "in".
            .let { if (it in SUPPORTED_LANGS) it else "en" }
    }
}

sealed interface OnboardingEvent {
    data object Done : OnboardingEvent
    data class NeedsDriveConsent(val sender: IntentSender) : OnboardingEvent
}

class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState(googleLinked = c.auth.isGoogleLinked))
    val state: StateFlow<OnboardingState> = _state
    private val _events = MutableSharedFlow<OnboardingEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<OnboardingEvent> = _events

    fun update(f: (OnboardingState) -> OnboardingState) = _state.update { f(it).copy(error = null) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                c.auth.ensureSignedIn()
                c.profiles.create(
                    gender = s.gender!!,
                    birthYear = s.birthYear.toInt(),
                    lang = s.lang,
                    country = Locale.getDefault().country,
                    receiveFrom = s.receiveFrom,
                    nickname = s.nickname.trim(),
                )
                if (c.auth.isGoogleLinked) c.profiles.confirmGoogleLink()
            }.onSuccess {
                _events.emit(OnboardingEvent.Done)
            }.onFailure { e ->
                _state.update { it.copy(busy = false, error = errorMessage(e)) }
            }
        }
    }

    /** "Restore with Google" on a fresh install / new phone. */
    fun restoreWithGoogle(activity: Activity) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                c.auth.ensureSignedIn()
                c.auth.connectGoogle(activity, confirmSwitch = { true }, discardCurrent = { c.api.deleteAccount() })
            }.onSuccess { result ->
                when (result) {
                    GoogleResult.Cancelled -> _state.update { it.copy(busy = false) }
                    // Google was not used before: continue onboarding with the account already linked.
                    GoogleResult.Linked -> _state.update { it.copy(busy = false, googleLinked = true, info = R.string.onboarding_google_new) }
                    GoogleResult.SwitchedToExisting -> afterSwitch()
                }
            }.onFailure { e -> _state.update { it.copy(busy = false, error = errorMessage(e)) } }
        }
    }

    private suspend fun afterSwitch() {
        val profile = runCatching { c.profiles.refresh() }.getOrNull()
        if (profile == null) {
            _state.update { it.copy(busy = false, googleLinked = true, info = R.string.onboarding_google_new) }
            return
        }
        when (val access = runCatching { c.backup.requestAccess() }.getOrNull()) {
            is DriveAccess.Granted -> restore(access.accessToken)
            is DriveAccess.NeedsConsent -> _events.emit(OnboardingEvent.NeedsDriveConsent(access.pendingIntent.intentSender))
            null -> _events.emit(OnboardingEvent.Done)
        }
    }

    fun onDriveConsentResult(data: Intent?) {
        viewModelScope.launch {
            val token = runCatching { c.backup.tokenFromConsent(data) }.getOrNull()
            if (token == null) _events.emit(OnboardingEvent.Done) else restore(token)
        }
    }

    private suspend fun restore(token: String) {
        runCatching { c.backup.restore(token) }
        _events.emit(OnboardingEvent.Done)
    }
}
