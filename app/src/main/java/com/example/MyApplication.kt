package com.example

import android.app.Application
import android.os.Build
import android.webkit.WebView
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

class MyApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150 MB
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
    }

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

        // Ensure WebView Code Cache directories (js, wasm, index-dir) are initialized
        // to prevent Chromium simple_file_enumerator opendir errors and simple_index_file warnings
        try {
            val basePaths = listOf(
                "WebView/Default/HTTP Cache/Code Cache",
                "webview/Default/HTTP Cache/Code Cache",
                "WebView/Default/Code Cache",
                "webview/Default/Code Cache",
                "WebView/HTTP Cache/Code Cache",
                "webview/HTTP Cache/Code Cache",
                "WebView/Default/HTTP Cache",
                "webview/Default/HTTP Cache",
                "webview_playfilme_webview/Default/HTTP Cache/Code Cache",
                "app_webview/Default/HTTP Cache/Code Cache"
            )
            listOf(cacheDir, filesDir).forEach { root ->
                basePaths.forEach { basePath ->
                    try {
                        val baseDir = File(root, basePath)
                        if (!baseDir.exists()) baseDir.mkdirs()
                        val jsDir = File(root, "$basePath/js")
                        val wasmDir = File(root, "$basePath/wasm")
                        val indexDir = File(root, "$basePath/index-dir")
                        if (!jsDir.exists()) jsDir.mkdirs()
                        if (!wasmDir.exists()) wasmDir.mkdirs()
                        if (!indexDir.exists()) indexDir.mkdirs()
                    } catch (_: Exception) {}
                }
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

