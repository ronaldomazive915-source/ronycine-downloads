package com.example

import com.example.data.remote.CalendarItem
import com.example.data.remote.CalendarItemDto
import com.example.data.remote.CalendarMediaType
import com.example.data.repository.CalendarRepository
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarAggregationDeduplicationTest {

    private val repository = CalendarRepository(
        org.robolectric.RuntimeEnvironment.getApplication()
    )

    @Test
    fun testUniqueKeyGenerationPriority() {
        // 1. TMDB ID Priority
        val keyWithTmdb = repository.generateUniqueKey(
            mediaType = CalendarMediaType.SERIES,
            tmdbId = 1399,
            imdbId = "tt0944947",
            title = "Game of Thrones",
            season = 2,
            episode = 8,
            airDate = "2026-09-25"
        )
        assertEquals("tv:1399:s2:e8", keyWithTmdb)

        val movieKeyWithTmdb = repository.generateUniqueKey(
            mediaType = CalendarMediaType.MOVIE,
            tmdbId = 550,
            imdbId = "tt0137523",
            title = "Fight Club",
            season = null,
            episode = null,
            airDate = "2026-10-10"
        )
        assertEquals("filme:tmdb:550", movieKeyWithTmdb)

        // 2. IMDb ID Priority (quando tmdbId é null)
        val keyWithImdbOnly = repository.generateUniqueKey(
            mediaType = CalendarMediaType.SERIES,
            tmdbId = null,
            imdbId = "tt123456",
            title = "Stranger Series",
            season = 1,
            episode = 4,
            airDate = "2026-09-28"
        )
        assertEquals("tv:imdb:tt123456:s1:e4", keyWithImdbOnly)

        // 3. Raw title + date fallback
        val rawKey = repository.generateUniqueKey(
            mediaType = CalendarMediaType.ANIME,
            tmdbId = null,
            imdbId = null,
            title = "Solo Leveling!!",
            season = 2,
            episode = 12,
            airDate = "2026-11-01"
        )
        assertEquals("anime:raw:solo leveling:2026-11-01:s2:e12", rawKey)
    }

    @Test
    fun testDeduplicationAndAttributeConsolidation() {
        // Item no Superflix
        val superflixDto = CalendarItemDto(
            title = "House of the Dragon",
            episode = "The Black Queen",
            season = 2,
            number = 8,
            airDate = "2026-09-25",
            type = 2,
            tmdbId = "94997",
            imdbId = "tt11198330",
            poster = "/poster_superflix.jpg",
            backdrop = null,
            status = "Confirmado"
        )

        // Mesmo item no WarezCDN com backdrop preenchido e poster diferente
        val warezDto = CalendarItemDto(
            title = "House of the Dragon",
            episode = "The Black Queen",
            season = 2,
            number = 8,
            airDate = "2026-09-25",
            type = 2,
            tmdbId = "94997",
            imdbId = "tt11198330",
            poster = "/poster_warez.jpg",
            backdrop = "/backdrop_warez.jpg",
            status = "Confirmado"
        )

        val item1 = repository.normalizeCalendarItem(superflixDto, CalendarRepository.SOURCE_SUPERFLIX)
        val item2 = repository.normalizeCalendarItem(warezDto, CalendarRepository.SOURCE_WAREZCDN)

        assertNotNull(item1)
        assertNotNull(item2)
        assertEquals(item1!!.id, item2!!.id)

        // Simular agregação central
        val map = LinkedHashMap<String, CalendarItem>()
        map[item1.id] = item1

        val existing = map[item2.id]
        assertNotNull(existing)

        // Consolidação
        val merged = existing!!.copy(
            sources = (existing.sources + item2.sources).distinct(),
            posterUrl = if (!existing.posterUrl.isNullOrBlank()) existing.posterUrl else item2.posterUrl,
            backdropUrl = if (!existing.backdropUrl.isNullOrBlank()) existing.backdropUrl else item2.backdropUrl,
            status = if (!existing.status.isNullOrBlank()) existing.status else item2.status
        )
        map[item2.id] = merged

        // Deve existir apenas 1 item consolidado
        assertEquals(1, map.size)
        val finalItem = map.values.first()

        assertEquals("tv:94997:s2:e8", finalItem.id)
        assertEquals(listOf("superflix", "warezcdn"), finalItem.sources)
        assertTrue(finalItem.posterUrl?.contains("poster_superflix.jpg") == true)
        assertTrue(finalItem.backdropUrl?.contains("backdrop_warez.jpg") == true)
    }

    @Test
    fun testSingleSourceItemsArePreserved() {
        // Item apenas no Superflix
        val superflixOnlyDto = CalendarItemDto(
            title = "Superflix Exclusivo",
            episode = "Episódio 1",
            season = 1,
            number = 1,
            airDate = "2026-09-20",
            type = 2,
            tmdbId = "11111",
            imdbId = null,
            poster = "/super_poster.jpg"
        )

        // Item apenas no WarezCDN
        val warezOnlyDto = CalendarItemDto(
            title = "Warez Exclusivo",
            episode = "Episódio 1",
            season = 1,
            number = 1,
            airDate = "2026-09-21",
            type = 2,
            tmdbId = "22222",
            imdbId = null,
            poster = "/warez_poster.jpg"
        )

        val item1 = repository.normalizeCalendarItem(superflixOnlyDto, CalendarRepository.SOURCE_SUPERFLIX)!!
        val item2 = repository.normalizeCalendarItem(warezOnlyDto, CalendarRepository.SOURCE_WAREZCDN)!!

        val map = LinkedHashMap<String, CalendarItem>()
        map[item1.id] = item1
        map[item2.id] = item2

        assertEquals(2, map.size)
        assertEquals(listOf("superflix"), map[item1.id]?.sources)
        assertEquals(listOf("warezcdn"), map[item2.id]?.sources)
    }

    @Test
    fun testChronologicalOrderingAsc() {
        val itemSept19 = CalendarItem(
            id = "1", title = "A", episodeTitle = null, seasonNumber = null, episodeNumber = null,
            airDate = "2026-09-19", mediaType = CalendarMediaType.MOVIE, tmdbId = 1, imdbId = null,
            posterUrl = null, backdropUrl = null, status = null
        )
        val itemSept25 = CalendarItem(
            id = "2", title = "B", episodeTitle = null, seasonNumber = null, episodeNumber = null,
            airDate = "2026-09-25", mediaType = CalendarMediaType.MOVIE, tmdbId = 2, imdbId = null,
            posterUrl = null, backdropUrl = null, status = null
        )
        val itemSept20 = CalendarItem(
            id = "3", title = "C", episodeTitle = null, seasonNumber = null, episodeNumber = null,
            airDate = "2026-09-20", mediaType = CalendarMediaType.MOVIE, tmdbId = 3, imdbId = null,
            posterUrl = null, backdropUrl = null, status = null
        )

        val unsorted = listOf(itemSept25, itemSept19, itemSept20)
        val sorted = unsorted.sortedWith(
            compareBy<CalendarItem> { it.airDate }
                .thenBy { it.title }
        )

        assertEquals("2026-09-19", sorted[0].airDate)
        assertEquals("2026-09-20", sorted[1].airDate)
        assertEquals("2026-09-25", sorted[2].airDate)
    }

    @Test
    fun testComputedStatusLabels() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayCal = Calendar.getInstance()
        val tomorrowCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val yesterdayCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }

        val todayStr = sdf.format(todayCal.time)
        val tomorrowStr = sdf.format(tomorrowCal.time)
        val yesterdayStr = sdf.format(yesterdayCal.time)

        val itemToday = CalendarItem(
            id = "today", title = "Hoje", episodeTitle = null, seasonNumber = 1, episodeNumber = 1,
            airDate = todayStr, mediaType = CalendarMediaType.SERIES, tmdbId = 1, imdbId = null,
            posterUrl = null, backdropUrl = null, status = null
        )
        assertEquals("● DISPONÍVEL HOJE", itemToday.getComputedStatus(todayStr, tomorrowStr))

        val itemTomorrow = CalendarItem(
            id = "tomorrow", title = "Amanhã", episodeTitle = null, seasonNumber = 1, episodeNumber = 1,
            airDate = tomorrowStr, mediaType = CalendarMediaType.SERIES, tmdbId = 2, imdbId = null,
            posterUrl = null, backdropUrl = null, status = null
        )
        assertEquals("AMANHÃ", itemTomorrow.getComputedStatus(todayStr, tomorrowStr))

        val itemPast = CalendarItem(
            id = "past", title = "Ontem", episodeTitle = null, seasonNumber = 1, episodeNumber = 1,
            airDate = yesterdayStr, mediaType = CalendarMediaType.SERIES, tmdbId = 3, imdbId = null,
            posterUrl = null, backdropUrl = null, status = null
        )
        assertEquals("✓ JÁ DISPONÍVEL", itemPast.getComputedStatus(todayStr, tomorrowStr))
    }
}
