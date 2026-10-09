package com.example.data.repository

import android.util.Log
import com.example.data.local.EpisodeEntity
import com.example.data.local.MediaEntity
import com.example.data.local.PlayFilmeDao
import com.example.data.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CentralRepository(
    private val api: CentralApiService,
    private val dao: PlayFilmeDao
) {

    suspend fun getMovies(page: Int, query: String? = null): Result<List<MediaEntity>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getMovies(page = page, query = query)
            if (response.isSuccessful && response.body()?.status == "success") {
                val items = response.body()?.data?.items ?: emptyList()
                // Cache locally
                if (items.isNotEmpty()) {
                    dao.insertMediaList(items)
                }
                Result.success(items)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Erro ao buscar filmes da API Central"))
            }
        } catch (e: Exception) {
            Log.e("CentralRepository", "Erro getMovies: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getSeries(page: Int, query: String? = null): Result<List<MediaEntity>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getSeries(page = page, query = query)
            if (response.isSuccessful && response.body()?.status == "success") {
                val items = response.body()?.data?.items ?: emptyList()
                // Cache locally
                if (items.isNotEmpty()) {
                    dao.insertMediaList(items)
                }
                Result.success(items)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Erro ao buscar séries da API Central"))
            }
        } catch (e: Exception) {
            Log.e("CentralRepository", "Erro getSeries: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getAnimes(page: Int, query: String? = null): Result<List<MediaEntity>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAnimes(page = page, query = query)
            if (response.isSuccessful && response.body()?.status == "success") {
                val items = response.body()?.data?.items ?: emptyList()
                if (items.isNotEmpty()) {
                    dao.insertMediaList(items)
                }
                Result.success(items)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Erro ao buscar animes da API Central"))
            }
        } catch (e: Exception) {
            Log.e("CentralRepository", "Erro getAnimes: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getDoramas(page: Int, query: String? = null): Result<List<MediaEntity>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getDoramas(page = page, query = query)
            if (response.isSuccessful && response.body()?.status == "success") {
                val items = response.body()?.data?.items ?: emptyList()
                if (items.isNotEmpty()) {
                    dao.insertMediaList(items)
                }
                Result.success(items)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Erro ao buscar doramas da API Central"))
            }
        } catch (e: Exception) {
            Log.e("CentralRepository", "Erro getDoramas: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getEpisodes(tmdbId: Int, season: Int? = null): Result<List<EpisodeEntity>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getEpisodes(tmdbId, season)
            if (response.isSuccessful && response.body()?.status == "success") {
                val items = response.body()?.data?.items ?: emptyList()
                // Cache locally
                if (items.isNotEmpty()) {
                    dao.insertEpisodes(items)
                }
                Result.success(items)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Erro ao buscar episódios da API Central"))
            }
        } catch (e: Exception) {
            Log.e("CentralRepository", "Erro getEpisodes: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun createRequest(request: MediaRequest): Result<MediaRequest> = withContext(Dispatchers.IO) {
        try {
            val response = api.createRequest(request)
            if (response.isSuccessful && response.body()?.status == "success") {
                Result.success(response.body()?.data!!)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Erro ao criar pedido na API Central"))
            }
        } catch (e: Exception) {
            Log.e("CentralRepository", "Erro createRequest: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun registerDevice(device: DeviceEntity): Result<DeviceEntity> = withContext(Dispatchers.IO) {
        try {
            val response = api.registerDevice(device)
            if (response.isSuccessful && response.body()?.status == "success") {
                Result.success(response.body()?.data!!)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Erro ao registrar dispositivo na API Central"))
            }
        } catch (e: Exception) {
            Log.e("CentralRepository", "Erro registerDevice: ${e.message}")
            Result.failure(e)
        }
    }
}
