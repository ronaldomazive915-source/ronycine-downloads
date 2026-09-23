package com.example.data.download

enum class StreamType {
    DIRECT_FILE,
    HLS_STREAM,
    EMBED_PAGE
}

object ExternalDownloadHelper {

    val IDM_PACKAGES = listOf(
        "idm.internet.download.manager",
        "idm.internet.download.manager.plus",
        "idm.internet.download.manager.lite"
    )

    val ADM_PACKAGES = listOf(
        "com.dv.adm",
        "com.dv.adm.pay"
    )

    fun sanitizeFileName(name: String): String {
        return name
            .replace(":", "_")
            .replace(Regex("""[/\\*?<>"|]"""), "")
            .trim()
    }

    fun formatMovieFileName(title: String, releaseYearOrDate: String?): String {
        val cleanTitle = sanitizeFileName(title)
        val year = if (!releaseYearOrDate.isNullOrBlank()) {
            val yearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").find(releaseYearOrDate)
            yearMatch?.value
        } else null

        return if (year != null) {
            "$cleanTitle ($year).mp4"
        } else {
            "$cleanTitle.mp4"
        }
    }

    fun formatEpisodeFileName(
        seriesTitle: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeTitle: String? = null
    ): String {
        val cleanSeries = sanitizeFileName(seriesTitle)
        val s = seasonNumber.toString().padStart(2, '0')
        val e = episodeNumber.toString().padStart(2, '0')
        val epCode = "S${s}E${e}"

        return if (!episodeTitle.isNullOrBlank()) {
            val cleanEp = sanitizeFileName(episodeTitle)
            "$cleanSeries - $epCode - $cleanEp.mp4"
        } else {
            "$cleanSeries - $epCode.mp4"
        }
    }
}
