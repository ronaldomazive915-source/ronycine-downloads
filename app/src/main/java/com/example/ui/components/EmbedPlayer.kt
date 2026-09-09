package com.example.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkSurface
import com.example.util.WebViewUtils
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream

/**
 * Audio Source specification:
 * - DUBLADO (Default): mgeb.top
 * - LEGENDADO: vidsrc.tw
 */
enum class EmbedAudioSource(val label: String, val providerDomain: String) {
    DUBLADO("Dublado", "mgeb.top"),
    LEGENDADO("Legendado", "vidsrc.tw")
}

/**
 * Generates official RONYCINE Embed URLs following strict specifications:
 *
 * Movies:
 *   Dublado:   https://mgeb.top/embed/{tmdb_id}?player=vidstack#color:E50914
 *   Legendado: https://vidsrc.tw/embed/movie/{tmdbId}
 *
 * Series:
 *   Dublado:   https://mgeb.top/embed/{tmdb_id}/{season_number}/{episode_number}?player=vidstack#color:E50914
 *   Legendado: https://vidsrc.tw/embed/tv/{tmdbId}/{season}/{episode}
 *
 * IMDb fallback when tmdbId is not available:
 *   Movie:     https://mgeb.top/embed/{imdb_id}?player=vidstack#color:E50914
 *   Series:    https://mgeb.top/embed/{imdb_id}/{season_number}/{episode_number}?player=vidstack#color:E50914
 */
object EmbedUrlBuilder {
    const val RONYCINE_COLOR = "E50914"
    const val DEFAULT_PLAYER = "vidstack"

    fun isValidTmdbId(tmdbId: Int?): Boolean {
        return tmdbId != null && tmdbId > 0
    }

    fun buildUrl(
        mediaType: String,
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
        imdbId: String? = null,
        audioSource: EmbedAudioSource = EmbedAudioSource.DUBLADO,
        player: String? = null,
        color: String? = null,
        dsLang: String? = null
    ): String {
        val audioLabel = if (audioSource == EmbedAudioSource.DUBLADO) "Dublado" else "Legendado"
        val effectivePlayer = player ?: DEFAULT_PLAYER
        val effectiveColor = color ?: RONYCINE_COLOR

        return com.example.util.PlayerUtils.buildPlayerUrl(
            provider = if (audioSource == EmbedAudioSource.DUBLADO) "MegaEmbed" else "vidsrc",
            mediaType = mediaType,
            tmdbId = if (tmdbId > 0) tmdbId else null,
            imdbId = imdbId,
            season = season,
            episode = episode,
            audio = audioLabel,
            player = effectivePlayer,
            color = effectiveColor,
            dsLang = dsLang
        )
    }
}

/**
 * Unified Embed Player for RONYCINE.
 * Single component responsible for all media playback embeds.
 *
 * Automatically mounts and dismantles WebViews on episode/source changes or screen exit,
 * ensuring no orphaned players, no audio duplicates, and pristine resource liberation.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbedPlayer(
    mediaType: String,
    tmdbId: Int,
    modifier: Modifier = Modifier,
    season: Int? = null,
    episode: Int? = null,
    imdbId: String? = null,
    audioSource: EmbedAudioSource = EmbedAudioSource.DUBLADO,
    customUrl: String? = null,
    title: String = "RONYCINE",
    isFullscreen: Boolean = false,
    autoplayEnabled: Boolean = true,
    onToggleFullscreen: (() -> Unit)? = null,
    onAudioSourceChange: ((EmbedAudioSource) -> Unit)? = null,
    onPlaybackProgress: ((currentTime: Double, duration: Double, event: String) -> Unit)? = null,
    onTryAgain: (() -> Unit)? = null,
    onWebViewCreated: ((WebView?) -> Unit)? = null,
    onUrlChanged: ((String) -> Unit)? = null,
    playerLoadId: Int = 0
) {
    val context = LocalContext.current

    val embedUrl = remember(mediaType, tmdbId, season, episode, imdbId, audioSource, customUrl) {
        if (!customUrl.isNullOrBlank()) {
            customUrl
        } else {
            EmbedUrlBuilder.buildUrl(
                mediaType = mediaType,
                tmdbId = tmdbId,
                season = season,
                episode = episode,
                imdbId = imdbId,
                audioSource = audioSource
            )
        }
    }

    LaunchedEffect(embedUrl) {
        android.util.Log.i("RONYCINE_PLAYER", "PLAYER_OPEN: tmdbId=$tmdbId, mediaType=$mediaType, season=$season, episode=$episode, url=$embedUrl")
    }

    val isValidTarget = embedUrl.isNotBlank() && (EmbedUrlBuilder.isValidTmdbId(tmdbId) || !imdbId.isNullOrBlank() || !customUrl.isNullOrBlank())

    var isLoading by remember(embedUrl) { mutableStateOf(true) }
    var hasError by remember(embedUrl) { mutableStateOf(!isValidTarget) }
    var errorMessage by remember(embedUrl) {
        mutableStateOf(
            if (!isValidTarget) "Não foi possível carregar o player. Identificador de mídia inválido."
            else null
        )
    }
    var loadProgress by remember(embedUrl) { mutableIntStateOf(0) }
    var retryCount by remember(embedUrl) { mutableIntStateOf(0) }

    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var isCustomViewShowing by remember { mutableStateOf(false) }
    var customViewContainerRef by remember { mutableStateOf<FrameLayout?>(null) }
    var customViewCallbackRef by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val showCustomView: (View?, WebChromeClient.CustomViewCallback?) -> Unit = remember(context, embedUrl) {
        { view, callback ->
            android.util.Log.i("RONYCINE_FULLSCREEN", "FULLSCREEN_REQUEST: HTML5 custom view requested. Source=$embedUrl")
            val activity = context as? Activity
            if (activity != null && view != null) {
                val decorView = activity.window?.decorView as? ViewGroup
                if (decorView != null) {
                    customViewContainerRef?.let { oldContainer ->
                        try { decorView.removeView(oldContainer) } catch (_: Exception) {}
                    }
                    val container = FrameLayout(context).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        addView(view, ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ))
                    }
                    customViewContainerRef = container
                    customViewCallbackRef = callback
                    isCustomViewShowing = true

                    decorView.addView(container)
                    activeWebView?.visibility = View.INVISIBLE

                    try {
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        val window = activity.window
                        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                        insetsController.hide(WindowInsetsCompat.Type.systemBars())
                        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } catch (_: Exception) {}

                    android.util.Log.i("RONYCINE_FULLSCREEN", "FULLSCREEN_ENTER: HTML5 custom view mounted in decorView. isFullscreen=true")
                    if (!isFullscreen) {
                        onToggleFullscreen?.invoke()
                    }
                }
            }
        }
    }

    val hideCustomView: () -> Unit = remember(context) {
        {
            android.util.Log.i("RONYCINE_FULLSCREEN", "FULLSCREEN_EXIT: Hiding HTML5 custom view")
            val activity = context as? Activity
            val decorView = activity?.window?.decorView as? ViewGroup

            customViewContainerRef?.let { container ->
                try {
                    container.removeAllViews()
                    decorView?.removeView(container)
                } catch (_: Exception) {}
            }
            customViewContainerRef = null

            try {
                customViewCallbackRef?.onCustomViewHidden()
            } catch (_: Exception) {}
            customViewCallbackRef = null
            isCustomViewShowing = false

            activeWebView?.visibility = View.VISIBLE

            if (activity != null) {
                try {
                    val window = activity.window
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                } catch (_: Exception) {}
            }

            if (isFullscreen) {
                onToggleFullscreen?.invoke()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            hideCustomView()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Observe App Lifecycle to pause/resume playback safely
    DisposableEffect(lifecycleOwner, activeWebView) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    try {
                        activeWebView?.onPause()
                        activeWebView?.pauseTimers()
                    } catch (_: Exception) {}
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    try {
                        activeWebView?.onResume()
                        activeWebView?.resumeTimers()
                    } catch (_: Exception) {}
                }
                androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> {
                    try {
                        activeWebView?.stopLoading()
                        activeWebView?.loadUrl("about:blank")
                        activeWebView?.destroy()
                    } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Timeout guard and loading dismiss guard: ensures overlay never gets stuck over video
    LaunchedEffect(embedUrl, retryCount, isValidTarget) {
        if (!isValidTarget) {
            isLoading = false
            hasError = true
            errorMessage = "Não foi possível carregar o player. TMDB ID não encontrado no catálogo."
            return@LaunchedEffect
        }

        isLoading = true
        hasError = false
        errorMessage = null
        loadProgress = 0

        // Auto-dismiss loading overlay after 3 seconds so the video canvas is never covered
        delay(3000L)
        if (isLoading && !hasError) {
            isLoading = false
        }
    }

    // Back handler for fullscreen HTML5 custom video views or player fullscreen mode
    BackHandler(enabled = isCustomViewShowing || isFullscreen) {
        if (isCustomViewShowing) {
            hideCustomView()
        } else if (isFullscreen) {
            android.util.Log.i("RONYCINE_FULLSCREEN", "FULLSCREEN_EXIT: Exit triggered by BackHandler")
            onToggleFullscreen?.invoke()
        }
    }

    // Keep playback seamlessly running during fullscreen enter/exit
    LaunchedEffect(isFullscreen) {
        val stateLog = if (isFullscreen) "FULLSCREEN_ENTER" else "FULLSCREEN_EXIT"
        android.util.Log.i("RONYCINE_FULLSCREEN", "$stateLog: isFullscreen changed to $isFullscreen for url=$embedUrl")
        try {
            activeWebView?.evaluateJavascript(
                """
                if (window.__ronycine_notify_transition) { window.__ronycine_notify_transition($isFullscreen); }
                (function() {
                    var docEl = document.documentElement;
                    var bodyEl = document.body;
                    if (docEl) {
                        if ($isFullscreen) { docEl.classList.add('ronycine-player-fullscreen'); }
                        else { docEl.classList.remove('ronycine-player-fullscreen'); }
                    }
                    if (bodyEl) {
                        if ($isFullscreen) { bodyEl.classList.add('ronycine-player-fullscreen'); }
                        else { bodyEl.classList.remove('ronycine-player-fullscreen'); }
                    }
                })();
                """.trimIndent(),
                null
            )
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("embed_player_container"),
        contentAlignment = Alignment.Center
    ) {
        // --- ACTIVE WEBVIEW INSTANCE ---
        // key ensures previous WebView is cleanly released and destroyed before new one mounts
        if (!hasError && embedUrl.isNotBlank()) {
            key(embedUrl, retryCount, playerLoadId) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            // Mitigation for MESA / RenderNode errors: 
                            // Software layer on emulators or after crash, avoiding forced LAYER_TYPE_HARDWARE.
                            WebViewUtils.applySafeLayerType(this, forceSoftware = (retryCount > 0))

                            // Third party cookies required for embeds
                            try {
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            } catch (_: Exception) {}

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                loadsImagesAutomatically = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                allowFileAccess = false
                                allowContentAccess = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                builtInZoomControls = false
                                displayZoomControls = false
                                setSupportMultipleWindows(false)
                                javaScriptCanOpenWindowsAutomatically = false
                                cacheMode = WebSettings.LOAD_DEFAULT
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    safeBrowsingEnabled = false
                                }
                                setGeolocationEnabled(false)
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            }

                            addJavascriptInterface(object {
                                @android.webkit.JavascriptInterface
                                fun onPlayerEvent(event: String, currentTime: Double, duration: Double) {
                                    android.util.Log.d("RONYCINE_PLAYER", "VIDSRC_EVENT: event=$event, time=$currentTime, dur=$duration")
                                    onPlaybackProgress?.invoke(currentTime, duration, event)
                                }

                                @android.webkit.JavascriptInterface
                                fun onFullscreenStateChanged(isJsFullscreen: Boolean) {
                                    android.util.Log.i("RONYCINE_FULLSCREEN", "PLAYER_STATE: JS fullscreen change event. isJsFullscreen=$isJsFullscreen")
                                    if (isJsFullscreen && !isFullscreen) {
                                        onToggleFullscreen?.invoke()
                                    }
                                }

                                @android.webkit.JavascriptInterface
                                fun requestOrientation(orientationMode: String?) {
                                    android.util.Log.i("RONYCINE_FULLSCREEN", "JS_ORIENTATION_REQUEST: mode=$orientationMode")
                                    val activity = context as? Activity ?: return
                                    activity.runOnUiThread {
                                        try {
                                            val mode = orientationMode?.lowercase() ?: ""
                                            when {
                                                mode.contains("landscape") -> {
                                                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                                }
                                                mode.contains("portrait") -> {
                                                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                                }
                                                mode == "toggle" || mode == "rotate" || mode == "change" || mode == "flip" || mode.isEmpty() -> {
                                                    val currentOrient = activity.resources.configuration.orientation
                                                    if (currentOrient == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                                                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                                    } else {
                                                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                                    }
                                                }
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            }, "RonycineBridge")

                            webViewClient = object : WebViewClient() {
                                private val blockedAdKeywords = listOf(
                                    "doubleclick", "googlesyndication", "popads", "adsterra",
                                    "monetag", "histats", "propellerads", "exoclick", "juicyads",
                                    "onclick", "adnxs", "trafficjunky", "adcolony", "admob",
                                    "taboola", "outbrain", "criteo", "pubmatic", "openx",
                                    "adroll", "smartadserver", "popunder", "adform", "yieldmo",
                                    "clickadu", "hilltopads", "bet365", "1xbet", "blaze"
                                )

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val uri = request?.url?.toString()?.lowercase() ?: return null
                                    for (kw in blockedAdKeywords) {
                                        if (uri.contains(kw)) {
                                            return WebResourceResponse(
                                                "text/plain",
                                                "UTF-8",
                                                ByteArrayInputStream(ByteArray(0))
                                            )
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val targetUrl = request?.url?.toString() ?: return false
                                    // Block non-http intents to avoid hijacking app navigation
                                    if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                                        android.util.Log.i("RONYCINE_PLAYER", "PLAYER_EXTERNAL_REDIRECT: Blocked non-http/https intent url=$targetUrl")
                                        return true
                                    }
                                    
                                    // Check if it looks like an ad redirect
                                    val lowercaseUrl = targetUrl.lowercase()
                                    val isPotentialAdRedirect = lowercaseUrl.contains("redirect") || 
                                            lowercaseUrl.contains("ads") || 
                                            lowercaseUrl.contains("popunder") || 
                                            lowercaseUrl.contains("click") || 
                                            lowercaseUrl.contains("promo") ||
                                            lowercaseUrl.contains("game") ||
                                            lowercaseUrl.contains("bet") ||
                                            lowercaseUrl.contains("cassino") ||
                                            lowercaseUrl.contains("slot")
                                    
                                    if (isPotentialAdRedirect) {
                                        android.util.Log.i("RONYCINE_PLAYER", "PLAYER_EXTERNAL_REDIRECT: Blocked potential ad redirect URL=$targetUrl")
                                        return true // Block ad redirects!
                                    }
                                    
                                    // Allow internal embed navigation, block external navigations away from embed domain
                                    return false
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    url?.let {
                                        onUrlChanged?.invoke(it)
                                        android.util.Log.i("RONYCINE_PLAYER", "PLAYER_URL: $it")
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    url?.let {
                                        onUrlChanged?.invoke(it)
                                        android.util.Log.i("RONYCINE_PLAYER", "PLAYER_NAVIGATION: Loaded page=$it")
                                    }
                                    // Inject guard to ensure video does not pause during fullscreen resize or orientation change
                                    try {
                                        view?.evaluateJavascript(
                                            """
                                            (function() {
                                                if (window.__ronycine_guard) return;
                                                window.__ronycine_guard = true;

                                                function safeReqOrient(mode) {
                                                    try {
                                                        if (window.RonycineBridge && window.RonycineBridge.requestOrientation) {
                                                            window.RonycineBridge.requestOrientation(String(mode || ''));
                                                        }
                                                    } catch(e) {}
                                                }

                                                if (window.screen && window.screen.orientation) {
                                                    try {
                                                        var origLock = window.screen.orientation.lock;
                                                        window.screen.orientation.lock = function(orient) {
                                                            safeReqOrient(orient);
                                                            return Promise.resolve();
                                                        };
                                                        if (window.ScreenOrientation && window.ScreenOrientation.prototype) {
                                                            window.ScreenOrientation.prototype.lock = function(orient) {
                                                                safeReqOrient(orient);
                                                                return Promise.resolve();
                                                            };
                                                            window.ScreenOrientation.prototype.unlock = function() {
                                                                return;
                                                            };
                                                        }
                                                        window.screen.orientation.unlock = function() {
                                                            return;
                                                        };
                                                    } catch(e) {}
                                                }

                                                if (window.screen) {
                                                    try {
                                                        window.screen.lockOrientation = function(orient) {
                                                            safeReqOrient(orient);
                                                            return true;
                                                        };
                                                        window.screen.unlockOrientation = function() { return true; };
                                                        if (window.screen.webkitLockOrientation) {
                                                            window.screen.webkitLockOrientation = window.screen.lockOrientation;
                                                            window.screen.webkitUnlockOrientation = window.screen.unlockOrientation;
                                                        }
                                                    } catch(e) {}
                                                }
                                                
                                                try {
                                                    var style = document.createElement('style');
                                                    style.id = 'ronycine-fullscreen-style';
                                                    style.innerHTML = `
                                                        html, body {
                                                            width: 100% !important;
                                                            height: 100% !important;
                                                            margin: 0 !important;
                                                            padding: 0 !important;
                                                            background: #000000 !important;
                                                            transition: none !important;
                                                            animation: none !important;
                                                            transform: none !important;
                                                        }
                                                        iframe, video {
                                                            width: 100% !important;
                                                            height: 100% !important;
                                                            border: none !important;
                                                            margin: 0 !important;
                                                            padding: 0 !important;
                                                            display: block !important;
                                                            box-sizing: border-box !important;
                                                            transition: none !important;
                                                            animation: none !important;
                                                            transform: none !important;
                                                        }
                                                        .ronycine-player-fullscreen {
                                                            width: 100vw !important;
                                                            height: 100dvh !important;
                                                            min-width: 100vw !important;
                                                            min-height: 100dvh !important;
                                                            max-width: 100vw !important;
                                                            max-height: 100dvh !important;
                                                            background: #000000 !important;
                                                            position: relative !important;
                                                            overflow: hidden !important;
                                                            transition: none !important;
                                                            animation: none !important;
                                                            transform: none !important;
                                                        }
                                                        .ronycine-player-fullscreen video,
                                                        .ronycine-player-fullscreen iframe {
                                                            width: 100% !important;
                                                            height: 100% !important;
                                                            border: none !important;
                                                            margin: 0 !important;
                                                            padding: 0 !important;
                                                            display: block !important;
                                                            transition: none !important;
                                                            animation: none !important;
                                                            transform: none !important;
                                                        }
                                                    `;
                                                    document.head.appendChild(style);
                                                } catch(e) {}

                                                var lastState = false;
                                                var isResizing = false;
                                                var resizeTimer = null;
                                                
                                                function getVideos() {
                                                    var vids = Array.from(document.querySelectorAll('video'));
                                                    try {
                                                        document.querySelectorAll('iframe').forEach(function(f) {
                                                            try {
                                                                var doc = f.contentDocument || f.contentWindow.document;
                                                                if (doc) vids.push(...Array.from(doc.querySelectorAll('video')));
                                                            } catch(e) {}
                                                        });
                                                    } catch(e) {}
                                                    return vids;
                                                }
                                                
                                                function setupVideo(v) {
                                                    if (v.__rc_hooked) return;
                                                    v.__rc_hooked = true;
                                                    v.addEventListener('play', function() { lastState = true; });
                                                    v.addEventListener('playing', function() { lastState = true; });
                                                    v.addEventListener('pause', function() {
                                                        if (isResizing && lastState) {
                                                            setTimeout(function() {
                                                                if (v.paused && lastState) {
                                                                    v.play().catch(function() {});
                                                                }
                                                            }, 60);
                                                        } else if (!isResizing) {
                                                            lastState = false;
                                                        }
                                                    });
                                                }
                                                
                                                window.__ronycine_notify_transition = function(isFS) {
                                                    isResizing = true;
                                                    getVideos().forEach(function(v) {
                                                        if (!v.paused) lastState = true;
                                                    });
                                                    if (resizeTimer) clearTimeout(resizeTimer);
                                                    resizeTimer = setTimeout(function() {
                                                        isResizing = false;
                                                        if (lastState) {
                                                            getVideos().forEach(function(v) {
                                                                if (v.paused) v.play().catch(function() {});
                                                            });
                                                        }
                                                    }, 300);
                                                };
                                                
                                                setInterval(function() {
                                                    getVideos().forEach(setupVideo);
                                                }, 1000);
                                                
                                                function handleFsChange() {
                                                    var isFs = !!(document.fullscreenElement || document.webkitFullscreenElement || document.mozFullScreenElement || document.msFullscreenElement);
                                                    if (window.RonycineBridge && window.RonycineBridge.onFullscreenStateChanged) {
                                                        window.RonycineBridge.onFullscreenStateChanged(isFs);
                                                    }
                                                }
                                                document.addEventListener('fullscreenchange', handleFsChange);
                                                document.addEventListener('webkitfullscreenchange', handleFsChange);

                                                window.addEventListener('resize', function() {
                                                    window.__ronycine_notify_transition();
                                                });
                                                window.addEventListener('orientationchange', function() {
                                                    window.__ronycine_notify_transition();
                                                });
                                                
                                                // Autoplay trigger if enabled
                                                if ($autoplayEnabled) {
                                                    setTimeout(function() {
                                                        getVideos().forEach(function(v) {
                                                            if (v.paused) {
                                                                var p = v.play();
                                                                if (p && p.catch) p.catch(function(err) {});
                                                            }
                                                        });
                                                        var playBtn = document.querySelector('.vds-play-button, .play-button, .vjs-play-control, button[aria-label*="Play"], button[aria-label*="play"]');
                                                        if (playBtn) { try { playBtn.click(); } catch(e) {} }
                                                    }, 800);
                                                }

                                                // VidSrc Player Events Listener (window.postMessage)
                                                window.addEventListener('message', function(event) {
                                                    try {
                                                        var msg = event.data;
                                                        if (typeof msg === 'string') {
                                                            try { msg = JSON.parse(msg); } catch(e) {}
                                                        }
                                                        if (msg) {
                                                            var type = msg.type || msg.event || msg.action || '';
                                                            var time = Number(msg.currentTime || msg.time || msg.progress || msg.position || 0);
                                                            var dur = Number(msg.duration || msg.total || 0);
                                                            if (typeof type === 'string') {
                                                                var evLower = type.toLowerCase();
                                                                if (evLower === 'playing' || evLower === 'paused' || evLower === 'completed' || evLower === 'seeked' || evLower === 'player_event') {
                                                                    var subEvent = (evLower === 'player_event' && msg.event) ? String(msg.event).toLowerCase() : evLower;
                                                                    if (window.RonycineBridge && window.RonycineBridge.onPlayerEvent) {
                                                                        window.RonycineBridge.onPlayerEvent(subEvent, time, dur);
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    } catch(e) {}
                                                });
                                            })();
                                            """.trimIndent(),
                                            null
                                        )
                                    } catch (_: Exception) {}
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        android.util.Log.e("RONYCINE_PLAYER", "PLAYER_ERROR: Failed to load url=${request.url} description=${error?.description}")
                                        isLoading = false
                                        hasError = true
                                        errorMessage = "Não foi possível carregar o player."
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
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
                                    detail: RenderProcessGoneDetail?
                                ): Boolean {
                                    android.util.Log.w(
                                        "RONYCINE_PLAYER",
                                        "PLAYER_CRASH: WebView render process crashed (didCrash=${detail?.didCrash()}), recovering safely..."
                                    )
                                    WebViewUtils.safeDestroy(view)

                                    if (activeWebView == view) {
                                        activeWebView = null
                                    }
                                    
                                    // Automatic recovery: reload if crash count is low
                                    if (retryCount < 2) {
                                        retryCount++
                                    } else {
                                        isLoading = false
                                        hasError = true
                                        errorMessage = "O reprodutor encerrou inesperadamente. Clique em Recarregar ou tente outro servidor."
                                    }
                                    return true
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun getDefaultVideoPoster(): Bitmap? {
                                    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                                }

                                override fun onPermissionRequest(request: PermissionRequest?) {
                                    try {
                                        request?.grant(request.resources)
                                    } catch (_: Exception) {}
                                }

                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?
                                ): Boolean {
                                    // Block popup windows from embeds
                                    return false
                                }

                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    loadProgress = newProgress
                                    if (newProgress >= 40) {
                                        isLoading = false
                                    }
                                }

                                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                    super.onShowCustomView(view, callback)
                                    showCustomView(view, callback)
                                }

                                override fun onHideCustomView() {
                                    super.onHideCustomView()
                                    hideCustomView()
                                }
                            }

                            activeWebView = this
                            onWebViewCreated?.invoke(this)
                            loadUrl(embedUrl)
                        }
                    },
                    update = { view ->
                        activeWebView = view
                        onWebViewCreated?.invoke(view)
                    },
                    onRelease = { webView ->
                        onWebViewCreated?.invoke(null)
                        WebViewUtils.safeDestroy(webView)
                        if (activeWebView == webView) {
                            activeWebView = null
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // --- PROFESSIONAL COMPACT LOADING STATE ---
        AnimatedVisibility(
            visible = isLoading && !hasError,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = BrandRed,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(42.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "CARREGANDO PLAYER...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Text(
                            text = "${audioSource.label.uppercase()} • ${audioSource.providerDomain}",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // --- ERROR STATE WITH RETRY & SOURCE SWITCH ---
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(46.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Não foi possível carregar o player.",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = errorMessage ?: "Ocorreu um erro ao conectar à fonte selecionada.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                hasError = false
                                isLoading = true
                                retryCount++
                                onTryAgain?.invoke()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("embed_retry_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("TENTAR NOVAMENTE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Switch audio source button if available
                        if (onAudioSourceChange != null) {
                            val alternateSource = if (audioSource == EmbedAudioSource.DUBLADO) {
                                EmbedAudioSource.LEGENDADO
                            } else {
                                EmbedAudioSource.DUBLADO
                            }

                            OutlinedButton(
                                onClick = {
                                    onAudioSourceChange(alternateSource)
                                    hasError = false
                                    isLoading = true
                                    retryCount++
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                            ) {
                                Text("Mudar para ${alternateSource.label}", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
