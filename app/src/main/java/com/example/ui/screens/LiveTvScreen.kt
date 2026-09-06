package com.example.ui.screens

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.remote.ApiChannel
import com.example.ui.components.EmbedPlayer
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.LiveTvViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(
    onNavigateToChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialChannelId: String? = null,
    liveTvViewModel: LiveTvViewModel = viewModel()
) {
    val channels by liveTvViewModel.channels.collectAsState()
    val channelCategories by liveTvViewModel.channelCategories.collectAsState()
    val searchQuery by liveTvViewModel.searchQuery.collectAsState()
    val selectedCategory by liveTvViewModel.selectedChannelCategory.collectAsState()
    val favoriteIds by liveTvViewModel.favoriteChannelIds.collectAsState()
    val isRefreshing by liveTvViewModel.isRefreshing.collectAsState()
    val errorMessage by liveTvViewModel.errorMessage.collectAsState()

    // Active playing channel state
    var selectedChannel by remember { mutableStateOf<ApiChannel?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var currentWebViewUrl by remember { mutableStateOf("") }

    // Auto-select initial channel if provided or if navigating directly
    LaunchedEffect(channels, initialChannelId) {
        if (selectedChannel == null && channels.isNotEmpty()) {
            if (!initialChannelId.isNullOrBlank()) {
                val matched = channels.find {
                    it.id.equals(initialChannelId, ignoreCase = true) ||
                            (it.slug != null && it.slug.equals(initialChannelId, ignoreCase = true))
                }
                if (matched != null) {
                    selectedChannel = matched
                }
            }
        }
    }

    // Comprehensive category list based on specifications
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

    // Real-time channel filtering
    val filteredChannels = remember(channels, selectedCategory, searchQuery, favoriteIds) {
        var list = channels

        // 1. Search Query Filter (Matches name, category, current program, or channel number e.g. "001")
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

        // 2. Category Filter
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

    // Handle physical or gesture back button when player webview or fullscreen is active
    BackHandler(enabled = isFullscreen || activeWebView != null) {
        val webView = activeWebView
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else if (isFullscreen) {
            isFullscreen = false
        } else {
            selectedChannel = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("live_tv_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ==========================================
            // 1. COMPACT HEADER
            // ==========================================
            item {
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

                        // Search Bar Inside Header
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
            // 2. PLAYER AREA
            // ==========================================
            item {
                Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                    val currentSelected = selectedChannel
                    if (currentSelected == null) {
                        // Empty State Player Box
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp)),
                            color = Color(0xFF0F0F12),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(BrandRed.copy(alpha = 0.15f), CircleShape)
                                        .border(1.dp, BrandRed.copy(alpha = 0.3f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LiveTv,
                                        contentDescription = null,
                                        tint = BrandRed,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Selecione um canal para reproduzir",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Escolha um dos canais da lista abaixo para iniciar a transmissão ao vivo",
                                    color = Color.Gray,
                                    fontSize = 11.5.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                    } else {
                        // Active Stream Embed Player
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                            ) {
                                EmbedPlayer(
                                    mediaType = "tv",
                                    tmdbId = 0,
                                    customUrl = currentSelected.getEffectiveEmbedUrl(),
                                    title = currentSelected.name,
                                    isFullscreen = isFullscreen,
                                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                                    onWebViewCreated = { activeWebView = it },
                                    onUrlChanged = { currentWebViewUrl = it }
                                )
                            }

                            // Active Playing Channel Bar
                            Surface(
                                color = DarkSurface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.8.dp, CardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
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

                                        IconButton(
                                            onClick = { selectedChannel = null },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Fechar Player",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 3. CATEGORIES SELECTION ROW
            // ==========================================
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
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
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
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
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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

            // ==========================================
            // 4. CHANNEL CARDS LIST & EMPTY STATES
            // ==========================================
            if (isRefreshing && channels.isEmpty()) {
                // Skeleton loading state
                items(6) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .padding(horizontal = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = BorderStroke(0.8.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray.copy(alpha = 0.4f))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(14.dp)
                                        .background(Color.DarkGray.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(10.dp)
                                        .background(Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }
            } else if (errorMessage != null && channels.isEmpty()) {
                // Error state
                item {
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
                // Empty state for filters, search, or favorites
                item {
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
                // List of Compact Channels
                itemsIndexed(
                    items = filteredChannels,
                    key = { _, channel -> channel.id }
                ) { _, channel ->
                    val originalIndex = channels.indexOf(channel) + 1
                    val isSelected = selectedChannel?.id == channel.id
                    val isFavorite = favoriteIds.contains(channel.id)

                    Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                        CompactChannelCard(
                            channel = channel,
                            channelNumber = originalIndex,
                            isSelected = isSelected,
                            isFavorite = isFavorite,
                            onClick = {
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
// COMPACT CHANNEL CARD COMPONENT
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
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) DarkSurface else Color(0xFF141418),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.8.dp,
            color = if (isSelected) BrandRed else CardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .testTag("channel_card_${channel.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // LOGO
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(channel.getEffectiveLogo())
                            .crossfade(true)
                            .build(),
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }

                // DETAILS
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Nº $numberFormatted",
                            color = BrandRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.3.sp
                        )

                        if (isSelected) {
                            Surface(
                                color = BrandRed,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "EM EXIBIÇÃO",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "•  ${channel.category ?: "Ao Vivo"}",
                                color = Color.Gray,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(1.dp))

                    Text(
                        text = channel.currentProgram ?: "Programação 24h",
                        color = Color.LightGray.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Heart Favorite Icon Button
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favoritar Canal",
                        tint = if (isFavorite) BrandRed else Color.Gray.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Chevron Right Arrow
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Selecionar",
                    tint = if (isSelected) BrandRed else Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
