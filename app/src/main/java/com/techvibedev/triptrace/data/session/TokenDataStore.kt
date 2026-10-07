package com.techvibedev.triptrace.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.Key
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "auth")

// The access token is a long-lived credential (180 days, see
// auth_service.py) — worth encrypting at rest rather than storing as plain
// text, since a rooted device can read a Preferences DataStore file
// directly, and (until allowBackup was also turned off in the manifest)
// the app's data directory was included in Android's default backups.
// Encrypted here with an AES-256/GCM key generated inside Android Keystore
// (not androidx.security:security-crypto, to avoid a new dependency for
// something the platform already provides directly) — the raw key
// material never leaves the Keystore and generally isn't exportable even
// on a rooted device, and Keystore-backed keys aren't included in Auto
// Backup either way, so a backup of just the encrypted bytes is useless
// without the original device.
class TokenDataStore(private val context: Context) {

    private val tokenKey = stringPreferencesKey("access_token")
    // GCM needs a fresh IV every time something is encrypted to stay
    // secure, so it travels alongside the ciphertext rather than being
    // fixed or derived.
    private val ivKey = stringPreferencesKey("access_token_iv")

    val tokenFlow: Flow<String?> = context.authDataStore.data.map { preferences ->
        val encrypted = preferences[tokenKey]
        val iv = preferences[ivKey]
        if (encrypted != null && iv != null) decrypt(encrypted, iv) else null
    }

    suspend fun saveToken(token: String) {
        val (encrypted, iv) = encrypt(token)
        context.authDataStore.edit { preferences ->
            preferences[tokenKey] = encrypted
            preferences[ivKey] = iv
        }
    }

    suspend fun clearToken() {
        context.authDataStore.edit { preferences ->
            preferences.remove(tokenKey)
            preferences.remove(ivKey)
        }
    }

    private fun encrypt(plainText: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP) to
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    // Returns null (rather than throwing) on any decryption failure — e.g.
    // the Keystore entry is gone after a factory reset or a restore onto a
    // different device. Treating that as "no token" (same as a fresh
    // install, routes to Login) is the right behavior, not a crash.
    private fun decrypt(encryptedText: String, ivText: String): String? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(ivText, Base64.NO_WRAP)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
            )
            val decryptedBytes = cipher.doFinal(Base64.decode(encryptedText, Base64.NO_WRAP))
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun getOrCreateKey(): Key {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "trip_trace_token_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
