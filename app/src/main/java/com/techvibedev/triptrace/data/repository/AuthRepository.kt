package com.techvibedev.triptrace.data.repository

import com.techvibedev.triptrace.data.network.AuthApiService
import com.techvibedev.triptrace.data.session.TokenDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthRepository(
    private val apiService: AuthApiService,
    private val tokenDataStore: TokenDataStore,
) {
    val isLoggedIn: Flow<Boolean> = tokenDataStore.tokenFlow.map { token -> token != null }

    // identifier: either the user's email or their username (trip-trace-api#57).
    suspend fun login(identifier: String, password: String): Result<Unit> {
        return try {
            val response = apiService.login(identifier, password)
            tokenDataStore.saveToken(response.accessToken)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        tokenDataStore.clearToken()
    }
}
