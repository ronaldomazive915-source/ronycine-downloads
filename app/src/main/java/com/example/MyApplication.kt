package com.example

import android.app.Application
import android.os.Build
import android.webkit.WebView
import java.io.File

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Configure WebView data directory suffix for multi-process isolation (Android P+)
        // Must only be set on secondary processes to avoid corrupting default webview cache
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val processName = getProcessName()
                if (packageName != processName) {
                    val suffix = processName.substringAfterLast(":").ifEmpty { "worker" }
                    WebView.setDataDirectorySuffix(suffix)
                }
            } catch (e: Exception) {
                android.util.Log.w("MyApplication", "WebView setDataDirectorySuffix: ${e.message}")
            }
        }

        // Ensure WebView Code Cache directories (js and wasm) are initialized
        // to prevent Chromium simple_file_enumerator opendir errors
        try {
            val basePaths = listOf(
                "WebView/Default/HTTP Cache/Code Cache",
                "webview/Default/HTTP Cache/Code Cache",
                "webview_playfilme_webview/Default/HTTP Cache/Code Cache"
            )
            basePaths.forEach { basePath ->
                val jsDir = File(cacheDir, "$basePath/js")
                val wasmDir = File(cacheDir, "$basePath/wasm")
                if (!jsDir.exists()) jsDir.mkdirs()
                if (!wasmDir.exists()) wasmDir.mkdirs()
            }
        } catch (_: Exception) {}

        // Inicializa o Firebase o mais cedo possível para evitar erros de FIS/FCM
        try {
            val service = com.example.data.remote.FirebaseService.getInstance(this)
            if (service.firebaseApp != null) {
                try {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled = false
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            android.util.Log.e("MyApplication", "Erro ao inicializar Firebase: ${e.message}")
        }
    }
}

