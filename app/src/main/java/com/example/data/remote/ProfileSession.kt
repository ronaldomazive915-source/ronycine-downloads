package com.example.data.remote

/**
 * Representa a sessão de autenticação de perfis no RONYCINE.
 *
 * @param activeProfileId ID do perfil atualmente ativo (ou null se nenhum selecionado)
 * @param unlockedProfileId ID do perfil protegido que foi desbloqueado nesta sessão temporária
 * @param authenticated true se a autenticação por PIN/senha foi validada com sucesso para a sessão atual
 */
data class ProfileSession(
    val activeProfileId: String? = null,
    val unlockedProfileId: String? = null,
    val authenticated: Boolean = false
) {
    /**
     * Verifica se um perfil está liberado para uso.
     * Perfis sem PIN estão sempre liberados.
     * Perfis protegidos por PIN só estão liberados se authenticated for true e unlockedProfileId corresponder ao ID do perfil.
     */
    fun isProfileUnlocked(profile: UserProfile): Boolean {
        if (!profile.isProtected) return true
        return authenticated && unlockedProfileId == profile.id && activeProfileId == profile.id
    }
}
