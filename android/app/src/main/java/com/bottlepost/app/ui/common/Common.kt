package com.bottlepost.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.bottlepost.app.R

/** Public pages served by Firebase Hosting (firebase/hosting). Replace with your domain. */
object Links {
    const val BASE = "https://bottlepost.web.app"
    // Korean originals; every other UI language gets the English translation.
    private val korean
        get() = (AppCompatDelegate.getApplicationLocales()[0] ?: java.util.Locale.getDefault()).language == "ko"
    val TERMS get() = if (korean) "$BASE/terms" else "$BASE/terms-en"
    val PRIVACY get() = if (korean) "$BASE/privacy" else "$BASE/privacy-en"
    const val CHILD_SAFETY = "$BASE/child-safety"
    const val SUPPORT_EMAIL = "kimyeoware@gmail.com"
}

/** Languages with translated UI strings (res/values-*). Letters in any other language still translate. */
val SUPPORTED_LANGS = listOf("ko", "en", "ja", "es", "pt", "id", "vi", "th")

@StringRes
fun langLabel(code: String): Int = when (code) {
    "ko" -> R.string.lang_ko
    "ja" -> R.string.lang_ja
    "es" -> R.string.lang_es
    "pt" -> R.string.lang_pt
    "id" -> R.string.lang_id
    "vi" -> R.string.lang_vi
    "th" -> R.string.lang_th
    else -> R.string.lang_en
}

@StringRes
fun genderLabel(code: String): Int = when (code) {
    "M" -> R.string.gender_male
    "F" -> R.string.gender_female
    else -> R.string.gender_any
}

/** Singular form for the user's own gender ("I am a man"); [genderLabel] is the plural recipient choice. */
@StringRes
fun selfGenderLabel(code: String): Int = if (code == "M") R.string.gender_self_male else R.string.gender_self_female

fun Context.findActivity(): Activity {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    error("No activity in context")
}

fun Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

fun Context.emailSupport(subject: String) {
    startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Links.SUPPORT_EMAIL}?subject=${Uri.encode(subject)}")))
}

/** Applies the per-app UI language (Android 13+ system setting, AppCompat backport below). */
fun applyAppLanguage(lang: String) {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    if (!current.startsWith(lang)) AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(options: List<String>, selected: String?, label: @Composable (String) -> String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            FilterChip(selected = opt == selected, onClick = { onSelect(opt) }, label = { Text(label(opt)) })
        }
    }
}

@Composable
fun genderText(code: String): String = stringResource(genderLabel(code))
