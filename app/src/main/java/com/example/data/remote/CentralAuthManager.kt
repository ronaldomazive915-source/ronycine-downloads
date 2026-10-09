package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.PlayFilmeDao
import com.example.data.repository.CentralRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CentralAuthManager(
    private val api: CentralApiService,
    private val dao: PlayFilmeDao,
    context: Context
) {
    private val prefs = context.getSharedPreferences("central_auth_prefs", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    init {
        val token = prefs.getString("auth_token", null)
        if (token != null) {
            CentralNetwork.setAuthToken(token)
            _isLoggedIn.value = true
            // Potentially fetch profile to verify
        }
    }

    suspend fun login(request: CentralLoginRequest): Result<UserEntity> {
        return try {
            val response = api.login(request)
            if (response.isSuccessful && response.body()?.status == "success") {
                val authData = response.body()?.data!!
                saveSession(authData.token, authData.user)
                Result.success(authData.user)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Falha no login"))
            }
        } catch (e: Exception) {
            Log.e("CentralAuthManager", "Erro login: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun register(request: CentralRegisterRequest): Result<UserEntity> {
        return try {
            val response = api.register(request)
            if (response.isSuccessful && response.body()?.status == "success") {
                val authData = response.body()?.data!!
                saveSession(authData.token, authData.user)
                Result.success(authData.user)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Falha no registro"))
            }
        } catch (e: Exception) {
            Log.e("CentralAuthManager", "Erro register: ${e.message}")
            Result.failure(e)
        }
    }

    private fun saveSession(token: String, user: UserEntity) {
        prefs.edit().putString("auth_token", token).apply()
        CentralNetwork.setAuthToken(token)
        _currentUser.value = user
        _isLoggedIn.value = true
    }

    fun logout() {
        prefs.edit().remove("auth_token").apply()
        CentralNetwork.setAuthToken(null)
        _currentUser.value = null
        _isLoggedIn.value = false
    }
}
