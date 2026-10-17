package com.bottlepost.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bottlepost.app.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        viewModelScope.launch {
            val cached = c.profiles.current()
            if (cached != null) {
                _state.value = StartState.Ready
                c.onReady()
                runCatching { c.profiles.refresh() }
                return@launch
            }
            runCatching {
                c.auth.ensureSignedIn()
                c.profiles.refresh()
            }.onSuccess { profile ->
                _state.value = if (profile == null) StartState.NeedsOnboarding else StartState.Ready
                if (profile != null) c.onReady()
            }.onFailure {
                _state.value = StartState.Offline
            }
        }
    }

    fun onOnboarded() {
        _state.value = StartState.Ready
        viewModelScope.launch { c.onReady() }
    }

    fun onAccountDeleted() {
        _state.value = StartState.Loading
        start()
    }
}
