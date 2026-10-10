package com.raf.garagedoor.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raf.garagedoor.myq.auth.OAuthTokens
import com.raf.garagedoor.myq.auth.TokenStore
import com.raf.garagedoor.myq.http.MyQJson
import kotlinx.coroutines.flow.first
import java.security.GeneralSecurityException

private val Context.myQSessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "myq_session")

/** Persists myQ tokens encrypted with a Keystore key; unreadable data is treated as "signed out". */
class EncryptedTokenStore(context: Context, private val cipher: KeystoreCipher) : TokenStore {

    private val dataStore = context.applicationContext.myQSessionDataStore

    override suspend fun loadTokens(): OAuthTokens? = readDecrypted(TOKENS_KEY)?.let(::decodeTokens)

    override suspend fun saveTokens(tokens: OAuthTokens) =
        writeEncrypted(TOKENS_KEY, MyQJson.json.encodeToString(OAuthTokens.serializer(), tokens))

    override suspend fun savePendingVerifier(verifier: String) = writeEncrypted(VERIFIER_KEY, verifier)

    override suspend fun takePendingVerifier(): String? {
        val verifier = readDecrypted(VERIFIER_KEY)
        dataStore.edit { it.remove(VERIFIER_KEY) }
        return verifier
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private suspend fun readDecrypted(key: Preferences.Key<String>): String? {
        val encrypted = dataStore.data.first()[key] ?: return null
        return try {
            cipher.decrypt(encrypted)
        } catch (keyOrDataInvalid: GeneralSecurityException) {
            null
        } catch (notBase64: IllegalArgumentException) {
            null
        }
    }

    private suspend fun writeEncrypted(key: Preferences.Key<String>, value: String) {
        val encrypted = cipher.encrypt(value)
        dataStore.edit { it[key] = encrypted }
    }

    private fun decodeTokens(json: String): OAuthTokens? = try {
        MyQJson.json.decodeFromString(OAuthTokens.serializer(), json)
    } catch (corrupt: IllegalArgumentException) {
        null
    }

    private companion object {
        val TOKENS_KEY = stringPreferencesKey("tokens")
        val VERIFIER_KEY = stringPreferencesKey("pending_pkce_verifier")
    }
}
