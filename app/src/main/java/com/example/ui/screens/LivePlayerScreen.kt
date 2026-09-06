package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.remote.ApiChannel
import com.example.data.remote.ApiEvent
import com.example.data.repository.LiveTvRepository
import com.example.ui.components.EmbedPlayer
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import kotlinx.coroutines.delay

@Composable
fun LivePlayerScreen(
    channelId: String,
    onNavigateBack: () -> Unit,
    onNavigateToChannel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val repository = remember { LiveTvRepository.getInstance() }

    val allChannels by repository.cachedChannels.collectAsState()
    val allEvents by repository.cachedEvents.collectAsState()

    // Check if channelId is a Channel or an Event
    val matchedChannel = remember(channelId, allChannels) {
        allChannels.find { it.id.equals(channelId, ignoreCase = true) || it.slug.equals(channelId, ignoreCase = true) }
    }
    val matchedEvent = remember(channelId, allEvents) {
        allEvents.find { it.id.equals(channelId, ignoreCase = true) || it.slug.equals(channelId, ignoreCase = true) }
    }

    val isEvent = matchedEvent != null && matchedChannel == null
    val currentTitle = if (isEvent) (matchedEvent?.title ?: "Evento ao Vivo") else (matchedChannel?.name ?: "Canal ao Vivo")
    val currentCategory = if (isEvent) (matchedEvent?.category ?: "Esportes") else (matchedChannel?.category ?: "TV")

    var selectedEmbedIndex by remember { mutableIntStateOf(0) }

    val activeEmbedUrl = remember(channelId, selectedEmbedIndex, matchedChannel, matchedEvent) {
        if (isEvent && matchedEvent != null) {
            val embeds = matchedEvent.embeds
            if (!embeds.isNullOrEmpty() && selectedEmbedIndex < embeds.size) {
                embeds[selectedEmbedIndex].embedUrl?.takeIf { it.isNotBlank() } ?: matchedEvent.getEffectiveEmbedUrl()
            } else {
                matchedEvent.getEffectiveEmbedUrl()
            }
        } else if (matchedChannel != null) {
            matchedChannel.getEffectiveEmbedUrl()
        } else {
            "https://reidosembeds.online/embed/$channelId"
        }
    }

    var isFullscreen by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }

    // WebView reference and URL tracking to safely handle ad redirects on Live TV channels
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var currentWebViewUrl by remember { mutableStateOf("") }

    val handleBackNavigation = {
        val webView = activeWebView
        if (webView != null) {
            val canGoBack = webView.canGoBack()
            // Check if URL has drifted from the expected original livestream video embed domain
            val isRedirected = currentWebViewUrl.isNotBlank() && 
                    currentWebViewUrl != "about:blank" && 
                    !currentWebViewUrl.startsWith(activeEmbedUrl.substringBefore("?")) &&
                    !currentWebViewUrl.contains("superflix") && 
                    !currentWebViewUrl.contains("embed") &&
                    !currentWebViewUrl.contains("reidosembeds")

            if (canGoBack) {
                android.util.Log.i("RONYCINE_PLAYER", "PLAYER_BACK: Navigating back within Live WebView history")
                webView.goBack()
            } else if (isRedirected) {
                android.util.Log.i("RONYCINE_PLAYER", "PLAYER_RESTORE: Ad redirect detected on Live Channel, restoring original url=$activeEmbedUrl")
                webView.loadUrl(activeEmbedUrl)
            } else if (isFullscreen) {
                android.util.Log.i("RONYCINE_PLAYER", "PLAYER_BACK: Exiting Live fullscreen mode")
                isFullscreen = false
            } else {
                android.util.Log.i("RONYCINE_PLAYER", "PLAYER_BACK: Exiting Live Player screen")
                onNavigateBack()
            }
        } else if (isFullscreen) {
            isFullscreen = false
        } else {
            onNavigateBack()
        }
    }

    // Handles the physical or gesture Back button on Android devices for live TV/livestreams
    BackHandler(enabled = true) {
        handleBackNavigation()
    }

    var showFullscreenControls by remember { mutableStateOf(true) }

    LaunchedEffect(showFullscreenControls, isFullscreen) {
        if (isFullscreen && showFullscreenControls) {
            delay(4000L)
            showFullscreenControls = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (activity != null) {
                try {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    val window = activity.window
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(isFullscreen) {
        if (activity != null) {
            try {
                activity.requestedOrientation = if (isFullscreen) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            } catch (_: Exception) {}
            try {
                val window = activity.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                if (isFullscreen) {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            } catch (_: Exception) {}
        }
    }

    val otherChannels = remember(channelId, allChannels) {
        allChannels.filter { !it.id.equals(channelId, ignoreCase = true) }.take(15)
    }

    val otherEvents = remember(channelId, allEvents) {
        allEvents.filter { !it.id.equals(channelId, ignoreCase = true) }.take(8)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("live_player_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Bar (Only visible in Portrait mode)
            if (!isFullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { handleBackNavigation() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Surface(
                            color = BrandRed,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "AO VIVO",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Text(
                            text = currentTitle,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = {
                            hasError = false
                            retryKey++
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recarregar",
                            tint = Color.LightGray
                        )
                    }
                }
            }

            // 2. UNIFIED LIVE EMBED PLAYER CONTAINER - NEVER UNMOUNTED ACROSS FULLSCREEN TRANSITIONS
            Box(
                modifier = if (isFullscreen) {
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp),
                    border = if (isFullscreen) null else BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (hasError) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Transmissão temporariamente indisponível.",
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    hasError = false
                                    retryKey++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("↻ Tentar novamente", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    } else {
                        key(retryKey, activeEmbedUrl) {
                            EmbedPlayer(
                                mediaType = "tv",
                                tmdbId = 99999,
                                customUrl = activeEmbedUrl,
                                title = currentTitle,
                                isFullscreen = isFullscreen,
                                onToggleFullscreen = {
                                    isFullscreen = !isFullscreen
                                    showFullscreenControls = true
                                    android.util.Log.i("RONYCINE_PLAYER", "PLAYER_FULLSCREEN: changed isFullscreen=$isFullscreen")
                                },
                                onWebViewCreated = { webView ->
                                    activeWebView = webView
                                },
                                onUrlChanged = { url ->
                                    currentWebViewUrl = url
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // 3. Scrollable Content below the Live Player in Portrait Mode
            if (!isFullscreen) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(DarkBackground),
                    contentPadding = PaddingValues(bottom = 30.dp)
                ) {

                // Multiple Embed Streams Selector (If Available)
                if (isEvent && matchedEvent?.embeds?.isNotEmpty() == true && (matchedEvent.embeds?.size ?: 0) > 1) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "OPÇÕES DE TRANSMISSÃO:",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                matchedEvent.embeds?.forEachIndexed { index, embed ->
                                    val isSelected = selectedEmbedIndex == index
                                    Button(
                                        onClick = {
                                            selectedEmbedIndex = index
                                            retryKey++
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) BrandRed else DarkSurface
                                        ),
                                        border = BorderStroke(1.dp, if (isSelected) BrandRed else CardBorder),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = embed.provider ?: embed.name ?: "Opção ${index + 1}",
                                            color = if (isSelected) Color.White else Color.LightGray,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Controls Bar: Fullscreen / Refresh
                item {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(BrandRed, CircleShape)
                                )
                                Text(
                                    text = "Sinal Estável HD",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }

                            Button(
                                onClick = { isFullscreen = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Tela Cheia",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tela Cheia", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Channel/Event Info Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = currentTitle,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = DarkSurface,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Text(
                                    text = currentCategory,
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Match or Channel Program Details
                        Surface(
                            color = DarkSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (isEvent && matchedEvent != null) {
                                    if (matchedEvent.visualModel == "match" && (matchedEvent.time1 != null || matchedEvent.time2 != null)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                if (!matchedEvent.time1Logo.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = matchedEvent.time1Logo,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                }
                                                Text(
                                                    text = matchedEvent.getTeam1DisplayName(),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Text("VS", color = BrandRed, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                if (!matchedEvent.time2Logo.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = matchedEvent.time2Logo,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                }
                                                Text(
                                                    text = matchedEvent.getTeam2DisplayName(),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    Text(
                                        text = "${matchedEvent.competition} • ${matchedEvent.startTime}",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                    if (!matchedEvent.description.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = matchedEvent.description,
                                            color = Color.LightGray,
                                            fontSize = 11.sp
                                        )
                                    }
                                } else if (matchedChannel != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(BrandRed, CircleShape)
                                        )
                                        Text(
                                            text = "AGORA: ${matchedChannel.currentProgram}",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "PRÓXIMO: ${matchedChannel.nextProgram}",
                                        color = Color.LightGray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // More Live Channels
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Text(
                            text = "MAIS CANAIS AO VIVO",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(otherChannels) { other ->
                                Column(
                                    modifier = Modifier
                                        .width(105.dp)
                                        .clickable { onNavigateToChannel(other.id) }
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                        border = BorderStroke(1.dp, CardBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(3f / 4f)
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(other.getEffectiveLogo())
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = other.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = other.name,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = other.category ?: "Geral",
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Related Events Row
                if (otherEvents.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            Text(
                                text = "OUTROS EVENTOS ESPORTIVOS",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(otherEvents) { ev ->
                                    Card(
                                        modifier = Modifier
                                            .width(200.dp)
                                            .clickable { onNavigateToChannel(ev.id) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                        border = BorderStroke(1.dp, CardBorder)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    color = if (ev.isLive()) BrandRed else DarkBackground,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (ev.isLive()) "AO VIVO" else "EM BREVE",
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = ev.category ?: "Esporte",
                                                    color = Color.LightGray,
                                                    fontSize = 10.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = ev.title,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = ev.competition ?: (ev.startTime ?: "Ao Vivo"),
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
