package com.bottlepost.app.ui.compose

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bottlepost.app.AppContainer
import com.bottlepost.app.R
import com.bottlepost.app.ui.common.BottleButton
import com.bottlepost.app.ui.common.ChoiceChips
import com.bottlepost.app.ui.common.PaperSheet
import com.bottlepost.app.ui.common.Sea
import com.bottlepost.app.ui.common.SeaBackdrop
import com.bottlepost.app.ui.common.SeaTopBar
import com.bottlepost.app.ui.common.ruledLines
import com.bottlepost.app.ui.common.errorMessage
import com.bottlepost.app.ui.common.findActivity
import com.bottlepost.app.ui.common.genderLabel
import com.bottlepost.app.ui.theme.LetterTextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Must match LIMITS in firebase/functions/src/config.ts. */
const val MIN_CHARS = 2
private const val MAX_CHARS = 1000

data class ComposeState(
    val wantGender: String = "ANY",
    val sameLanguageOnly: Boolean = false,
    val body: String = "",
    val sending: Boolean = false,
    val sentThreadId: String? = null,
    @StringRes val error: Int? = null,
) {
    val canSend get() = body.trim().length in MIN_CHARS..MAX_CHARS && !sending && sentThreadId == null
}

class ComposeViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ComposeState())
    val state: StateFlow<ComposeState> = _state

    fun update(f: (ComposeState) -> ComposeState) = _state.update { f(it).copy(error = null) }

    fun send(activity: Activity) {
        val s = _state.value
        if (!s.canSend) return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            // Planned ad slot: shown after the tap, before the letter leaves (see ads/AdGate.kt).
            c.adGate.beforeSend(activity)
            val lang = c.profiles.current()?.lang ?: "en"
            runCatching { c.mail.sendFirst(s.body.trim(), s.wantGender, s.sameLanguageOnly, lang) }
                .onSuccess { id ->
                    c.notices.trySend(R.string.letter_launched)
                    _state.update { it.copy(sending = false, sentThreadId = id) }
                }
                .onFailure { e -> _state.update { it.copy(sending = false, error = errorMessage(e)) } }
        }
    }
}

@Composable
fun ComposeScreen(container: AppContainer, onBack: () -> Unit, onSent: () -> Unit) {
    val vm: ComposeViewModel = viewModel { ComposeViewModel(container) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The bottle drifts out to sea, then we are back at the mailbox.
    val drift by animateFloatAsState(if (s.sentThreadId != null) 1f else 0f, tween(900), label = "drift")
    s.sentThreadId?.let { id ->
        LaunchedEffect(id) {
            delay(1000)
            onSent()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            SeaTopBar(
                title = { Text(stringResource(R.string.compose_title), fontFamily = FontFamily.Serif) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        ComposeForm(s, drift, Modifier.padding(padding), vm::update) { vm.send(context.findActivity()) }
    }
}

/** The letter form itself, without the view model (also rendered for store screenshots). */
@Composable
internal fun ComposeForm(
    s: ComposeState,
    drift: Float,
    modifier: Modifier,
    update: ((ComposeState) -> ComposeState) -> Unit,
    onSend: () -> Unit,
) {
    SeaBackdrop(Modifier.fillMaxSize()) {
        Column(
            modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .graphicsLayer {
                    translationX = drift * size.width
                    translationY = -drift * size.height * 0.3f
                    rotationZ = drift * 12f
                    alpha = 1f - drift
                },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.compose_to), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(listOf("ANY", "F", "M"), s.wantGender, { stringResource(genderLabel(it)) }) { g ->
                update { it.copy(wantGender = g) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.compose_same_language), modifier = Modifier.weight(1f))
                Switch(checked = s.sameLanguageOnly, onCheckedChange = { v -> update { it.copy(sameLanguageOnly = v) } })
            }
            PaperSheet(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)) {
                BasicTextField(
                    value = s.body,
                    onValueChange = { v -> update { it.copy(body = v.take(MAX_CHARS)) } },
                    textStyle = LetterTextStyle.copy(color = Sea.InkText),
                    cursorBrush = SolidColor(Sea.Deep),
                    minLines = 10,
                    modifier = Modifier.fillMaxWidth().ruledLines(LetterTextStyle.lineHeight),
                    decorationBox = { inner ->
                        if (s.body.isEmpty()) {
                            Text(stringResource(R.string.compose_placeholder), style = LetterTextStyle, color = Sea.InkText.copy(alpha = 0.45f))
                        }
                        inner()
                    },
                )
                Text(
                    "${s.body.length} / $MAX_CHARS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Sea.InkText.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End),
                )
            }
            Text(stringResource(R.string.compose_rules), style = MaterialTheme.typography.bodySmall)
            s.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            BottleButton(
                stringResource(if (s.sending) R.string.compose_sending else R.string.compose_send),
                onClick = onSend,
                enabled = s.canSend,
                icon = Icons.AutoMirrored.Filled.Send,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            )
        }
    }
}
