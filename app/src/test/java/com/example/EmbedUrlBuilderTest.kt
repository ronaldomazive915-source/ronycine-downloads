package com.example

import com.example.ui.components.EmbedAudioSource
import com.example.ui.components.EmbedUrlBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbedUrlBuilderTest {

    @Test
    fun testMovieDubladoUrl() {
        val url = EmbedUrlBuilder.buildUrl(
            mediaType = "movie",
            tmdbId = 1439930,
            audioSource = EmbedAudioSource.DUBLADO
        )
        assertEquals(
            "https://mgeb.top/embed/1439930?player=vidstack#color:E50914",
            url
        )
    }

    @Test
    fun testMovieLegendadoUrl() {
        val url = EmbedUrlBuilder.buildUrl(
            mediaType = "movie",
            tmdbId = 1439930,
            audioSource = EmbedAudioSource.LEGENDADO
        )
        assertEquals(
            "https://vidsrc.tw/embed/movie/1439930",
            url
        )
    }

    @Test
    fun testSeriesDubladoUrl() {
        val url = EmbedUrlBuilder.buildUrl(
            mediaType = "tv",
            tmdbId = 1399,
            season = 1,
            episode = 1,
            audioSource = EmbedAudioSource.DUBLADO
        )
        assertEquals(
            "https://mgeb.top/embed/1399/1/1?player=vidstack#color:E50914",
            url
        )
    }

    @Test
    fun testSeriesLegendadoUrl() {
        val url = EmbedUrlBuilder.buildUrl(
            mediaType = "tv",
            tmdbId = 1399,
            season = 1,
            episode = 1,
            audioSource = EmbedAudioSource.LEGENDADO
        )
        assertEquals(
            "https://vidsrc.tw/embed/tv/1399/1/1",
            url
        )
    }

    @Test
    fun testImdbFallbackWhenTmdbIdMissing() {
        val movieUrl = EmbedUrlBuilder.buildUrl(
            mediaType = "movie",
            tmdbId = 0,
            imdbId = "tt1234567",
            audioSource = EmbedAudioSource.DUBLADO
        )
        assertEquals(
            "https://mgeb.top/embed/tt1234567?player=vidstack#color:E50914",
            movieUrl
        )

        val seriesUrl = EmbedUrlBuilder.buildUrl(
            mediaType = "tv",
            tmdbId = 0,
            season = 2,
            episode = 5,
            imdbId = "tt7654321",
            audioSource = EmbedAudioSource.DUBLADO
        )
        assertEquals(
            "https://mgeb.top/embed/tt7654321/2/5?player=vidstack#color:E50914",
            seriesUrl
        )
    }

    @Test
    fun testTmdbIdPriorityOverImdbId() {
        // When TMDB ID is available (> 0), it MUST be used, not IMDb ID
        val url = EmbedUrlBuilder.buildUrl(
            mediaType = "movie",
            tmdbId = 1439930,
            imdbId = "tt1234567",
            audioSource = EmbedAudioSource.DUBLADO
        )
        assertEquals(
            "https://mgeb.top/embed/1439930?player=vidstack#color:E50914",
            url
        )
    }

    @Test
    fun testParameterOrderPlayerBeforeHash() {
        val url = EmbedUrlBuilder.buildUrl(
            mediaType = "movie",
            tmdbId = 1439930,
            audioSource = EmbedAudioSource.DUBLADO
        )
        val playerIndex = url.indexOf("?player=vidstack")
        val hashIndex = url.indexOf("#color:E50914")
        assertTrue("player parameter must appear before hash color", playerIndex in 0..<hashIndex)
    }

    @Test
    fun testValidTmdbId() {
        assertTrue(EmbedUrlBuilder.isValidTmdbId(1439930))
        assertFalse(EmbedUrlBuilder.isValidTmdbId(0))
        assertFalse(EmbedUrlBuilder.isValidTmdbId(-1))
        assertFalse(EmbedUrlBuilder.isValidTmdbId(null))
    }
}
