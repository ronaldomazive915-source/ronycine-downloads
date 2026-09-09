package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.util.WebViewUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.BrandRed
import kotlinx.coroutines.delay

/**
 * Trailer playback lifecycle states as required by architecture:
 * IDLE -> LOADING -> READY -> PLAYING / PAUSED / ENDED / ERROR
 */
enum class TrailerPlaybackState {
    IDLE,
    LOADING,
    READY,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR
}

/**
 * Diagnostic error information for trailer playback
 */
data class TrailerErrorInfo(
    val videoKey: String,
    val errorCode: Int = -1,
    val errorMessage: String = "Não foi possível carregar este trailer.",
    val isEmbedRestricted: Boolean = false,
    val isTimeout: Boolean = false,
    val stage: String = "unknown"
)

/**
 * Data representation for testing trailers in Admin mode
 */
data class AdminTrailerTestData(
    val title: String,
    val mediaType: String,
    val trailerKey: String,
    val tmdbId: Int = 0,
    val isOfficial: Boolean = true
)

/**
 * Robust YouTube video ID extractor that supports:
 * - Pure 11-char ID: e.g. "dQw4w9WgXcQ" or "zz4rsZLcaUY"
 * - watch URLs: "https://www.youtube.com/watch?v=zz4rsZLcaUY"
 * - share/short URLs: "https://youtu.be/zz4rsZLcaUY"
 * - embed URLs: "https://www.youtube.com/embed/zz4rsZLcaUY" or "https://www.youtube-nocookie.com/embed/zz4rsZLcaUY"
 * - shorts URLs: "https://www.youtube.com/shorts/zz4rsZLcaUY"
 * - v / live URLs: "https://www.youtube.com/v/zz4rsZLcaUY", "https://www.youtube.com/live/zz4rsZLcaUY"
 * - URLs with additional query params (t=..., si=..., feature=...)
 */
fun extractYoutubeVideoId(input: String?): String? {
    if (input.isNullOrBlank()) return null
    val trimmed = input.trim()

    val pureIdRegex = Regex("^[a-zA-Z0-9_-]{11}$")
    if (pureIdRegex.matches(trimmed)) {
        return trimmed
    }

    val urlRegex = Regex("(?i)(?:youtube(?:-nocookie)?\\.com/(?:[^/\\n\\s]+/\\S+/|(?:v|e(?:mbed)?|shorts|live)/|.*[?&]v=)|youtu\\.be/)([a-zA-Z0-9_-]{11})")
    val match = urlRegex.find(trimmed)
    if (match != null && match.groupValues.size > 1) {
        return match.groupValues[1]
    }

    if (trimmed.contains("v=")) {
        val afterV = trimmed.substringAfter("v=").substringBefore("&").substringBefore("?").substringBefore("#")
        if (pureIdRegex.matches(afterV)) return afterV
    }
    if (trimmed.contains("/embed/")) {
        val afterEmbed = trimmed.substringAfter("/embed/").substringBefore("?").substringBefore("&").substringBefore("#").substringBefore("/")
        if (pureIdRegex.matches(afterEmbed)) return afterEmbed
    }
    if (trimmed.contains("youtu.be/")) {
        val afterShort = trimmed.substringAfter("youtu.be/").substringBefore("?").substringBefore("&").substringBefore("#").substringBefore("/")
        if (pureIdRegex.matches(afterShort)) return afterShort
    }

    return null
}

/**
 * Builds the clean, standard YouTube Embed URL using the official embedded player mechanism.
 */
fun buildYoutubeEmbedUrl(
    youtubeId: String,
    autoPlay: Boolean = false,
    isMuted: Boolean = true,
    showControls: Boolean = false
): String {
    val autoPlayParam = if (autoPlay) "1" else "0"
    val muteParam = if (isMuted) "1" else "0"
    val controlsParam = if (showControls) "1" else "0"
    return "https://www.youtube.com/embed/$youtubeId" +
            "?enablejsapi=1" +
            "&autoplay=$autoPlayParam" +
            "&mute=$muteParam" +
            "&controls=$controlsParam" +
            "&playsinline=1" +
            "&rel=0" +
            "&modestbranding=1" +
            "&origin=https://ronycine.app"
}

/**
 * Helper to open trailer directly on YouTube official app or browser as legitimate fallback.
 */
fun openYouTubeExternal(context: Context, videoKey: String) {
    try {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoKey")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(appIntent)
    } catch (_: Exception) {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoKey")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(webIntent)
        } catch (e: Exception) {
            Log.e("TRAILER", "Failed to open YouTube externally: ${e.message}")
        }
    }
}

/**
 * Generates the HTML player document.
 * Uses a direct standard <iframe> embedded element with postMessage listeners for YouTube events,
 * guaranteeing instantaneous loading without depending on asynchronous dynamic script injection.
 */
private fun buildTrailerPlayerHtml(
    youtubeId: String,
    autoPlay: Boolean,
    isMuted: Boolean,
    showControls: Boolean
): String {
    val autoPlayVal = if (autoPlay) 1 else 0
    val muteVal = if (isMuted) 1 else 0
    val controlsVal = if (showControls) 1 else 0
    val embedUrl = buildYoutubeEmbedUrl(youtubeId, autoPlay, isMuted, showControls)

    return """
    <!DOCTYPE html>
    <html lang="pt-BR">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <meta name="referrer" content="strict-origin-when-cross-origin">
        <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            html, body { width: 100%; height: 100%; overflow: hidden; background-color: #000000; }
            .player-wrapper {
                position: relative;
                width: 100%;
                height: 100%;
                background: #000000;
                overflow: hidden;
            }
            #youtube-iframe {
                position: absolute;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                border: 0;
                display: block;
            }
        </style>
    </head>
    <body>
        <div class="player-wrapper">
            <iframe
                id="youtube-iframe"
                type="text/html"
                width="100%"
                height="100%"
                src="$embedUrl"
                frameborder="0"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                allowfullscreen
                onload="handleIframeLoaded()">
            </iframe>
        </div>
        <script>
            var isReadyNotified = false;
            var isErrorNotified = false;
            var iframeEl = document.getElementById('youtube-iframe');

            function handleIframeLoaded() {
                if (window.AndroidBridge) {
                    window.AndroidBridge.onIframeLoaded();
                }
                // Fallback: If YouTube postMessage doesn't arrive within 1.5s after iframe finishes loading,
                // mark ready so user can see and click play
                setTimeout(function() {
                    if (!isReadyNotified && !isErrorNotified && window.AndroidBridge) {
                        isReadyNotified = true;
                        window.AndroidBridge.onPlayerReady();
                    }
                }, 1500);
            }

            // Listen for official YouTube IFrame postMessage events
            window.addEventListener('message', function(event) {
                try {
                    var data = event.data;
                    if (typeof data === 'string') {
                        data = JSON.parse(data);
                    }
                    if (!data) return;

                    // YouTube onReady event
                    if (data.event === 'onReady' || data.event === 'initialDelivery') {
                        if (!isReadyNotified) {
                            isReadyNotified = true;
                            if (window.AndroidBridge) {
                                window.AndroidBridge.onPlayerReady();
                            }
                        }
                    }

                    // YouTube infoDelivery events (playback states)
                    // playerState: -1 (unstarted), 0 (ended), 1 (playing), 2 (paused), 3 (buffering), 5 (cued)
                    if (data.event === 'infoDelivery' && data.info) {
                        var state = data.info.playerState;
                        if (state === 1) { // PLAYING
                            isReadyNotified = true;
                            if (window.AndroidBridge) {
                                window.AndroidBridge.onPlayerPlaying();
                            }
                        } else if (state === 0) { // ENDED
                            if (window.AndroidBridge) {
                                window.AndroidBridge.onPlayerEnded();
                            }
                        } else if (state === 2) { // PAUSED
                            if (window.AndroidBridge) {
                                window.AndroidBridge.onPlayerPaused();
                            }
                        }
                    }

                    // YouTube onError event:
                    // 2: invalid parameter
                    // 5: HTML5 player error
                    // 100: video not found or removed
                    // 101, 150, 153: video owner does not allow embedded playback
                    if (data.event === 'onError') {
                        var errCode = (data.info !== undefined && data.info !== null) ? data.info : -1;
                        isErrorNotified = true;
                        if (window.AndroidBridge) {
                            window.AndroidBridge.onPlayerError(errCode);
                        }
                    }
                } catch (e) {
                    // Ignore non-json postMessages
                }
            });

            function sendCommand(func, args) {
                try {
                    if (iframeEl && iframeEl.contentWindow) {
                        var msg = JSON.stringify({
                            event: 'command',
                            func: func,
                            args: args || []
                        });
                        iframeEl.contentWindow.postMessage(msg, '*');
                    }
                } catch (e) {}
            }

            function setMuted(muted) {
                sendCommand(muted ? 'mute' : 'unMute');
            }

            function playVideo() {
                sendCommand('playVideo');
            }

            function pauseVideo() {
                sendCommand('pauseVideo');
            }

            // JavaScript safety timeout: 10s maximum before notifying timeout
            setTimeout(function() {
                if (!isReadyNotified && !isErrorNotified && window.AndroidBridge) {
                    window.AndroidBridge.onPlayerTimeout();
                }
            }, 10000);
        </script>
    </body>
    </html>
    """.trimIndent()
}

/**
 * Unified Trailer Player using Android WebView with direct official iframe embed,
 * explicit lifecycle states, automatic candidate fallback support, and mandatory 10-12s timeout.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HeroTrailerPlayer(
    youtubeId: String,
    autoPlay: Boolean,
    isMuted: Boolean,
    onPlayerReady: () -> Unit,
    onPlayerError: () -> Unit,
    onPlayerEnded: () -> Unit,
    modifier: Modifier = Modifier,
    showControls: Boolean = false,
    candidateFallbackKeys: List<String> = emptyList(),
    onPlaying: () -> Unit = {},
    onErrorDetails: ((TrailerErrorInfo) -> Unit)? = null
) {
    // Current active candidates list
    val allCandidates = remember(youtubeId, candidateFallbackKeys) {
        val list = mutableListOf<String>()
        val primary = extractYoutubeVideoId(youtubeId)
        if (!primary.isNullOrBlank()) {
            list.add(primary)
        }
        candidateFallbackKeys.forEach { key ->
            val id = extractYoutubeVideoId(key)
            if (!id.isNullOrBlank() && !list.contains(id)) {
                list.add(id)
            }
        }
        if (list.isEmpty() && !youtubeId.isBlank()) {
            list.add(youtubeId)
        }
        list
    }

    var currentCandidateIndex by remember(youtubeId) { mutableStateOf(0) }
    val activeYoutubeId = allCandidates.getOrNull(currentCandidateIndex) ?: youtubeId

    var rendererCrashCount by remember(activeYoutubeId) { mutableStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var playbackState by remember(activeYoutubeId) { mutableStateOf(TrailerPlaybackState.LOADING) }

    val lifecycleOwner = LocalLifecycleOwner.current

    val htmlContent = remember(activeYoutubeId, autoPlay, isMuted, showControls) {
        val embedUrl = buildYoutubeEmbedUrl(activeYoutubeId, autoPlay, isMuted, showControls)
        Log.d("TRAILER", "[TRAILER] tmdbId: candidate, videoKey: $activeYoutubeId, site: YouTube, embedUrl: $embedUrl, autoPlay: $autoPlay, muted: $isMuted")
        buildTrailerPlayerHtml(activeYoutubeId, autoPlay, isMuted, showControls)
    }

    // MANDATORY TIMEOUT: Never allow infinite loading. If after 11 seconds the player is still LOADING,
    // transition to ERROR state and notify caller.
    LaunchedEffect(activeYoutubeId) {
        playbackState = TrailerPlaybackState.LOADING
        delay(11_000L)
        if (playbackState == TrailerPlaybackState.LOADING) {
            Log.e("TRAILER ERROR", "[TRAILER ERROR] videoKey: $activeYoutubeId, stage: loading_timeout, errorCode: 408, errorMessage: Timeout ao carregar trailer (11s esgotados)")
            if (currentCandidateIndex + 1 < allCandidates.size) {
                val nextIdx = currentCandidateIndex + 1
                val nextKey = allCandidates[nextIdx]
                Log.i("TRAILER", "[TRAILER] Timeout. Alternando para candidato alternativo ($nextIdx de ${allCandidates.size}): $nextKey")
                currentCandidateIndex = nextIdx
            } else {
                playbackState = TrailerPlaybackState.ERROR
                val errInfo = TrailerErrorInfo(
                    videoKey = activeYoutubeId,
                    errorCode = 408,
                    errorMessage = "Tempo limite esgotado ao carregar o trailer.",
                    isTimeout = true,
                    stage = "loading_timeout"
                )
                onErrorDetails?.invoke(errInfo)
                onPlayerError()
            }
        }
    }

    LaunchedEffect(isMuted) {
        webViewRef?.evaluateJavascript("setMuted($isMuted)", null)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                webViewRef?.evaluateJavascript("pauseVideo()", null)
                webViewRef?.onPause()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                webViewRef?.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(activeYoutubeId) {
        onDispose {
            webViewRef?.let { wv ->
                try {
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.destroy()
                } catch (_: Exception) {}
            }
            webViewRef = null
        }
    }

    Box(modifier = modifier) {
        androidx.compose.runtime.key(activeYoutubeId, rendererCrashCount) {
            AndroidView(
                factory = { ctx ->
                    // Pre-create WebView Code Cache directories to prevent Chromium opendir errors
                    try {
                        val basePaths = listOf(
                            "WebView/Default/HTTP Cache/Code Cache",
                            "webview/Default/HTTP Cache/Code Cache",
                            "WebView/Default/HTTP Cache/Code Cache/js",
                            "WebView/Default/HTTP Cache/Code Cache/wasm",
                            "webview/Default/HTTP Cache/Code Cache/js",
                            "webview/Default/HTTP Cache/Code Cache/wasm"
                        )
                        basePaths.forEach { p ->
                            val dir = java.io.File(ctx.cacheDir, p)
                            if (!dir.exists()) dir.mkdirs()
                        }
                    } catch (_: Exception) {}

                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        WebViewUtils.applySafeLayerType(this)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            // Remove "; wv" from user agent so YouTube serves standard mobile web player
                            val currentUa = userAgentString ?: ""
                            if (currentUa.contains("; wv")) {
                                userAgentString = currentUa.replace("; wv", "")
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                safeBrowsingEnabled = false
                            }
                        }

                        webChromeClient = object : WebChromeClient() {}

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onIframeLoaded() {
                                post {
                                    Log.d("TRAILER", "[TRAILER] iframeLoaded para vídeo: $activeYoutubeId")
                                }
                            }

                            @JavascriptInterface
                            fun onPlayerReady() {
                                post {
                                    Log.d("TRAILER", "[TRAILER] Status da reprodução: PRONTO (ID: $activeYoutubeId)")
                                    playbackState = TrailerPlaybackState.READY
                                    onPlayerReady()
                                }
                            }

                            @JavascriptInterface
                            fun onPlayerPlaying() {
                                post {
                                    Log.d("TRAILER", "[TRAILER] Status da reprodução: REPRODUZINDO (ID: $activeYoutubeId)")
                                    playbackState = TrailerPlaybackState.PLAYING
                                    onPlaying()
                                }
                            }

                            @JavascriptInterface
                            fun onPlayerPaused() {
                                post {
                                    Log.d("TRAILER", "[TRAILER] Status da reprodução: PAUSADO (ID: $activeYoutubeId)")
                                    playbackState = TrailerPlaybackState.PAUSED
                                }
                            }

                            @JavascriptInterface
                            fun onPlayerTimeout() {
                                post {
                                    if (playbackState == TrailerPlaybackState.LOADING) {
                                        Log.w("TRAILER", "[TRAILER] Timeout JS atingido para ID: $activeYoutubeId")
                                        if (currentCandidateIndex + 1 < allCandidates.size) {
                                            val nextIdx = currentCandidateIndex + 1
                                            currentCandidateIndex = nextIdx
                                        } else {
                                            playbackState = TrailerPlaybackState.ERROR
                                            val errInfo = TrailerErrorInfo(
                                                videoKey = activeYoutubeId,
                                                errorCode = 408,
                                                errorMessage = "Tempo limite esgotado ao carregar o trailer.",
                                                isTimeout = true,
                                                stage = "js_timeout"
                                            )
                                            onErrorDetails?.invoke(errInfo)
                                            onPlayerError()
                                        }
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onPlayerError(errorCode: Int) {
                                post {
                                    val isRestricted = (errorCode == 101 || errorCode == 150 || errorCode == 153)
                                    val msg = if (isRestricted) {
                                        "Este trailer não pode ser reproduzido incorporado (restrito pelo proprietário)."
                                    } else {
                                        "Erro do YouTube ($errorCode) ao reproduzir trailer."
                                    }
                                    Log.e("TRAILER ERROR", "[TRAILER ERROR] videoKey: $activeYoutubeId, stage: player_event, errorCode: $errorCode, errorMessage: $msg")

                                    // Attempt next candidate if available
                                    if (currentCandidateIndex + 1 < allCandidates.size) {
                                        val nextIdx = currentCandidateIndex + 1
                                        val nextKey = allCandidates[nextIdx]
                                        Log.i("TRAILER", "[TRAILER] Alternando para próximo candidato TMDB ($nextIdx de ${allCandidates.size}): $nextKey")
                                        currentCandidateIndex = nextIdx
                                    } else {
                                        playbackState = TrailerPlaybackState.ERROR
                                        val errInfo = TrailerErrorInfo(
                                            videoKey = activeYoutubeId,
                                            errorCode = errorCode,
                                            errorMessage = msg,
                                            isEmbedRestricted = isRestricted,
                                            stage = "youtube_error"
                                        )
                                        onErrorDetails?.invoke(errInfo)
                                        onPlayerError()
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onPlayerEnded() {
                                post {
                                    Log.d("TRAILER", "[TRAILER] Reprodução do trailer finalizada (ID: $activeYoutubeId)")
                                    playbackState = TrailerPlaybackState.ENDED
                                    onPlayerEnded()
                                }
                            }
                        }, "AndroidBridge")

                        webViewClient = object : WebViewClient() {
                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: android.webkit.SslErrorHandler?,
                                error: android.net.http.SslError?
                            ) {
                                try {
                                    handler?.proceed()
                                } catch (_: Exception) {
                                    handler?.cancel()
                                }
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: android.webkit.RenderProcessGoneDetail?
                            ): Boolean {
                                Log.w("TRAILER", "[TRAILER] WebView render process crash detected, recovering safely...")
                                WebViewUtils.safeDestroy(view)
                                webViewRef = null
                                playbackState = TrailerPlaybackState.ERROR
                                val errInfo = TrailerErrorInfo(
                                    videoKey = activeYoutubeId,
                                    errorCode = 500,
                                    errorMessage = "Processo de renderização do WebView foi encerrado.",
                                    stage = "render_process_gone"
                                )
                                try {
                                    onErrorDetails?.invoke(errInfo)
                                } catch (_: Exception) {}
                                try {
                                    onPlayerError()
                                } catch (_: Exception) {}
                                return true
                            }
                        }

                        loadDataWithBaseURL("https://ronycine.app", htmlContent, "text/html", "UTF-8", null)
                        webViewRef = this
                    }
                },
                update = { wv ->
                    webViewRef = wv
                },
                onRelease = { wv ->
                    WebViewUtils.safeDestroy(wv)
                    webViewRef = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Loading indicator: ONLY visible during LOADING state.
        // As soon as state becomes READY, PLAYING, or ERROR, it disappears immediately
        // allowing the underlying player to be interactive.
        AnimatedVisibility(
            visible = (playbackState == TrailerPlaybackState.LOADING),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = BrandRed,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

