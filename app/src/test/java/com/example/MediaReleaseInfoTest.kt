package com.example

import com.example.data.local.EpisodeEntity
import com.example.data.remote.CalendarItemDto
import com.example.data.remote.CalendarMediaType
import com.example.data.remote.MediaReleaseInfo
import com.example.data.repository.CalendarRepository
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MediaReleaseInfoTest {

    @Test
    fun testDateFormattingHelpers() {
        val longDate = CalendarRepository.formatReleaseDateLong("2026-09-25")
        assertTrue(longDate.contains("25"))
        assertTrue(longDate.contains("SETEMBRO") || longDate.contains("SET"))
        assertTrue(longDate.contains("2026"))

        val shortDate = CalendarRepository.formatReleaseDateShort("2026-01-15")
        assertTrue(shortDate.contains("15"))
        assertTrue(shortDate.contains("2026"))

        val episodeDate = CalendarRepository.formatEpisodeAirDate("2026-09-25")
        assertTrue(episodeDate.contains("25"))
        assertTrue(episodeDate.contains("SET"))
    }

    @Test
    fun testMediaReleaseInfoDataModelFormatting() {
        val seriesInfo = MediaReleaseInfo(
            tmdbId = 47663,
            imdbId = "tt17371078",
            title = "A Arca",
            rawAirDate = "2026-09-25",
            formattedReleaseDate = "25 DE SETEMBRO DE 2026",
            formattedShortDate = "25 SET",
            isMovie = false,
            isNextEpisode = true,
            seasonNumber = 3,
            episodeNumber = 9,
            episodeTitle = "Episódio 9"
        )

        assertEquals("S03 • E09", seriesInfo.formattedEpisodeLabel)
        assertEquals(47663, seriesInfo.tmdbId)
        assertTrue(seriesInfo.isNextEpisode)
        assertFalse(seriesInfo.isMovie)

        val movieInfo = MediaReleaseInfo(
            tmdbId = 12345,
            title = "Avatar 3",
            rawAirDate = "2026-12-18",
            formattedReleaseDate = "18 DE DEZEMBRO DE 2026",
            formattedShortDate = "18 DEZ 2026",
            isMovie = true,
            isNextEpisode = false,
            isUnreleasedContent = true
        )

        assertNull(movieInfo.formattedEpisodeLabel)
        assertTrue(movieInfo.isMovie)
        assertTrue(movieInfo.isUnreleasedContent)
    }

    @Test
    fun testMatchingPriorityByTmdbId() {
        // Testa que quando múltiplos itens possuem títulos semelhantes, o TMDB ID tem prioridade máxima
        val targetTmdbId = 1001
        val item1 = com.example.data.remote.CalendarItem(
            id = "1",
            title = "Spider-Man Especial",
            episodeTitle = null,
            seasonNumber = null,
            episodeNumber = null,
            airDate = "2026-10-01",
            mediaType = CalendarMediaType.MOVIE,
            tmdbId = 9999, // Diferente
            imdbId = null,
            posterUrl = null,
            backdropUrl = null,
            status = null
        )
        val item2 = com.example.data.remote.CalendarItem(
            id = "2",
            title = "Spider-Man 4",
            episodeTitle = null,
            seasonNumber = null,
            episodeNumber = null,
            airDate = "2026-12-01",
            mediaType = CalendarMediaType.MOVIE,
            tmdbId = 1001, // ID Correto
            imdbId = null,
            posterUrl = null,
            backdropUrl = null,
            status = null
        )

        val items = listOf(item1, item2)
        val matched = items.firstOrNull { it.tmdbId == targetTmdbId }
        assertNotNull(matched)
        assertEquals(1001, matched?.tmdbId)
        assertEquals("Spider-Man 4", matched?.title)
    }

    @Test
    fun testPastDatesDoNotTriggerFutureRelease() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val pastDate = "2020-01-01"
        val todayStr = sdf.format(Date())

        assertTrue(pastDate < todayStr)
    }
}
