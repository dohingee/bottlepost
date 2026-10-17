package com.bottlepost.app.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

sealed interface TranslationResult {
    data class Done(val text: String, val sourceLang: String) : TranslationResult
    data object SameLanguage : TranslationResult
    data object Unsupported : TranslationResult
    data class Failed(val error: Throwable) : TranslationResult
}

/**
 * On-device translation with ML Kit: free, works offline after a one-time ~30 MB model download per
 * language, and letter text never leaves the phone.
 */
class LetterTranslator {
    private val identifier = LanguageIdentification.getClient()
    private val translators = mutableMapOf<Pair<String, String>, Translator>()
    private val lock = Mutex()

    /** [hintLang] is the sender's app language from the delivery; detection wins when confident. */
    suspend fun translate(text: String, hintLang: String, targetTag: String): TranslationResult = try {
        val detected = identifier.identifyLanguage(text).await().takeIf { it != "und" } ?: hintLang
        val source = TranslateLanguage.fromLanguageTag(detected)
        val target = TranslateLanguage.fromLanguageTag(targetTag)
        when {
            source == null || target == null -> TranslationResult.Unsupported
            source == target -> TranslationResult.SameLanguage
            else -> {
                val translator = translatorFor(source, target)
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                TranslationResult.Done(translator.translate(text).await(), source)
            }
        }
    } catch (e: Exception) {
        TranslationResult.Failed(e)
    }

    private suspend fun translatorFor(source: String, target: String): Translator = lock.withLock {
        translators.getOrPut(source to target) {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        }
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
        identifier.close()
    }
}
