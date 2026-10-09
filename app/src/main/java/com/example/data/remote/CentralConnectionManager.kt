package com.example.data.remote

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.local.PlayFilmeDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

sealed class CentralConnectionState {
    object Disconnected : CentralConnectionState()
    object Pairing : CentralConnectionState()
    data class Connected(val accountName: String?) : CentralConnectionState()
    data class Error(val message: String) : CentralConnectionState()
}

class CentralConnectionManager(
    private val api: CentralApiService,
    private val dao: PlayFilmeDao,
    private val context: Context
) {
    private val prefs = context.getSharedPreferences("central_connection_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _connectionState = MutableStateFlow<CentralConnectionState>(CentralConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private var heartbeatJob: Job? = null
    private var syncJob: Job? = null
    private var webSocketClient: CentralWebSocketClient? = null

    init {
        val instanceId = prefs.getString("instance_id", null)
        val token = prefs.getString("connection_token", null)
        val accountName = prefs.getString("account_name", null)

        if (instanceId != null && token != null) {
            _connectionState.value = CentralConnectionState.Connected(accountName)
            startActiveConnection(instanceId, token)
        }
    }

    fun pair(pairingCode: String) {
        scope.launch {
            _connectionState.value = CentralConnectionState.Pairing
            try {
                val instanceId = prefs.getString("instance_id", UUID.randomUUID().toString()) ?: UUID.randomUUID().toString()
                prefs.edit().putString("instance_id", instanceId).apply()

                val deviceInfo = CentralDeviceInfo(
                    model = Build.MODEL,
                    manufacturer = Build.MANUFACTURER,
                    androidVersion = Build.VERSION.RELEASE,
                    appVersion = getAppVersion(),
                    instanceId = instanceId
                )

                val response = api.pair(CentralPairRequest(pairingCode, deviceInfo))
                if (response.isSuccessful && response.body()?.status == "success") {
                    val data = response.body()?.data!!
                    saveConnection(data.instanceId, data.connectionToken, data.accountName)
                    _connectionState.value = CentralConnectionState.Connected(data.accountName)
                    startActiveConnection(data.instanceId, data.connectionToken)
                } else {
                    _connectionState.value = CentralConnectionState.Error(response.body()?.message ?: "Código de pareamento inválido")
                }
            } catch (e: Exception) {
                Log.e("CentralConn", "Erro ao parear: ${e.message}")
                _connectionState.value = CentralConnectionState.Error("Erro de conexão com a Central")
            }
        }
    }

    private fun saveConnection(instanceId: String, token: String, accountName: String?) {
        prefs.edit().apply {
            putString("instance_id", instanceId)
            putString("connection_token", token)
            putString("account_name", accountName)
            apply()
        }
    }

    fun disconnect() {
        prefs.edit().clear().apply()
        stopActiveConnection()
        _connectionState.value = CentralConnectionState.Disconnected
    }

    private fun startActiveConnection(instanceId: String, token: String) {
        stopActiveConnection()
        startHeartbeat(instanceId, token)
        startSync(token)
        startWebSocket(token)
    }

    private fun stopActiveConnection() {
        heartbeatJob?.cancel()
        syncJob?.cancel()
        webSocketClient?.disconnect()
        webSocketClient = null
    }

    private fun startHeartbeat(instanceId: String, token: String) {
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    // We might need to set the token in CentralNetwork or use a different client
                    // For now, let's assume CentralNetwork handles the main app auth,
                    // and we might need a dedicated client or interceptor for connection token.
                    // If the Central API uses the same Bearer logic:
                    CentralNetwork.setAuthToken(token) 
                    api.heartbeat(CentralHeartbeatRequest(instanceId))
                } catch (e: Exception) {
                    Log.e("CentralConn", "Heartbeat failed: ${e.message}")
                }
                delay(30000) // 30 seconds
            }
        }
    }

    private fun startSync(token: String) {
        syncJob = scope.launch {
            while (isActive) {
                try {
                    val lastSync = prefs.getLong("last_sync_timestamp", 0L)
                    CentralNetwork.setAuthToken(token)
                    val response = api.sync(lastSync)
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val data = response.body()?.data!!
                        handleSyncChanges(data.changes)
                        prefs.edit().putLong("last_sync_timestamp", data.lastSync).apply()
                    }
                } catch (e: Exception) {
                    Log.e("CentralConn", "Sync failed: ${e.message}")
                }
                delay(60000) // 1 minute
            }
        }
    }

    private fun startWebSocket(token: String) {
        webSocketClient = CentralWebSocketClient("https://api.ronycine.com/api/v1/") { type, data ->
            scope.launch {
                // Handle real-time events via handleSyncChanges or similar
            }
        }
        webSocketClient?.connect(token)
    }

    private suspend fun handleSyncChanges(changes: List<CentralChange>) {
        // Implement logic to apply changes to Local DAO
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}
