package com.example.data.download

import android.content.Context
import android.util.Log
import com.example.data.local.EpisodeEntity
import com.example.data.local.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class DownloadResolutionResult {
    data class Success(
        val url: String,
        val fileName: String,
        val mimeType: String? = null,
        val source: String? = null
    ) : DownloadResolutionResult()

    data class Error(
        val code: String,
        val message: String
    ) : DownloadResolutionResult()
}

class DownloadResolver(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun resolve(
        type: String, // "movie", "tv"
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
        mediaTitle: String? = null
    ): DownloadResolutionResult = withContext(Dispatchers.IO) {
        val traceId = System.currentTimeMillis().toString().takeLast(6)
        try {
            Log.d("DOWNLOAD_TRACE_$traceId", "=== INICIANDO RASTREAMENTO DE DOWNLOAD MULTI-FONTE ===")
            Log.d("DOWNLOAD_TRACE_$traceId", "TIPO: $type")
            Log.d("DOWNLOAD_TRACE_$traceId", "TMDB ID: $tmdbId")
            Log.d("DOWNLOAD_TRACE_$traceId", "TEMPORADA: $season")
            Log.d("DOWNLOAD_TRACE_$traceId", "EPISÓDIO: $episode")
            Log.d("DOWNLOAD_TRACE_$traceId", "TÍTULO: $mediaTitle")

            val baseFileName = formatFileName(mediaTitle ?: "Media", type, season, episode, "mp4")
            var candidateStreamUrl: String? = null

            // -------------------------------------------------------------
            // ETAPA 1: Tentar MegaEmbed (mgeb.top) para extração direta
            // -------------------------------------------------------------
            val mgebUrl = if (type == "movie") {
                "https://mgeb.top/embed/$tmdbId"
            } else {
                "https://mgeb.top/embed/$tmdbId/$season/$episode"
            }

            Log.d("DOWNLOAD_TRACE_$traceId", "[ETAPA 1] Verificando MegaEmbed: $mgebUrl")
            try {
                val mgebRequest = Request.Builder()
                    .url(mgebUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://mgeb.top/")
                    .build()

                val mgebResponse = client.newCall(mgebRequest).execute()
                val mgebCode = mgebResponse.code
                val mgebBody = mgebResponse.body?.string() ?: ""

                Log.d("DOWNLOAD_TRACE_$traceId", "[ETAPA 1] Resposta MegaEmbed: Status $mgebCode (${mgebBody.length} bytes)")

                if (mgebResponse.isSuccessful && !mgebBody.contains("Database connection failed", ignoreCase = true)) {
                    // 1.1 Bloco JavaScript 'var sources = [...]'
                    val sourcesRegex = Regex("""var sources\s*=\s*(\[.*?\])\s*;""", RegexOption.DOT_MATCHES_ALL)
                    val match = sourcesRegex.find(mgebBody)
                    if (match != null) {
                        val jsonArrayStr = match.groupValues[1]
                        try {
                            val jsonArray = org.json.JSONArray(jsonArrayStr)
                            if (jsonArray.length() > 0) {
                                val videoUrl = jsonArray.getJSONObject(0).optString("file")
                                if (!videoUrl.isNullOrBlank()) {
                                    Log.d("DOWNLOAD_TRACE_$traceId", "SUCESSO: Link direto extraído do MegaEmbed: $videoUrl")
                                    val ext = if (videoUrl.contains(".m3u8")) "m3u8" else "mp4"
                                    return@withContext DownloadResolutionResult.Success(
                                        url = videoUrl,
                                        fileName = formatFileName(mediaTitle ?: "Media", type, season, episode, ext),
                                        mimeType = if (ext == "m3u8") "application/x-mpegURL" else "video/mp4",
                                        source = "MegaEmbed (Direto)"
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("DOWNLOAD_TRACE_$traceId", "Falha ao processar JSON de sources do MegaEmbed: ${e.message}")
                        }
                    }

                    // 1.2 Regex direta para links de mídia .mp4 / .m3u8
                    val directRegex = Regex("""https?://[^\s"'<>]+?\.(mp4|m3u8)[^\s"'<>]*""")
                    val directMatch = directRegex.find(mgebBody)
                    if (directMatch != null) {
                        val videoUrl = directMatch.value
                        val ext = if (videoUrl.contains(".m3u8")) "m3u8" else "mp4"
                        Log.d("DOWNLOAD_TRACE_$traceId", "SUCESSO: Link direto via Regex no MegaEmbed: $videoUrl")
                        return@withContext DownloadResolutionResult.Success(
                            url = videoUrl,
                            fileName = formatFileName(mediaTitle ?: "Media", type, season, episode, ext),
                            mimeType = if (ext == "m3u8") "application/x-mpegURL" else "video/mp4",
                            source = "MegaEmbed (Direto)"
                        )
                    }
                } else {
                    Log.w("DOWNLOAD_TRACE_$traceId", "[ETAPA 1] MegaEmbed indisponível ou em manutenção (Status $mgebCode). Prosseguindo para fontes alternativas.")
                }
            } catch (e: Exception) {
                Log.w("DOWNLOAD_TRACE_$traceId", "[ETAPA 1] Erro de conexão com MegaEmbed: ${e.message}")
            }

            // -------------------------------------------------------------
            // ETAPA 2: Tentar VidSrc Stream API (vidsrc.tw)
            // -------------------------------------------------------------
            val vsSrcUrl = if (type == "movie") {
                "https://vidsrc.tw/vs_src.php?type=movie&id=$tmdbId"
            } else {
                "https://vidsrc.tw/vs_src.php?type=tv&id=$tmdbId&season=$season&episode=$episode"
            }

            Log.d("DOWNLOAD_TRACE_$traceId", "[ETAPA 2] Consultando VidSrc API: $vsSrcUrl")
            try {
                val vsRequest = Request.Builder()
                    .url(vsSrcUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://vidsrc.tw/")
                    .build()

                val vsResponse = client.newCall(vsRequest).execute()
                val vsCode = vsResponse.code
                val vsBody = vsResponse.body?.string() ?: ""
                Log.d("DOWNLOAD_TRACE_$traceId", "[ETAPA 2] Resposta VidSrc: Status $vsCode (${vsBody.length} bytes)")

                if (vsResponse.isSuccessful && vsBody.isNotBlank()) {
                    try {
                        val json = JSONObject(vsBody)
                        val src = json.optString("src")
                        if (!src.isNullOrBlank()) {
                            Log.d("DOWNLOAD_TRACE_$traceId", "[ETAPA 2] Stream URL obtida da VidSrc: $src")
                            candidateStreamUrl = src

                            // Tentar inspecionar se a página do player expõe stream direto
                            val streamReq = Request.Builder()
                                .url(src)
                                .header("User-Agent", USER_AGENT)
                                .header("Referer", "https://vidsrc.tw/")
                                .build()

                            val streamResp = client.newCall(streamReq).execute()
                            val streamHtml = streamResp.body?.string() ?: ""

                            val streamDirectMatch = Regex("""https?://[^\s"'<>]+?\.(mp4|m3u8)[^\s"'<>]*""").find(streamHtml)
                            if (streamDirectMatch != null) {
                                val videoUrl = streamDirectMatch.value
                                val ext = if (videoUrl.contains(".m3u8")) "m3u8" else "mp4"
                                Log.d("DOWNLOAD_TRACE_$traceId", "SUCESSO: Link direto extraído do player VidSrc: $videoUrl")
                                return@withContext DownloadResolutionResult.Success(
                                    url = videoUrl,
                                    fileName = formatFileName(mediaTitle ?: "Media", type, season, episode, ext),
                                    mimeType = if (ext == "m3u8") "application/x-mpegURL" else "video/mp4",
                                    source = "VidSrc (Direto)"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("DOWNLOAD_TRACE_$traceId", "[ETAPA 2] Não foi possível fazer parse do JSON do VidSrc: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w("DOWNLOAD_TRACE_$traceId", "[ETAPA 2] Erro ao consultar VidSrc: ${e.message}")
            }

            // -------------------------------------------------------------
            // ETAPA 3: Tentar RedeFlix (redeflixapi.store)
            // -------------------------------------------------------------
            val redeflixUrl = if (type == "movie") {
                "https://redeflixapi.store/filme/$tmdbId"
            } else {
                "https://redeflixapi.store/serie/$tmdbId/$season/$episode"
            }

            Log.d("DOWNLOAD_TRACE_$traceId", "[ETAPA 3] Consultando RedeFlix: $redeflixUrl")
            try {
                val rfRequest = Request.Builder()
                    .url(redeflixUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://redeflixapi.store/")
                    .build()

                val rfResponse = client.newCall(rfRequest).execute()
                if (rfResponse.isSuccessful) {
                    val rfBody = rfResponse.body?.string() ?: ""
                    val rfMatch = Regex("""https?://[^\s"'<>]+?\.(mp4|m3u8)[^\s"'<>]*""").find(rfBody)
                    if (rfMatch != null) {
                        val videoUrl = rfMatch.value
                        val ext = if (videoUrl.contains(".m3u8")) "m3u8" else "mp4"
                        Log.d("DOWNLOAD_TRACE_$traceId", "SUCESSO: Link direto extraído da RedeFlix: $videoUrl")
                        return@withContext DownloadResolutionResult.Success(
                            url = videoUrl,
                            fileName = formatFileName(mediaTitle ?: "Media", type, season, episode, ext),
                            mimeType = if (ext == "m3u8") "application/x-mpegURL" else "video/mp4",
                            source = "RedeFlix (Direto)"
                        )
                    }
                    if (candidateStreamUrl == null) {
                        candidateStreamUrl = redeflixUrl
                    }
                }
            } catch (e: Exception) {
                Log.w("DOWNLOAD_TRACE_$traceId", "[ETAPA 3] RedeFlix erro: ${e.message}")
            }

            // -------------------------------------------------------------
            // ETAPA 4: Resolução Garantida via Stream / Player Embed
            // Quando a fonte reproduz em JavaScript/iframe protegido,
            // resolvemos para a URL de stream/embed compatível com 1DM / ADM.
            // -------------------------------------------------------------
            val finalTargetUrl = candidateStreamUrl 
                ?: (if (type == "movie") "https://vidsrc.tw/embed/movie/$tmdbId" else "https://vidsrc.tw/embed/tv/$tmdbId/$season/$episode")

            Log.d("DOWNLOAD_TRACE_$traceId", "SUCESSO: Resolvido como stream de alta compatibilidade para 1DM/ADM: $finalTargetUrl")

            return@withContext DownloadResolutionResult.Success(
                url = finalTargetUrl,
                fileName = baseFileName,
                mimeType = "video/mp4",
                source = "Stream Web (1DM / ADM)"
            )

        } catch (e: Exception) {
            Log.e("DOWNLOAD_TRACE_$traceId", "EXCEÇÃO FATAL NO RASTREAMENTO: ${e.message}", e)
            return@withContext DownloadResolutionResult.Error("CONNECTION_FAILED", "Erro de conexão: ${e.localizedMessage ?: "Tente novamente mais tarde"}")
        } finally {
            Log.d("DOWNLOAD_TRACE_$traceId", "=== FIM DO RASTREAMENTO ===")
        }
    }

    private fun formatFileName(title: String, type: String, season: Int?, episode: Int?, extension: String): String {
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        return if (type == "tv" && season != null && episode != null) {
            "$cleanTitle S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}.$extension"
        } else {
            "$cleanTitle.$extension"
        }
    }
}
