package com.example.data.remote

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ReiDosEmbedsApiService(
    private val baseUrl: String = "https://reidosembeds.online/api"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val TAG = "ReiDosEmbedsApi"

    suspend fun getChannels(category: String? = null): List<ApiChannel> = withContext(Dispatchers.IO) {
        try {
            val url = if (category.isNullOrBlank() || category.equals("all", ignoreCase = true) || category.equals("todos", ignoreCase = true)) {
                "$baseUrl/channels"
            } else {
                "$baseUrl/channels?category=${java.net.URLEncoder.encode(category, "UTF-8")}"
            }
            val json = fetchRawJson(url) ?: return@withContext emptyList()
            parseChannelsJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching channels: ${e.message}")
            emptyList()
        }
    }

    suspend fun getChannelById(channelId: String): ApiChannel? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/channels/$channelId"
            val json = fetchRawJson(url) ?: return@withContext null
            val obj = if (json.trim().startsWith("{")) {
                val root = JSONObject(json)
                if (root.has("data") && root.get("data") is JSONObject) root.getJSONObject("data") else root
            } else null ?: return@withContext null

            ApiChannel(
                id = obj.optString("id", channelId),
                name = obj.optString("name", "Canal $channelId"),
                category = obj.optString("category", "Geral"),
                logo = obj.optString("logo", obj.optString("logo_url", "")),
                embedUrl = obj.optString("embed_url", obj.optString("url", "https://reidosembeds.online/embed/$channelId")),
                description = obj.optString("description", ""),
                status = obj.optString("status", "online"),
                currentProgram = obj.optString("current_program", "Programação ao Vivo"),
                nextProgram = obj.optString("next_program", "Transmissão 24 Horas"),
                slug = obj.optString("slug", channelId)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching channel $channelId: ${e.message}")
            null
        }
    }

    suspend fun getChannelCategories(): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/channels/categories"
            val json = fetchRawJson(url) ?: return@withContext emptyList()
            parseStringArrayJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching channel categories: ${e.message}")
            emptyList()
        }
    }

    suspend fun getEvents(category: String? = null, status: String? = null): List<ApiEvent> = withContext(Dispatchers.IO) {
        try {
            val queryParams = mutableListOf<String>()
            if (!category.isNullOrBlank() && !category.equals("all", ignoreCase = true) && !category.equals("todos", ignoreCase = true)) {
                queryParams.add("category=${java.net.URLEncoder.encode(category, "UTF-8")}")
            }
            if (!status.isNullOrBlank() && !status.equals("all", ignoreCase = true)) {
                queryParams.add("status=${java.net.URLEncoder.encode(status, "UTF-8")}")
            }
            val url = if (queryParams.isEmpty()) {
                "$baseUrl/eventos"
            } else {
                "$baseUrl/eventos?" + queryParams.joinToString("&")
            }
            val json = fetchRawJson(url) ?: return@withContext emptyList()
            parseEventsJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching events: ${e.message}")
            emptyList()
        }
    }

    suspend fun getEventById(eventId: String): ApiEvent? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/eventos/$eventId"
            val json = fetchRawJson(url) ?: return@withContext null
            val obj = if (json.trim().startsWith("{")) {
                val root = JSONObject(json)
                if (root.has("data") && root.get("data") is JSONObject) root.getJSONObject("data") else root
            } else null ?: return@withContext null

            parseSingleEvent(obj)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching event $eventId: ${e.message}")
            null
        }
    }

    suspend fun getEventCategories(): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/eventos/categories"
            val json = fetchRawJson(url) ?: return@withContext emptyList()
            parseStringArrayJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching event categories: ${e.message}")
            emptyList()
        }
    }

    suspend fun search(query: String): Pair<List<ApiChannel>, List<ApiEvent>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/pesquisa?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val json = fetchRawJson(url) ?: return@withContext Pair(emptyList(), emptyList())
            val root = JSONObject(json)
            val channels = if (root.has("channels")) {
                parseChannelsJson(root.get("channels").toString())
            } else if (root.has("data") && root.getJSONObject("data").has("channels")) {
                parseChannelsJson(root.getJSONObject("data").get("channels").toString())
            } else emptyList()

            val events = if (root.has("eventos")) {
                parseEventsJson(root.get("eventos").toString())
            } else if (root.has("events")) {
                parseEventsJson(root.get("events").toString())
            } else if (root.has("data") && root.getJSONObject("data").has("eventos")) {
                parseEventsJson(root.getJSONObject("data").get("eventos").toString())
            } else emptyList()

            Pair(channels, events)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching: ${e.message}")
            Pair(emptyList(), emptyList())
        }
    }

    suspend fun getGuide(): List<ApiGuideItem> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/guia"
            val json = fetchRawJson(url) ?: return@withContext emptyList()
            parseGuideJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching guide: ${e.message}")
            emptyList()
        }
    }

    private fun fetchRawJson(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) PlayfilmePlusApp/1.0")
                .addHeader("Accept", "application/json, text/plain, */*")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.w(TAG, "HTTP ${response.code} for $url")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception fetching $url: ${e.message}")
            null
        }
    }

    private fun parseChannelsJson(json: String): List<ApiChannel> {
        val list = mutableListOf<ApiChannel>()
        try {
            val trimmed = json.trim()
            val array = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    when {
                        obj.has("data") && obj.get("data") is JSONArray -> obj.getJSONArray("data")
                        obj.has("channels") && obj.get("channels") is JSONArray -> obj.getJSONArray("channels")
                        else -> JSONArray()
                    }
                }
                else -> JSONArray()
            }

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val id = item.optString("id", item.optString("slug", "ch_$i"))
                val name = item.optString("name", "Canal $id")
                val cat = item.optString("category", "Variedades")
                val logo = item.optString("logo", item.optString("logo_url", item.optString("image", "")))
                val embed = item.optString("embed_url", item.optString("url", "https://reidosembeds.online/embed/$id"))
                val desc = item.optString("description", "")
                val status = item.optString("status", "online")
                val curProg = item.optString("current_program", item.optString("programa_atual", "Transmissão ao Vivo"))
                val nextProg = item.optString("next_program", item.optString("proximo_programa", "Programação Contínua"))
                val slug = item.optString("slug", id)

                list.add(
                    ApiChannel(
                        id = id,
                        name = name,
                        category = cat,
                        logo = logo,
                        embedUrl = embed,
                        description = desc,
                        status = status,
                        currentProgram = curProg,
                        nextProgram = nextProg,
                        slug = slug
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing channels JSON: ${e.message}")
        }
        return list
    }

    private fun parseEventsJson(json: String): List<ApiEvent> {
        val list = mutableListOf<ApiEvent>()
        try {
            val trimmed = json.trim()
            val array = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    when {
                        obj.has("data") && obj.get("data") is JSONArray -> obj.getJSONArray("data")
                        obj.has("eventos") && obj.get("eventos") is JSONArray -> obj.getJSONArray("eventos")
                        obj.has("events") && obj.get("events") is JSONArray -> obj.getJSONArray("events")
                        else -> JSONArray()
                    }
                }
                else -> JSONArray()
            }

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                parseSingleEvent(item)?.let { list.add(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing events JSON: ${e.message}")
        }
        return list
    }

    private fun parseSingleEvent(item: JSONObject): ApiEvent? {
        return try {
            val id = item.optString("id", item.optString("slug", "ev_${System.currentTimeMillis()}"))
            val title = item.optString("title", item.optString("nome", "Evento Esportivo"))
            val desc = item.optString("description", "")
            val poster = item.optString("poster", item.optString("banner", ""))
            val time1 = item.optString("time1", "")
            val time2 = item.optString("time2", "")
            val time1Name = item.optString("time1_name", time1)
            val time2Name = item.optString("time2_name", time2)
            val time1Logo = item.optString("time1_logo", "")
            val time2Logo = item.optString("time2_logo", "")
            val visualModel = item.optString("visual_model", if (time1.isNotEmpty() || time2.isNotEmpty()) "match" else "event")
            val eventLogo = item.optString("event_logo", "")
            val compLogo = item.optString("competition_logo", "")
            val sportKey = item.optString("sport_key", "futebol")
            val startTime = item.optString("start_time", item.optString("horario", "Ao Vivo"))
            val endTime = item.optString("end_time", "")
            val status = item.optString("status", "live")
            val category = item.optString("category", item.optString("esporte", "Futebol"))
            val competition = item.optString("competition", item.optString("campeonato", "Ao Vivo"))
            val slug = item.optString("slug", id)
            val basePublicSlug = item.optString("base_public_slug", "")
            val publicSlug = item.optString("public_slug", "")
            val pageUrl = item.optString("page_url", "")
            val playEventUrl = item.optString("play_event_url", "https://reidosembeds.online/embed/evento/$id")
            val hasYt = item.optBoolean("has_youtube_live", false)
            val ytScan = item.optString("youtube_scan", "")

            val embedsList = mutableListOf<ApiEventEmbed>()
            if (item.has("embeds")) {
                val embedsArray = item.getJSONArray("embeds")
                for (j in 0 until embedsArray.length()) {
                    val emb = embedsArray.getJSONObject(j)
                    embedsList.add(
                        ApiEventEmbed(
                            provider = emb.optString("provider", emb.optString("name", "Opção ${j + 1}")),
                            quality = emb.optString("quality", "HD"),
                            slug = emb.optString("slug", ""),
                            logo = emb.optString("logo", ""),
                            embedUrl = emb.optString("embed_url", emb.optString("url", playEventUrl)),
                            name = emb.optString("name", "Opção ${j + 1}")
                        )
                    )
                }
            }

            ApiEvent(
                id = id,
                title = title,
                description = desc,
                poster = poster,
                time1 = time1,
                time2 = time2,
                time1Name = time1Name,
                time2Name = time2Name,
                time1Logo = time1Logo,
                time2Logo = time2Logo,
                visualModel = visualModel,
                eventLogo = eventLogo,
                competitionLogo = compLogo,
                sportKey = sportKey,
                startTime = startTime,
                endTime = endTime,
                status = status,
                category = category,
                competition = competition,
                slug = slug,
                basePublicSlug = basePublicSlug,
                publicSlug = publicSlug,
                pageUrl = pageUrl,
                playEventUrl = playEventUrl,
                hasYoutubeLive = hasYt,
                youtubeScan = ytScan,
                embeds = embedsList
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing single event: ${e.message}")
            null
        }
    }

    private fun parseStringArrayJson(json: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val trimmed = json.trim()
            val array = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    when {
                        obj.has("data") && obj.get("data") is JSONArray -> obj.getJSONArray("data")
                        obj.has("categories") && obj.get("categories") is JSONArray -> obj.getJSONArray("categories")
                        else -> JSONArray()
                    }
                }
                else -> JSONArray()
            }
            for (i in 0 until array.length()) {
                val item = array.get(i)
                if (item is String) {
                    list.add(item)
                } else if (item is JSONObject) {
                    val name = item.optString("name", item.optString("title", item.optString("category", "")))
                    if (name.isNotEmpty()) list.add(name)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing string array: ${e.message}")
        }
        return list
    }

    private fun parseGuideJson(json: String): List<ApiGuideItem> {
        val list = mutableListOf<ApiGuideItem>()
        try {
            val trimmed = json.trim()
            val array = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    when {
                        obj.has("data") && obj.get("data") is JSONArray -> obj.getJSONArray("data")
                        obj.has("guia") && obj.get("guia") is JSONArray -> obj.getJSONArray("guia")
                        obj.has("programs") && obj.get("programs") is JSONArray -> obj.getJSONArray("programs")
                        else -> JSONArray()
                    }
                }
                else -> JSONArray()
            }
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(
                    ApiGuideItem(
                        channelId = item.optString("channel_id", item.optString("channel", "")),
                        channelName = item.optString("channel_name", item.optString("channel", "Canal")),
                        time = item.optString("time", item.optString("horario", "20:00")),
                        title = item.optString("title", item.optString("programa", "Programação")),
                        description = item.optString("description", ""),
                        category = item.optString("category", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing guide JSON: ${e.message}")
        }
        return list
    }
}
