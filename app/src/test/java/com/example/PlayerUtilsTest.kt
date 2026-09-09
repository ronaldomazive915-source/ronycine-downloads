package com.example

import com.example.data.local.MediaEntity
import com.example.data.remote.MegaEmbedPlayerConfig
import com.example.data.remote.MegaEmbedPlayerType
import com.example.data.remote.PlayerSource
import com.example.util.PlayerUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerUtilsTest {

    private fun createDummyMedia(isMovie: Boolean = true, tmdbId: Int = 1439930): MediaEntity {
        return MediaEntity(
            tmdbId = tmdbId,
            title = "Filme de Teste",
            mediaType = if (isMovie) "movie" else "tv",
            posterPath = null,
            backdropPath = null,
            overview = "Sinopse de teste",
            releaseYear = "2024",
            rating = 8.5,
            genres = "Ação, Aventura"
        )
    }

    private fun createMegaEmbedSource(player: String = "megaplay", color: String = "fb542b"): PlayerSource {
        return PlayerSource(
            id = "mgeb",
            name = "MegaEmbed",
            enabled = true,
            isDefault = true,
            internalPlayer = player,
            playerColor = color,
            movieTmdbUrl = "https://mgeb.top/embed/{tmdb_id}",
            tvTmdbUrl = "https://mgeb.top/embed/{tmdb_id}/{season_number}/{episode_number}"
        )
    }

    @Test
    fun testMegaPlayMovieUrl() {
        val source = createMegaEmbedSource(player = "megaplay", color = "#fb542b")
        val media = createDummyMedia(isMovie = true, tmdbId = 1439930)
        val url = PlayerUtils.buildPlayerUrl(source, media)

        assertEquals("https://mgeb.top/embed/1439930?player=megaplay#color:fb542b", url)
        assertTrue(url.indexOf("?player=") < url.indexOf("#color:"))
    }

    @Test
    fun testMegaTubeMovieUrl() {
        val source = createMegaEmbedSource(player = "megatube", color = "e50914")
        val media = createDummyMedia(isMovie = true, tmdbId = 1439930)
        val url = PlayerUtils.buildPlayerUrl(source, media)

        assertEquals("https://mgeb.top/embed/1439930?player=megatube#color:e50914", url)
        assertTrue(url.indexOf("?player=") < url.indexOf("#color:"))
    }

    @Test
    fun testVidstackMovieUrl() {
        val source = createMegaEmbedSource(player = "vidstack", color = "#00D26A")
        val media = createDummyMedia(isMovie = true, tmdbId = 1439930)
        val url = PlayerUtils.buildPlayerUrl(source, media)

        assertEquals("https://mgeb.top/embed/1439930?player=vidstack#color:00D26A", url)
        assertTrue(url.indexOf("?player=") < url.indexOf("#color:"))
    }

    @Test
    fun testClapprMovieUrl() {
        val source = createMegaEmbedSource(player = "clappr", color = "#1E88E5")
        val media = createDummyMedia(isMovie = true, tmdbId = 1439930)
        val url = PlayerUtils.buildPlayerUrl(source, media)

        assertEquals("https://mgeb.top/embed/1439930?player=clappr#color:1E88E5", url)
        assertTrue(url.indexOf("?player=") < url.indexOf("#color:"))
    }

    @Test
    fun testSeriesUrlStructure() {
        val source = createMegaEmbedSource(player = "megaplay", color = "fb542b")
        val media = createDummyMedia(isMovie = false, tmdbId = 1399)
        val url = PlayerUtils.buildPlayerUrl(source, media, season = 2, episode = 5)

        assertEquals("https://mgeb.top/embed/1399/2/5?player=megaplay#color:fb542b", url)
        assertTrue(url.indexOf("?player=") < url.indexOf("#color:"))
    }

    @Test
    fun testDirectBuildPlayerUrlFunction() {
        val url = PlayerUtils.buildPlayerUrl(
            provider = "MegaEmbed",
            mediaType = "movie",
            tmdbId = 1439930,
            player = "megatube",
            color = "#FF0055"
        )
        assertEquals("https://mgeb.top/embed/1439930?player=megatube#color:FF0055", url)
    }

    @Test
    fun testColorNormalization() {
        assertEquals("fb542b", MegaEmbedPlayerType.normalizeColor(null))
        assertEquals("fb542b", MegaEmbedPlayerType.normalizeColor(""))
        assertEquals("FF0000", MegaEmbedPlayerType.normalizeColor("#FF0000"))
        assertEquals("123456", MegaEmbedPlayerType.normalizeColor("123456"))
        assertEquals("ABC", MegaEmbedPlayerType.normalizeColor("#ABC"))
    }

    @Test
    fun testPlayerTypeDisplayNames() {
        assertEquals("MegaPlay (Premium)", MegaEmbedPlayerType.getDisplayName("megaplay"))
        assertEquals("MegaTube (Youtube)", MegaEmbedPlayerType.getDisplayName("megatube"))
        assertEquals("Vidstack (Moderno)", MegaEmbedPlayerType.getDisplayName("vidstack"))
        assertEquals("Clappr (Clássico)", MegaEmbedPlayerType.getDisplayName("clappr"))
    }

    @Test
    fun testVidSrcMovieLegendadoUrl() {
        val url = PlayerUtils.buildPlayerUrl(
            provider = "VidSrc",
            mediaType = "movie",
            tmdbId = 550,
            audio = "Legendado"
        )
        assertEquals("https://vidsrc.tw/embed/movie/550", url)
    }

    @Test
    fun testVidSrcTvLegendadoUrl() {
        val url = PlayerUtils.buildPlayerUrl(
            provider = "VidSrc",
            mediaType = "tv",
            tmdbId = 1399,
            season = 1,
            episode = 1,
            audio = "Legendado"
        )
        assertEquals("https://vidsrc.tw/embed/tv/1399/1/1", url)
    }
}
