package com.bottlepost.app.ui.thread

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bottlepost.app.AppContainer
import com.bottlepost.app.R
import com.bottlepost.app.data.local.LetterEntity
import com.bottlepost.app.data.local.ThreadEntity
import com.bottlepost.app.data.remote.ApiException
import com.bottlepost.app.translate.TranslationResult
import com.bottlepost.app.ui.compose.MIN_CHARS
import com.bottlepost.app.ui.common.errorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Translation {
    data object Loading : Translation
    data class Shown(val text: String) : Translation
    data class Error(@StringRes val message: Int) : Translation
}

data class ThreadUiState(
    val reply: String = "",
    val sending: Boolean = false,
    val translations: Map<String, Translation> = emptyMap(),
    @StringRes val error: Int? = null,
    @StringRes val toast: Int? = null,
    val leave: Boolean = false,
)

val REPORT_REASONS = listOf("spam", "scam", "sexual", "harassment", "minor", "hate", "self_harm", "other")

class ThreadViewModel(private val c: AppContainer, private val threadId: String) : ViewModel() {
    val thread: StateFlow<ThreadEntity?> = c.mail.observeThread(threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val letters: StateFlow<List<LetterEntity>> = c.mail.observeLetters(threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(ThreadUiState())
    val ui: StateFlow<ThreadUiState> = _ui

    init {
        // Also on every new letter while the conversation is open, so no badge is left for letters already seen.
        viewModelScope.launch { letters.collect { c.mail.markRead(threadId) } }
    }

    fun onReplyChange(text: String) = _ui.update { it.copy(reply = text.take(1000), error = null) }

    fun sendReply() {
        val body = _ui.value.reply.trim()
        if (body.length < MIN_CHARS || _ui.value.sending) {
            _ui.update { it.copy(error = R.string.error_too_short) }
            return
        }
        _ui.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching { c.mail.reply(threadId, body, c.profiles.current()?.lang ?: "en") }
                .onSuccess { _ui.update { it.copy(sending = false, reply = "") } }
                .onFailure { e ->
                    if ((e as? ApiException)?.code == "thread_closed") c.mail.markClosed(threadId)
                    _ui.update { it.copy(sending = false, error = errorMessage(e)) }
                }
        }
    }

    fun toggleTranslation(letter: LetterEntity) {
        if (_ui.value.translations.containsKey(letter.deliveryId)) {
            _ui.update { it.copy(translations = it.translations - letter.deliveryId) }
            return
        }
        setTranslation(letter.deliveryId, Translation.Loading)
        viewModelScope.launch {
            val target = c.profiles.current()?.lang ?: "en"
            val result = when (val r = c.translator.translate(letter.body, letter.lang, target)) {
                is TranslationResult.Done -> Translation.Shown(r.text)
                TranslationResult.SameLanguage -> Translation.Error(R.string.translate_same_language)
                TranslationResult.Unsupported -> Translation.Error(R.string.translate_unsupported)
                is TranslationResult.Failed -> Translation.Error(R.string.translate_failed)
            }
            setTranslation(letter.deliveryId, result)
        }
    }

    private fun setTranslation(id: String, t: Translation) = _ui.update { it.copy(translations = it.translations + (id to t)) }

    fun block() {
        viewModelScope.launch {
            runCatching { c.mail.block(threadId) }
                .onSuccess { _ui.update { it.copy(toast = R.string.block_done, leave = true) } }
                .onFailure { e -> _ui.update { it.copy(error = errorMessage(e)) } }
        }
    }

    fun report(reason: String, note: String) {
        viewModelScope.launch {
            runCatching { c.mail.report(threadId, reason, note) }
                .onSuccess { _ui.update { it.copy(toast = R.string.report_done, leave = true) } }
                .onFailure { e -> _ui.update { it.copy(error = errorMessage(e)) } }
        }
    }

    fun leave() {
        viewModelScope.launch {
            runCatching { c.mail.leave(threadId) }
                .onSuccess { _ui.update { it.copy(toast = R.string.leave_done) } }
                .onFailure { e -> _ui.update { it.copy(error = errorMessage(e)) } }
        }
    }

    fun delete() {
        val t = thread.value ?: return
        viewModelScope.launch {
            runCatching { c.mail.delete(t) }
                .onSuccess {
                    c.notices.trySend(R.string.delete_done)
                    _ui.update { it.copy(leave = true) }
                }
                .onFailure { e -> _ui.update { it.copy(error = errorMessage(e)) } }
        }
    }

    fun toastShown() = _ui.update { it.copy(toast = null) }
}
