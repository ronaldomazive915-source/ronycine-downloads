package com.example.data.remote

import com.example.data.local.EpisodeEntity
import com.example.data.local.MediaEntity
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CentralResponse<T>(
    @Json(name = "status") val status: String,
    @Json(name = "data") val data: T?,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class CentralAuthResponse(
    @Json(name = "token") val token: String,
    @Json(name = "user") val user: UserEntity
)

@JsonClass(generateAdapter = true)
data class CentralLoginRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String,
    @Json(name = "deviceId") val deviceId: String
)

@JsonClass(generateAdapter = true)
data class CentralRegisterRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String,
    @Json(name = "displayName") val displayName: String,
    @Json(name = "username") val username: String,
    @Json(name = "deviceId") val deviceId: String
)

@JsonClass(generateAdapter = true)
data class CentralMediaListResponse(
    @Json(name = "items") val items: List<MediaEntity>,
    @Json(name = "total") val total: Int,
    @Json(name = "page") val page: Int
)

@JsonClass(generateAdapter = true)
data class CentralEpisodeListResponse(
    @Json(name = "items") val items: List<EpisodeEntity>,
    @Json(name = "season") val season: Int
)

// --- Central Central Connection Models ---

@JsonClass(generateAdapter = true)
data class CentralPairRequest(
    @Json(name = "pairingCode") val pairingCode: String,
    @Json(name = "deviceInfo") val deviceInfo: CentralDeviceInfo
)

@JsonClass(generateAdapter = true)
data class CentralDeviceInfo(
    @Json(name = "model") val model: String,
    @Json(name = "manufacturer") val manufacturer: String,
    @Json(name = "androidVersion") val androidVersion: String,
    @Json(name = "appVersion") val appVersion: String,
    @Json(name = "instanceId") val instanceId: String
)

@JsonClass(generateAdapter = true)
data class CentralPairResponse(
    @Json(name = "instanceId") val instanceId: String,
    @Json(name = "connectionToken") val connectionToken: String,
    @Json(name = "accountName") val accountName: String? = null
)

@JsonClass(generateAdapter = true)
data class CentralHeartbeatRequest(
    @Json(name = "instanceId") val instanceId: String,
    @Json(name = "status") val status: String = "online"
)

@JsonClass(generateAdapter = true)
data class CentralSyncResponse(
    @Json(name = "lastSync") val lastSync: Long,
    @Json(name = "changes") val changes: List<CentralChange>
)

@JsonClass(generateAdapter = true)
data class CentralChange(
    @Json(name = "type") val type: String, // "movie", "series", "episode"
    @Json(name = "action") val action: String, // "created", "updated", "deleted"
    @Json(name = "data") val data: Map<String, Any>
)
