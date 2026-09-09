package com.example.data.remote

data class CommunityMessage(
    val id: String = "",
    val userId: String = "",
    val profileId: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val isVerified: Boolean = false,
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: String = "ACTIVE", // ACTIVE, DELETED
    val replyToId: String? = null,
    val replyToUsername: String? = null,
    val replyToText: String? = null,
    val isAiResponse: Boolean = false,
    val recommendedMediaId: Int? = null,
    val recommendedMediaType: String? = null, // movie, tv
    val recommendedMediaTitle: String? = null,
    val recommendedMediaPoster: String? = null,
    val aiProcessed: Boolean = false,
    val reactions: Map<String, String> = emptyMap(), // Map of userId -> emoji
    val deliveryStatus: String = "SENT" // "SENDING", "SENT", "FAILED"
)
