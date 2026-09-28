package com.techvibedev.triptrace.data.repository

import com.techvibedev.triptrace.data.model.PasswordChangeRequest
import com.techvibedev.triptrace.data.model.RegisterRequest
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

    private suspend fun authHeader(): String {
        val token = tokenDataStore.tokenFlow.first() ?: error("No auth token available")
        return "Bearer $token"
    }

    // Creates the account only — it doesn't log in. What happens next is up to
    // the caller (RegisterScreen logs in right after).
    suspend fun register(email: String, username: String, password: String): Result<Unit> {
        return try {
            apiService.register(RegisterRequest(email = email, username = username, password = password))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // identifier: either the user's email or their username.
    suspend fun login(identifier: String, password: String): Result<Unit> {
        return try {
            val response = apiService.login(identifier, password)
            tokenDataStore.saveToken(response.accessToken)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // For the Perfil tab — email/username to display.
    suspend fun getMe(): Result<UserResponse> {
        return try {
            Result.success(apiService.getMe(authHeader()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // A 400 here (wrong current_password) surfaces as a failed Result like
    // any other error — the caller (UserScreen) is responsible for showing
    // a message.
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            apiService.changePassword(
                authHeader(),
                PasswordChangeRequest(currentPassword = currentPassword, newPassword = newPassword),
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        tokenDataStore.clearToken()
    }
}
