package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.remote.ApiChannel
import com.example.ui.components.EmbedPlayer
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.LiveTvViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(
    onNavigateToChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialChannelId: String? = null,
    liveTvViewModel: LiveTvViewModel = viewModel(),
    onFullscreenChanged: (Boolean) -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    // Calculate grid columns based on screen width:
    // Mobile (< 600dp): 2 columns
    // Tablet (600dp - 899dp): 3 columns
    // Desktop / Large screen (>= 900dp): 4 columns
    val columnsCount = when {
        screenWidth >= 900 -> 4
        screenWidth >= 600 -> 3
        else -> 2
    }

    val channels by liveTvViewModel.channels.collectAsState()
    val channelCategories by liveTvViewModel.channelCategories.collectAsState()
    val searchQuery by liveTvViewModel.searchQuery.collectAsState()
    val selectedCategory by liveTvViewModel.selectedChannelCategory.collectAsState()
    val favoriteIds by liveTvViewModel.favoriteChannelIds.collectAsState()
    val isRefreshing by liveTvViewModel.isRefreshing.collectAsState()
    val errorMessage by liveTvViewModel.errorMessage.collectAsState()

    // Central Active Playing Channel State (Preserved across fullscreen toggles)
    var selectedChannel by remember { mutableStateOf<ApiChannel?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Sync local fullscreen state with parent activity
    LaunchedEffect(isFullscreen) {
        onFullscreenChanged(isFullscreen)
    }

    // Auto-select initial channel or first available channel
    LaunchedEffect(channels, initialChannelId) {
        if (channels.isNotEmpty()) {
            if (selectedChannel == null) {
                if (!initialChannelId.isNullOrBlank()) {
                    val matched = channels.find {
                        it.id.equals(initialChannelId, ignoreCase = true) ||
                                (it.slug != null && it.slug.equals(initialChannelId, ignoreCase = true))
                    }
                    selectedChannel = matched ?: channels.firstOrNull()
                } else {
                    selectedChannel = channels.firstOrNull()
                }
            } else {
                // If selected channel is no longer valid after refresh, fallback to first
                val stillValid = channels.any { it.id == selectedChannel?.id }
                if (!stillValid) {
                    selectedChannel = channels.firstOrNull()
                }
            }
        }
    }

    // Categories list
    val categoriesList = remember(channelCategories) {
        val standard = listOf(
            "Todos",
            "Favoritos",
            "Jogos do Dia",
            "Canais gratuitos",
            "24 Horas",
            "Anime",
            "Canais abertos",
            "Documentários",
            "Esportes",
            "Filmes e séries",
            "Infantil",
            "Notícias"
        )
        (standard + channelCategories).distinct()
    }

    // Filtered channels list
    val filteredChannels = remember(channels, selectedCategory, searchQuery, favoriteIds) {
        var list = channels

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter { ch ->
                val originalIndex = channels.indexOf(ch) + 1
                val numberString = String.format("%03d", originalIndex)
                val nameMatch = ch.name.lowercase().contains(q)
                val catMatch = (ch.category ?: "").lowercase().contains(q)
                val progMatch = (ch.currentProgram ?: "").lowercase().contains(q)
                val numMatch = numberString.contains(q) || originalIndex.toString() == q

                nameMatch || catMatch || progMatch || numMatch
            }
        }

        if (!selectedCategory.equals("Todos", ignoreCase = true)) {
            if (selectedCategory.equals("Favoritos", ignoreCase = true)) {
                list = list.filter { favoriteIds.contains(it.id) }
            } else if (selectedCategory.equals("24 Horas", ignoreCase = true)) {
                list = list.filter {
                    (it.category ?: "").lowercase().contains("24") ||
                            it.name.lowercase().contains("24") ||
                            (it.currentProgram ?: "").lowercase().contains("24")
                }
            } else if (selectedCategory.equals("Jogos do Dia", ignoreCase = true) || selectedCategory.equals("Esportes", ignoreCase = true)) {
                list = list.filter {
                    (it.category ?: "").lowercase().contains("esporte") ||
                            (it.category ?: "").lowercase().contains("jogo") ||
                            (it.category ?: "").lowercase().contains("futebol") ||
                            it.name.lowercase().contains("espn") ||
                            it.name.lowercase().contains("sportv") ||
                            it.name.lowercase().contains("premiere") ||
                            it.name.lowercase().contains("tnt")
                }
            } else if (selectedCategory.equals("Canais gratuitos", ignoreCase = true) || selectedCategory.equals("Canais abertos", ignoreCase = true)) {
                list = list.filter {
                    (it.category ?: "").lowercase().contains("aberto") ||
                            (it.category ?: "").lowercase().contains("geral") ||
                            (it.category ?: "").lowercase().contains("variedades") ||
                            it.name.lowercase().contains("globo") ||
                            it.name.lowercase().contains("sbt") ||
                            it.name.lowercase().contains("record") ||
                            it.name.lowercase().contains("band")
                }
            } else if (selectedCategory.equals("Filmes e séries", ignoreCase = true)) {
                list = list.filter {
                    (it.category ?: "").lowercase().contains("filme") ||
                            (it.category ?: "").lowercase().contains("série") ||
                            it.name.lowercase().contains("hbo") ||
                            it.name.lowercase().contains("telecine") ||
                            it.name.lowercase().contains("cinemax")
                }
            } else if (selectedCategory.equals("Infantil", ignoreCase = true)) {
                list = list.filter {
                    (it.category ?: "").lowercase().contains("infantil") ||
                            (it.category ?: "").lowercase().contains("desenho") ||
                            it.name.lowercase().contains("cartoon") ||
                            it.name.lowercase().contains("disney") ||
                            it.name.lowercase().contains("discovery kids") ||
                            it.name.lowercase().contains("gloob")
                }
            } else if (selectedCategory.equals("Notícias", ignoreCase = true)) {
                list = list.filter {
                    (it.category ?: "").lowercase().contains("notícia") ||
                            (it.category ?: "").lowercase().contains("news") ||
                            it.name.lowercase().contains("cnn") ||
                            it.name.lowercase().contains("globonews") ||
                            it.name.lowercase().contains("jovem pan")
                }
            } else if (selectedCategory.equals("Documentários", ignoreCase = true)) {
                list = list.filter {
                    (it.category ?: "").lowercase().contains("doc") ||
                            it.name.lowercase().contains("discovery") ||
                            it.name.lowercase().contains("national geographic") ||
                            it.name.lowercase().contains("history")
                }
            } else if (selectedCategory.equals("Anime", ignoreCase = true)) {
                list = list.filter {
                    (it.category ?: "").lowercase().contains("anime") ||
                            it.name.lowercase().contains("anime")
                }
            } else {
                list = list.filter {
                    (it.category ?: "").equals(selectedCategory, ignoreCase = true) ||
                            (it.category ?: "").lowercase().contains(selectedCategory.lowercase())
                }
            }
        }

        list
    }

    // Intercept physical/gesture back button during fullscreen
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("live_tv_screen")
    ) {
        // ==========================================
        // 1. COMPACT HEADER (Only visible when NOT in fullscreen)
        // ==========================================
        if (!isFullscreen) {
            Surface(
                color = Color(0xFF0A0A0C),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📺 TV AO VIVO",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { liveTvViewModel.refreshAll() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Atualizar Transmissões",
                                    tint = if (isRefreshing) BrandRed else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { liveTvViewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("🔎 Pesquisar canais por nome, número...", color = Color.Gray, fontSize = 12.5.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = CardBorder,
                            cursorColor = BrandRed,
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("tv_search_input"),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { liveTvViewModel.onSearchQueryChanged("") },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpar", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                }
            }
        }

        // ==========================================
        // 2. UNIFIED PERSISTENT LIVE TV PLAYER
        // ==========================================
        // The player container is NEVER unmounted. It stays in the composition tree always.
        Box(
            modifier = if (isFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            }
        ) {
            LiveTVPlayer(
                channel = selectedChannel,
                isFullscreen = isFullscreen,
                onToggleFullscreen = { isFullscreen = !isFullscreen }
            )
        }

        // ==========================================
        // 3. CHANNEL INFO, CATEGORIES & COMPACT CHANNEL GRID (Visible when NOT in fullscreen)
        // ==========================================
        if (!isFullscreen) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnsCount),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 90.dp, top = 4.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ACTIVE PLAYING CHANNEL METADATA CARD (Spans full width)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val currentSelected = selectedChannel
                    if (currentSelected != null) {
                        Surface(
                            color = DarkSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.8.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.Black,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        AsyncImage(
                                            model = currentSelected.getEffectiveLogo(),
                                            contentDescription = currentSelected.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = currentSelected.name,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Surface(
                                                color = BrandRed,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "🔴 AO VIVO",
                                                    color = Color.White,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = currentSelected.currentProgram ?: "Programação 24h",
                                            color = Color.Gray,
                                            fontSize = 10.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isFav = favoriteIds.contains(currentSelected.id)
                                    IconButton(
                                        onClick = { liveTvViewModel.toggleFavorite(currentSelected.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Favoritar",
                                            tint = if (isFav) BrandRed else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // CATEGORIES SELECTOR & COUNTER (Spans full width)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 0.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categoriesList) { cat ->
                                val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                                Surface(
                                    modifier = Modifier.clickable { liveTvViewModel.selectChannelCategory(cat) },
                                    color = if (isSelected) BrandRed else DarkSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(0.8.dp, if (isSelected) BrandRed else CardBorder)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        if (cat.equals("Favoritos", ignoreCase = true)) {
                                            Icon(
                                                imageVector = Icons.Filled.Favorite,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else BrandRed,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        } else if (cat.equals("Jogos do Dia", ignoreCase = true) || cat.equals("Esportes", ignoreCase = true)) {
                                            Icon(
                                                imageVector = Icons.Default.SportsSoccer,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else Color.LightGray,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }

                                        Text(
                                            text = cat,
                                            color = if (isSelected) Color.White else Color.LightGray,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // Channel Counter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val countText = when {
                                selectedCategory.equals("Favoritos", ignoreCase = true) -> "${filteredChannels.size} favoritos"
                                searchQuery.isNotBlank() -> "${filteredChannels.size} encontrados"
                                else -> "${filteredChannels.size} canais"
                            }

                            Text(
                                text = countText.uppercase(),
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )

                            if (searchQuery.isNotBlank() || !selectedCategory.equals("Todos", ignoreCase = true)) {
                                Text(
                                    text = "Filtrado por: ${if (searchQuery.isNotBlank()) "\"$searchQuery\"" else selectedCategory}",
                                    color = BrandRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // REFRESHING / ERROR / EMPTY STATES
                if (isRefreshing && channels.isEmpty()) {
                    items(6) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurface,
                            border = BorderStroke(0.8.dp, CardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.DarkGray.copy(alpha = 0.4f))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .width(70.dp)
                                            .height(12.dp)
                                            .background(Color.DarkGray.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(90.dp)
                                            .height(9.dp)
                                            .background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                    }
                } else if (errorMessage != null && channels.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = BrandRed,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Não foi possível carregar os canais.",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { liveTvViewModel.refreshAll() },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tentar novamente", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (filteredChannels.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val icon = when {
                                selectedCategory.equals("Favoritos", ignoreCase = true) -> Icons.Outlined.FavoriteBorder
                                searchQuery.isNotBlank() -> Icons.Default.SearchOff
                                else -> Icons.Default.TvOff
                            }

                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(44.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            val titleText = when {
                                selectedCategory.equals("Favoritos", ignoreCase = true) -> "Você ainda não adicionou canais aos favoritos."
                                searchQuery.isNotBlank() -> "Nenhum canal encontrado para \"$searchQuery\"."
                                else -> "Não há canais disponíveis nesta categoria."
                            }

                            Text(
                                text = titleText,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            val subText = when {
                                selectedCategory.equals("Favoritos", ignoreCase = true) -> "Toque no ícone de coração nos canais para adicionar a esta lista."
                                else -> "Tente pesquisar por outro termo ou selecione uma categoria diferente."
                            }

                            Text(
                                text = subText,
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            if (searchQuery.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { liveTvViewModel.onSearchQueryChanged("") },
                                    border = BorderStroke(1.dp, BrandRed),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Limpar pesquisa", color = BrandRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (!selectedCategory.equals("Todos", ignoreCase = true)) {
                                OutlinedButton(
                                    onClick = { liveTvViewModel.selectChannelCategory("Todos") },
                                    border = BorderStroke(1.dp, BrandRed),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Voltar para todos os canais", color = BrandRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // COMPACT CHANNEL GRID ITEMS
                    itemsIndexed(
                        items = filteredChannels,
                        key = { _, channel -> channel.id }
                    ) { _, channel ->
                        val originalIndex = channels.indexOf(channel) + 1
                        val isSelected = selectedChannel?.id == channel.id
                        val isFavorite = favoriteIds.contains(channel.id)

                        CompactChannelCard(
                            channel = channel,
                            channelNumber = originalIndex,
                            isSelected = isSelected,
                            isFavorite = isFavorite,
                            onClick = {
                                // Selecting channel updates active player stream, keeping layout stable
                                selectedChannel = channel
                            },
                            onFavoriteToggle = {
                                liveTvViewModel.toggleFavorite(channel.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// UNIFIED PERSISTENT LIVE TV PLAYER COMPONENT
// ==========================================
@Composable
fun LiveTVPlayer(
    channel: ApiChannel?,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = LocalContext.current as? Activity
    var retryCount by remember { mutableIntStateOf(0) }
    var isChannelLoading by remember { mutableStateOf(false) }
    var channelHasError by remember { mutableStateOf(false) }

    val currentUrl = remember(channel, retryCount) {
        channel?.getEffectiveEmbedUrl() ?: ""
    }

    // System bars & landscape orientation for fullscreen mode
    LaunchedEffect(isFullscreen) {
        if (activity != null) {
            try {
                if (isFullscreen) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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

    // Trigger loading state when channel or retryCount changes
    LaunchedEffect(channel?.id, retryCount) {
        if (channel != null && currentUrl.isNotBlank()) {
            isChannelLoading = true
            channelHasError = false
            delay(2800L) // Hide loading overlay after 2.8s so video canvas is clear
            isChannelLoading = false
        } else if (channel != null && currentUrl.isBlank()) {
            channelHasError = true
            isChannelLoading = false
        }
    }

    Surface(
        modifier = if (isFullscreen) {
            Modifier.fillMaxSize()
        } else {
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
        },
        color = Color.Black,
        border = if (isFullscreen) null else BorderStroke(1.dp, CardBorder),
        shape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 1. EMBED PLAYER STREAM (ALWAYS MOUNTED UNDERNEATH)
            if (channel != null && currentUrl.isNotBlank() && !channelHasError) {
                key(channel.id, retryCount) {
                    EmbedPlayer(
                        mediaType = "tv",
                        tmdbId = 0,
                        customUrl = currentUrl,
                        title = channel.name,
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = onToggleFullscreen,
                        onTryAgain = {
                            retryCount++
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (channel == null) {
                // Initial loading state before channel list arrives
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "CARREGANDO CANAL...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 2. LOADING OVERLAY ("CARREGANDO CANAL...")
            if (isChannelLoading && channel != null && !channelHasError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.88f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = BrandRed,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "CARREGANDO CANAL...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = channel.name,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 3. ERROR OVERLAY ("NÃO FOI POSSÍVEL REPRODUZIR ESTE CANAL")
            if (channelHasError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "NÃO FOI POSSÍVEL REPRODUZIR ESTE CANAL",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                channelHasError = false
                                retryCount++
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("TENTAR NOVAMENTE", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 4. FULLSCREEN BUTTON (SOMENTE NA TV AO VIVO)
            Surface(
                onClick = onToggleFullscreen,
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .testTag("tv_live_fullscreen_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Sair da Tela Cheia" else "Tela Cheia",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isFullscreen) "SAIR" else "TELA CHEIA",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// ELEGANT COMPACT CHANNEL CARD FOR GRID LAYOUT
// ==========================================
@Composable
fun CompactChannelCard(
    channel: ApiChannel,
    channelNumber: Int,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val numberFormatted = String.format("%03d", channelNumber)

    Surface(
        onClick = onClick,
        color = if (isSelected) Color(0xFF1E1012) else DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = if (isSelected) 1.2.dp else 0.8.dp,
            color = if (isSelected) BrandRed else CardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("channel_card_${channel.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 8.dp)
        ) {
            // Header Row: Number + Live/Status tag + Favorite Heart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Nº $numberFormatted",
                        color = BrandRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (isSelected) {
                        Surface(
                            color = BrandRed,
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = "EM EXIBIÇÃO",
                                color = Color.White,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(BrandRed)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "AO VIVO",
                                color = Color.Gray,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Favorite Heart Button (compact)
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favoritar",
                        tint = if (isFavorite) BrandRed else Color.Gray,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Body Row: Logo + Channel Name & Program
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black,
                    modifier = Modifier.size(34.dp)
                ) {
                    AsyncImage(
                        model = channel.getEffectiveLogo(),
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(2.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(1.dp))

                    Text(
                        text = channel.currentProgram ?: (channel.category ?: "Programação 24h"),
                        color = Color.Gray,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
