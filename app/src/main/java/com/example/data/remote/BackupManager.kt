package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.local.*
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okio.buffer
import okio.sink
import okio.source
import java.io.*
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object BackupManager {

    private const val TAG = "BackupManager"
    private const val BATCH_SIZE = 150

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val movieAdapter = moshi.adapter(MediaEntity::class.java)
    private val episodeAdapter = moshi.adapter(EpisodeEntity::class.java)
    private val channelAdapter = moshi.adapter(ChannelEntity::class.java)
    private val featuredAdapter = moshi.adapter(FeaturedMediaEntity::class.java)
    private val megaEmbedAdapter = moshi.adapter(MegaEmbedConfig::class.java)

    /**
     * Obtém metadados básicos do arquivo selecionado (nome, tamanho em bytes e formatado).
     */
    fun getFileInfo(context: Context, uri: Uri): Pair<String, Long> {
        var name = "backup.json"
        var size = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to query file info: ${e.message}")
        }

        if (size <= 0L) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    size = afd.length
                }
            } catch (e: Throwable) {
                // Ignore fallback error
            }
        }

        return Pair(name, size)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formatted = DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units[digitGroups.coerceIn(0, units.size - 1)]}"
    }

    /**
     * Detecta se um InputStream é um arquivo ZIP baseado nos magic bytes ou nome.
     */
    private fun isZipFile(headerBytes: ByteArray, fileName: String): Boolean {
        if (fileName.lowercase().endsWith(".zip")) return true
        if (headerBytes.size >= 4) {
            if (headerBytes[0] == 0x50.toByte() && headerBytes[1] == 0x4B.toByte()) {
                return true
            }
        }
        return false
    }

    /**
     * Cria uma fonte limpa para leitura (descompacta stream ZIP se for ZIP ou lê direto se for JSON).
     */
    private fun openAppropriateInputStream(context: Context, uri: Uri, fileName: String): Pair<InputStream, Boolean> {
        val rawStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Não foi possível abrir o arquivo selecionado.")

        val buffered = BufferedInputStream(rawStream, 65536)
        buffered.mark(8)
        val header = ByteArray(4)
        val readCount = buffered.read(header, 0, 4)
        buffered.reset()

        val isZip = if (readCount >= 2) isZipFile(header, fileName) else fileName.lowercase().endsWith(".zip")

        if (isZip) {
            val zipStream = ZipInputStream(buffered)
            var entry: ZipEntry? = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && (entry.name.lowercase().endsWith(".json") || !entry.name.contains("."))) {
                    return Pair(zipStream, true)
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()
            throw IOException("Nenhum arquivo JSON de backup válido encontrado dentro do arquivo ZIP.")
        } else {
            return Pair(buffered, false)
        }
    }

    // --- MÉTODOS LEITORES TOLERANTES E RESILIENTES DE PROPRIEDADES ---

    private fun readNullableString(reader: JsonReader): String? {
        if (reader.peek() == JsonReader.Token.NULL) {
            reader.nextNull<Unit>()
            return null
        }
        val str = reader.nextString()
        return if (str.isBlank() || str.lowercase() == "null") null else str
    }

    private fun readIntOrString(reader: JsonReader): Int {
        return when (reader.peek()) {
            JsonReader.Token.NUMBER -> reader.nextInt()
            JsonReader.Token.STRING -> {
                val str = reader.nextString()
                str.toIntOrNull() ?: str.toDoubleOrNull()?.toInt() ?: 0
            }
            JsonReader.Token.BOOLEAN -> if (reader.nextBoolean()) 1 else 0
            JsonReader.Token.NULL -> { reader.nextNull<Unit>(); 0 }
            else -> { reader.skipValue(); 0 }
        }
    }

    private fun readLongOrString(reader: JsonReader): Long {
        return when (reader.peek()) {
            JsonReader.Token.NUMBER -> reader.nextLong()
            JsonReader.Token.STRING -> {
                val str = reader.nextString()
                str.toLongOrNull() ?: System.currentTimeMillis()
            }
            JsonReader.Token.NULL -> { reader.nextNull<Unit>(); System.currentTimeMillis() }
            else -> { reader.skipValue(); System.currentTimeMillis() }
        }
    }

    private fun readDoubleOrString(reader: JsonReader): Double {
        return when (reader.peek()) {
            JsonReader.Token.NUMBER -> reader.nextDouble()
            JsonReader.Token.STRING -> {
                val str = reader.nextString()
                str.toDoubleOrNull() ?: 0.0
            }
            JsonReader.Token.NULL -> { reader.nextNull<Unit>(); 0.0 }
            else -> { reader.skipValue(); 0.0 }
        }
    }

    private fun readBooleanOrInt(reader: JsonReader): Boolean {
        return when (reader.peek()) {
            JsonReader.Token.BOOLEAN -> reader.nextBoolean()
            JsonReader.Token.NUMBER -> reader.nextInt() != 0
            JsonReader.Token.STRING -> {
                val str = reader.nextString().lowercase()
                str == "true" || str == "1" || str == "yes" || str == "sim"
            }
            JsonReader.Token.NULL -> { reader.nextNull<Unit>(); false }
            else -> { reader.skipValue(); false }
        }
    }

    private fun readStringOrNumber(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonReader.Token.STRING -> reader.nextString()
            JsonReader.Token.NUMBER -> reader.nextString()
            JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
            JsonReader.Token.NULL -> { reader.nextNull<Unit>(); "" }
            else -> { reader.skipValue(); "" }
        }
    }

    private fun readStringOrArray(reader: JsonReader): String {
        if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
            val list = mutableListOf<String>()
            reader.beginArray()
            while (reader.hasNext()) {
                try { list.add(readStringOrNumber(reader)) } catch (e: Throwable) { reader.skipValue() }
            }
            reader.endArray()
            return list.filter { it.isNotBlank() }.joinToString(", ")
        } else {
            return readStringOrNumber(reader)
        }
    }

    // --- PARSERS DE OBJETOS DO DOMÍNIO ---

    private fun parseMediaEntityFromReader(
        jsonReader: JsonReader,
        defaultMediaType: String? = null
    ): Pair<MediaEntity, List<EpisodeEntity>>? {
        if (jsonReader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            try { jsonReader.skipValue() } catch (e: Throwable) {}
            return null
        }

        var tmdbId = 0
        var title = ""
        var originalTitle = ""
        var mediaType = defaultMediaType ?: "movie"
        var posterPath: String? = null
        var backdropPath: String? = null
        var overview = ""
        var releaseYear = ""
        var rating = 0.0
        var genres = ""
        var durationMinutes = 120
        var cast = ""
        var director = ""
        var seasonsCount = 1
        var episodesCount = 1
        var trailerKey: String? = null
        var isHeroFeatured = false
        var addedAt = System.currentTimeMillis()

        val extractedEpisodes = ArrayList<EpisodeEntity>()

        jsonReader.beginObject()
        while (jsonReader.hasNext()) {
            val key = jsonReader.nextName().lowercase().replace("_", "")
            when (key) {
                "tmdbid", "tmdb", "id" -> {
                    if (key == "id" && tmdbId > 0) {
                        // mantém tmdbId já extraído se for válido
                    } else {
                        try { tmdbId = readIntOrString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                    }
                }
                "title", "name", "nome", "titulo" -> {
                    if (title.isBlank()) {
                        try { title = jsonReader.nextString() } catch (t: Throwable) { jsonReader.skipValue() }
                    } else {
                        jsonReader.skipValue()
                    }
                }
                "originaltitle" -> {
                    try { originalTitle = jsonReader.nextString() } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "mediatype", "type", "kind", "typemedia" -> {
                    try {
                        val rawType = jsonReader.nextString().lowercase()
                        if (rawType.contains("tv") || rawType.contains("serie") || rawType.contains("show")) {
                            mediaType = "tv"
                        } else if (rawType.contains("movie") || rawType.contains("filme") || rawType.contains("film")) {
                            mediaType = "movie"
                        }
                    } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "posterpath", "poster", "cover", "capa" -> {
                    try { posterPath = readNullableString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "backdroppath", "backdrop", "banner" -> {
                    try { backdropPath = readNullableString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "overview", "description", "sinopse", "resumo" -> {
                    try { overview = jsonReader.nextString() } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "releaseyear", "releasedate", "year", "ano", "firstairdate" -> {
                    try {
                        val rawYear = readStringOrNumber(jsonReader)
                        releaseYear = if (rawYear.length >= 4) rawYear.substring(0, 4) else rawYear
                    } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "rating", "voteaverage", "nota", "score" -> {
                    try { rating = readDoubleOrString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "genres", "genre", "generos" -> {
                    try {
                        if (jsonReader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                            val gList = mutableListOf<String>()
                            jsonReader.beginArray()
                            while (jsonReader.hasNext()) {
                                try { gList.add(jsonReader.nextString()) } catch (t: Throwable) { jsonReader.skipValue() }
                            }
                            jsonReader.endArray()
                            genres = gList.joinToString(", ")
                        } else {
                            genres = jsonReader.nextString()
                        }
                    } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "durationminutes", "duration", "runtime" -> {
                    try { durationMinutes = readIntOrString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "cast", "elenco" -> {
                    try { cast = readStringOrArray(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "director", "diretor" -> {
                    try { director = readStringOrArray(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "seasonscount", "seasons" -> {
                    if (jsonReader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                        var sCount = 0
                        jsonReader.beginArray()
                        while (jsonReader.hasNext()) {
                            if (jsonReader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                                sCount++
                                parseSeasonForEpisodes(jsonReader, tmdbId, extractedEpisodes)
                            } else {
                                jsonReader.skipValue()
                            }
                        }
                        jsonReader.endArray()
                        if (sCount > 0) seasonsCount = sCount
                    } else {
                        try { seasonsCount = readIntOrString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                    }
                }
                "episodescount", "episodes", "episodios" -> {
                    if (jsonReader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                        var epCount = 0
                        jsonReader.beginArray()
                        while (jsonReader.hasNext()) {
                            val ep = parseEpisodeEntityFromReader(jsonReader, tmdbId)
                            if (ep != null) {
                                extractedEpisodes.add(ep)
                                epCount++
                            }
                        }
                        jsonReader.endArray()
                        if (epCount > 0) episodesCount = epCount
                    } else {
                        try { episodesCount = readIntOrString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                    }
                }
                "trailerkey", "trailer", "trailerurl" -> {
                    try { trailerKey = readNullableString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "isherofeatured", "isfeatured", "featured" -> {
                    try { isHeroFeatured = readBooleanOrInt(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "addedat", "timestamp", "createdat" -> {
                    try { addedAt = readLongOrString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                else -> {
                    try { jsonReader.skipValue() } catch (t: Throwable) {}
                }
            }
        }
        jsonReader.endObject()

        if (tmdbId <= 0 && title.isBlank()) return null
        if (tmdbId <= 0) tmdbId = Math.abs(title.hashCode())

        if (seasonsCount > 1 || extractedEpisodes.isNotEmpty()) {
            mediaType = "tv"
        }

        val entity = MediaEntity(
            id = 0,
            tmdbId = tmdbId,
            title = if (title.isBlank()) "Sem título ($tmdbId)" else title,
            originalTitle = originalTitle,
            mediaType = mediaType,
            posterPath = posterPath,
            backdropPath = backdropPath,
            overview = overview,
            releaseYear = if (releaseYear.isBlank()) "2024" else releaseYear,
            rating = rating,
            genres = genres,
            durationMinutes = durationMinutes,
            cast = cast,
            director = director,
            seasonsCount = seasonsCount,
            episodesCount = if (extractedEpisodes.isNotEmpty()) extractedEpisodes.size else episodesCount,
            trailerKey = trailerKey,
            isHeroFeatured = isHeroFeatured,
            addedAt = addedAt
        )

        val fixedEpisodes = extractedEpisodes.map { ep ->
            if (ep.mediaTmdbId <= 0) ep.copy(mediaTmdbId = tmdbId) else ep
        }

        return Pair(entity, fixedEpisodes)
    }

    private fun parseSeasonForEpisodes(
        jsonReader: JsonReader,
        parentTmdbId: Int,
        outEpisodes: MutableList<EpisodeEntity>
    ) {
        var seasonNum = 1
        jsonReader.beginObject()
        while (jsonReader.hasNext()) {
            val key = jsonReader.nextName().lowercase().replace("_", "")
            when (key) {
                "seasonnumber", "season", "temporada" -> {
                    try { seasonNum = readIntOrString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "episodes", "episodios" -> {
                    if (jsonReader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                        jsonReader.beginArray()
                        while (jsonReader.hasNext()) {
                            val ep = parseEpisodeEntityFromReader(jsonReader, parentTmdbId)
                            if (ep != null) {
                                outEpisodes.add(if (ep.seasonNumber <= 0) ep.copy(seasonNumber = seasonNum) else ep)
                            }
                        }
                        jsonReader.endArray()
                    } else {
                        jsonReader.skipValue()
                    }
                }
                else -> jsonReader.skipValue()
            }
        }
        jsonReader.endObject()
    }

    private fun parseEpisodeEntityFromReader(
        jsonReader: JsonReader,
        defaultMediaTmdbId: Int = 0
    ): EpisodeEntity? {
        if (jsonReader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            try { jsonReader.skipValue() } catch (e: Throwable) {}
            return null
        }

        var mediaTmdbId = defaultMediaTmdbId
        var seasonNumber = 1
        var episodeNumber = 1
        var title = ""
        var overview = ""
        var duration = "45 min"
        var stillPath: String? = null
        var videoUrl: String? = null

        jsonReader.beginObject()
        while (jsonReader.hasNext()) {
            val key = jsonReader.nextName().lowercase().replace("_", "")
            when (key) {
                "mediatmdbid", "mediaid", "showid", "seriesid", "tmdbid" -> {
                    try { mediaTmdbId = readIntOrString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "seasonnumber", "season", "temporada" -> {
                    try { seasonNumber = readIntOrString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "episodenumber", "episode", "episodio" -> {
                    try { episodeNumber = readIntOrString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "title", "name", "nome", "titulo" -> {
                    try { title = jsonReader.nextString() } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "overview", "description", "sinopse" -> {
                    try { overview = jsonReader.nextString() } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "duration", "runtime", "duracao" -> {
                    try { duration = readStringOrNumber(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "stillpath", "still", "image", "thumb", "thumbnail" -> {
                    try { stillPath = readNullableString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "videourl", "url", "streamurl", "link", "stream" -> {
                    try { videoUrl = readNullableString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                else -> {
                    try { jsonReader.skipValue() } catch (t: Throwable) {}
                }
            }
        }
        jsonReader.endObject()

        if (title.isBlank()) title = "Episódio $episodeNumber"

        return EpisodeEntity(
            mediaTmdbId = mediaTmdbId,
            seasonNumber = seasonNumber.coerceAtLeast(1),
            episodeNumber = episodeNumber.coerceAtLeast(1),
            title = title,
            overview = overview,
            duration = if (duration.isBlank()) "45 min" else duration,
            stillPath = stillPath,
            videoUrl = videoUrl
        )
    }

    private fun parseChannelEntityFromReader(jsonReader: JsonReader): ChannelEntity? {
        if (jsonReader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            try { jsonReader.skipValue() } catch (e: Throwable) {}
            return null
        }

        var id = ""
        var name = ""
        var category = "Geral"
        var logoUrl = ""
        var streamUrl = ""
        var isOnline = true
        var addedAt = System.currentTimeMillis()

        jsonReader.beginObject()
        while (jsonReader.hasNext()) {
            val key = jsonReader.nextName().lowercase().replace("_", "")
            when (key) {
                "id", "channelid" -> {
                    try { id = readStringOrNumber(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "name", "title", "nome" -> {
                    try { name = jsonReader.nextString() } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "category", "categoria", "group" -> {
                    try { category = jsonReader.nextString() } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "logourl", "logo", "icon" -> {
                    try { logoUrl = readNullableString(jsonReader) ?: "" } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "streamurl", "url", "stream", "link" -> {
                    try { streamUrl = readNullableString(jsonReader) ?: "" } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "isonline", "online", "active" -> {
                    try { isOnline = readBooleanOrInt(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "addedat", "timestamp" -> {
                    try { addedAt = readLongOrString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                else -> {
                    try { jsonReader.skipValue() } catch (t: Throwable) {}
                }
            }
        }
        jsonReader.endObject()

        if (name.isBlank() && streamUrl.isBlank()) return null
        if (id.isBlank()) id = "ch_${Math.abs((name + streamUrl).hashCode())}"

        return ChannelEntity(
            id = id,
            name = if (name.isBlank()) "Canal Sem Nome" else name,
            category = if (category.isBlank()) "Geral" else category,
            logoUrl = logoUrl,
            streamUrl = streamUrl,
            isOnline = isOnline,
            addedAt = addedAt
        )
    }

    private fun parseFeaturedMediaEntityFromReader(jsonReader: JsonReader): FeaturedMediaEntity? {
        if (jsonReader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            try { jsonReader.skipValue() } catch (e: Throwable) {}
            return null
        }

        var mediaTmdbId = 0
        var mediaType = "movie"
        var trailerUrl = ""
        var autoPlayTrailer = false
        var displayOrder = 0
        var isActive = true
        var updatedAt = System.currentTimeMillis()

        jsonReader.beginObject()
        while (jsonReader.hasNext()) {
            val key = jsonReader.nextName().lowercase().replace("_", "")
            when (key) {
                "mediatmdbid", "tmdbid", "id" -> {
                    try { mediaTmdbId = readIntOrString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "mediatype", "type" -> {
                    try { mediaType = jsonReader.nextString() } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "trailerurl", "trailer" -> {
                    try { trailerUrl = readNullableString(jsonReader) ?: "" } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "autoplaytrailer", "autoplay" -> {
                    try { autoPlayTrailer = readBooleanOrInt(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "displayorder", "order" -> {
                    try { displayOrder = readIntOrString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "isactive", "active" -> {
                    try { isActive = readBooleanOrInt(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "updatedat", "timestamp" -> {
                    try { updatedAt = readLongOrString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                else -> jsonReader.skipValue()
            }
        }
        jsonReader.endObject()

        if (mediaTmdbId <= 0) return null

        return FeaturedMediaEntity(
            mediaTmdbId = mediaTmdbId,
            mediaType = mediaType,
            trailerUrl = trailerUrl,
            autoPlayTrailer = autoPlayTrailer,
            displayOrder = displayOrder,
            isActive = isActive,
            updatedAt = updatedAt
        )
    }

    private fun parseMegaEmbedConfigFromReader(jsonReader: JsonReader): MegaEmbedConfig? {
        if (jsonReader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            try { jsonReader.skipValue() } catch (e: Throwable) {}
            return null
        }

        var defaultPlayer = "megaplay"
        var defaultLanguage = "dubbed"
        var colorHex = "E50914"
        var baseDomain = "https://mgeb.top"
        var cacheHours = 2
        var isAutoSyncEnabled = true

        jsonReader.beginObject()
        while (jsonReader.hasNext()) {
            val key = jsonReader.nextName().lowercase().replace("_", "")
            when (key) {
                "defaultplayer", "player" -> {
                    try { defaultPlayer = jsonReader.nextString() } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "defaultlanguage", "language", "idioma" -> {
                    try { defaultLanguage = jsonReader.nextString() } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "colorhex", "color" -> {
                    try { colorHex = jsonReader.nextString() } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "basedomain", "domain", "url", "baseurl" -> {
                    try { baseDomain = jsonReader.nextString() } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "cachehours", "cache" -> {
                    try { cacheHours = readIntOrString(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                "isautosyncenabled", "autosync", "enabled" -> {
                    try { isAutoSyncEnabled = readBooleanOrInt(jsonReader) } catch (t: Throwable) { jsonReader.skipValue() }
                }
                else -> jsonReader.skipValue()
            }
        }
        jsonReader.endObject()

        return MegaEmbedConfig(
            defaultPlayer = defaultPlayer,
            defaultLanguage = defaultLanguage,
            colorHex = colorHex,
            baseDomain = baseDomain,
            cacheHours = cacheHours,
            isAutoSyncEnabled = isAutoSyncEnabled
        )
    }

    /**
     * Inspeciona e valida o arquivo de backup de forma ultrarrápida usando streaming,
     * contando os itens sem carregar o arquivo nem os dados completos na memória RAM.
     */
    suspend fun inspectBackupFile(context: Context, uri: Uri): BackupPreviewInfo = withContext(Dispatchers.IO) {
        val (fileName, fileSize) = getFileInfo(context, uri)
        val formattedSize = formatFileSize(fileSize)

        Log.d(TAG, "[BACKUP] Arquivo recebido: $fileName | Tamanho: $formattedSize ($fileSize bytes)")

        var streamPair: Pair<InputStream, Boolean>? = null
        var jsonReader: JsonReader? = null

        try {
            streamPair = openAppropriateInputStream(context, uri, fileName)
            val inputStream = streamPair.first
            val isZip = streamPair.second

            val source = inputStream.source().buffer()
            jsonReader = JsonReader.of(source)
            jsonReader.isLenient = true

            var version = 100
            var timestamp = System.currentTimeMillis()
            var moviesCount = 0
            var seriesCount = 0
            var episodesCount = 0
            var channelsCount = 0
            var featuredCount = 0
            var hasConfig = false
            var totalKeysScanned = 0

            fun scanObject(reader: JsonReader, depth: Int = 0) {
                if (depth > 5) return
                reader.beginObject()
                while (reader.hasNext()) {
                    totalKeysScanned++
                    val rawKey = reader.nextName()
                    val key = rawKey.lowercase().replace("_", "")

                    when (key) {
                        "version", "schemaversion", "appversion" -> {
                            try { version = readIntOrString(reader) } catch (t: Throwable) { reader.skipValue() }
                        }
                        "timestamp", "createdat", "date" -> {
                            try { timestamp = readLongOrString(reader) } catch (t: Throwable) { reader.skipValue() }
                        }
                        "movies", "filmes", "movie", "films" -> {
                            if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val pair = parseMediaEntityFromReader(reader, "movie")
                                    if (pair != null) {
                                        moviesCount++
                                        episodesCount += pair.second.size
                                    }
                                }
                                reader.endArray()
                            } else {
                                reader.skipValue()
                            }
                        }
                        "series", "serie", "tv", "shows", "tvshows" -> {
                            if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val pair = parseMediaEntityFromReader(reader, "tv")
                                    if (pair != null) {
                                        seriesCount++
                                        episodesCount += pair.second.size
                                    }
                                }
                                reader.endArray()
                            } else {
                                reader.skipValue()
                            }
                        }
                        "catalog", "catalogo", "media", "items" -> {
                            if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val pair = parseMediaEntityFromReader(reader, null)
                                    if (pair != null) {
                                        if (pair.first.mediaType == "tv") {
                                            seriesCount++
                                        } else {
                                            moviesCount++
                                        }
                                        episodesCount += pair.second.size
                                    }
                                }
                                reader.endArray()
                            } else if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                                scanObject(reader, depth + 1)
                            } else {
                                reader.skipValue()
                            }
                        }
                        "episodes", "episodios" -> {
                            if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val ep = parseEpisodeEntityFromReader(reader)
                                    if (ep != null) {
                                        episodesCount++
                                    }
                                }
                                reader.endArray()
                            } else {
                                reader.skipValue()
                            }
                        }
                        "channels", "canais", "livechannels", "streams", "tvchannels" -> {
                            if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val ch = parseChannelEntityFromReader(reader)
                                    if (ch != null) {
                                        channelsCount++
                                    }
                                }
                                reader.endArray()
                            } else {
                                reader.skipValue()
                            }
                        }
                        "featured", "destaques", "featuredmedia", "hero" -> {
                            if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val ft = parseFeaturedMediaEntityFromReader(reader)
                                    if (ft != null) {
                                        featuredCount++
                                    }
                                }
                                reader.endArray()
                            } else {
                                reader.skipValue()
                            }
                        }
                        "megaembedconfig", "config", "settings", "configuration" -> {
                            hasConfig = true
                            reader.skipValue()
                        }
                        "data", "collections", "db", "backup", "payload", "result", "content" -> {
                            if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                                scanObject(reader, depth + 1)
                            } else if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val pair = parseMediaEntityFromReader(reader, null)
                                    if (pair != null) {
                                        if (pair.first.mediaType == "tv") seriesCount++ else moviesCount++
                                        episodesCount += pair.second.size
                                    }
                                }
                                reader.endArray()
                            } else {
                                reader.skipValue()
                            }
                        }
                        else -> {
                            if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                                scanObject(reader, depth + 1)
                            } else if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val pair = parseMediaEntityFromReader(reader, null)
                                    if (pair != null) {
                                        if (pair.first.mediaType == "tv") seriesCount++ else moviesCount++
                                        episodesCount += pair.second.size
                                    }
                                }
                                reader.endArray()
                            } else {
                                reader.skipValue()
                            }
                        }
                    }
                }
                reader.endObject()
            }

            val token = jsonReader.peek()
            Log.d(TAG, "[BACKUP] Token inicial do JSON: $token")

            if (token == JsonReader.Token.BEGIN_OBJECT) {
                scanObject(jsonReader)
            } else if (token == JsonReader.Token.BEGIN_ARRAY) {
                jsonReader.beginArray()
                while (jsonReader.hasNext()) {
                    val pair = parseMediaEntityFromReader(jsonReader, null)
                    if (pair != null) {
                        if (pair.first.mediaType == "tv") seriesCount++ else moviesCount++
                        episodesCount += pair.second.size
                    }
                }
                jsonReader.endArray()
            } else {
                return@withContext BackupPreviewInfo(
                    uri = uri,
                    fileName = fileName,
                    fileSizeFormatted = formattedSize,
                    fileSizeBytes = fileSize,
                    isZip = isZip,
                    isValid = false,
                    errorMessage = "Arquivo de backup inválido ou corrompido."
                )
            }

            val totalItems = moviesCount + seriesCount + episodesCount + channelsCount + featuredCount
            Log.d(TAG, "[BACKUP] Análise concluída: Filmes=$moviesCount, Séries=$seriesCount, Episódios=$episodesCount, Canais=$channelsCount, Destaques=$featuredCount, HasConfig=$hasConfig, TotalKeys=$totalKeysScanned")

            if (totalItems == 0 && !hasConfig) {
                val errorMsg = if (fileSize > 1000) {
                    "Não foi possível identificar a estrutura deste backup ($formattedSize). Verifique se o arquivo pertence ao RONYCINE."
                } else {
                    "Este backup não contém dados de catálogo."
                }
                return@withContext BackupPreviewInfo(
                    uri = uri,
                    fileName = fileName,
                    fileSizeFormatted = formattedSize,
                    fileSizeBytes = fileSize,
                    isZip = isZip,
                    isValid = false,
                    errorMessage = errorMsg
                )
            }

            return@withContext BackupPreviewInfo(
                uri = uri,
                fileName = fileName,
                fileSizeFormatted = formattedSize,
                fileSizeBytes = fileSize,
                isZip = isZip,
                version = version,
                timestamp = timestamp,
                moviesCount = moviesCount,
                seriesCount = seriesCount,
                episodesCount = episodesCount,
                channelsCount = channelsCount,
                featuredCount = featuredCount,
                hasConfig = hasConfig,
                isValid = true
            )

        } catch (t: Throwable) {
            Log.e(TAG, "[BACKUP] ERRO na análise do arquivo: ${t.message}", t)
            return@withContext BackupPreviewInfo(
                uri = uri,
                fileName = fileName,
                fileSizeFormatted = formattedSize,
                fileSizeBytes = fileSize,
                isZip = false,
                isValid = false,
                errorMessage = "Arquivo de backup inválido ou corrompido: ${t.localizedMessage}"
            )
        } finally {
            try { jsonReader?.close() } catch (e: Throwable) {}
            try { streamPair?.first?.close() } catch (e: Throwable) {}
        }
    }

    /**
     * Executa a restauração completa de forma incremental / streaming, em lotes controlados,
     * gravando no banco Room local e no Firestore por batches, sem manter o arquivo nem
     * os dados completos na memória RAM.
     */
    suspend fun restoreBackupStreaming(
        context: Context,
        uri: Uri,
        previewInfo: BackupPreviewInfo,
        dao: PlayFilmeDao,
        firebaseService: FirebaseService,
        onProgress: (RestoreRealProgress) -> Unit,
        isCancelled: () -> Boolean
    ): RestoreRealProgress = withContext(Dispatchers.IO) {
        val fileName = previewInfo.fileName
        val totalExpected = (previewInfo.moviesCount + previewInfo.seriesCount + previewInfo.episodesCount + previewInfo.channelsCount + previewInfo.featuredCount).coerceAtLeast(1)

        Log.d(TAG, "[BACKUP RESTORE] Iniciando restauração. Esperados: Movies=${previewInfo.moviesCount}, Series=${previewInfo.seriesCount}, Episodes=${previewInfo.episodesCount}, Channels=${previewInfo.channelsCount}")

        var currentProgress = RestoreRealProgress(
            status = RestoreStatus.RESTORING,
            progress = 0.02f,
            percentage = 2,
            currentPhase = "Iniciando leitura e validação do backup...",
            moviesTotal = previewInfo.moviesCount,
            seriesTotal = previewInfo.seriesCount,
            episodesTotal = previewInfo.episodesCount,
            channelsTotal = previewInfo.channelsCount,
            featuredTotal = previewInfo.featuredCount
        )
        onProgress(currentProgress)

        var streamPair: Pair<InputStream, Boolean>? = null
        var jsonReader: JsonReader? = null

        val movieBatch = ArrayList<MediaEntity>(BATCH_SIZE)
        val episodeBatch = ArrayList<EpisodeEntity>(BATCH_SIZE)
        val channelBatch = ArrayList<ChannelEntity>(BATCH_SIZE)
        val featuredBatch = ArrayList<FeaturedMediaEntity>(BATCH_SIZE)

        var moviesRestored = 0
        var seriesRestored = 0
        var episodesRestored = 0
        var channelsRestored = 0
        var featuredRestored = 0
        var errorsCount = 0
        var itemsIgnored = 0

        suspend fun flushMediaBatch() {
            if (movieBatch.isNotEmpty()) {
                val toSave = ArrayList(movieBatch)
                movieBatch.clear()

                val mCount = toSave.count { it.mediaType == "movie" }
                val sCount = toSave.size - mCount

                dao.insertMediaList(toSave)
                firebaseService.batchUpsertMedia(toSave)

                moviesRestored += mCount
                seriesRestored += sCount

                val done = moviesRestored + seriesRestored + episodesRestored + channelsRestored + featuredRestored
                val ratio = (done.toFloat() / totalExpected.toFloat()).coerceIn(0.05f, 0.98f)
                currentProgress = currentProgress.copy(
                    progress = ratio,
                    percentage = (ratio * 100).toInt(),
                    currentPhase = if (sCount > mCount) "Restaurando séries..." else "Restaurando filmes...",
                    currentItemTitle = toSave.lastOrNull()?.title ?: "",
                    moviesRestored = moviesRestored,
                    seriesRestored = seriesRestored,
                    errorsCount = errorsCount,
                    itemsIgnored = itemsIgnored
                )
                onProgress(currentProgress)
                yield()
            }
        }

        suspend fun flushEpisodeBatch() {
            if (episodeBatch.isNotEmpty()) {
                val toSave = ArrayList(episodeBatch)
                episodeBatch.clear()

                dao.insertEpisodes(toSave)
                firebaseService.batchUpsertEpisodes(toSave)

                episodesRestored += toSave.size

                val done = moviesRestored + seriesRestored + episodesRestored + channelsRestored + featuredRestored
                val ratio = (done.toFloat() / totalExpected.toFloat()).coerceIn(0.05f, 0.98f)
                currentProgress = currentProgress.copy(
                    progress = ratio,
                    percentage = (ratio * 100).toInt(),
                    currentPhase = "Restaurando episódios...",
                    currentItemTitle = toSave.lastOrNull()?.title ?: "",
                    episodesRestored = episodesRestored,
                    errorsCount = errorsCount,
                    itemsIgnored = itemsIgnored
                )
                onProgress(currentProgress)
                yield()
            }
        }

        suspend fun flushChannelBatch() {
            if (channelBatch.isNotEmpty()) {
                val toSave = ArrayList(channelBatch)
                channelBatch.clear()

                dao.insertChannels(toSave)
                firebaseService.batchUpsertChannels(toSave)

                channelsRestored += toSave.size

                val done = moviesRestored + seriesRestored + episodesRestored + channelsRestored + featuredRestored
                val ratio = (done.toFloat() / totalExpected.toFloat()).coerceIn(0.05f, 0.98f)
                currentProgress = currentProgress.copy(
                    progress = ratio,
                    percentage = (ratio * 100).toInt(),
                    currentPhase = "Restaurando canais...",
                    currentItemTitle = toSave.lastOrNull()?.name ?: "",
                    channelsRestored = channelsRestored,
                    errorsCount = errorsCount,
                    itemsIgnored = itemsIgnored
                )
                onProgress(currentProgress)
                yield()
            }
        }

        suspend fun flushFeaturedBatch() {
            if (featuredBatch.isNotEmpty()) {
                val toSave = ArrayList(featuredBatch)
                featuredBatch.clear()

                dao.insertFeaturedMediaList(toSave)
                firebaseService.batchUpsertFeatured(toSave)

                featuredRestored += toSave.size

                val done = moviesRestored + seriesRestored + episodesRestored + channelsRestored + featuredRestored
                val ratio = (done.toFloat() / totalExpected.toFloat()).coerceIn(0.05f, 0.98f)
                currentProgress = currentProgress.copy(
                    progress = ratio,
                    percentage = (ratio * 100).toInt(),
                    currentPhase = "Restaurando destaques...",
                    featuredRestored = featuredRestored,
                    errorsCount = errorsCount,
                    itemsIgnored = itemsIgnored
                )
                onProgress(currentProgress)
                yield()
            }
        }

        suspend fun processMediaPair(pair: Pair<MediaEntity, List<EpisodeEntity>>?) {
            if (pair == null) {
                itemsIgnored++
                return
            }
            val (media, eps) = pair
            movieBatch.add(media)
            if (eps.isNotEmpty()) {
                episodeBatch.addAll(eps)
            }

            if (movieBatch.size >= BATCH_SIZE) flushMediaBatch()
            if (episodeBatch.size >= BATCH_SIZE) flushEpisodeBatch()
        }

        suspend fun processEpisode(ep: EpisodeEntity?) {
            if (ep == null) {
                itemsIgnored++
                return
            }
            episodeBatch.add(ep)
            if (episodeBatch.size >= BATCH_SIZE) flushEpisodeBatch()
        }

        suspend fun processChannel(ch: ChannelEntity?) {
            if (ch == null) {
                itemsIgnored++
                return
            }
            channelBatch.add(ch)
            if (channelBatch.size >= BATCH_SIZE) flushChannelBatch()
        }

        suspend fun processFeatured(ft: FeaturedMediaEntity?) {
            if (ft == null) return
            featuredBatch.add(ft)
            if (featuredBatch.size >= BATCH_SIZE) flushFeaturedBatch()
        }

        suspend fun restoreObject(reader: JsonReader, depth: Int = 0) {
            if (depth > 5) return
            reader.beginObject()
            while (reader.hasNext()) {
                if (isCancelled()) break
                val rawKey = reader.nextName()
                val key = rawKey.lowercase().replace("_", "")

                when (key) {
                    "movies", "filmes", "movie", "films" -> {
                        if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                if (isCancelled()) break
                                try {
                                    processMediaPair(parseMediaEntityFromReader(reader, "movie"))
                                } catch (t: Throwable) {
                                    errorsCount++
                                    try { reader.skipValue() } catch (e: Throwable) {}
                                }
                            }
                            reader.endArray()
                            flushMediaBatch()
                            flushEpisodeBatch()
                        } else reader.skipValue()
                    }
                    "series", "serie", "tv", "shows", "tvshows" -> {
                        if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                if (isCancelled()) break
                                try {
                                    processMediaPair(parseMediaEntityFromReader(reader, "tv"))
                                } catch (t: Throwable) {
                                    errorsCount++
                                    try { reader.skipValue() } catch (e: Throwable) {}
                                }
                            }
                            reader.endArray()
                            flushMediaBatch()
                            flushEpisodeBatch()
                        } else reader.skipValue()
                    }
                    "catalog", "catalogo", "media", "items" -> {
                        if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                if (isCancelled()) break
                                try {
                                    processMediaPair(parseMediaEntityFromReader(reader, null))
                                } catch (t: Throwable) {
                                    errorsCount++
                                    try { reader.skipValue() } catch (e: Throwable) {}
                                }
                            }
                            reader.endArray()
                            flushMediaBatch()
                            flushEpisodeBatch()
                        } else if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                            restoreObject(reader, depth + 1)
                        } else reader.skipValue()
                    }
                    "episodes", "episodios" -> {
                        if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                if (isCancelled()) break
                                try {
                                    processEpisode(parseEpisodeEntityFromReader(reader))
                                } catch (t: Throwable) {
                                    errorsCount++
                                    try { reader.skipValue() } catch (e: Throwable) {}
                                }
                            }
                            reader.endArray()
                            flushEpisodeBatch()
                        } else reader.skipValue()
                    }
                    "channels", "canais", "livechannels", "streams", "tvchannels" -> {
                        if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                if (isCancelled()) break
                                try {
                                    processChannel(parseChannelEntityFromReader(reader))
                                } catch (t: Throwable) {
                                    errorsCount++
                                    try { reader.skipValue() } catch (e: Throwable) {}
                                }
                            }
                            reader.endArray()
                            flushChannelBatch()
                        } else reader.skipValue()
                    }
                    "featured", "destaques", "featuredmedia", "hero" -> {
                        if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                if (isCancelled()) break
                                try {
                                    processFeatured(parseFeaturedMediaEntityFromReader(reader))
                                } catch (t: Throwable) {
                                    try { reader.skipValue() } catch (e: Throwable) {}
                                }
                            }
                            reader.endArray()
                            flushFeaturedBatch()
                        } else reader.skipValue()
                    }
                    "megaembedconfig", "config", "settings", "configuration" -> {
                        try {
                            val cfg = parseMegaEmbedConfigFromReader(reader)
                            if (cfg != null) {
                                firebaseService.updateMegaEmbedConfigInCloud(cfg)
                            }
                        } catch (t: Throwable) {
                            try { reader.skipValue() } catch (e: Throwable) {}
                        }
                    }
                    "data", "collections", "db", "backup", "payload", "result", "content" -> {
                        if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                            restoreObject(reader, depth + 1)
                        } else if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                if (isCancelled()) break
                                try {
                                    processMediaPair(parseMediaEntityFromReader(reader, null))
                                } catch (t: Throwable) {
                                    errorsCount++
                                    try { reader.skipValue() } catch (e: Throwable) {}
                                }
                            }
                            reader.endArray()
                            flushMediaBatch()
                            flushEpisodeBatch()
                        } else reader.skipValue()
                    }
                    else -> {
                        if (reader.peek() == JsonReader.Token.BEGIN_OBJECT) {
                            restoreObject(reader, depth + 1)
                        } else if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                if (isCancelled()) break
                                try {
                                    processMediaPair(parseMediaEntityFromReader(reader, null))
                                } catch (t: Throwable) {
                                    errorsCount++
                                    try { reader.skipValue() } catch (e: Throwable) {}
                                }
                            }
                            reader.endArray()
                            flushMediaBatch()
                            flushEpisodeBatch()
                        } else reader.skipValue()
                    }
                }
            }
            reader.endObject()
        }

        try {
            streamPair = openAppropriateInputStream(context, uri, fileName)
            val inputStream = streamPair.first
            val source = inputStream.source().buffer()
            jsonReader = JsonReader.of(source)
            jsonReader.isLenient = true

            val token = jsonReader.peek()
            if (token == JsonReader.Token.BEGIN_OBJECT) {
                restoreObject(jsonReader)
            } else if (token == JsonReader.Token.BEGIN_ARRAY) {
                jsonReader.beginArray()
                while (jsonReader.hasNext()) {
                    if (isCancelled()) break
                    try {
                        processMediaPair(parseMediaEntityFromReader(jsonReader, null))
                    } catch (t: Throwable) {
                        errorsCount++
                        try { jsonReader.skipValue() } catch (e: Throwable) {}
                    }
                }
                jsonReader.endArray()
            }

            flushMediaBatch()
            flushEpisodeBatch()
            flushChannelBatch()
            flushFeaturedBatch()

            if (isCancelled()) {
                currentProgress = currentProgress.copy(
                    status = RestoreStatus.CANCELLED,
                    currentPhase = "Restauração cancelada pelo usuário."
                )
                onProgress(currentProgress)
                return@withContext currentProgress
            }

            currentProgress = currentProgress.copy(
                progress = 0.99f,
                percentage = 99,
                currentPhase = "Finalizando e sincronizando catálogo com dispositivos..."
            )
            onProgress(currentProgress)

            firebaseService.publishAndBumpCatalogVersion()

            val totalRestored = moviesRestored + seriesRestored + episodesRestored + channelsRestored
            dao.insertBackupHistory(
                BackupHistoryEntity(
                    type = "RESTORE",
                    itemCount = totalRestored,
                    status = if (errorsCount == 0) "SUCCESS" else "PARTIAL",
                    result = "$moviesRestored filmes, $seriesRestored séries, $episodesRestored episódios, $channelsRestored canais"
                )
            )

            Log.d(TAG, "[BACKUP RESTORE] Concluído com sucesso: $totalRestored itens restaurados, $errorsCount erros")

            currentProgress = currentProgress.copy(
                status = RestoreStatus.COMPLETED,
                progress = 1.0f,
                percentage = 100,
                currentPhase = "RESTAURAÇÃO CONCLUÍDA COM SUCESSO!",
                moviesRestored = moviesRestored,
                seriesRestored = seriesRestored,
                episodesRestored = episodesRestored,
                channelsRestored = channelsRestored,
                featuredRestored = featuredRestored,
                itemsIgnored = itemsIgnored,
                errorsCount = errorsCount
            )
            onProgress(currentProgress)
            return@withContext currentProgress

        } catch (t: Throwable) {
            Log.e(TAG, "[BACKUP RESTORE] Erro fatal durante a restauração: ${t.message}", t)
            val partialCount = moviesRestored + seriesRestored + episodesRestored + channelsRestored
            val errMsg = if (partialCount > 0) {
                "Restauração parcial ($partialCount itens salvos). Erro no arquivo: ${t.localizedMessage}"
            } else {
                "Não foi possível concluir a restauração: ${t.localizedMessage}"
            }

            currentProgress = currentProgress.copy(
                status = RestoreStatus.ERROR,
                errorMessage = errMsg,
                currentPhase = "Erro na restauração",
                moviesRestored = moviesRestored,
                seriesRestored = seriesRestored,
                episodesRestored = episodesRestored,
                channelsRestored = channelsRestored,
                errorsCount = errorsCount + 1
            )
            onProgress(currentProgress)
            return@withContext currentProgress

        } finally {
            movieBatch.clear()
            episodeBatch.clear()
            channelBatch.clear()
            featuredBatch.clear()

            try { jsonReader?.close() } catch (e: Throwable) {}
            try { streamPair?.first?.close() } catch (e: Throwable) {}
        }
    }

    /**
     * Exporta o catálogo e as configurações diretamente para a Uri de saída em streaming com JsonWriter,
     * evitando qualquer alocação de String JSON completa na memória RAM.
     */
    suspend fun exportBackupStreaming(
        context: Context,
        uri: Uri,
        dao: PlayFilmeDao,
        firebaseService: FirebaseService,
        onProgress: (Float, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var jsonWriter: JsonWriter? = null
        var outputStream: OutputStream? = null

        try {
            outputStream = context.contentResolver.openOutputStream(uri)
                ?: return@withContext false

            val sink = outputStream.sink().buffer()
            jsonWriter = JsonWriter.of(sink)
            jsonWriter.indent = "  "

            onProgress(0.05f, "Preparando dados do catálogo...")

            val movies = dao.getMediaByTypeSync("movie")
            val series = dao.getMediaByTypeSync("tv")
            val episodes = dao.getAllEpisodesSync()
            val channels = dao.getAllChannelsSync()
            val featured = dao.getAllFeaturedMediaSync()
            val megaEmbedConfig = firebaseService.getMegaEmbedConfigSync()
            val catalogVersion = firebaseService.getCatalogVersionSync()

            jsonWriter.beginObject()
            jsonWriter.name("version").value(100)
            jsonWriter.name("appVersion").value("1.0")
            jsonWriter.name("timestamp").value(System.currentTimeMillis())
            jsonWriter.name("catalogVersion").value(catalogVersion)

            // Movies
            onProgress(0.2f, "Gravando filmes...")
            jsonWriter.name("movies").beginArray()
            for (movie in movies) {
                movieAdapter.toJson(jsonWriter, movie)
            }
            jsonWriter.endArray()

            // Series
            onProgress(0.45f, "Gravando séries...")
            jsonWriter.name("series").beginArray()
            for (serie in series) {
                movieAdapter.toJson(jsonWriter, serie)
            }
            jsonWriter.endArray()

            // Episodes
            onProgress(0.7f, "Gravando episódios...")
            jsonWriter.name("episodes").beginArray()
            for (episode in episodes) {
                episodeAdapter.toJson(jsonWriter, episode)
            }
            jsonWriter.endArray()

            // Channels
            onProgress(0.85f, "Gravando canais...")
            jsonWriter.name("channels").beginArray()
            for (channel in channels) {
                channelAdapter.toJson(jsonWriter, channel)
            }
            jsonWriter.endArray()

            // Featured
            onProgress(0.92f, "Gravando destaques...")
            jsonWriter.name("featured").beginArray()
            for (feat in featured) {
                featuredAdapter.toJson(jsonWriter, feat)
            }
            jsonWriter.endArray()

            // Config
            if (megaEmbedConfig != null) {
                jsonWriter.name("megaEmbedConfig")
                megaEmbedAdapter.toJson(jsonWriter, megaEmbedConfig)
            }

            jsonWriter.endObject()
            jsonWriter.flush()

            dao.insertBackupHistory(
                BackupHistoryEntity(
                    type = "EXPORT",
                    itemCount = movies.size + series.size + episodes.size + channels.size,
                    status = "SUCCESS",
                    result = "${movies.size} filmes, ${series.size} séries, ${episodes.size} episódios, ${channels.size} canais"
                )
            )

            onProgress(1.0f, "Backup exportado com sucesso!")
            return@withContext true
        } catch (t: Throwable) {
            Log.e(TAG, "Error streaming export: ${t.message}", t)
            return@withContext false
        } finally {
            try { jsonWriter?.close() } catch (e: Throwable) {}
            try { outputStream?.close() } catch (e: Throwable) {}
        }
    }
}
