package com.bottlepost.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bottlepost.app.data.remote.BottlepostApi
import com.bottlepost.app.data.remote.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("profile")

/** Server profile mirrored into DataStore so the UI works offline and starts instantly. */
class ProfileRepository(private val context: Context, private val api: BottlepostApi) {

    private object K {
        val gender = stringPreferencesKey("gender")
        val birthYear = intPreferencesKey("birthYear")
        val lang = stringPreferencesKey("lang")
        val receiveFrom = stringPreferencesKey("receiveFrom")
        val nickname = stringPreferencesKey("nickname")
        val linked = booleanPreferencesKey("linked")
        val status = stringPreferencesKey("status")
        val suspendedUntil = longPreferencesKey("suspendedUntil")
        val lastBackupAt = longPreferencesKey("lastBackupAt")
    }

    val profile: Flow<Profile?> = context.dataStore.data.map { it.toProfile() }
    val lastBackupAt: Flow<Long?> = context.dataStore.data.map { it[K.lastBackupAt] }

    suspend fun current(): Profile? = profile.first()

    private fun Preferences.toProfile(): Profile? {
        val gender = this[K.gender] ?: return null
        return Profile(
            gender = gender,
            birthYear = this[K.birthYear] ?: 0,
            lang = this[K.lang] ?: "en",
            receiveFrom = this[K.receiveFrom] ?: "ANY",
            nickname = this[K.nickname] ?: "",
            linked = this[K.linked] ?: false,
            status = this[K.status] ?: "active",
            suspendedUntil = this[K.suspendedUntil],
        )
    }

    private suspend fun save(p: Profile?) {
        context.dataStore.edit { prefs ->
            if (p == null) {
                prefs.clear()
                return@edit
            }
            prefs[K.gender] = p.gender
            prefs[K.birthYear] = p.birthYear
            prefs[K.lang] = p.lang
            prefs[K.receiveFrom] = p.receiveFrom
            prefs[K.nickname] = p.nickname
            prefs[K.linked] = p.linked
            prefs[K.status] = p.status
            if (p.suspendedUntil != null) prefs[K.suspendedUntil] = p.suspendedUntil else prefs.remove(K.suspendedUntil)
        }
    }

    suspend fun create(
        gender: String,
        birthYear: Int,
        lang: String,
        country: String,
        receiveFrom: String,
        nickname: String,
    ): Profile = api.createProfile(gender, birthYear, lang, country, receiveFrom, nickname).also { save(it) }

    /** Returns null when the signed-in account has never been onboarded. */
    suspend fun refresh(): Profile? = api.getProfile().also { save(it) }

    suspend fun update(changes: Map<String, Any?>): Profile = api.updateProfile(changes).also { save(it) }

    suspend fun confirmGoogleLink(): Profile = api.confirmGoogleLink().also { save(it) }

    suspend fun markBackedUp(at: Long) {
        context.dataStore.edit { it[K.lastBackupAt] = at }
    }

    suspend fun clear() = save(null)
}
