package com.example.data.remote

data class UserEntity(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val username: String = "", // Formato de exibição, ex: "@ronaldo"
    val usernameNormalized: String = "", // Formato para índice e busca, ex: "ronaldo"
    val photoUrl: String? = null,
    val role: String = "USER", // USER, ADMIN, FOUNDER
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val deviceId: String = "",
    val accessStatus: String = "ACTIVE", // ACTIVE, SUSPENDED, BANNED
    val lastUsernameChangeAt: Long = 0L // Timestamp da última alteração de username
)
