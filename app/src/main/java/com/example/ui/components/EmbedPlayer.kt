package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.*
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
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkSurface
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream

/**
 * Audio Source specification:
 * - DUBLADO (Default): mgeb.top
 * - LEGENDADO: nhdapi.com
 */
enum class EmbedAudioSource(val label: String, val providerDomain: String) {
    DUBLADO("Dublado", "mgeb.top"),
    LEGENDADO("Legendado", "nhdapi.com")
}

/**
 * Generates official RONYCINE Embed URLs following strict specifications:
 *
 * Movies:
 *   Dublado:   https://mgeb.top/embed/{tmdb_id}?player=vidstack#color:E50914
 *   Legendado: https://nhdapi.com/embed/movie/{tmdbId}
 *
 * Series:
 *   Dublado:   https://mgeb.top/embed/{tmdb_id}/{season_number}/{episode_number}?player=vidstack#color:E50914
 *   Legendado: https://nhdapi.com/embed/tv/{tmdbId}/{season}/{episode}
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
        audioSource: EmbedAudioSource = EmbedAudioSource.DUBLADO
    ): String {
        val isMovie = mediaType.equals("movie", ignoreCase = true) || mediaType.equals("filme", ignoreCase = true)

        // Rule: Only use IMDb when TMDB ID is not available (> 0) and IMDb ID exists
        val idSegment = if (tmdbId > 0) {
            tmdbId.toString()
        } else if (!imdbId.isNullOrBlank()) {
            imdbId.trim()
        } else {
            return ""
        }

        return if (isMovie) {
            when (audioSource) {
                EmbedAudioSource.DUBLADO -> {
                    "https://mgeb.top/embed/$idSegment?player=$DEFAULT_PLAYER#color:$RONYCINE_COLOR"
                }
                EmbedAudioSource.LEGENDADO -> {
                    "https://nhdapi.com/embed/movie/$idSegment"
                }
            }
        } else {
            val s = if (season != null && season > 0) season else 1
            val e = if (episode != null && episode > 0) episode else 1
            when (audioSource) {
                EmbedAudioSource.DUBLADO -> {
                    "https://mgeb.top/embed/$idSegment/$s/$e?player=$DEFAULT_PLAYER#color:$RONYCINE_COLOR"
                }
                EmbedAudioSource.LEGENDADO -> {
                    "https://nhdapi.com/embed/tv/$idSegment/$s/$e"
                }
            }
        }
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
    onToggleFullscreen: (() -> Unit)? = null,
    onAudioSourceChange: ((EmbedAudioSource) -> Unit)? = null,
    onTryAgain: (() -> Unit)? = null,
    onWebViewCreated: ((WebView?) -> Unit)? = null,
    onUrlChanged: ((String) -> Unit)? = null
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
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

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

    // Timeout guard: 20 seconds maximum to prevent endless spinning
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

        delay(20000L)
        if (isLoading && !hasError) {
            isLoading = false
            hasError = true
            errorMessage = "O servidor de vídeo demorou para responder. Verifique sua conexão ou tente a outra fonte de áudio."
        }
    }

    // Back handler for fullscreen HTML5 custom video views
    BackHandler(enabled = customView != null || isFullscreen) {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
        } else if (isFullscreen) {
            onToggleFullscreen?.invoke()
        }
    }

    // Keep playback seamlessly running during fullscreen enter/exit
    LaunchedEffect(isFullscreen) {
        try {
            activeWebView?.evaluateJavascript(
                "if (window.__ronycine_notify_transition) { window.__ronycine_notify_transition(); }",
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
            key(embedUrl, retryCount) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            // Third party cookies required for embeds
                            try {
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            } catch (_: Exception) {}

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
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
                                                
                                                window.__ronycine_notify_transition = function() {
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
                                                
                                                window.addEventListener('resize', function() {
                                                    window.__ronycine_notify_transition();
                                                });
                                                window.addEventListener('orientationchange', function() {
                                                    window.__ronycine_notify_transition();
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
                                    android.util.Log.w("RONYCINE_PLAYER", "PLAYER_CRASH: WebView render process crashed, safely recovering...")
                                    try {
                                        view?.stopLoading()
                                        (view?.parent as? ViewGroup)?.removeView(view)
                                        view?.destroy()
                                    } catch (_: Exception) {}

                                    if (activeWebView == view) {
                                        activeWebView = null
                                    }
                                    isLoading = false
                                    hasError = true
                                    errorMessage = "O reprodutor encerrou inesperadamente. Clique em Recarregar ou tente outro servidor."
                                    return true
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
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
                                    if (newProgress >= 85) {
                                        isLoading = false
                                    }
                                }

                                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                    super.onShowCustomView(view, callback)
                                    customView = view
                                    customViewCallback = callback
                                    if (!isFullscreen) {
                                        onToggleFullscreen?.invoke()
                                    }
                                }

                                override fun onHideCustomView() {
                                    super.onHideCustomView()
                                    customView = null
                                    customViewCallback?.onCustomViewHidden()
                                    customViewCallback = null
                                    if (isFullscreen) {
                                        onToggleFullscreen?.invoke()
                                    }
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
                        try {
                            webView.stopLoading()
                            webView.webChromeClient = null
                            webView.webViewClient = object : WebViewClient() {
                                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean = true
                            }
                            webView.loadUrl("about:blank")
                            webView.onPause()
                            webView.pauseTimers()
                            webView.removeAllViews()
                            webView.destroy()
                        } catch (_: Exception) {}
                        if (activeWebView == webView) {
                            activeWebView = null
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // --- FULLSCREEN HTML5 CUSTOM VIEW OVERLAY ---
        if (customView != null) {
            AndroidView(
                factory = { ctx ->
                    val cv = customView
                    (cv?.parent as? ViewGroup)?.removeView(cv)
                    cv ?: View(ctx)
                },
                modifier = Modifier.fillMaxSize()
            )
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
