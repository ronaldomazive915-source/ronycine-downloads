package com.example.ui.components

import android.content.Intent
import android.net.Uri
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
import com.example.ui.components.RonycineSmileLoader
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkSurface
import com.example.util.WebViewUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    initialPositionSeconds: Double = 0.0,
    onToggleFullscreen: (() -> Unit)? = null,
    onAudioSourceChange: ((EmbedAudioSource) -> Unit)? = null,
    onPlaybackProgress: ((currentTime: Double, duration: Double, event: String) -> Unit)? = null,
    onTryAgain: (() -> Unit)? = null,
    onTryAnotherPlayer: (() -> Unit)? = null,
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

    // State for specific authorization error from RedeFlixApi
    var isAuthorizationError by remember(embedUrl, retryCount) { mutableStateOf(false) }

    // Unified logs for debugging (only in DEBUG mode)
    LaunchedEffect(embedUrl, audioSource) {
        val isRedeFlix = embedUrl.contains("redeflix", ignoreCase = true)
        if (isRedeFlix) {
            android.util.Log.d("REDEFLIX", "Player selecionado: RedeFlixApi")
            android.util.Log.d("REDEFLIX", "URL: $embedUrl")
            android.util.Log.d("REDEFLIX", "TMDB ID: $tmdbId")
            android.util.Log.d("REDEFLIX", "Tipo: $mediaType")
            android.util.Log.d("REDEFLIX", "Temporada: ${season ?: "N/A"}")
            android.util.Log.d("REDEFLIX", "Episódio: ${episode ?: "N/A"}")
        }
    }
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
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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

    val handlePlayerFailure: (String) -> Unit = remember(onTryAnotherPlayer, embedUrl, mediaType, tmdbId) {
        { reason ->
            android.util.Log.w(
                "RONYCINE_DIAG",
                "PLAYER_FAILURE_TRIGGERED: contentType=$mediaType, tmdbId=$tmdbId, playerUrl=$embedUrl, finalUrl=${activeWebView?.url ?: "N/A"}, reason=$reason, autoFallback=${onTryAnotherPlayer != null}"
            )
            (context as? Activity)?.runOnUiThread {
                if (onTryAnotherPlayer != null) {
                    onTryAnotherPlayer.invoke()
                } else {
                    isLoading = false
                    hasError = true
                    errorMessage = reason
                }
            }
        }
    }

    // Timeout guard and loading dismiss guard: ensures overlay never gets stuck over video
    LaunchedEffect(embedUrl, retryCount, playerLoadId, isValidTarget) {
        if (!isValidTarget) {
            isLoading = false
            hasError = true
            errorMessage = "Não foi possível carregar o player. Identificador de mídia ou URL inválida."
            return@LaunchedEffect
        }

        isLoading = true
        hasError = false
        isAuthorizationError = false
        errorMessage = null
        loadProgress = 0

        android.util.Log.i("RONYCINE_DIAG", "[PLAYER_CLICK] Toque para iniciar player. tmdbId=$tmdbId, mediaType=$mediaType")
        android.util.Log.i("RONYCINE_DIAG", "[PLAYER_URL_CREATED] URL gerada: $embedUrl")
        android.util.Log.i("RONYCINE_DIAG", "[PLAYER_REQUEST_STARTED] WebView iniciando carregamento. timestamp=${System.currentTimeMillis()}")

        // Improved auto-dismiss logic: check periodically for progress or player events
        launch {
            for (i in 1..80) { // Up to 8 seconds total, 100ms intervals
                delay(100L)
                // If progress is significant (>20%) OR we received a JS event saying it's ready/playing
                if (!isLoading || hasError) break
                
                if (loadProgress >= 25) {
                    isLoading = false
                    android.util.Log.i("RONYCINE_DIAG", "[PLAYER_UI_READY] Progresso atingiu $loadProgress% em ${i*100}ms. Removendo overlay.")
                    break
                }
            }
            // Final safety check after total timeout
            if (isLoading && !hasError && loadProgress >= 15) {
                isLoading = false
            }
        }

        // Hard timeout after 10 seconds if the WebView hasn't loaded enough content
        delay(6000L) // Total 10 seconds
        if (isLoading && !hasError) {
            android.util.Log.w("RONYCINE_DIAG", "PLAYER_TIMEOUT: Timeout após 10s sem resposta para contentType=$mediaType, tmdbId=$tmdbId, url=$embedUrl")
            handlePlayerFailure("O provedor demorou muito para responder. Tentando alternativa...")
        }
    }

    // Back handler for fullscreen HTML5 custom video views or player fullscreen mode
    BackHandler(enabled = isCustomViewShowing || isFullscreen || isAuthorizationError) {
        if (isAuthorizationError) {
            isAuthorizationError = false
            onTryAgain?.invoke()
        } else if (isCustomViewShowing) {
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
                            WebViewUtils.applySafeLayerType(this, forceSoftware = false)
                            setBackgroundColor(android.graphics.Color.BLACK)

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
                                allowFileAccess = true
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
                                
                                // Clean User-Agent: Modern mobile browser to avoid bot detection
                                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
                            }

                            addJavascriptInterface(object {
                                @android.webkit.JavascriptInterface
                                fun onPlayerEvent(event: String, currentTime: Double, duration: Double) {
                                    android.util.Log.d("RONYCINE_DIAG", "PLAYER_EVENT: event=$event, time=$currentTime, dur=$duration")
                                    if (event == "ended" || event == "completed") {
                                        (context as? Activity)?.runOnUiThread {
                                            if (isCustomViewShowing) {
                                                hideCustomView()
                                            }
                                        }
                                    } else if (event == "media_ready" || event == "playing" || event == "play") {
                                        (context as? Activity)?.runOnUiThread {
                                            isLoading = false
                                        }
                                    }
                                    onPlaybackProgress?.invoke(currentTime, duration, event)
                                }

                                @android.webkit.JavascriptInterface
                                fun logDiagnostic(hasVideo: Boolean, hasIframe: Boolean, readyState: Int, statusSnippet: String) {
                                    android.util.Log.i(
                                        "RONYCINE_DIAG",
                                        "DIAGNOSTIC: contentType=$mediaType, tmdbId=$tmdbId, playerUrl=$embedUrl, finalUrl=${activeWebView?.url ?: "N/A"}, videoReadyState=$readyState, hasVideo=$hasVideo, hasIframe=$hasIframe, statusSnippet=$statusSnippet"
                                    )
                                }

                                @android.webkit.JavascriptInterface
                                fun onContentFailed(reason: String) {
                                    android.util.Log.e(
                                        "RONYCINE_DIAG",
                                        "PLAYER_ERROR: contentType=$mediaType, tmdbId=$tmdbId, playerUrl=$embedUrl, finalUrl=${activeWebView?.url ?: "N/A"}, reason=$reason"
                                    )
                                    handlePlayerFailure(reason)
                                }

                                @android.webkit.JavascriptInterface
                                fun onAuthorizationError(type: String) {
                                    android.util.Log.e("REDEFLIX", "Authorization error detected: $type")
                                    (context as? Activity)?.runOnUiThread {
                                        isAuthorizationError = true
                                        isLoading = false
                                    }
                                }

                                @android.webkit.JavascriptInterface
                                fun onFullscreenStateChanged(isJsFullscreen: Boolean) {
                                    android.util.Log.i("RONYCINE_FULLSCREEN", "PLAYER_STATE: JS fullscreen change event. isJsFullscreen=$isJsFullscreen, isFullscreen=$isFullscreen")
                                    (context as? Activity)?.runOnUiThread {
                                        if (isJsFullscreen) {
                                            if (!isFullscreen) {
                                                onToggleFullscreen?.invoke()
                                            }
                                        } else {
                                            if (isCustomViewShowing) {
                                                hideCustomView()
                                            }
                                            if (isFullscreen) {
                                                onToggleFullscreen?.invoke()
                                            }
                                        }
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
                                                mode == "toggle" -> {
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
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val urlStr = request?.url?.toString() ?: return null
                                    val host = request.url?.host?.lowercase() ?: ""
                                    
                                    // Intercept and block ads, popups, and tracker requests to prevent Chromium renderer process crashes (aw_browser_terminator.cc)
                                    if (!request.isForMainFrame) {
                                        val isAd = host.contains("pop") || 
                                                host.contains("click") || 
                                                host.contains("ads") || 
                                                host.contains("adsystem") || 
                                                host.contains("adservice") || 
                                                host.contains("tracker") || 
                                                host.contains("analytic") || 
                                                host.contains("exoclick") || 
                                                host.contains("doubleclick") || 
                                                host.contains("adsterra") || 
                                                host.contains("juicyads") || 
                                                host.contains("propeller") || 
                                                host.contains("onclick") || 
                                                host.contains("histats") || 
                                                host.contains("miner") || 
                                                host.contains("coin") || 
                                                host.contains("bet") || 
                                                host.contains("casino") || 
                                                urlStr.contains("/ads/") || 
                                                urlStr.contains("ad_type") || 
                                                urlStr.contains("ad_key") || 
                                                urlStr.contains("ad_slot")
                                        
                                        val mainHost = try { Uri.parse(embedUrl).host?.lowercase() ?: "" } catch(_: Exception) { "" }
                                        val isMainPlayerResource = host == mainHost || (mainHost.isNotEmpty() && host.endsWith("." + mainHost))
                                        
                                        if (isAd && !isMainPlayerResource) {
                                            android.util.Log.v("RONYCINE_ADBLOCK", "AD_BLOCKED: $urlStr")
                                            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val targetUri = request?.url ?: return false
                                    val scheme = targetUri.scheme?.lowercase() ?: ""
                                    
                                    android.util.Log.i("RONYCINE_DIAG", "[LIVE_REDIRECT_STATUS] Redirect detected: $targetUri. timestamp=${System.currentTimeMillis()}")

                                    // Prevent non-http/https intents (e.g. market://, intent://, tg://) from crashing the WebView
                                    if (scheme != "http" && scheme != "https") {
                                        android.util.Log.i("RONYCINE_PLAYER", "PLAYER_EXTERNAL_INTENT: Ignored non-web scheme: $scheme")
                                        return true
                                    }

                                    // Allow normal http/https player navigation, iframe redirects, media manifests and CDNs
                                    return false
                                }

                                 override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    url?.let {
                                        onUrlChanged?.invoke(it)
                                        android.util.Log.i("RONYCINE_DIAG", "[LIVE_EMBED_STATUS] Carregamento iniciado: $it. timestamp=${System.currentTimeMillis()}")
                                        android.util.Log.i("RONYCINE_DIAG", "[PLAYER_RESPONSE] Resposta recebida da URL: $it. timestamp=${System.currentTimeMillis()}")
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    url?.let {
                                        onUrlChanged?.invoke(it)
                                        android.util.Log.i("RONYCINE_DIAG", "[PLAYER_IFRAME_LOADED] WebView onPageFinished para url: $it. timestamp=${System.currentTimeMillis()}")
                                    }
                                    
                                    // Deep inspection for provider errors and video readiness
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            function inspectPage() {
                                                var body = document.body;
                                                var text = (body ? (body.innerText || body.textContent || '') : '') || '';
                                                var title = document.title || '';
                                                var videos = document.querySelectorAll('video');
                                                var iframes = document.querySelectorAll('iframe');
                                                var hasVideo = videos.length > 0;
                                                var hasIframe = iframes.length > 0;
                                                var v = document.querySelector('video');
                                                var readyState = v ? v.readyState : -1;
                                                
                                                if (window.RonycineBridge && window.RonycineBridge.logDiagnostic) {
                                                    window.RonycineBridge.logDiagnostic(hasVideo, hasIframe, readyState, (title + ' | ' + text).substring(0, 80));
                                                }
                                                
                                                var lower = text.toLowerCase();
                                                if (lower.includes('database connection failed') ||
                                                    lower.includes('connection failed. please check logs') ||
                                                    lower.includes('site em manutenção')) {
                                                    if (window.RonycineBridge && window.RonycineBridge.onContentFailed) {
                                                        window.RonycineBridge.onContentFailed('Servidor do provedor offline (Falha de banco de dados)');
                                                    }
                                                    return;
                                                }
                                                
                                                if (lower.includes('esta autorização pertence a outro dispositivo')) {
                                                    if (window.RonycineBridge && window.RonycineBridge.onAuthorizationError) {
                                                        window.RonycineBridge.onAuthorizationError('redeflix_device_mismatch');
                                                    }
                                                    return;
                                                }
                                                
                                                if ((lower.includes('error 404') || lower.includes('not found') || lower.includes('404 not found')) && !hasIframe && !hasVideo) {
                                                    if (window.RonycineBridge && window.RonycineBridge.onContentFailed) {
                                                        window.RonycineBridge.onContentFailed('Conteúdo não encontrado neste provedor (404)');
                                                    }
                                                    return;
                                                }
                                                
                                                if (hasVideo || hasIframe) {
                                                    if (window.RonycineBridge && window.RonycineBridge.onPlayerEvent) {
                                                        window.RonycineBridge.onPlayerEvent('media_ready', 0, 0);
                                                    }
                                                }
                                            }
                                            inspectPage();
                                            setTimeout(inspectPage, 600);
                                            setTimeout(inspectPage, 1500);
                                            setTimeout(inspectPage, 3000);
                                        })();
                                        """.trimIndent(),
                                        null
                                    )
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
                                                        }
                                                        iframe, video {
                                                            width: 100% !important;
                                                            height: 100% !important;
                                                            border: none !important;
                                                            margin: 0 !important;
                                                            padding: 0 !important;
                                                            display: block !important;
                                                            box-sizing: border-box !important;
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
                                                        }
                                                        .ronycine-player-fullscreen video,
                                                        .ronycine-player-fullscreen iframe {
                                                            width: 100% !important;
                                                            height: 100% !important;
                                                            border: none !important;
                                                            margin: 0 !important;
                                                            padding: 0 !important;
                                                            display: block !important;
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
                                                
                                                var initialPos = $initialPositionSeconds;
                                                var hasAppliedResume = false;

                                                function applyResumeSeek(v) {
                                                    if (hasAppliedResume || initialPos <= 2.0) return;
                                                    if (v && v.duration && v.duration > 0) {
                                                        try {
                                                            if (Math.abs(v.currentTime - initialPos) > 2) {
                                                                v.currentTime = initialPos;
                                                                hasAppliedResume = true;
                                                                console.log('[CONTINUE WATCHING] Retomando video em ' + initialPos + ' segundos');
                                                            }
                                                        } catch(e) {}
                                                    }
                                                }

                                                var lastReportedTime = 0;
                                                function notifyBridge(event, time, dur) {
                                                    try {
                                                        if (window.RonycineBridge && window.RonycineBridge.onPlayerEvent) {
                                                            var now = Date.now();
                                                            if (event === 'timeupdate') {
                                                                if (now - lastReportedTime < 2500) return;
                                                                lastReportedTime = now;
                                                            }
                                                            window.RonycineBridge.onPlayerEvent(event, time || 0, dur || 0);
                                                        }
                                                    } catch(e) {}
                                                }

                                                function setupVideo(v) {
                                                    if (v.__rc_hooked) return;
                                                    v.__rc_hooked = true;
                                                    applyResumeSeek(v);
                                                    v.addEventListener('loadedmetadata', function() {
                                                        applyResumeSeek(v);
                                                        notifyBridge('loadedmetadata', v.currentTime, v.duration);
                                                    });
                                                    v.addEventListener('canplay', function() {
                                                        applyResumeSeek(v);
                                                    });
                                                    v.addEventListener('play', function() {
                                                        lastState = true;
                                                        notifyBridge('play', v.currentTime, v.duration);
                                                    });
                                                    v.addEventListener('playing', function() {
                                                        lastState = true;
                                                        notifyBridge('playing', v.currentTime, v.duration);
                                                    });
                                                    v.addEventListener('timeupdate', function() {
                                                        notifyBridge('timeupdate', v.currentTime, v.duration);
                                                    });
                                                    v.addEventListener('durationchange', function() {
                                                        notifyBridge('timeupdate', v.currentTime, v.duration);
                                                    });
                                                    v.addEventListener('pause', function() {
                                                        notifyBridge('pause', v.currentTime, v.duration);
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
                                                    v.addEventListener('ended', function() {
                                                        lastState = false;
                                                        notifyBridge('ended', v.currentTime, v.duration);
                                                    });
                                                }

                                                document.addEventListener('ended', function(e) {
                                                    if (e.target && (e.target.tagName === 'VIDEO' || e.target.nodeName === 'VIDEO')) {
                                                        notifyBridge('ended', e.target.currentTime, e.target.duration);
                                                    }
                                                }, true);

                                                document.addEventListener('timeupdate', function(e) {
                                                    if (e.target && (e.target.tagName === 'VIDEO' || e.target.nodeName === 'VIDEO')) {
                                                        notifyBridge('timeupdate', e.target.currentTime, e.target.duration);
                                                    }
                                                }, true);
                                                
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
                                                    }, 1500);
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
                                                                if (evLower === 'playing' || evLower === 'paused' || evLower === 'completed' || evLower === 'ended' || evLower === 'end' || evLower === 'finish' || evLower === 'seeked' || evLower === 'player_event') {
                                                                    var subEvent = (evLower === 'player_event' && msg.event) ? String(msg.event).toLowerCase() : evLower;
                                                                    if (subEvent === 'completed' || subEvent === 'ended' || subEvent === 'finish' || subEvent === 'end') {
                                                                        subEvent = 'ended';
                                                                    }
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
                                    val isMainFrame = request?.isForMainFrame == true
                                    val errCode = error?.errorCode ?: -1
                                    val errDesc = error?.description?.toString() ?: "Falha de conexão"
                                    
                                    if (isMainFrame) {
                                        android.util.Log.e("RONYCINE_DIAG", "[LIVE_STREAM_STATUS] Erro de rede no frame principal: $errCode ($errDesc). URL=${request?.url}")
                                    }

                                    super.onReceivedError(view, request, error)
                                    
                                    if (isMainFrame) {
                                        android.util.Log.e("RONYCINE_DIAG", "PLAYER_ERROR: onReceivedError, contentType=$mediaType, tmdbId=$tmdbId, playerUrl=$embedUrl, finalUrl=${request?.url}, code=$errCode, desc=$errDesc")
                                        handlePlayerFailure("Falha ao carregar reprodução ($errDesc)")
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?
                                ) {
                                    val statusCode = errorResponse?.statusCode ?: -1
                                    val isMainFrame = request?.isForMainFrame == true
                                    
                                    android.util.Log.e("RONYCINE_DIAG", "[LIVE_STREAM_STATUS] Erro HTTP detectado: $statusCode. URL=${request?.url}. isMainFrame=$isMainFrame")

                                    super.onReceivedHttpError(view, request, errorResponse)
                                    
                                    if (isMainFrame && (statusCode >= 400)) {
                                        android.util.Log.e("RONYCINE_DIAG", "HTTP_STATUS_ERROR: contentType=$mediaType, tmdbId=$tmdbId, playerUrl=$embedUrl, finalUrl=${request?.url}, status=$statusCode")
                                        handlePlayerFailure("Erro do servidor ($statusCode)")
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
                                        handlePlayerFailure("O reprodutor encerrou inesperadamente.")
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
                            
                            // Inject Referer header to avoid 403 Forbidden errors from providers
                            val referer = when {
                                embedUrl.contains("rdembed") || embedUrl.contains("reidosembeds") -> "https://reidosembeds.online"
                                embedUrl.contains("redeflix") -> "https://redeflixapi.store"
                                else -> "https://ronycine.app"
                            }
                            val extraHeaders = mapOf("Referer" to referer)
                            loadUrl(embedUrl, extraHeaders)
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
            visible = isLoading && !hasError && !isAuthorizationError,
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
                    RonycineSmileLoader(
                        color = BrandRed,
                        size = 42.dp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "PREPARANDO REPRODUÇÃO",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Conectando ao player...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val currentProviderHost = remember(embedUrl) {
                        try {
                            android.net.Uri.parse(embedUrl).host?.lowercase() ?: audioSource.providerDomain
                        } catch (_: Exception) {
                            audioSource.providerDomain
                        }
                    }

                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Text(
                            text = "${audioSource.label.uppercase()} • $currentProviderHost",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // --- REDEFLIX AUTHORIZATION ERROR OVERLAY ---
        if (isAuthorizationError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "REDEFLIXAPI",
                        color = BrandRed,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Esta autorização pertence a outro dispositivo.",
                        color = Color.White,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "O servidor da RedeFlixApi detectou um conflito de identidade. Isso pode ocorrer se o seu IP mudou ou se há dados de sessão antigos.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(32.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                android.util.Log.i("REDEFLIX", "User chose to try another player")
                                isAuthorizationError = false
                                onTryAgain?.invoke()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Text("OUTRO PLAYER", color = Color.White)
                        }
                        
                                Button(
                                    onClick = {
                                        android.util.Log.i("REDEFLIX", "Resetting all web storage and authorization data for RedeFlixApi")
                                        // Clear all cookies, localStorage, and web storage to completely reset identity
                                        try {
                                            // 1. Clear Cookies
                                            val cookieManager = CookieManager.getInstance()
                                            cookieManager.removeAllCookies {
                                                android.util.Log.d("REDEFLIX", "Cookies removed: $it")
                                            }
                                            
                                            // 2. Clear WebStorage (LocalStorage, IndexedDB, etc)
                                            WebStorage.getInstance().deleteAllData()
                                            
                                            // 3. Clear WebView Cache
                                            activeWebView?.clearCache(true)
                                            activeWebView?.clearFormData()
                                            activeWebView?.clearHistory()

                                            (context as? Activity)?.runOnUiThread {
                                                isAuthorizationError = false
                                                retryCount++ // Triggers reload with fresh state
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("REDEFLIX", "Error during reset: ${e.message}")
                                            isAuthorizationError = false
                                            retryCount++
                                        }
                                    },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                        ) {
                            Text("REDEFINIR", color = Color.White)
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://redeflixapi.store/"))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }) {
                        Text("Verificar no Navegador", color = Color.Gray, fontSize = 12.sp)
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

                        if (onTryAnotherPlayer != null) {
                            OutlinedButton(
                                onClick = {
                                    hasError = false
                                    isLoading = true
                                    onTryAnotherPlayer.invoke()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.testTag("embed_other_player_btn")
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("OUTRO PLAYER", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (onAudioSourceChange != null) {
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
