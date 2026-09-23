package com.example.data.remote

sealed class AuthorizationState {
    object Idle : AuthorizationState()
    object AuthLoading : AuthorizationState()
    object Authenticated : AuthorizationState()
    object AuthorizationLoading : AuthorizationState()
    data class Authorized(val user: UserEntity) : AuthorizationState()
    object Unauthorized : AuthorizationState()
    data class Error(val message: String) : AuthorizationState()
}

data class UserPermissions(
    val dashboard: Boolean = true,
    val administrators: Boolean = false,
    val users: Boolean = false,
    val catalog: Boolean = false,
    val importFilme: Boolean = false,
    val importSerie: Boolean = false,
    val importacaoMassa: Boolean = false,
    val pedidosTmdb: Boolean = false,
    val top10: Boolean = false,
    val destaques: Boolean = false,
    val tvAoVivo: Boolean = false,
    val sincronizarCatalogo: Boolean = false,
    val players: Boolean = false,
    val configurarPlayers: Boolean = false,
    val dispositivos: Boolean = false,
    val atualizacoes: Boolean = false,
    val notificacoes: Boolean = false,
    val configuracoes: Boolean = false,
    val auditoria: Boolean = false,
    val deleteContent: Boolean = false,
    val deleteUsers: Boolean = false,
    val manageAdmins: Boolean = false,
    val managePermissions: Boolean = false
) {
    companion object {
        fun getAllPermissions(): List<String> = listOf(
            "dashboard", "administradores", "users", "catalog", "importFilme", "importSerie",
            "importacaoMassa", "pedidosTmdb", "top10", "destaques", "tvAoVivo", "sincronizarCatalogo",
            "players", "configurarPlayers", "dispositivos", "atualizacoes", "notificacoes",
            "configuracoes", "auditoria", "deleteContent", "deleteUsers", "manageAdmins", "managePermissions"
        )
        
        fun getLabel(permission: String): String = when(permission) {
            "dashboard" -> "Dashboard"
            "administradores" -> "Administradores"
            "users" -> "Gerenciar Usuários"
            "catalog" -> "Catálogo Completo"
            "importFilme" -> "Importar Filme"
            "importSerie" -> "Importar Série"
            "importacaoMassa" -> "Importação em Massa"
            "pedidosTmdb" -> "Pedidos TMDB"
            "top10" -> "TOP 10 Hoje"
            "destaques" -> "Destaques Home"
            "tvAoVivo" -> "TV ao Vivo"
            "sincronizarCatalogo" -> "Sincronizar Catálogo"
            "players" -> "Players de Reprodução"
            "configurarPlayers" -> "Configurar Players"
            "dispositivos" -> "Dispositivos"
            "atualizacoes" -> "Enviar Atualizações"
            "notificacoes" -> "Enviar Notificações"
            "configuracoes" -> "Configurações Globais"
            "auditoria" -> "Auditoria / Logs"
            "deleteContent" -> "Excluir Conteúdo"
            "deleteUsers" -> "Excluir Usuários"
            "manageAdmins" -> "Gerenciar Admins"
            "managePermissions" -> "Gerenciar Permissões"
            else -> permission
        }
    }
}

data class UserEntity(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val username: String = "", // Formato de exibição, ex: "@ronaldo"
    val usernameNormalized: String = "", // Formato para índice e busca, ex: "ronaldo"
    val photoUrl: String? = null,
    val role: String = "USER", // USER, ADMIN, FOUNDER
    val permissions: UserPermissions = UserPermissions(),
    val isVerified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val deviceId: String = "",
    val accessStatus: String = "ACTIVE", // ACTIVE, SUSPENDED, BANNED
    val lastUsernameChangeAt: Long = 0L // Timestamp da última alteração de username
) {
    /**
     * Centralized authorization check for RONYCINE.
     * FOUNDER always has full access.
     * ADMIN only has access if the specific permission is TRUE.
     * USER never has administrative access.
     */
    fun hasPermission(permissionKey: String): Boolean {
        if (role.equals("FOUNDER", ignoreCase = true) || email.equals("ronaldomazive915@gmail.com", ignoreCase = true)) return true
        if (!role.equals("ADMIN", ignoreCase = true)) return false
        
        return when (permissionKey) {
            "dashboard" -> permissions.dashboard
            "administrators" -> permissions.administrators
            "administradores" -> permissions.administrators // For backward compatibility
            "users" -> permissions.users
            "catalog" -> permissions.catalog
            "importFilme" -> permissions.importFilme
            "importSerie" -> permissions.importSerie
            "importacaoMassa" -> permissions.importacaoMassa
            "pedidosTmdb" -> permissions.pedidosTmdb
            "top10" -> permissions.top10
            "destaques" -> permissions.destaques
            "tvAoVivo" -> permissions.tvAoVivo
            "sincronizarCatalogo" -> permissions.sincronizarCatalogo
            "players" -> permissions.players
            "configurarPlayers" -> permissions.configurarPlayers
            "dispositivos" -> permissions.dispositivos
            "atualizacoes" -> permissions.atualizacoes
            "notificacoes" -> permissions.notificacoes
            "configuracoes" -> permissions.configuracoes
            "auditoria" -> permissions.auditoria
            "deleteContent" -> permissions.deleteContent
            "deleteUsers" -> permissions.deleteUsers
            "manageAdmins" -> permissions.manageAdmins
            "managePermissions" -> permissions.managePermissions
            else -> false
        }
    }
}
