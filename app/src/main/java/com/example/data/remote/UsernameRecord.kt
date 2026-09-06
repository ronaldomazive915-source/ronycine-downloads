package com.example.data.remote

data class UsernameRecord(
    val uid: String = "",
    val username: String = "",
    val usernameNormalized: String = "",
    val email: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
