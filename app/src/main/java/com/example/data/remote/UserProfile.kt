package com.example.data.remote

data class UserProfile(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    val avatarType: String = "DEFAULT", // DEFAULT, CUSTOM
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
