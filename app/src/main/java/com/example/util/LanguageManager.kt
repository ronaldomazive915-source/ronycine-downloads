package com.example.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.example.ui.components.EmbedAudioSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class SupportedLanguage(
    val code: String,
    val displayName: String,
    val flagEmoji: String
)

object LanguageManager {

    val SUPPORTED_LANGUAGES = listOf(
        SupportedLanguage("pt-BR", "Português (Brasil)", "🇧🇷"),
        SupportedLanguage("en", "English", "🇺🇸"),
        SupportedLanguage("es", "Español", "🇪🇸"),
        SupportedLanguage("fr", "Français", "🇫🇷")
    )

    const val PLAYER_MODE_AUTO = "auto"
    const val PLAYER_MODE_DUBBED = "dublado"
    const val PLAYER_MODE_SUBTITLED = "legendado"

    private const val PREFS_NAME = "playfilme_prefs"
    private const val KEY_APP_LANGUAGE = "ronycine.language"
    private const val KEY_PLAYER_LANGUAGE = "ronycine.preferredPlayerLanguage"
    private const val KEY_LANGUAGE_SOURCE = "ronycine.languageSource"

    private val _appLanguage = MutableStateFlow("pt-BR")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    private val _preferredPlayerLanguage = MutableStateFlow(PLAYER_MODE_AUTO)
    val preferredPlayerLanguage: StateFlow<String> = _preferredPlayerLanguage.asStateFlow()

    private val _languageSource = MutableStateFlow("auto")
    val languageSource: StateFlow<String> = _languageSource.asStateFlow()

    private var isInitialized = false

    /**
     * Detects device language based on system default locale.
     */
    fun detectDeviceLanguage(): String {
        val locale = Locale.getDefault()
        val lang = locale.language.lowercase()
        return when (lang) {
            "pt" -> "pt-BR"
            "en" -> "en"
            "es" -> "es"
            "fr" -> "fr"
            else -> "pt-BR"
        }
    }

    /**
     * Initializes language manager from local SharedPreferences.
     */
    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val savedSource = prefs.getString(KEY_LANGUAGE_SOURCE, null)
        val savedLang = prefs.getString(KEY_APP_LANGUAGE, null)
        val savedPlayerLang = prefs.getString(KEY_PLAYER_LANGUAGE, PLAYER_MODE_AUTO) ?: PLAYER_MODE_AUTO

        if (!savedLang.isNullOrBlank()) {
            _appLanguage.value = normalizeLanguage(savedLang)
            _languageSource.value = savedSource ?: "manual"
        } else {
            // First time launch: detect device language
            val detected = detectDeviceLanguage()
            _appLanguage.value = detected
            _languageSource.value = "auto"
            prefs.edit()
                .putString(KEY_APP_LANGUAGE, detected)
                .putString(KEY_LANGUAGE_SOURCE, "auto")
                .apply()
        }

        _preferredPlayerLanguage.value = savedPlayerLang
        isInitialized = true
    }

    fun normalizeLanguage(code: String): String {
        val clean = code.trim().lowercase()
        return when {
            clean.startsWith("pt") -> "pt-BR"
            clean.startsWith("en") -> "en"
            clean.startsWith("es") -> "es"
            clean.startsWith("fr") -> "fr"
            else -> "pt-BR"
        }
    }

    fun setAppLanguage(context: Context, code: String, source: String = "manual") {
        val normalized = normalizeLanguage(code)
        _appLanguage.value = normalized
        _languageSource.value = source

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_APP_LANGUAGE, normalized)
            .putString(KEY_LANGUAGE_SOURCE, source)
            .apply()
    }

    fun setPreferredPlayerLanguage(context: Context, mode: String) {
        val cleanMode = mode.lowercase().trim()
        _preferredPlayerLanguage.value = cleanMode

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PLAYER_LANGUAGE, cleanMode)
            .apply()
    }

    /**
     * Resolves the playback mode (DUBLADO vs LEGENDADO) based on rules:
     * - "dublado" -> DUBLADO
     * - "legendado" -> LEGENDADO
     * - "auto":
     *    pt-BR -> DUBLADO
     *    en / es / fr -> LEGENDADO
     */
    fun resolvePreferredPlaybackMode(
        appLang: String = _appLanguage.value,
        playerPref: String = _preferredPlayerLanguage.value
    ): EmbedAudioSource {
        val cleanPlayer = playerPref.lowercase().trim()
        return when (cleanPlayer) {
            "dublado", "dubbed" -> EmbedAudioSource.DUBLADO
            "legendado", "subtitled" -> EmbedAudioSource.LEGENDADO
            else -> {
                if (normalizeLanguage(appLang) == "pt-BR") {
                    EmbedAudioSource.DUBLADO
                } else {
                    EmbedAudioSource.LEGENDADO
                }
            }
        }
    }

    /**
     * Translates a given key into the current language, with fallback to pt-BR and human readable text.
     */
    fun t(key: String, lang: String = _appLanguage.value): String {
        val langMap = translations[key]
        if (langMap == null) {
            return humanizeKey(key)
        }
        val text = langMap[normalizeLanguage(lang)]
            ?: langMap["pt-BR"]
            ?: langMap.values.firstOrNull()
        return text ?: humanizeKey(key)
    }

    private fun humanizeKey(key: String): String {
        val last = key.substringAfterLast('.')
        return last.replace('_', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    // Comprehensive Internationalization Dictionary
    private val translations: Map<String, Map<String, String>> = mapOf(
        // --- Navigation ---
        "nav.home" to mapOf("pt-BR" to "Início", "en" to "Home", "es" to "Inicio", "fr" to "Accueil"),
        "nav.explore" to mapOf("pt-BR" to "Explorar", "en" to "Explore", "es" to "Explorar", "fr" to "Explorer"),
        "nav.liveTV" to mapOf("pt-BR" to "TV ao Vivo", "en" to "Live TV", "es" to "TV en Vivo", "fr" to "TV en Direct"),
        "nav.trending" to mapOf("pt-BR" to "Em Alta", "en" to "Trending", "es" to "Tendencias", "fr" to "Tendances"),
        "nav.myList" to mapOf("pt-BR" to "Minha Lista", "en" to "My List", "es" to "Mi Lista", "fr" to "Ma Liste"),
        "nav.community" to mapOf("pt-BR" to "Comunidade", "en" to "Community", "es" to "Comunidad", "fr" to "Communauté"),
        "nav.profile" to mapOf("pt-BR" to "Meu Perfil", "en" to "My Profile", "es" to "Mi Perfil", "fr" to "Mon Profil"),
        "nav.settings" to mapOf("pt-BR" to "Configurações", "en" to "Settings", "es" to "Ajustes", "fr" to "Paramètres"),
        "nav.history" to mapOf("pt-BR" to "Histórico", "en" to "History", "es" to "Historial", "fr" to "Historique"),
        "nav.downloads" to mapOf("pt-BR" to "Downloads", "en" to "Downloads", "es" to "Descargas", "fr" to "Téléchargements"),
        "nav.admin" to mapOf("pt-BR" to "Painel Admin", "en" to "Admin Panel", "es" to "Panel Admin", "fr" to "Panneau Admin"),

        // --- Common Actions & Buttons ---
        "action.watch" to mapOf("pt-BR" to "Assistir", "en" to "Watch", "es" to "Ver", "fr" to "Regarder"),
        "action.watch_now" to mapOf("pt-BR" to "Assistir Agora", "en" to "Watch Now", "es" to "Ver Ahora", "fr" to "Regarder Maintenant"),
        "action.add_list" to mapOf("pt-BR" to "Minha Lista", "en" to "My List", "es" to "Mi Lista", "fr" to "Ma Liste"),
        "action.remove_list" to mapOf("pt-BR" to "Remover", "en" to "Remove", "es" to "Quitar", "fr" to "Retirer"),
        "action.share" to mapOf("pt-BR" to "Compartilhar", "en" to "Share", "es" to "Compartir", "fr" to "Partager"),
        "action.download" to mapOf("pt-BR" to "Baixar", "en" to "Download", "es" to "Descargar", "fr" to "Télécharger"),
        "action.trailer" to mapOf("pt-BR" to "Trailer", "en" to "Trailer", "es" to "Tráiler", "fr" to "Bande-annonce"),
        "action.save" to mapOf("pt-BR" to "Salvar", "en" to "Save", "es" to "Guardar", "fr" to "Enregistrer"),
        "action.cancel" to mapOf("pt-BR" to "Cancelar", "en" to "Cancel", "es" to "Cancelar", "fr" to "Annuler"),
        "action.close" to mapOf("pt-BR" to "Fechar", "en" to "Close", "es" to "Cerrar", "fr" to "Fermer"),
        "action.done" to mapOf("pt-BR" to "Concluído", "en" to "Done", "es" to "Hecho", "fr" to "Terminé"),
        "action.edit" to mapOf("pt-BR" to "Editar", "en" to "Edit", "es" to "Editar", "fr" to "Modifier"),
        "action.delete" to mapOf("pt-BR" to "Excluir", "en" to "Delete", "es" to "Eliminar", "fr" to "Supprimer"),
        "action.search" to mapOf("pt-BR" to "Pesquisar", "en" to "Search", "es" to "Buscar", "fr" to "Rechercher"),
        "action.filter" to mapOf("pt-BR" to "Filtrar", "en" to "Filter", "es" to "Filtrar", "fr" to "Filtrer"),
        "action.retry" to mapOf("pt-BR" to "Tentar Novamente", "en" to "Try Again", "es" to "Reintentar", "fr" to "Réessayer"),
        "action.back" to mapOf("pt-BR" to "Voltar", "en" to "Back", "es" to "Volver", "fr" to "Retour"),
        "action.view_all" to mapOf("pt-BR" to "Ver todos", "en" to "View all", "es" to "Ver todos", "fr" to "Voir tout"),
        "action.logout" to mapOf("pt-BR" to "Sair da Conta", "en" to "Sign Out", "es" to "Cerrar Sesión", "fr" to "Déconnexion"),

        // --- Audio Options ---
        "audio.dubbed" to mapOf("pt-BR" to "DUBLADO", "en" to "DUBBED", "es" to "DOBLADO", "fr" to "DOUBLÉ"),
        "audio.subtitled" to mapOf("pt-BR" to "LEGENDADO", "en" to "SUBTITLED", "es" to "SUBTITULADO", "fr" to "SOUS-TITRÉ"),
        "audio.auto" to mapOf("pt-BR" to "AUTOMÁTICO", "en" to "AUTOMATIC", "es" to "AUTOMÁTICO", "fr" to "AUTOMATIQUE"),

        // --- Home & Content Titles ---
        "home.featured" to mapOf("pt-BR" to "Filmes em Destaque", "en" to "Featured Movies", "es" to "Películas Destacadas", "fr" to "Films en Vedette"),
        "home.popular_series" to mapOf("pt-BR" to "Séries Populares", "en" to "Popular Series", "es" to "Series Populares", "fr" to "Séries Populaires"),
        "home.recently_added" to mapOf("pt-BR" to "Adicionados Recentemente", "en" to "Recently Added", "es" to "Añadidos Recientemente", "fr" to "Récemment Ajoutés"),
        "home.continue_watching" to mapOf("pt-BR" to "Continuar Assistindo", "en" to "Continue Watching", "es" to "Continuar Viendo", "fr" to "Continuer à Regarder"),
        "home.top_rated" to mapOf("pt-BR" to "Mais Vistos & Bem Avaliados", "en" to "Top Rated & Most Watched", "es" to "Más Vistos y Valorados", "fr" to "Les Mieux Notés"),
        "home.search_placeholder" to mapOf("pt-BR" to "Pesquisar filmes, séries...", "en" to "Search movies, series...", "es" to "Buscar películas, series...", "fr" to "Rechercher des films, séries..."),
        "home.all" to mapOf("pt-BR" to "Todos", "en" to "All", "es" to "Todos", "fr" to "Tous"),
        "home.movies" to mapOf("pt-BR" to "Filmes", "en" to "Movies", "es" to "Películas", "fr" to "Films"),
        "home.series" to mapOf("pt-BR" to "Séries", "en" to "Series", "es" to "Series", "fr" to "Séries"),
        "home.animes" to mapOf("pt-BR" to "Animes", "en" to "Animes", "es" to "Animes", "fr" to "Animes"),
        "home.doramas" to mapOf("pt-BR" to "Doramas", "en" to "Doramas", "es" to "Doramas", "fr" to "Doramas"),

        // --- Detail Screen & Media ---
        "detail.synopsis" to mapOf("pt-BR" to "Sinopse", "en" to "Synopsis", "es" to "Sinopsis", "fr" to "Synopsis"),
        "detail.cast" to mapOf("pt-BR" to "Elenco Principal", "en" to "Main Cast", "es" to "Reparto Principal", "fr" to "Casting Principal"),
        "detail.full_cast" to mapOf("pt-BR" to "Ver elenco completo", "en" to "View full cast", "es" to "Ver reparto completo", "fr" to "Voir tout le casting"),
        "detail.recommended" to mapOf("pt-BR" to "Você também pode gostar", "en" to "You might also like", "es" to "También te puede gustar", "fr" to "Vous aimerez aussi"),
        "detail.season" to mapOf("pt-BR" to "Temporada", "en" to "Season", "es" to "Temporada", "fr" to "Saison"),
        "detail.seasons" to mapOf("pt-BR" to "Temporadas", "en" to "Seasons", "es" to "Temporadas", "fr" to "Saisons"),
        "detail.episodes" to mapOf("pt-BR" to "Episódios", "en" to "Episodes", "es" to "Episodios", "fr" to "Épisodes"),
        "detail.next_episode" to mapOf("pt-BR" to "Próximo Episódio", "en" to "Next Episode", "es" to "Siguiente Episodio", "fr" to "Épisode Suivant"),
        "detail.prev_episode" to mapOf("pt-BR" to "Episódio Anterior", "en" to "Previous Episode", "es" to "Episodio Anterior", "fr" to "Épisode Précédent"),
        "detail.no_episodes" to mapOf("pt-BR" to "Nenhum episódio encontrado.", "en" to "No episodes found.", "es" to "No se encontraron episodios.", "fr" to "Aucun épisode trouvé."),

        // --- Settings Screen ---
        "settings.title" to mapOf("pt-BR" to "Configurações", "en" to "Settings", "es" to "Ajustes", "fr" to "Paramètres"),
        "settings.language" to mapOf("pt-BR" to "Idioma", "en" to "Language", "es" to "Idioma", "fr" to "Langue"),
        "settings.app_language" to mapOf("pt-BR" to "IDIOMA DO APLICATIVO", "en" to "APP LANGUAGE", "es" to "IDIOMA DE LA APLICACIÓN", "fr" to "LANGUE DE L'APPLICATION"),
        "settings.player_language" to mapOf("pt-BR" to "IDIOMA DO PLAYER", "en" to "PLAYER LANGUAGE", "es" to "IDIOMA DEL REPRODUCTOR", "fr" to "LANGUE DU LECTEUR"),
        "settings.playback_preference" to mapOf("pt-BR" to "Preferência de reprodução", "en" to "Playback preference", "es" to "Preferencia de reproducción", "fr" to "Préférence de lecture"),
        "settings.player_auto" to mapOf("pt-BR" to "Automático", "en" to "Automatic", "es" to "Automático", "fr" to "Automatique"),
        "settings.player_auto_desc" to mapOf(
            "pt-BR" to "Usar o idioma do aplicativo para escolher automaticamente.",
            "en" to "Use app language to choose automatically.",
            "es" to "Usar el idioma de la aplicación para elegir automáticamente.",
            "fr" to "Utiliser la langue de l'application pour choisir automatiquement."
        ),
        "settings.player_dubbed" to mapOf("pt-BR" to "Dublado", "en" to "Dubbed", "es" to "Doblado", "fr" to "Doublé"),
        "settings.player_dubbed_desc" to mapOf("pt-BR" to "Preferir áudio dublado.", "en" to "Prefer dubbed audio.", "es" to "Preferir audio doblado.", "fr" to "Préférer l'audio doublé."),
        "settings.player_subtitled" to mapOf("pt-BR" to "Legendado", "en" to "Subtitled", "es" to "Subtitulado", "fr" to "Sous-titré"),
        "settings.player_subtitled_desc" to mapOf("pt-BR" to "Preferir conteúdo legendado.", "en" to "Prefer subtitled content.", "es" to "Preferir contenido subtitulado.", "fr" to "Préférer le contenu sous-titré."),
        "settings.lang_auto_note" to mapOf(
            "pt-BR" to "O idioma do aplicativo pode ser detectado automaticamente pelo seu dispositivo. Você pode alterar essa preferência a qualquer momento.",
            "en" to "App language can be automatically detected by your device. You can change this preference at any time.",
            "es" to "El idioma de la aplicación se puede detectar automáticamente por su dispositivo. Puede cambiar esta preferencia en cualquier momento.",
            "fr" to "La langue de l'application peut être automatiquement détectée par votre appareil. Vous pouvez modifier cette préférence à tout moment."
        ),
        "settings.player_auto_note" to mapOf(
            "pt-BR" to "Quando definido como Automático, o RONYCINE escolhe Dublado ou Legendado de acordo com sua preferência de idioma.",
            "en" to "When set to Automatic, RONYCINE chooses Dubbed or Subtitled according to your language preference.",
            "es" to "Cuando se establece en Automático, RONYCINE elige Doblado o Subtitulado según su preferencia de idioma.",
            "fr" to "Lorsqu'il est réglé sur Automatique, RONYCINE choisit Doublé ou Sous-titré selon vos préférences linguistiques."
        ),
        "settings.notifications" to mapOf("pt-BR" to "Notificações", "en" to "Notifications", "es" to "Notificaciones", "fr" to "Notifications"),
        "settings.quality" to mapOf("pt-BR" to "Qualidade de Reprodução", "en" to "Playback Quality", "es" to "Calidad de Reproducción", "fr" to "Qualité de Lecture"),
        "settings.data_saver" to mapOf("pt-BR" to "Economia de Dados", "en" to "Data Saver", "es" to "Ahorro de Datos", "fr" to "Économie de Données"),
        "settings.autoplay" to mapOf("pt-BR" to "Reprodução Automática", "en" to "Autoplay", "es" to "Reproducción Automática", "fr" to "Lecture Automatique"),
        "settings.clear_cache" to mapOf("pt-BR" to "Limpar Cache", "en" to "Clear Cache", "es" to "Borrar Caché", "fr" to "Vider le Cache"),
        "settings.about" to mapOf("pt-BR" to "Sobre o RONYCINE", "en" to "About RONYCINE", "es" to "Acerca de RONYCINE", "fr" to "À propos de RONYCINE"),
        "settings.username" to mapOf("pt-BR" to "Nome de usuário", "en" to "Username", "es" to "Nombre de usuario", "fr" to "Nom d'utilisateur"),

        // --- Profile Management ---
        "profile.who_is_watching" to mapOf("pt-BR" to "Quem está assistindo?", "en" to "Who's watching?", "es" to "¿Quién está viendo?", "fr" to "Qui regarde?"),
        "profile.manage" to mapOf("pt-BR" to "GERENCIAR PERFIS", "en" to "MANAGE PROFILES", "es" to "GESTIONAR PERFILES", "fr" to "GÉRER LES PROFILS"),
        "profile.add" to mapOf("pt-BR" to "Adicionar Perfil", "en" to "Add Profile", "es" to "Añadir Perfil", "fr" to "Ajouter un Profil"),
        "profile.edit" to mapOf("pt-BR" to "Editar Perfil", "en" to "Edit Profile", "es" to "Editar Perfil", "fr" to "Modifier le Profil"),
        "profile.name" to mapOf("pt-BR" to "Nome do Perfil", "en" to "Profile Name", "es" to "Nombre del Perfil", "fr" to "Nom du Profil"),
        "profile.photo" to mapOf("pt-BR" to "Foto de Perfil", "en" to "Profile Photo", "es" to "Foto de Perfil", "fr" to "Photo de Profil"),
        "profile.take_photo" to mapOf("pt-BR" to "Tirar Foto com a Câmera", "en" to "Take Photo with Camera", "es" to "Tomar Foto con Cámara", "fr" to "Prendre une Photo"),
        "profile.choose_gallery" to mapOf("pt-BR" to "Escolher da Galeria", "en" to "Choose from Gallery", "es" to "Elegir de Galería", "fr" to "Choisir dans la Galerie"),
        "profile.choose_catalog" to mapOf("pt-BR" to "Catálogo de Avatares", "en" to "Avatar Catalog", "es" to "Catálogo de Avatares", "fr" to "Catalogue d'Avatars"),
        "profile.download_photo" to mapOf("pt-BR" to "Salvar / Baixar no Celular", "en" to "Save / Download to Phone", "es" to "Guardar / Descargar en Teléfono", "fr" to "Enregistrer sur le Téléphone"),
        "profile.remove_photo" to mapOf("pt-BR" to "Remover Foto de Perfil", "en" to "Remove Profile Photo", "es" to "Eliminar Foto de Perfil", "fr" to "Supprimer la Photo"),

        // --- Avatar Picker ---
        "avatar_picker.title" to mapOf("pt-BR" to "ESCOLHA SUA FOTO", "en" to "CHOOSE YOUR PHOTO", "es" to "ELIGE TU FOTO", "fr" to "CHOISISSEZ VOTRE PHOTO"),
        "avatar_picker.subtitle" to mapOf("pt-BR" to "Personagens, Animes, Séries e VIPs", "en" to "Characters, Anime, Series & VIPs", "es" to "Personajes, Anime, Series y VIPs", "fr" to "Personnages, Animes, Séries et VIPs"),
        "avatar_picker.search_placeholder" to mapOf("pt-BR" to "Buscar personagem ou série...", "en" to "Search character or series...", "es" to "Buscar personaje o serie...", "fr" to "Rechercher personnage ou série..."),
        "avatar_picker.use_initials" to mapOf("pt-BR" to "Usar minhas iniciais", "en" to "Use my initials", "es" to "Usar mis iniciales", "fr" to "Utiliser mes initiales"),
        "avatar_picker.no_characters" to mapOf("pt-BR" to "Nenhum personagem encontrado.", "en" to "No characters found.", "es" to "No se encontraron personajes.", "fr" to "Aucun personnage trouvé."),
        "avatar_picker.save" to mapOf("pt-BR" to "SALVAR AVATAR", "en" to "SAVE AVATAR", "es" to "GUARDAR AVATAR", "fr" to "ENREGISTRER L'AVATAR"),
        "avatar_picker.tab_characters" to mapOf("pt-BR" to "Personagens", "en" to "Characters", "es" to "Personajes", "fr" to "Personnages"),
        "avatar_picker.tab_my_photo" to mapOf("pt-BR" to "Minha Foto", "en" to "My Photo", "es" to "Mi Foto", "fr" to "Ma Photo"),

        // --- Auth ---
        "auth.login" to mapOf("pt-BR" to "Entrar", "en" to "Sign In", "es" to "Iniciar Sesión", "fr" to "Se Connecter"),
        "auth.create_account" to mapOf("pt-BR" to "Criar Conta", "en" to "Create Account", "es" to "Crear Cuenta", "fr" to "Créer un Compte"),
        "auth.email" to mapOf("pt-BR" to "E-mail", "en" to "Email", "es" to "Correo Electrónico", "fr" to "E-mail"),
        "auth.password" to mapOf("pt-BR" to "Senha", "en" to "Password", "es" to "Contraseña", "fr" to "Mot de Passe"),

        // --- States & Errors ---
        "state.loading" to mapOf("pt-BR" to "Carregando...", "en" to "Loading...", "es" to "Cargando...", "fr" to "Chargement..."),
        "state.empty_list" to mapOf("pt-BR" to "Sua lista está vazia.", "en" to "Your list is empty.", "es" to "Tu lista está vacía.", "fr" to "Votre liste est vide."),
        "state.error_network" to mapOf("pt-BR" to "Sem conexão com a internet.", "en" to "No internet connection.", "es" to "Sin conexión a internet.", "fr" to "Pas de connexion internet."),
        "state.success" to mapOf("pt-BR" to "Sucesso!", "en" to "Success!", "es" to "¡Éxito!", "fr" to "Succès!"),

        // --- Admin Panel ---
        "admin.dashboard" to mapOf("pt-BR" to "Dashboard", "en" to "Dashboard", "es" to "Panel de Control", "fr" to "Tableau de bord"),
        "admin.catalog" to mapOf("pt-BR" to "Catálogo", "en" to "Catalog", "es" to "Catálogo", "fr" to "Catalogue"),
        "admin.import_movies" to mapOf("pt-BR" to "Importar Filmes", "en" to "Import Movies", "es" to "Importar Películas", "fr" to "Importer des Films"),
        "admin.mass_import" to mapOf("pt-BR" to "Importação em Massa", "en" to "Mass Import", "es" to "Importación Masiva", "fr" to "Importation en Masse"),
        "admin.top10" to mapOf("pt-BR" to "TOP 10", "en" to "TOP 10", "es" to "TOP 10", "fr" to "TOP 10"),
        "admin.highlights" to mapOf("pt-BR" to "Destaques", "en" to "Highlights", "es" to "Destacados", "fr" to "En Vedette"),
        "admin.live_tv" to mapOf("pt-BR" to "TV ao Vivo", "en" to "Live TV", "es" to "TV en Vivo", "fr" to "TV en Direct"),
        "admin.users" to mapOf("pt-BR" to "Usuários", "en" to "Users", "es" to "Usuarios", "fr" to "Utilisateurs"),
        "admin.profiles" to mapOf("pt-BR" to "Perfis", "en" to "Profiles", "es" to "Perfiles", "fr" to "Profils"),
        "admin.devices" to mapOf("pt-BR" to "Dispositivos", "en" to "Devices", "es" to "Dispositivos", "fr" to "Appareils"),
        "admin.admins" to mapOf("pt-BR" to "Administradores", "en" to "Administrators", "es" to "Administradores", "fr" to "Administrateurs"),
        "admin.notifications" to mapOf("pt-BR" to "Notificações", "en" to "Notifications", "es" to "Notificaciones", "fr" to "Notifications"),
        "admin.requests" to mapOf("pt-BR" to "Pedidos", "en" to "Requests", "es" to "Solicitudes", "fr" to "Demandes"),
        "admin.app_versions" to mapOf("pt-BR" to "Versões do Aplicativo", "en" to "App Versions", "es" to "Versiones de la App", "fr" to "Versions de l'Application"),
        "admin.updates" to mapOf("pt-BR" to "Atualizações", "en" to "Updates", "es" to "Actualizaciones", "fr" to "Mises à Jour"),
        "admin.device_control" to mapOf("pt-BR" to "Controle por Dispositivo", "en" to "Device Control", "es" to "Control por Dispositivo", "fr" to "Contrôle par Appareil"),
        "admin.sync" to mapOf("pt-BR" to "Sincronização", "en" to "Sync", "es" to "Sincronización", "fr" to "Synchronisation"),
        "admin.settings" to mapOf("pt-BR" to "Configurações", "en" to "Settings", "es" to "Ajustes", "fr" to "Paramètres"),
        "admin.logs" to mapOf("pt-BR" to "Logs", "en" to "Logs", "es" to "Registros", "fr" to "Journaux"),
        "admin.history" to mapOf("pt-BR" to "Histórico", "en" to "History", "es" to "Historial", "fr" to "Historique"),
        "admin.system_status" to mapOf("pt-BR" to "Status do Firebase", "en" to "Firebase Status", "es" to "Estado de Firebase", "fr" to "Statut de Firebase"),

        // --- Player ---
        "player.restricted" to mapOf("pt-BR" to "CONTEÚDO RESTRITO", "en" to "RESTRICTED CONTENT", "es" to "CONTENIDO RESTRINGIDO", "fr" to "CONTENU RESTREINT"),
        "player.restricted_desc" to mapOf(
            "pt-BR" to "Este conteúdo é classificado como +18 e pode conter cenas impróprias para menores.",
            "en" to "This content is rated 18+ and may contain material unsuitable for minors.",
            "es" to "Este contenido está clasificado como +18 y puede contener escenas inapropiadas para menores.",
            "fr" to "Ce contenu est classé 18+ et peut contenir des scènes inadaptées aux mineurs."
        ),
        "player.yes_18" to mapOf("pt-BR" to "SIM, TENHO 18 ANOS", "en" to "YES, I AM 18+", "es" to "SÍ, TENGO 18 AÑOS", "fr" to "OUI, J'AI 18 ANS"),
        "player.error_load" to mapOf("pt-BR" to "Não foi possível carregar o player.", "en" to "Could not load player.", "es" to "No se pudo cargar el reproductor.", "fr" to "Impossible de charger le lecteur."),
        "player.close" to mapOf("pt-BR" to "Fechar Player", "en" to "Close Player", "es" to "Cerrar Reproductor", "fr" to "Fermer le Lecteur"),

        // --- Search ---
        "search.no_results" to mapOf("pt-BR" to "Nenhum resultado encontrado.", "en" to "No results found.", "es" to "No se encontraron resultados.", "fr" to "Aucun résultat trouvé."),
        "search.placeholder" to mapOf("pt-BR" to "Pesquisar filmes, séries, animes...", "en" to "Search movies, series, anime...", "es" to "Buscar películas, series, animes...", "fr" to "Rechercher films, séries, animes..."),

        // --- WhatsApp Group Invite ---
        "whatsapp_invite.title" to mapOf("pt-BR" to "Entre na comunidade RONYCINE", "en" to "Join the RONYCINE community", "es" to "Únete a la comunidade RONYCINE", "fr" to "Rejoignez la comunidade RONYCINE"),
        "whatsapp_invite.subtitle" to mapOf("pt-BR" to "Grupo Oficial no WhatsApp", "en" to "Official WhatsApp Group", "es" to "Grupo Oficial de WhatsApp", "fr" to "Groupe WhatsApp Officiel"),
        "whatsapp_invite.description" to mapOf(
            "pt-BR" to "Participe do nosso grupo oficial no WhatsApp e fique por dentro das novidades.",
            "en" to "Join our official WhatsApp group and stay up to date with the latest news.",
            "es" to "Participa en nuestro grupo oficial de WhatsApp y mantente al día con las novedades.",
            "fr" to "Rejoignez notre groupe WhatsApp officiel et restez informé des nouveautés."
        ),
        "whatsapp_invite.button" to mapOf("pt-BR" to "ENTRAR NO GRUPO", "en" to "JOIN GROUP", "es" to "ENTRAR AL GRUPO", "fr" to "REJOINDRE LE GROUPE"),
        "whatsapp_invite.dismiss" to mapOf("pt-BR" to "Agora não", "en" to "Not now", "es" to "Ahora no", "fr" to "Pas maintenant"),

        // --- Info / Institutional ---
        "info.title" to mapOf("pt-BR" to "Sobre o RONYCINE", "en" to "About RONYCINE", "es" to "Acerca de RONYCINE", "fr" to "À propos de RONYCINE"),
        "info.subtitle" to mapOf("pt-BR" to "Seu cinema particular", "en" to "Your private cinema", "es" to "Tu cine privado", "fr" to "Votre cinéma privé"),
        "info.description" to mapOf(
            "pt-BR" to "Entretenimento premium para você descobrir filmes, séries e conteúdos que combinam com você.",
            "en" to "Premium entertainment for you to discover movies, series and content that match your style.",
            "es" to "Entretenimiento premium para descubrir películas, series y contenidos a tu medida.",
            "fr" to "Divertissement premium pour découvrir des films, séries et contenus qui vous correspondent."
        ),
        "info.about_title" to mapOf("pt-BR" to "SOBRE O PROJETO", "en" to "ABOUT THE PROJECT", "es" to "SOBRE EL PROYECTO", "fr" to "À PROPOS DU PROJET"),
        "info.about_desc" to mapOf(
            "pt-BR" to "O RONYCINE é uma plataforma de entretenimento criada para proporcionar uma experiência moderna e organizada para descobrir filmes, séries e conteúdos.",
            "en" to "RONYCINE is an entertainment platform created to provide a modern and organized experience to discover movies, series and content.",
            "es" to "RONYCINE es una plataforma de entretenimiento creada para brindar una experiencia moderna y organizada para descubrir películas, series y contenidos.",
            "fr" to "RONYCINE est une plateforme de divertissement créée pour offrir une expérience moderne et organisée pour découvrir des films, séries et contenus."
        ),
        "info.contact_us" to mapOf("pt-BR" to "FALE CONOSCO", "en" to "CONTACT US", "es" to "CONTÁCTANOS", "fr" to "CONTACTEZ-NOUS"),
        "info.whatsapp_sub" to mapOf("pt-BR" to "Fale conosco", "en" to "Contact us", "es" to "Contáctanos", "fr" to "Contactez-nous"),
        "info.telegram_sub" to mapOf("pt-BR" to "Entre no nosso canal", "en" to "Join our channel", "es" to "Únete a nuestro canal", "fr" to "Rejoignez notre canal"),
        "info.website_title" to mapOf("pt-BR" to "NOSSO SITE", "en" to "OUR WEBSITE", "es" to "NUESTRO SITIO", "fr" to "NOTRE SITE WEB"),
        "info.website_sub" to mapOf("pt-BR" to "Conheça nosso site oficial", "en" to "Visit our official website", "es" to "Conoce nuestro sitio oficial", "fr" to "Découvrez notre site officiel"),
        "info.visit_website" to mapOf("pt-BR" to "VISITAR SITE", "en" to "VISIT WEBSITE", "es" to "VISITAR SITIO", "fr" to "VISITER LE SITE"),
        "info.help_title" to mapOf("pt-BR" to "PRECISA DE AJUDA?", "en" to "NEED HELP?", "es" to "¿NECESITAS AYUDA?", "fr" to "BESOIN D'AIDE ?"),
        "info.help_desc" to mapOf(
            "pt-BR" to "Entre em contato conosco caso tenha alguma dúvida ou problema.",
            "en" to "Contact us if you have any questions or issues.",
            "es" to "Ponte en contacto con nosotros si tienes alguna duda o problema.",
            "fr" to "Contactez-nous si vous avez des questions ou un problème."
        ),
        "info.support_btn" to mapOf("pt-BR" to "FALAR COM SUPORTE", "en" to "TALK TO SUPPORT", "es" to "HABLAR CON SOPORTE", "fr" to "CONTACTER LE SUPPORT"),

        // --- Community Screen ---
        "community.title" to mapOf("pt-BR" to "Comunidade RONYCINE", "en" to "RONYCINE Community", "es" to "Comunidad RONYCINE", "fr" to "Communauté RONYCINE"),
        "community.subtitle" to mapOf("pt-BR" to "Converse, descubra e compartilhe.", "en" to "Chat, discover and share.", "es" to "Habla, descubre y comparte.", "fr" to "Discutez, découvrez et partagez."),
        "community.placeholder" to mapOf("pt-BR" to "Escreva uma mensagem...", "en" to "Write a message...", "es" to "Escribe un mensaje...", "fr" to "Écrivez un message..."),
        "community.new_message" to mapOf("pt-BR" to "↓ Nova mensagem", "en" to "↓ New message", "es" to "↓ Nuevo mensaje", "fr" to "↓ Nouveau message"),
        "community.online" to mapOf("pt-BR" to "membros online", "en" to "members online", "es" to "miembros en línea", "fr" to "membres en ligne"),
        "community.online_fallback" to mapOf("pt-BR" to "Comunidade online", "en" to "Community online", "es" to "Comunidad en línea", "fr" to "Communauté en ligne"),
        "community.empty_title" to mapOf("pt-BR" to "A comunidade está começando", "en" to "The community is starting", "es" to "La comunidad está comenzando", "fr" to "La communauté commence"),
        "community.empty_sub" to mapOf("pt-BR" to "Converse com outros membros, compartilhe descobertas e encontre novos filmes e séries.", "en" to "Chat with other members, share discoveries and find new movies and series.", "es" to "Habla con otros miembros, comparte descubrimientos y encuentra nuevas películas y series.", "fr" to "Discutez avec d'autres membres, partagez vos découvertes et trouvez de nouveaux films et séries."),
        "community.suggest_movie" to mapOf("pt-BR" to "🎬 Recomende um filme", "en" to "🎬 Recommend a movie", "es" to "🎬 Recomienda una película", "fr" to "🎬 Recommander un film"),
        "community.suggest_series" to mapOf("pt-BR" to "📺 Fale sobre uma série", "en" to "📺 Talk about a series", "es" to "📺 Hablar sobre una serie", "fr" to "📺 Parler d'une série"),
        "community.suggest_chat" to mapOf("pt-BR" to "💬 Comece uma conversa", "en" to "💬 Start a conversation", "es" to "💬 Iniciar una conversación", "fr" to "💬 Commencer une discussion"),
        "community.offline_message" to mapOf("pt-BR" to "Não foi possível carregar as mensagens.", "en" to "Could not load messages.", "es" to "No se pudieron cargar los mensajes.", "fr" to "Impossible de charger les messages."),
        "community.offline_retry" to mapOf("pt-BR" to "Tentar novamente", "en" to "Try again", "es" to "Intentar de nuevo", "fr" to "Réessayer"),
        "community.ia_hint" to mapOf("pt-BR" to "Digite @ia para pedir uma recomendação", "en" to "Type @ia to ask for a recommendation", "es" to "Escribe @ia para pedir una recomendación", "fr" to "Écrivez @ia pour demander une recommandation"),
        "date.today" to mapOf("pt-BR" to "Hoje", "en" to "Today", "es" to "Hoy", "fr" to "Aujourd'hui"),
        "date.yesterday" to mapOf("pt-BR" to "Ontem", "en" to "Yesterday", "es" to "Ayer", "fr" to "Hier")
    )
}

/**
 * Composable helper to collect current language and translate key.
 */
@Composable
fun stringI18n(key: String): String {
    val currentLang = LanguageManager.appLanguage.collectAsState().value
    return LanguageManager.t(key, currentLang)
}
