package com.bottlepost.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bottlepost.app.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface StartState {
    data object Loading : StartState
    data object NeedsOnboarding : StartState
    data object Ready : StartState
    data object Offline : StartState
}

/** Decides where the app starts: onboarding for new installs, the mailbox for everyone else. */
class AppViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<StartState>(StartState.Loading)
    val state: StateFlow<StartState> = _state

    init {
        start()
    }

    fun start() {
        _state.value = StartState.Loading
        val shownAt = System.currentTimeMillis()
        // Keep the launch screen up long enough to read the app name instead of flashing it.
        suspend fun settle(next: StartState) {
            delay((MIN_SPLASH_MS - (System.currentTimeMillis() - shownAt)).coerceAtLeast(0))
            _state.value = next
        }
        viewModelScope.launch {
            val cached = c.profiles.current()
            if (cached != null) {
                settle(StartState.Ready)
                c.onReady()
                // The account may have been deleted elsewhere (web deletion page, admin). Start over.
                runCatching { c.profiles.refresh() }.onSuccess { fresh ->
                    if (fresh == null) {
                        c.wipeLocal()
                        _state.value = StartState.NeedsOnboarding
                    }
                }
                return@launch
            }
            runCatching {
                c.auth.ensureSignedIn()
                c.profiles.refresh()
            }.onSuccess { profile ->
                settle(if (profile == null) StartState.NeedsOnboarding else StartState.Ready)
                if (profile != null) c.onReady()
            }.onFailure {
                settle(StartState.Offline)
            }
        }
    }

    fun onOnboarded() {
        _state.value = StartState.Ready
        viewModelScope.launch { c.onReady() }
    }

    private companion object {
        const val MIN_SPLASH_MS = 900L
    }

    fun onAccountDeleted() {
        _state.value = StartState.Loading
        start()
    }
}
