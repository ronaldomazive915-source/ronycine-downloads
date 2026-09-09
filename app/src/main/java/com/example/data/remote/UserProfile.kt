package com.example.data.remote

data class UserProfile(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val username: String = "",
    val avatarUrl: String? = null,
    val photoUrl: String? = null,
    val avatarType: String = "DEFAULT", // "DEFAULT", "PRESET", "CUSTOM"
    val avatarId: String = "",
    val isDefault: Boolean = false,
    val language: String? = null,
    val preferredPlayerLanguage: String? = null,
    val languageSource: String? = null,
    val isVerified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
