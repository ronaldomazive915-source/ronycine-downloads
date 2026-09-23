package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class EmbedTvApiService(
    private val baseUrl: String = "https://embedtv.lat/api"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val TAG = "EmbedTvApi"

    suspend fun getChannels(): List<ApiChannel> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/channels"
            val json = fetchRawJson(url) ?: return@withContext emptyList()
            parseChannelsJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching channels: ${e.message}")
            emptyList()
        }
    }

    suspend fun getEvents(): List<ApiEvent> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/events"
            val json = fetchRawJson(url) ?: return@withContext emptyList()
            parseEventsJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching events: ${e.message}")
            emptyList()
        }
    }

    suspend fun getGuide(): List<ApiGuideItem> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/epg_all"
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
                .addHeader("Accept", "application/json")
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
                val id = item.optString("id", item.optString("slug", ""))
                if (id.isEmpty()) continue

                val name = item.optString("name", item.optString("title", "Canal $id"))
                val cat = item.optString("category", item.optString("genre", "Variedades"))
                val logoUrl = item.optString("logo", item.optString("logo_url", item.optString("image", "")))
                val embed = item.optString("url", item.optString("embed_url", item.optString("stream", "")))
                val desc = item.optString("description", "")
                val isActive = item.optBoolean("status", item.optBoolean("isActive", true))
                val slug = item.optString("slug", id)

                list.add(
                    ApiChannel(
                        id = id,
                        name = name,
                        category = cat,
                        logoUrl = logoUrl,
                        embedUrl = embed,
                        description = desc,
                        isActive = isActive,
                        slug = slug,
                        sourceProvider = "embedtv"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing channels: ${e.message}")
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
                        obj.has("events") && obj.get("events") is JSONArray -> obj.getJSONArray("events")
                        else -> JSONArray()
                    }
                }
                else -> JSONArray()
            }

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val id = item.optString("id", item.optString("slug", "ev_$i"))
                val title = item.optString("title", item.optString("name", "Evento"))
                val desc = item.optString("description", "")
                val poster = item.optString("poster", item.optString("logo", item.optString("image", "")))
                val startTime = item.optString("start_time", item.optString("horario", "Ao Vivo"))
                val endTime = item.optString("end_time", "")
                val status = item.optString("status", "live")
                val category = item.optString("category", "Esportes")
                val competition = item.optString("competition", "Geral")
                val slug = item.optString("slug", id)
                val playUrl = item.optString("url", item.optString("embed_url", ""))

                list.add(
                    ApiEvent(
                        id = id,
                        title = title,
                        description = desc,
                        poster = poster,
                        startTime = startTime,
                        endTime = endTime,
                        status = status,
                        category = category,
                        competition = competition,
                        slug = slug,
                        playEventUrl = playUrl,
                        sourceProvider = "embedtv"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing events: ${e.message}")
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
                        obj.has("epg") && obj.get("epg") is JSONArray -> obj.getJSONArray("epg")
                        else -> JSONArray()
                    }
                }
                else -> JSONArray()
            }
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(
                    ApiGuideItem(
                        channelId = item.optString("channel_id", item.optString("id", "")),
                        channelName = item.optString("channel_name", ""),
                        time = item.optString("start_time", item.optString("time", "")),
                        title = item.optString("title", item.optString("program", "")),
                        description = item.optString("description", ""),
                        category = item.optString("category", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing guide: ${e.message}")
        }
        return list
    }
}
