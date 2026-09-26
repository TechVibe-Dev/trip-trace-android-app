package com.techvibedev.triptrace.data.repository

import com.techvibedev.triptrace.data.model.UserResponse
import com.techvibedev.triptrace.data.network.AuthApiService
import com.techvibedev.triptrace.data.session.TokenDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    // For the Usuario tab (android#87) — email/username to display.
    suspend fun getMe(): Result<UserResponse> {
        return try {
            val token = tokenDataStore.tokenFlow.first() ?: error("No auth token available")
            Result.success(apiService.getMe("Bearer $token"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        tokenDataStore.clearToken()
    }
}
