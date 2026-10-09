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
     * Determines whether the environment requires software layer rendering.
     * Real devices should always use standard hardware acceleration to avoid memory crashes.
     */
    val isSoftwareRendererNeeded: Boolean by lazy {
        false
    }

    /**
     * Applies safe rendering layer type to a WebView.
     */
    fun applySafeLayerType(webView: WebView, forceSoftware: Boolean = false) {
        try {
            if (forceSoftware) {
                webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                android.util.Log.i("WebViewUtils", "Applied LAYER_TYPE_SOFTWARE to WebView")
            } else {
                // LAYER_TYPE_NONE allows normal hardware-accelerated surface compositing
                webView.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        } catch (_: Exception) {}
    }

    /**
     * Safely cleans up and disposes of a WebView instance.
     * When isDead = true (from onRenderProcessGone), avoids calling methods on the destroyed engine.
     */
    fun safeDestroy(webView: WebView?, isDead: Boolean = false) {
        if (webView == null) return
        try {
            (webView.parent as? ViewGroup)?.removeView(webView)
            if (!isDead) {
                try {
                    webView.stopLoading()
                    webView.webChromeClient = null
                    webView.webViewClient = object : WebViewClient() {
                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean = true
                    }
                    webView.loadUrl("about:blank")
                    webView.onPause()
                } catch (_: Exception) {}
            }
            webView.destroy()
        } catch (_: Exception) {}
    }
}
