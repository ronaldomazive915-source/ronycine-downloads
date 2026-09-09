package com.example.util

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

/**
 * Utility to provide safe rendering and lifecycle management for Android WebViews.
 * Specifically prevents Mesa DRI rendernode crashes (Failed to open rendernode: No such file or directory)
 * and Chromium renderer process crashes (aw_browser_terminator.cc code -1) in emulators and virtual environments.
 */
object WebViewUtils {

    /**
     * Checks whether the current device is an emulator/virtual environment.
     */
    fun isEmulator(): Boolean {
        val fp = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val mfr = Build.MANUFACTURER.lowercase()
        val hw = Build.HARDWARE.lowercase()
        val prod = Build.PRODUCT.lowercase()
        val brand = Build.BRAND.lowercase()
        val dev = Build.DEVICE.lowercase()
        val board = Build.BOARD.lowercase()

        return fp.startsWith("generic") ||
                fp.startsWith("unknown") ||
                model.contains("google_sdk") ||
                model.contains("emulator") ||
                model.contains("droid4x") ||
                model.contains("android sdk") ||
                mfr.contains("genymotion") ||
                hw.contains("goldfish") ||
                hw.contains("ranchu") ||
                hw.contains("vbox") ||
                prod.contains("sdk") ||
                prod.contains("google_sdk") ||
                prod.contains("emulator") ||
                prod.contains("simulator") ||
                prod.contains("vbox") ||
                prod.contains("cuttlefish") ||
                dev.contains("cuttlefish") ||
                board.contains("goldfish") ||
                brand.startsWith("generic") ||
                dev.startsWith("generic")
    }

    /**
     * Determines whether the environment requires software layer rendering
     * (e.g., emulators or virtual containers using Mesa without /dev/dri/renderD128).
     */
    val isSoftwareRendererNeeded: Boolean by lazy {
        val emulator = isEmulator()

        val hasHardwareDrmNode = try {
            File("/dev/dri/renderD128").exists() || File("/dev/dri/card0").exists()
        } catch (_: Exception) {
            false
        }

        // On emulators and environments lacking DRM rendernodes, Mesa cannot allocate GPU surfaces,
        // which causes Chromium's renderer process to crash.
        emulator || !hasHardwareDrmNode
    }

    /**
     * Applies safe rendering layer type to a WebView.
     *
     * IMPORTANT: WebViews rendering HTML5 video, WebGL, or Canvas MUST use View.LAYER_TYPE_NONE.
     * Setting View.LAYER_TYPE_SOFTWARE causes HTML5 <video> elements to decode audio normally
     * but prevents hardware video texture compositing, causing the video frame to be completely black.
     */
    fun applySafeLayerType(webView: WebView, forceSoftware: Boolean = false) {
        try {
            webView.setLayerType(View.LAYER_TYPE_NONE, null)
        } catch (_: Exception) {}
    }

    /**
     * Safely cleans up and disposes of a WebView instance.
     */
    fun safeDestroy(webView: WebView?) {
        if (webView == null) return
        try {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = object : WebViewClient() {
                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean = true
            }
            try {
                webView.loadUrl("about:blank")
            } catch (_: Exception) {}
            try {
                webView.onPause()
                webView.pauseTimers()
            } catch (_: Exception) {}
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        } catch (_: Exception) {}
    }
}
