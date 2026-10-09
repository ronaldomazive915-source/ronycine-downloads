package com.example.data.remote

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.*
import okio.ByteString

class CentralWebSocketClient(
    private val baseUrl: String,
    private val onMessageReceived: (String, String) -> Unit // type, data
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    fun connect(token: String?) {
        val wsUrl = baseUrl.replace("https://", "wss://").replace("http://", "ws://") + "/ws"
        val request = Request.Builder()
            .url(wsUrl)
            .apply {
                token?.let { header("Authorization", "Bearer $it") }
            }
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("CentralWS", "WebSocket Aberto")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("CentralWS", "Mensagem Recebida: $text")
                try {
                    val json = moshi.adapter(Map::class.java).fromJson(text) as? Map<String, Any>
                    val type = json?.get("type") as? String
                    val data = json?.get("data").toString()
                    if (type != null) {
                        onMessageReceived(type, data)
                    }
                } catch (e: Exception) {
                    Log.e("CentralWS", "Erro ao processar mensagem: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                Log.d("CentralWS", "WebSocket Fechando: $code / $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("CentralWS", "Erro WebSocket: ${t.message}")
                // Attempt reconnect after delay
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Desconectado pelo usuário")
    }
}
