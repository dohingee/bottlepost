package com.bottlepost.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bottlepost.app.AppContainer
import com.bottlepost.app.R
import com.bottlepost.app.ui.common.ChoiceChips
import com.bottlepost.app.ui.common.Links
import com.bottlepost.app.ui.common.SUPPORTED_LANGS
import com.bottlepost.app.ui.common.applyAppLanguage
import com.bottlepost.app.ui.common.findActivity
import com.bottlepost.app.ui.common.genderLabel
import com.bottlepost.app.ui.common.langLabel
import com.bottlepost.app.ui.common.openUrl

@Composable
fun OnboardingScreen(container: AppContainer, onDone: () -> Unit) {
    val vm: OnboardingViewModel = viewModel { OnboardingViewModel(container) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.onDriveConsentResult(it.data)
    }
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                OnboardingEvent.Done -> {
                    container.profiles.current()?.let { applyAppLanguage(it.lang) }
                    onDone()
                }
                is OnboardingEvent.NeedsDriveConsent -> consent.launch(IntentSenderRequest.Builder(e.sender).build())
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.onboarding_tagline), style = MaterialTheme.typography.bodyLarge)

        if (!s.googleLinked) {
            OutlinedButton(
                onClick = { vm.restoreWithGoogle(context.findActivity()) },
                enabled = !s.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.onboarding_restore_google)) }
        }
        s.info?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.secondary) }
        HorizontalDivider()

        Section(R.string.onboarding_my_gender) {
            ChoiceChips(listOf("M", "F"), s.gender, { stringResource(genderLabel(it)) }) { g -> vm.update { it.copy(gender = g) } }
        }
        Section(R.string.onboarding_birth_year) {
            OutlinedTextField(
                value = s.birthYear,
                onValueChange = { v -> vm.update { it.copy(birthYear = v.filter(Char::isDigit).take(4)) } },
                singleLine = true,
                isError = s.yearInvalid,
                supportingText = { Text(stringResource(if (s.yearInvalid) R.string.onboarding_age_error else R.string.onboarding_age_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Section(R.string.onboarding_language) {
            ChoiceChips(SUPPORTED_LANGS, s.lang, { stringResource(langLabel(it)) }) { l -> vm.update { it.copy(lang = l) } }
        }
        Section(R.string.onboarding_receive_from) {
            ChoiceChips(listOf("ANY", "M", "F"), s.receiveFrom, { stringResource(genderLabel(it)) }) { g ->
                vm.update { it.copy(receiveFrom = g) }
            }
        }
        Section(R.string.onboarding_nickname) {
            OutlinedTextField(
                value = s.nickname,
                onValueChange = { v -> vm.update { it.copy(nickname = v.take(20)) } },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.onboarding_nickname_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider()
        Agreement(s.agreeAge, R.string.onboarding_agree_age, null) { v -> vm.update { it.copy(agreeAge = v) } }
        Agreement(s.agreeTerms, R.string.onboarding_agree_terms, Links.TERMS) { v -> vm.update { it.copy(agreeTerms = v) } }
        Agreement(s.agreePrivacy, R.string.onboarding_agree_privacy, Links.PRIVACY) { v -> vm.update { it.copy(agreePrivacy = v) } }
        Text(stringResource(R.string.onboarding_privacy_note), style = MaterialTheme.typography.bodySmall)

        s.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        Button(onClick = vm::submit, enabled = s.canSubmit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.onboarding_start))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun Agreement(checked: Boolean, label: Int, url: String?, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(stringResource(label), modifier = Modifier.weight(1f))
        if (url != null) TextButton(onClick = { context.openUrl(url) }) { Text(stringResource(R.string.action_view)) }
    }
}
