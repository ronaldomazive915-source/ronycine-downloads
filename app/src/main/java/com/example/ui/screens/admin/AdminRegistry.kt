package com.example.ui.screens.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.viewmodel.AdminSection

/**
 * Categorias oficiais do Painel Administrativo RONYCINE
 */
enum class AdminCategory(val title: String, val order: Int) {
    PAINEL("PAINEL", 1),
    CONTEUDO("CONTEÚDO", 2),
    USUARIOS_DISPOSITIVOS("USUÁRIOS E DISPOSITIVOS", 3),
    COMUNICACAO("COMUNICAÇÃO", 4),
    ATUALIZACOES("ATUALIZAÇÕES", 5),
    SISTEMA("SISTEMA", 6)
}

/**
 * Registro oficial de módulo administrativo.
 * Previne duplicidade de menus, rotas, telas e ações.
 */
data class AdminModule(
    val id: String,
    val section: AdminSection,
    val title: String,
    val subtitle: String,
    val category: AdminCategory,
    val icon: ImageVector,
    val permission: String = "ADMIN",
    val testTag: String
)

/**
 * Catálogo Único e Centralizado de todos os 21 módulos administrativos do RONYCINE.
 */
object AdminRegistry {

    val modules: List<AdminModule> = listOf(
        // 1. PAINEL
        AdminModule(
            id = "visao_geral",
            section = AdminSection.ESTATISTICAS,
            title = "Visão Geral",
            subtitle = "Dashboard com métricas e atalhos rápidos",
            category = AdminCategory.PAINEL,
            icon = Icons.Default.Dashboard,
            testTag = "admin_menu_visao_geral"
        ),

        // 2. CONTEÚDO
        AdminModule(
            id = "catalogo",
            section = AdminSection.CATALOGO,
            title = "Catálogo",
            subtitle = "Gerenciar filmes e séries cadastrados",
            category = AdminCategory.CONTEUDO,
            icon = Icons.Default.Movie,
            testTag = "admin_menu_catalogo"
        ),
        AdminModule(
            id = "importar_filmes",
            section = AdminSection.IMPORTACAO,
            title = "Importar Filmes",
            subtitle = "Buscar no TMDB com prévia e importar título individual",
            category = AdminCategory.CONTEUDO,
            icon = Icons.Default.CloudDownload,
            testTag = "admin_menu_importar_filmes"
        ),
        AdminModule(
            id = "importacao_massa",
            section = AdminSection.IMPORTACAO_MASSA,
            title = "Importação em Massa",
            subtitle = "Importar filmes e séries em lote por tendências",
            category = AdminCategory.CONTEUDO,
            icon = Icons.Default.LibraryAdd,
            testTag = "admin_menu_importacao_massa"
        ),
        AdminModule(
            id = "top_10",
            section = AdminSection.TOP_10,
            title = "TOP 10",
            subtitle = "Gerenciar os 10 títulos no ranking principal",
            category = AdminCategory.CONTEUDO,
            icon = Icons.Default.Whatshot,
            testTag = "admin_menu_top10"
        ),
        AdminModule(
            id = "destaques",
            section = AdminSection.DESTAQUES,
            title = "Destaques",
            subtitle = "Banners, trailers e destaques da Home",
            category = AdminCategory.CONTEUDO,
            icon = Icons.Default.Star,
            testTag = "admin_menu_destaques"
        ),
        AdminModule(
            id = "tv_ao_vivo",
            section = AdminSection.TV_AO_VIVO,
            title = "TV ao Vivo",
            subtitle = "Gerenciar canais e transmissões ao vivo",
            category = AdminCategory.CONTEUDO,
            icon = Icons.Default.LiveTv,
            testTag = "admin_menu_tv"
        ),

        // 3. USUÁRIOS E DISPOSITIVOS
        AdminModule(
            id = "usuarios",
            section = AdminSection.USUARIOS,
            title = "Usuários",
            subtitle = "Contas cadastradas, status e permissões",
            category = AdminCategory.USUARIOS_DISPOSITIVOS,
            icon = Icons.Default.People,
            testTag = "admin_menu_usuarios"
        ),
        AdminModule(
            id = "perfis",
            section = AdminSection.PERFIS,
            title = "Perfis",
            subtitle = "Perfis globais das contas e preferências",
            category = AdminCategory.USUARIOS_DISPOSITIVOS,
            icon = Icons.Default.AccountCircle,
            testTag = "admin_menu_perfis"
        ),
        AdminModule(
            id = "dispositivos",
            section = AdminSection.DISPOSITIVOS,
            title = "Dispositivos",
            subtitle = "TVs, TV Boxes, celulares e status de acesso",
            category = AdminCategory.USUARIOS_DISPOSITIVOS,
            icon = Icons.Default.Devices,
            testTag = "admin_menu_dispositivos"
        ),
        AdminModule(
            id = "administradores",
            section = AdminSection.ADMINISTRADORES,
            title = "Administradores",
            subtitle = "Gestão de papéis, privilégios e permissões",
            category = AdminCategory.USUARIOS_DISPOSITIVOS,
            icon = Icons.Default.AdminPanelSettings,
            testTag = "admin_menu_administradores"
        ),

        // 4. COMUNICAÇÃO
        AdminModule(
            id = "notificacoes",
            section = AdminSection.NOTIFICACOES,
            title = "Notificações",
            subtitle = "Central de comunicados e envios em massa via FCM",
            category = AdminCategory.COMUNICACAO,
            icon = Icons.Default.Notifications,
            testTag = "admin_menu_notificacoes"
        ),
        AdminModule(
            id = "pedidos",
            section = AdminSection.PEDIDOS,
            title = "Pedidos",
            subtitle = "Solicitações de filmes e séries enviadas pelos usuários",
            category = AdminCategory.COMUNICACAO,
            icon = Icons.Default.PlaylistAddCheck,
            testTag = "admin_menu_pedidos"
        ),

        // 5. ATUALIZAÇÕES
        AdminModule(
            id = "versoes_app",
            section = AdminSection.ATUALIZACOES_APP,
            title = "Versões do Aplicativo",
            subtitle = "Criar, publicar e gerenciar novas versões de APK",
            category = AdminCategory.ATUALIZACOES,
            icon = Icons.Default.SystemUpdate,
            testTag = "admin_menu_versoes_app"
        ),
        AdminModule(
            id = "atualizacoes",
            section = AdminSection.ATUALIZACOES,
            title = "Atualizações",
            subtitle = "Campanhas de atualização, forçar updates e status global",
            category = AdminCategory.ATUALIZACOES,
            icon = Icons.Default.CloudSync,
            testTag = "admin_menu_atualizacoes"
        ),
        AdminModule(
            id = "controle_dispositivo",
            section = AdminSection.CONTROLE_REMOTO,
            title = "Controle por Dispositivo",
            subtitle = "Comandos remotos e atualizações por dispositivo",
            category = AdminCategory.ATUALIZACOES,
            icon = Icons.Default.SettingsRemote,
            testTag = "admin_menu_controle_dispositivo"
        ),

        // 6. SISTEMA
        AdminModule(
            id = "sincronizacao",
            section = AdminSection.SINCRONIZACAO,
            title = "Sincronização",
            subtitle = "Status em tempo real das coleções e sincronia imediata",
            category = AdminCategory.SISTEMA,
            icon = Icons.Default.Sync,
            testTag = "admin_menu_sincronizacao"
        ),
        AdminModule(
            id = "configuracoes",
            section = AdminSection.CONFIGURACOES,
            title = "Configurações",
            subtitle = "Parâmetros gerais, chaves e regras da plataforma",
            category = AdminCategory.SISTEMA,
            icon = Icons.Default.Settings,
            testTag = "admin_menu_configuracoes"
        ),
        AdminModule(
            id = "logs",
            section = AdminSection.LOGS,
            title = "Logs",
            subtitle = "Auditoria de ações em tempo real de administradores",
            category = AdminCategory.SISTEMA,
            icon = Icons.Default.History,
            testTag = "admin_menu_logs"
        ),
        AdminModule(
            id = "historico",
            section = AdminSection.HISTORICO,
            title = "Histórico",
            subtitle = "Histórico de importações, sincronizações e backups",
            category = AdminCategory.SISTEMA,
            icon = Icons.Default.ManageHistory,
            testTag = "admin_menu_historico"
        ),
        AdminModule(
            id = "status_firebase",
            section = AdminSection.STATUS_SISTEMA,
            title = "Status do Firebase",
            subtitle = "Diagnóstico e saúde dos serviços em nuvem",
            category = AdminCategory.SISTEMA,
            icon = Icons.Default.CheckCircle,
            testTag = "admin_menu_status_firebase"
        )
    )

    fun getModulesByCategory(): Map<AdminCategory, List<AdminModule>> {
        return modules.groupBy { it.category }
            .toSortedMap(compareBy { it.order })
    }

    fun findModule(section: AdminSection): AdminModule? {
        return modules.find { it.section == section }
    }
}
