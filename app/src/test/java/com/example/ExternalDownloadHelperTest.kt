package com.example

import com.example.data.download.ExternalDownloadHelper
import com.example.data.download.StreamType
import org.junit.Assert.*
import org.junit.Test

class ExternalDownloadHelperTest {

    @Test
    fun testFormatMovieFileName_withReleaseYear() {
        val fileName = ExternalDownloadHelper.formatMovieFileName("Interestelar", "2014")
        assertEquals("Interestelar (2014).mp4", fileName)
    }

    @Test
    fun testFormatMovieFileName_withReleaseDateLong() {
        val fileName = ExternalDownloadHelper.formatMovieFileName("Vingadores: Ultimato", "2019-04-26")
        // Sanitizes colon and extracts year
        assertEquals("Vingadores_ Ultimato (2019).mp4", fileName)
    }

    @Test
    fun testFormatMovieFileName_withoutReleaseYear() {
        val fileName = ExternalDownloadHelper.formatMovieFileName("Filme Teste", null)
        assertEquals("Filme Teste.mp4", fileName)
    }

    @Test
    fun testFormatEpisodeFileName_withTitle() {
        val fileName = ExternalDownloadHelper.formatEpisodeFileName(
            seriesTitle = "Stranger Things",
            seasonNumber = 1,
            episodeNumber = 1,
            episodeTitle = "O Desaparecimento de Will Byers"
        )
        assertEquals("Stranger Things - S01E01 - O Desaparecimento de Will Byers.mp4", fileName)
    }

    @Test
    fun testFormatEpisodeFileName_withoutTitle() {
        val fileName = ExternalDownloadHelper.formatEpisodeFileName(
            seriesTitle = "Breaking Bad",
            seasonNumber = 2,
            episodeNumber = 7,
            episodeTitle = null
        )
        assertEquals("Breaking Bad - S02E07.mp4", fileName)
    }

    @Test
    fun testSanitizeFileName_removesInvalidCharacters() {
        val dirty = "Filme: O Retorno / O Confronto * 2026? <Final> | Parte \"1\""
        val clean = ExternalDownloadHelper.sanitizeFileName(dirty)
        assertFalse(clean.contains(":"))
        assertFalse(clean.contains("/"))
        assertFalse(clean.contains("*"))
        assertFalse(clean.contains("?"))
        assertFalse(clean.contains("<"))
        assertFalse(clean.contains(">"))
        assertFalse(clean.contains("|"))
        assertFalse(clean.contains("\""))
    }

    @Test
    fun testDownloadPackagesConfigured() {
        assertTrue(ExternalDownloadHelper.IDM_PACKAGES.contains("idm.internet.download.manager"))
        assertTrue(ExternalDownloadHelper.IDM_PACKAGES.contains("idm.internet.download.manager.plus"))
        assertTrue(ExternalDownloadHelper.ADM_PACKAGES.contains("com.dv.adm"))
    }
}
