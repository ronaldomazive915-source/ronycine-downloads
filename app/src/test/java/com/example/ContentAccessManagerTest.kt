package com.example

import com.example.data.local.MediaEntity
import com.example.data.remote.UserProfile
import com.example.util.ContentAccessManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentAccessManagerTest {

    private val adultProfile = UserProfile(
        id = "p_adult",
        name = "Beto Adulto",
        avatarUrl = "",
        isKidsProfile = false
    )

    private val kidsProfile = UserProfile(
        id = "p_kids",
        name = "Pedrinho Kids",
        avatarUrl = "",
        isKidsProfile = true
    )

    private fun createMedia(
        id: Int,
        title: String,
        genres: String,
        restricted18: Boolean = false,
        overview: String = "Uma história legal."
    ): MediaEntity {
        return MediaEntity(
            tmdbId = id,
            title = title,
            originalTitle = title,
            overview = overview,
            posterPath = "/poster$id.jpg",
            backdropPath = "/backdrop$id.jpg",
            mediaType = "movie",
            rating = 8.0,
            releaseYear = "2023",
            genres = genres,
            durationMinutes = 90,
            restricted18 = restricted18
        )
    }

    @Test
    fun `adult profile can access all contents`() {
        val horrorMedia = createMedia(1, "Invocação do Mal", "Terror, Mistério")
        val kidMedia = createMedia(2, "Toy Story", "Animação, Família, Aventura")

        assertTrue(ContentAccessManager.canProfileAccessContent(adultProfile, horrorMedia))
        assertTrue(ContentAccessManager.canProfileAccessContent(adultProfile, kidMedia))
    }

    @Test
    fun `kids profile can access verified kids friendly content`() {
        val safeAnimation = createMedia(10, "Toy Story", "Animação, Família, Aventura")
        val safeFamily = createMedia(11, "Paddington 2", "Família, Comédia, Aventura")
        val safeKids = createMedia(12, "Peppa Pig", "Kids, Animação")

        assertTrue(ContentAccessManager.canProfileAccessContent(kidsProfile, safeAnimation))
        assertTrue(ContentAccessManager.canProfileAccessContent(kidsProfile, safeFamily))
        assertTrue(ContentAccessManager.canProfileAccessContent(kidsProfile, safeKids))
    }

    @Test
    fun `kids profile blocks mature content, horror and adult ratings`() {
        val adultFlag = createMedia(20, "Filme Maduro", "Drama", restricted18 = true)
        val horror = createMedia(21, "O Exorcista", "Terror, Suspense")
        val crime = createMedia(22, "O Poderoso Chefão", "Crime, Drama, Violência")

        assertFalse(ContentAccessManager.canProfileAccessContent(kidsProfile, adultFlag))
        assertFalse(ContentAccessManager.canProfileAccessContent(kidsProfile, horror))
        assertFalse(ContentAccessManager.canProfileAccessContent(kidsProfile, crime))
    }

    @Test
    fun `kids profile blocks adult animation like south park or gore`() {
        val adultAnimation = createMedia(
            30,
            "South Park Maior e Pior",
            "Animação, Comédia",
            overview = "Palavrões e situações pesadas com gore."
        )
        val animeGore = createMedia(
            31,
            "Anime Sombrio",
            "Animação, Ação, Gore",
            overview = "Terror e sangue."
        )

        assertFalse(ContentAccessManager.canProfileAccessContent(kidsProfile, adultAnimation))
        assertFalse(ContentAccessManager.canProfileAccessContent(kidsProfile, animeGore))
    }

    @Test
    fun `kids profile blocks media without sufficient safety metadata`() {
        val emptyMetadata = createMedia(40, "Título Misterioso", "", overview = "")
        assertFalse(ContentAccessManager.canProfileAccessContent(kidsProfile, emptyMetadata))
    }

    @Test
    fun `filterContentForProfile filters immediately and correctly`() {
        val catalog = listOf(
            createMedia(100, "Peppa Pig", "Kids, Animação"),
            createMedia(101, "Deadpool", "Ação, Comédia", restricted18 = true),
            createMedia(102, "Frozen", "Animação, Família"),
            createMedia(103, "Massacre da Serra Elétrica", "Terror")
        )

        val kidsFiltered = ContentAccessManager.filterContentForProfile(kidsProfile, catalog)
        assertEquals(2, kidsFiltered.size)
        assertEquals("Peppa Pig", kidsFiltered[0].title)
        assertEquals("Frozen", kidsFiltered[1].title)

        val adultFiltered = ContentAccessManager.filterContentForProfile(adultProfile, catalog)
        assertEquals(4, adultFiltered.size)
    }
}
