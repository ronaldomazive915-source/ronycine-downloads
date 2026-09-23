package com.example.data.remote

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class UserProfile(
    @get:PropertyName("id")
    val id: String = "",

    @get:PropertyName("userId")
    val userId: String = "",

    @get:PropertyName("name")
    val name: String = "",

    @get:PropertyName("username")
    val username: String = "",

    @get:PropertyName("avatarUrl")
    val avatarUrl: String? = null,

    @get:PropertyName("photoUrl")
    val photoUrl: String? = null,

    @get:PropertyName("avatarType")
    val avatarType: String = "DEFAULT", // "DEFAULT", "PRESET", "CUSTOM"

    @get:PropertyName("avatarId")
    val avatarId: String = "",

    @get:PropertyName("isDefault")
    val isDefault: Boolean = false,

    @get:PropertyName("isKidsProfile")
    val isKidsProfile: Boolean = false,

    @get:PropertyName("pinEnabled")
    val pinEnabled: Boolean = false,

    @get:PropertyName("pinHash")
    val pinHash: String? = null,

    @get:PropertyName("pin")
    val pin: String? = null, // Compatibilidade com documentos legados que gravaram pin direto

    @get:PropertyName("language")
    val language: String? = null,

    @get:PropertyName("preferredPlayerLanguage")
    val preferredPlayerLanguage: String? = null,

    @get:PropertyName("languageSource")
    val languageSource: String? = null,

    @get:PropertyName("isVerified")
    val isVerified: Boolean = false,

    @get:PropertyName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @get:PropertyName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Retorna se o perfil possui bloqueio por PIN/senha ativado.
     */
    val isProtected: Boolean
        get() = pinEnabled || !pinHash.isNullOrBlank() || !pin.isNullOrBlank()

    /**
     * Retorna a representação de hash do PIN para validação segura.
     */
    fun getEffectivePinHash(): String? {
        val hash = pinHash
        if (!hash.isNullOrBlank()) return hash
        val p = pin
        if (!p.isNullOrBlank()) return com.example.util.SecurityUtils.hashPin(p)
        return null
    }

    /**
     * Valida um PIN digitado pelo usuário contra este perfil.
     */
    fun verifyPin(inputPin: String): Boolean {
        if (!isProtected) return true
        val targetHash = getEffectivePinHash() ?: return false
        return com.example.util.SecurityUtils.verifyPin(inputPin, targetHash)
    }
}



