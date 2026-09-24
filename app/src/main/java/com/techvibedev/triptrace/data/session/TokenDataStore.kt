package com.techvibedev.triptrace.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "auth")

class TokenDataStore(private val context: Context) {

    private val tokenKey = stringPreferencesKey("access_token")

    val tokenFlow: Flow<String?> = context.authDataStore.data.map { preferences ->
        preferences[tokenKey]
    }

    suspend fun saveToken(token: String) {
        context.authDataStore.edit { preferences ->
            preferences[tokenKey] = token
        }
    }

    suspend fun clearToken() {
        context.authDataStore.edit { preferences ->
            preferences.remove(tokenKey)
        }
    }
}
