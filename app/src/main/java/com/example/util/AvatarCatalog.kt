package com.example.util

data class PresetAvatar(
    val id: String,
    val name: String,
    val showName: String,
    val category: String, // "Personagens", "Avatar+", "VIP"
    val subCategory: String = "", // e.g. "A Series of Unfortunate Events", "Animes", "Séries", "Atores / Personagens", "Filmes"
    val characterName: String? = null,
    val actorName: String? = null,
    val url: String
) {
    // Backward compatibility for components reading .title
    val title: String get() = name
}

object AvatarCatalog {
    const val CATEGORY_CHARACTERS = "Personagens"
    const val CATEGORY_AVATAR_PLUS = "Avatar+"
    const val CATEGORY_VIP = "VIP"

    val categories = listOf(
        CATEGORY_CHARACTERS,
        CATEGORY_AVATAR_PLUS,
        CATEGORY_VIP
    )

    // All curated avatars deduplicated
    val avatars: List<PresetAvatar> = listOf(
        // ==========================================
        // 1. A SERIES OF UNFORTUNATE EVENTS (FRANQUIA DESTAQUE)
        // ==========================================
        PresetAvatar(
            id = "char_asoue_carmelita",
            name = "Carmelita Spats",
            showName = "A Series of Unfortunate Events",
            category = CATEGORY_CHARACTERS,
            subCategory = "A Series of Unfortunate Events",
            characterName = "Carmelita Spats",
            actorName = "Kitana Turnbull",
            url = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "char_asoue_olaf",
            name = "Count Olaf",
            showName = "A Series of Unfortunate Events",
            category = CATEGORY_CHARACTERS,
            subCategory = "A Series of Unfortunate Events",
            characterName = "Count Olaf",
            actorName = "Neil Patrick Harris",
            url = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "char_asoue_klaus",
            name = "Klaus Baudelaire",
            showName = "A Series of Unfortunate Events",
            category = CATEGORY_CHARACTERS,
            subCategory = "A Series of Unfortunate Events",
            characterName = "Klaus Baudelaire",
            actorName = "Louis Hynes",
            url = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "char_asoue_violet",
            name = "Violet Baudelaire",
            showName = "A Series of Unfortunate Events",
            category = CATEGORY_CHARACTERS,
            subCategory = "A Series of Unfortunate Events",
            characterName = "Violet Baudelaire",
            actorName = "Malina Weissman",
            url = "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "char_asoue_esme",
            name = "Esmé Squalor",
            showName = "A Series of Unfortunate Events",
            category = CATEGORY_CHARACTERS,
            subCategory = "A Series of Unfortunate Events",
            characterName = "Esmé Squalor",
            actorName = "Lucy Punch",
            url = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "char_asoue_lemony",
            name = "Lemony Snicket",
            showName = "A Series of Unfortunate Events",
            category = CATEGORY_CHARACTERS,
            subCategory = "A Series of Unfortunate Events",
            characterName = "Lemony Snicket",
            actorName = "Patrick Warburton",
            url = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=400&q=80"
        ),

        // ==========================================
        // 2. 🎌 ANIMES (PERSONAGENS FAMOSOS)
        // ==========================================
        PresetAvatar(
            id = "anime_naruto",
            name = "Naruto Uzumaki",
            showName = "Naruto Shippuden",
            category = CATEGORY_CHARACTERS,
            subCategory = "Animes",
            characterName = "Naruto Uzumaki",
            url = "https://images.unsplash.com/photo-1578632767115-351597cf2477?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "anime_sasuke",
            name = "Sasuke Uchiha",
            showName = "Naruto Shippuden",
            category = CATEGORY_CHARACTERS,
            subCategory = "Animes",
            characterName = "Sasuke Uchiha",
            url = "https://images.unsplash.com/photo-1563089145-599997674d42?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "anime_tanjiro",
            name = "Tanjiro Kamado",
            showName = "Demon Slayer",
            category = CATEGORY_CHARACTERS,
            subCategory = "Animes",
            characterName = "Tanjiro Kamado",
            url = "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "anime_nezuko",
            name = "Nezuko Kamado",
            showName = "Demon Slayer",
            category = CATEGORY_CHARACTERS,
            subCategory = "Animes",
            characterName = "Nezuko Kamado",
            url = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "anime_gojo",
            name = "Satoru Gojo",
            showName = "Jujutsu Kaisen",
            category = CATEGORY_CHARACTERS,
            subCategory = "Animes",
            characterName = "Satoru Gojo",
            url = "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "anime_luffy",
            name = "Monkey D. Luffy",
            showName = "One Piece",
            category = CATEGORY_CHARACTERS,
            subCategory = "Animes",
            characterName = "Monkey D. Luffy",
            url = "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "anime_zoro",
            name = "Roronoa Zoro",
            showName = "One Piece",
            category = CATEGORY_CHARACTERS,
            subCategory = "Animes",
            characterName = "Roronoa Zoro",
            url = "https://images.unsplash.com/photo-1501196354995-cbb51c65aaea?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "anime_levi",
            name = "Levi Ackerman",
            showName = "Attack on Titan",
            category = CATEGORY_CHARACTERS,
            subCategory = "Animes",
            characterName = "Levi Ackerman",
            url = "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "anime_anya",
            name = "Anya Forger",
            showName = "Spy x Family",
            category = CATEGORY_CHARACTERS,
            subCategory = "Animes",
            characterName = "Anya Forger",
            url = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "anime_goku",
            name = "Son Goku",
            showName = "Dragon Ball Z",
            category = CATEGORY_CHARACTERS,
            subCategory = "Animes",
            characterName = "Son Goku",
            url = "https://images.unsplash.com/photo-1552053831-71594a27632d?auto=format&fit=crop&w=400&q=80"
        ),

        // ==========================================
        // 3. 📺 SÉRIES POPULARES
        // ==========================================
        PresetAvatar(
            id = "series_eleven",
            name = "Eleven (011)",
            showName = "Stranger Things",
            category = CATEGORY_CHARACTERS,
            subCategory = "Séries",
            characterName = "Eleven",
            actorName = "Millie Bobby Brown",
            url = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "series_walter_white",
            name = "Walter White (Heisenberg)",
            showName = "Breaking Bad",
            category = CATEGORY_CHARACTERS,
            subCategory = "Séries",
            characterName = "Walter White",
            actorName = "Bryan Cranston",
            url = "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "series_wednesday",
            name = "Wednesday Addams",
            showName = "Wandinha",
            category = CATEGORY_CHARACTERS,
            subCategory = "Séries",
            characterName = "Wednesday Addams",
            actorName = "Jenna Ortega",
            url = "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "series_tommy_shelby",
            name = "Tommy Shelby",
            showName = "Peaky Blinders",
            category = CATEGORY_CHARACTERS,
            subCategory = "Séries",
            characterName = "Tommy Shelby",
            actorName = "Cillian Murphy",
            url = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "series_joel_miller",
            name = "Joel Miller",
            showName = "The Last of Us",
            category = CATEGORY_CHARACTERS,
            subCategory = "Séries",
            characterName = "Joel Miller",
            actorName = "Pedro Pascal",
            url = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "series_homelander",
            name = "Homelander",
            showName = "The Boys",
            category = CATEGORY_CHARACTERS,
            subCategory = "Séries",
            characterName = "Homelander",
            actorName = "Antony Starr",
            url = "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=400&q=80"
        ),

        // ==========================================
        // 4. ⭐ ATORES / PERSONAGENS DO CINEMA
        // ==========================================
        PresetAvatar(
            id = "actor_cillian_murphy",
            name = "Cillian Murphy",
            showName = "Oppenheimer",
            category = CATEGORY_CHARACTERS,
            subCategory = "Atores / Personagens",
            characterName = "J. Robert Oppenheimer",
            actorName = "Cillian Murphy",
            url = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "actor_pedro_pascal",
            name = "Pedro Pascal",
            showName = "The Mandalorian & The Last of Us",
            category = CATEGORY_CHARACTERS,
            subCategory = "Atores / Personagens",
            characterName = "Din Djarin / Joel",
            actorName = "Pedro Pascal",
            url = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "actor_zendaya",
            name = "Zendaya",
            showName = "Duna / Homem-Aranha",
            category = CATEGORY_CHARACTERS,
            subCategory = "Atores / Personagens",
            characterName = "Chani / MJ",
            actorName = "Zendaya",
            url = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "actor_margot_robbie",
            name = "Margot Robbie",
            showName = "Barbie / Arlequina",
            category = CATEGORY_CHARACTERS,
            subCategory = "Atores / Personagens",
            characterName = "Barbie / Harley Quinn",
            actorName = "Margot Robbie",
            url = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "actor_keanu_reeves",
            name = "Keanu Reeves",
            showName = "John Wick / Matrix",
            category = CATEGORY_CHARACTERS,
            subCategory = "Atores / Personagens",
            characterName = "John Wick / Neo",
            actorName = "Keanu Reeves",
            url = "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "actor_tom_cruise",
            name = "Tom Cruise",
            showName = "Top Gun / Missão Impossível",
            category = CATEGORY_CHARACTERS,
            subCategory = "Atores / Personagens",
            characterName = "Maverick / Ethan Hunt",
            actorName = "Tom Cruise",
            url = "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=400&q=80"
        ),

        // ==========================================
        // 5. 🚀 AVATAR+ (3D, CYBER & MODERN)
        // ==========================================
        PresetAvatar(
            id = "plus_neon_gamer",
            name = "Neon Cyberpunk",
            showName = "Estilo 3D Neon",
            category = CATEGORY_AVATAR_PLUS,
            subCategory = "3D & Cyber",
            url = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "plus_red_motion",
            name = "Crimson Vector",
            showName = "Design RONYCINE",
            category = CATEGORY_AVATAR_PLUS,
            subCategory = "3D & Cyber",
            url = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "plus_cyber_sphere",
            name = "Orbital Matrix",
            showName = "Geometria 3D",
            category = CATEGORY_AVATAR_PLUS,
            subCategory = "3D & Cyber",
            url = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "plus_holo_cube",
            name = "Holograma Sci-Fi",
            showName = "Render Futurista",
            category = CATEGORY_AVATAR_PLUS,
            subCategory = "3D & Cyber",
            url = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "plus_cosmic_fluid",
            name = "Cosmic Fluid",
            showName = "Nebulosa Cósmica",
            category = CATEGORY_AVATAR_PLUS,
            subCategory = "3D & Cyber",
            url = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "plus_aurora_violet",
            name = "Aurora Violet",
            showName = "Gradiente Prisma",
            category = CATEGORY_AVATAR_PLUS,
            subCategory = "3D & Cyber",
            url = "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=400&q=80"
        ),

        // ==========================================
        // 6. 👑 ESPECIAIS VIP (EXCLUSIVOS RONYCINE)
        // ==========================================
        PresetAvatar(
            id = "vip_gold_crown",
            name = "Golden Crown VIP",
            showName = "Clube VIP RONYCINE",
            category = CATEGORY_VIP,
            subCategory = "Exclusivos VIP",
            url = "https://images.unsplash.com/photo-1563089145-599997674d42?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "vip_crimson_emblem",
            name = "Emblema Carmesim",
            showName = "Coleção Fundador",
            category = CATEGORY_VIP,
            subCategory = "Exclusivos VIP",
            url = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "vip_founder_star",
            name = "Founder Star",
            showName = "Membro Fundador",
            category = CATEGORY_VIP,
            subCategory = "Exclusivos VIP",
            url = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?auto=format&fit=crop&w=400&q=80"
        ),
        PresetAvatar(
            id = "vip_diamond_master",
            name = "Diamond Streamer",
            showName = "Passe Diamante",
            category = CATEGORY_VIP,
            subCategory = "Exclusivos VIP",
            url = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=400&q=80"
        )
    )

    /**
     * Retorna avatares deduplicados por ID e URL.
     */
    fun getAllDeduplicated(): List<PresetAvatar> {
        val seenIds = mutableSetOf<String>()
        val seenUrls = mutableSetOf<String>()
        return avatars.filter { avatar ->
            val isNewId = seenIds.add(avatar.id.lowercase())
            val isNewUrl = seenUrls.add(avatar.url.lowercase())
            isNewId && isNewUrl
        }
    }

    /**
     * Filtra por categoria e consulta de busca (nome, obra, ator, personagem).
     */
    fun filter(category: String, query: String = ""): List<PresetAvatar> {
        val trimmed = query.trim().lowercase()
        return getAllDeduplicated().filter { avatar ->
            val matchCategory = avatar.category.equals(category, ignoreCase = true)
            val matchQuery = if (trimmed.isBlank()) {
                true
            } else {
                avatar.name.lowercase().contains(trimmed) ||
                avatar.showName.lowercase().contains(trimmed) ||
                (avatar.characterName?.lowercase()?.contains(trimmed) == true) ||
                (avatar.actorName?.lowercase()?.contains(trimmed) == true) ||
                avatar.subCategory.lowercase().contains(trimmed)
            }
            matchCategory && matchQuery
        }
    }

    /**
     * Retorna subgrupos de uma categoria (por exemplo, dentro de Personagens).
     */
    fun getSubCategories(category: String): List<String> {
        return getAllDeduplicated()
            .filter { it.category.equals(category, ignoreCase = true) }
            .map { it.subCategory.ifBlank { "Geral" } }
            .distinct()
    }

    fun getByCategory(category: String): List<PresetAvatar> {
        return filter(category = category, query = "")
    }
}
