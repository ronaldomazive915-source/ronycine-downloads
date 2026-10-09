package com.example.data.remote

import retrofit2.Response
import retrofit2.http.*

interface CentralApiService {

    @POST("auth/login")
    suspend fun login(@Body request: CentralLoginRequest): Response<CentralResponse<CentralAuthResponse>>

    @POST("auth/register")
    suspend fun register(@Body request: CentralRegisterRequest): Response<CentralResponse<CentralAuthResponse>>

    @GET("auth/profile")
    suspend fun getProfile(): Response<CentralResponse<UserEntity>>

    @GET("movies")
    suspend fun getMovies(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("query") query: String? = null
    ): Response<CentralResponse<CentralMediaListResponse>>

    @GET("series")
    suspend fun getSeries(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("query") query: String? = null
    ): Response<CentralResponse<CentralMediaListResponse>>

    @GET("animes")
    suspend fun getAnimes(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("query") query: String? = null
    ): Response<CentralResponse<CentralMediaListResponse>>

    @GET("doramas")
    suspend fun getDoramas(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("query") query: String? = null
    ): Response<CentralResponse<CentralMediaListResponse>>

    @GET("series/{tmdbId}/episodes")
    suspend fun getEpisodes(
        @Path("tmdbId") tmdbId: Int,
        @Query("season") season: Int? = null
    ): Response<CentralResponse<CentralEpisodeListResponse>>

    @POST("requests")
    suspend fun createRequest(@Body request: MediaRequest): Response<CentralResponse<MediaRequest>>

    @POST("devices")
    suspend fun registerDevice(@Body device: DeviceEntity): Response<CentralResponse<DeviceEntity>>

    // --- RONYCINE CENTRAL Connection Endpoints ---

    @POST("app/pair")
    suspend fun pair(@Body request: CentralPairRequest): Response<CentralResponse<CentralPairResponse>>

    @POST("app/heartbeat")
    suspend fun heartbeat(@Body request: CentralHeartbeatRequest): Response<CentralResponse<Unit>>

    @GET("sync")
    suspend fun sync(@Query("since") since: Long): Response<CentralResponse<CentralSyncResponse>>
}
