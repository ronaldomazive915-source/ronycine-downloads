package com.example.data.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.example.data.local.EpisodeEntity
import com.example.data.local.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class StreamType(val label: String) {
    DIRECT_MP4("MP4 Direto"),
    DIRECT_VIDEO("Vídeo Direto"),
    HLS("Transmissão HLS (.m3u8)"),
    DASH("Transmissão DASH (.mpd)"),
    DRM_PROTECTED("Protegido por DRM"),
    UNKNOWN("Desconhecido")
}

data class ResolvedStream(
    val url: String,
    val fileName: String,
    val streamType: StreamType,
    val mimeType: String,
    val userAgent: String,
    val referer: String,
    val isDirectFile: Boolean
)

sealed class StreamResolutionResult {
    data class Success(val stream: ResolvedStream) : StreamResolutionResult()
    data class NotSupported(val reason: String) : StreamResolutionResult()
    data class Error(val errorMessage: String) : StreamResolutionResult()
}

enum class ExternalDownloaderType {
    INTERNAL_RONYCINE,
    IDM,
    ADM,
    CHOOSER
}

data class AppAvailability(
    val type: ExternalDownloaderType,
    val title: String,
    val subtitle: String,
    val isInstalled: Boolean,
    val installedPackage: String? = null
)

sealed class DownloadLaunchResult {
    data class Success(val appName: String) : DownloadLaunchResult()
    data class AppNotInstalled(val appName: String, val playStorePackage: String) : DownloadLaunchResult()
    data class NoAppFound(val message: String) : DownloadLaunchResult()
    data class Error(val message: String) : DownloadLaunchResult()
}

object ExternalDownloadHelper {

    private const val TAG = "ExternalDownload"
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    const val DEFAULT_REFERER = "https://embed.playmoz.xyz/"

    val IDM_PACKAGES = listOf(
        "idm.internet.download.manager",
        "idm.internet.download.manager.plus",
        "idm.internet.download.manager.lite"
    )

    val ADM_PACKAGES = listOf(
        "com.dv.adm",
        "com.dv.adm.pay"
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun sanitizeFileName(name: String): String {
        val invalidChars = Regex("[/\\\\:*?\"<>|\\x00-\\x1F]")
        return name.replace(invalidChars, "_")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .ifBlank { "video" }
    }

    fun formatMovieFileName(title: String, releaseYear: String?): String {
        val safeTitle = sanitizeFileName(title)
        val year = releaseYear?.trim()?.take(4) ?: ""
        return if (year.isNotBlank() && year.all { it.isDigit() }) {
            "$safeTitle ($year).mp4"
        } else {
            "$safeTitle.mp4"
        }
    }

    fun formatEpisodeFileName(
        seriesTitle: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeTitle: String?
    ): String {
        val safeSeries = sanitizeFileName(seriesTitle)
        val sStr = String.format("%02d", seasonNumber.coerceAtLeast(1))
        val eStr = String.format("%02d", episodeNumber.coerceAtLeast(1))
        val epName = if (!episodeTitle.isNullOrBlank()) {
            " - ${sanitizeFileName(episodeTitle)}"
        } else {
            ""
        }
        return "$safeSeries - S${sStr}E${eStr}${epName}.mp4"
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun findInstalledPackage(context: Context, packages: List<String>): String? {
        for (pkg in packages) {
            if (isPackageInstalled(context, pkg)) return pkg
        }
        return null
    }

    fun checkInstalledDownloadApps(context: Context): List<AppAvailability> {
        val idmPackage = findInstalledPackage(context, IDM_PACKAGES)
        val admPackage = findInstalledPackage(context, ADM_PACKAGES)

        return listOf(
            AppAvailability(
                type = ExternalDownloaderType.INTERNAL_RONYCINE,
                title = "Baixar no RONYCINE",
                subtitle = "Download interno para assistir offline no reprodutor do app",
                isInstalled = true
            ),
            AppAvailability(
                type = ExternalDownloaderType.IDM,
                title = "1DM (Internet Download Manager)",
                subtitle = if (idmPackage != null) "Aplicativo instalado • Suporta aceleração e HLS" else "Não instalado no dispositivo",
                isInstalled = idmPackage != null,
                installedPackage = idmPackage
            ),
            AppAvailability(
                type = ExternalDownloaderType.ADM,
                title = "ADM (Advanced Download Manager)",
                subtitle = if (admPackage != null) "Aplicativo instalado • Download multi-conexões" else "Não instalado no dispositivo",
                isInstalled = admPackage != null,
                installedPackage = admPackage
            ),
            AppAvailability(
                type = ExternalDownloaderType.CHOOSER,
                title = "Outro aplicativo",
                subtitle = "Abrir menu do Android para selecionar outro baixador",
                isInstalled = true
            )
        )
    }

    suspend fun resolveStreamInfo(
        media: MediaEntity,
        episode: EpisodeEntity? = null,
        customUrl: String? = null
    ): StreamResolutionResult = withContext(Dispatchers.IO) {
        val isTv = media.mediaType == "tv" || media.mediaType == "serie" || episode != null
        val seasonNum = episode?.seasonNumber ?: 1
        val episodeNum = episode?.episodeNumber ?: 1

        val fileName = if (isTv) {
            formatEpisodeFileName(
                seriesTitle = media.title,
                seasonNumber = seasonNum,
                episodeNumber = episodeNum,
                episodeTitle = episode?.title
            )
        } else {
            formatMovieFileName(
                title = media.title,
                releaseYear = media.releaseYear
            )
        }

        // 1. Check custom direct URL first if provided
        val directUrl = customUrl?.trim() ?: episode?.videoUrl?.trim()
        if (!directUrl.isNullOrBlank()) {
            val lower = directUrl.lowercase()

            // Check if it's an image or poster instead of video
            if (lower.contains("image.tmdb.org") || lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".webp")) {
                return@withContext StreamResolutionResult.Error("A URL associada é uma imagem de capa e não um vídeo.")
            }

            // Check if it's a YouTube trailer
            if (lower.contains("youtube.com") || lower.contains("youtu.be")) {
                return@withContext StreamResolutionResult.Error("Este link pertence a um trailer externo e não ao filme completo.")
            }

            // Check for DRM markers
            if (lower.contains("widevine") || lower.contains("drm") || lower.contains("license") || lower.contains("clearkey")) {
                return@withContext StreamResolutionResult.NotSupported("Este conteúdo possui proteção DRM e não pode ser baixado externamente.")
            }

            // Check for DASH (.mpd)
            if (lower.endsWith(".mpd") || lower.contains(".mpd?")) {
                return@withContext StreamResolutionResult.NotSupported("Transmissões no formato DASH (.mpd) não possuem suporte para download direto.")
            }

            // Check for HLS (.m3u8)
            if (lower.endsWith(".m3u8") || lower.contains(".m3u8?")) {
                return@withContext StreamResolutionResult.Success(
                    ResolvedStream(
                        url = directUrl,
                        fileName = fileName.replace(".mp4", ".m3u8"),
                        streamType = StreamType.HLS,
                        mimeType = "application/x-mpegURL",
                        userAgent = USER_AGENT,
                        referer = DEFAULT_REFERER,
                        isDirectFile = false
                    )
                )
            }

            // Direct MP4 / MKV / WebM
            if (lower.endsWith(".mp4") || lower.contains(".mp4?") || lower.endsWith(".mkv") || lower.endsWith(".webm") || lower.contains("/video/") || lower.contains("/media/")) {
                val mime = when {
                    lower.contains(".mkv") -> "video/x-matroska"
                    lower.contains(".webm") -> "video/webm"
                    else -> "video/mp4"
                }
                return@withContext StreamResolutionResult.Success(
                    ResolvedStream(
                        url = directUrl,
                        fileName = fileName,
                        streamType = StreamType.DIRECT_MP4,
                        mimeType = mime,
                        userAgent = USER_AGENT,
                        referer = DEFAULT_REFERER,
                        isDirectFile = true
                    )
                )
            }
        }

        // 2. Resolve via PlayMoz sources API using tmdbId
        val tmdbId = media.tmdbId
        if (tmdbId <= 0) {
            return@withContext StreamResolutionResult.Error("Identificador TMDB inválido para resolução de fontes.")
        }

        val endpointUrl = if (isTv) {
            "https://embed.playmoz.xyz/serie/?action=sources&id=$tmdbId&t=$seasonNum&e=$episodeNum"
        } else {
            "https://embed.playmoz.xyz/filme/?action=sources&id=$tmdbId"
        }

        val refererUrl = if (isTv) {
            "https://embed.playmoz.xyz/tv/$tmdbId/$seasonNum/$episodeNum"
        } else {
            "https://embed.playmoz.xyz/movie/$tmdbId"
        }

        Log.d(TAG, "Consultando fontes PlayMoz: $endpointUrl")

        try {
            val request = Request.Builder()
                .url(endpointUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", refererUrl)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "PlayMoz sources retornou status HTTP ${response.code}")
                return@withContext StreamResolutionResult.Error("Servidor de catálogo retornou código de erro HTTP ${response.code}.")
            }

            val bodyString = response.body?.string()
            if (bodyString.isNullOrBlank()) {
                return@withContext StreamResolutionResult.Error("Resposta vazia do servidor de catálogo.")
            }

            val json = JSONObject(bodyString)
            val sourcesArray = json.optJSONArray("sources")
            if (sourcesArray == null || sourcesArray.length() == 0) {
                return@withContext StreamResolutionResult.Error("Nenhuma fonte de vídeo disponível para download no momento.")
            }

            val candidateMp4Urls = mutableListOf<String>()
            val candidateHlsUrls = mutableListOf<String>()

            for (i in 0 until sourcesArray.length()) {
                val item = sourcesArray.optJSONObject(i) ?: continue
                val fileUrl = item.optString("file").trim()
                val type = item.optString("type").lowercase()
                if (fileUrl.isBlank()) continue

                if (type == "hls" || fileUrl.contains(".m3u8")) {
                    candidateHlsUrls.add(fileUrl)
                } else if (type == "mp4" || fileUrl.contains(".mp4") || fileUrl.contains(".mkv") || fileUrl.contains(".webm")) {
                    candidateMp4Urls.add(fileUrl)
                }
            }

            // Prioritize high-speed direct MP4/MKV sources
            if (candidateMp4Urls.isNotEmpty()) {
                val sortedCandidates = candidateMp4Urls.sortedByDescending {
                    when {
                        it.contains("r2.cloudflarestorage.com") -> 3
                        it.contains("hubby.cx") -> 2
                        it.contains(".mp4") -> 1
                        else -> 0
                    }
                }

                // Verify with quick range request
                for (candidate in sortedCandidates) {
                    try {
                        val testReq = Request.Builder()
                            .url(candidate)
                            .header("User-Agent", USER_AGENT)
                            .header("Referer", DEFAULT_REFERER)
                            .header("Range", "bytes=0-1024")
                            .build()

                        val testResp = httpClient.newCall(testReq).execute()
                        val code = testResp.code
                        val cType = testResp.header("Content-Type")?.lowercase() ?: ""
                        testResp.close()

                        if ((code in 200..299 || code in 300..399) && !cType.contains("text/html")) {
                            Log.d(TAG, "Fonte MP4 validada com sucesso: $candidate")
                            return@withContext StreamResolutionResult.Success(
                                ResolvedStream(
                                    url = candidate,
                                    fileName = fileName,
                                    streamType = StreamType.DIRECT_MP4,
                                    mimeType = "video/mp4",
                                    userAgent = USER_AGENT,
                                    referer = DEFAULT_REFERER,
                                    isDirectFile = true
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Teste de fonte falhou para $candidate: ${e.message}")
                    }
                }

                // Fallback to top candidate if range test didn't complete
                val topCandidate = sortedCandidates.first()
                return@withContext StreamResolutionResult.Success(
                    ResolvedStream(
                        url = topCandidate,
                        fileName = fileName,
                        streamType = StreamType.DIRECT_MP4,
                        mimeType = "video/mp4",
                        userAgent = USER_AGENT,
                        referer = DEFAULT_REFERER,
                        isDirectFile = true
                    )
                )
            }

            // If no MP4 is available but HLS is available
            if (candidateHlsUrls.isNotEmpty()) {
                val topHls = candidateHlsUrls.first()
                return@withContext StreamResolutionResult.Success(
                    ResolvedStream(
                        url = topHls,
                        fileName = fileName.replace(".mp4", ".m3u8"),
                        streamType = StreamType.HLS,
                        mimeType = "application/x-mpegURL",
                        userAgent = USER_AGENT,
                        referer = DEFAULT_REFERER,
                        isDirectFile = false
                    )
                )
            }

            return@withContext StreamResolutionResult.Error("Nenhuma fonte de reprodução direta compatível com download foi localizada.")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao resolver fontes: ${e.message}", e)
            return@withContext StreamResolutionResult.Error("Falha na conexão ao buscar fontes: ${e.localizedMessage ?: "Erro desconhecido"}")
        }
    }

    fun launchExternalDownloader(
        context: Context,
        appType: ExternalDownloaderType,
        stream: ResolvedStream
    ): DownloadLaunchResult {
        when (appType) {
            ExternalDownloaderType.IDM -> {
                val installedPkg = findInstalledPackage(context, IDM_PACKAGES)
                if (installedPkg == null) {
                    return DownloadLaunchResult.AppNotInstalled("1DM", "idm.internet.download.manager")
                }

                return try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(stream.url), stream.mimeType)
                        setPackage(installedPkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("extra_filename", stream.fileName)
                        putExtra("filename", stream.fileName)
                        putExtra("extra_title", stream.fileName)
                        putExtra("title", stream.fileName)
                        putExtra("extra_referer", stream.referer)
                        putExtra("referer", stream.referer)
                        putExtra("extra_user_agent", stream.userAgent)
                        putExtra("userAgent", stream.userAgent)
                        putExtra("secure_uri", true)
                        putExtra("android.intent.extra.TEXT", stream.url)
                    }
                    context.startActivity(intent)
                    DownloadLaunchResult.Success("1DM")
                } catch (e: ActivityNotFoundException) {
                    DownloadLaunchResult.AppNotInstalled("1DM", "idm.internet.download.manager")
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao iniciar 1DM: ${e.message}", e)
                    DownloadLaunchResult.Error("Falha ao abrir o 1DM: ${e.localizedMessage}")
                }
            }

            ExternalDownloaderType.ADM -> {
                val installedPkg = findInstalledPackage(context, ADM_PACKAGES)
                if (installedPkg == null) {
                    return DownloadLaunchResult.AppNotInstalled("ADM", "com.dv.adm")
                }

                return try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(stream.url), stream.mimeType)
                        setPackage(installedPkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("com.dv.adm.extra.FILENAME", stream.fileName)
                        putExtra("com.dv.adm.extra.USER_AGENT", stream.userAgent)
                        putExtra("com.dv.adm.extra.REFERER", stream.referer)
                        putExtra("extra_filename", stream.fileName)
                        putExtra("android.intent.extra.TEXT", stream.url)
                    }
                    context.startActivity(intent)
                    DownloadLaunchResult.Success("ADM")
                } catch (e: ActivityNotFoundException) {
                    DownloadLaunchResult.AppNotInstalled("ADM", "com.dv.adm")
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao iniciar ADM: ${e.message}", e)
                    DownloadLaunchResult.Error("Falha ao abrir o ADM: ${e.localizedMessage}")
                }
            }

            ExternalDownloaderType.CHOOSER -> {
                return try {
                    val baseIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(stream.url), stream.mimeType)
                        putExtra(Intent.EXTRA_TITLE, stream.fileName)
                        putExtra("extra_filename", stream.fileName)
                        putExtra("filename", stream.fileName)
                        putExtra("title", stream.fileName)
                        putExtra("extra_title", stream.fileName)
                        putExtra("android.intent.extra.TEXT", stream.url)
                        putExtra("extra_referer", stream.referer)
                        putExtra("referer", stream.referer)
                        putExtra("extra_user_agent", stream.userAgent)
                        putExtra("userAgent", stream.userAgent)
                    }

                    val chooser = Intent.createChooser(baseIntent, "Baixar com...").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                    DownloadLaunchResult.Success("Gerenciador Externo")
                } catch (e: ActivityNotFoundException) {
                    DownloadLaunchResult.NoAppFound("Nenhum aplicativo de download compatível foi encontrado no dispositivo.")
                } catch (e: Exception) {
                    Log.e(TAG, "Erro no Seletor: ${e.message}", e)
                    DownloadLaunchResult.Error("Falha ao abrir opções do Android: ${e.localizedMessage}")
                }
            }

            ExternalDownloaderType.INTERNAL_RONYCINE -> {
                return DownloadLaunchResult.Success("RONYCINE")
            }
        }
    }

    fun openPlayStore(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}
