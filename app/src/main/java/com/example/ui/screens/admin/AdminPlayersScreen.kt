package com.example.ui.screens.admin

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.remote.PlayerSource
import com.example.ui.theme.BrandRed
import com.example.ui.viewmodel.AdminViewModel
import com.example.util.PlayerUtils
import com.example.util.WebViewUtils
import kotlinx.coroutines.delay

private val DarkBackground = Color(0xFF0A0A0E)
private val DarkCard = Color(0xFF13131A)
private val DarkCardBorder = Color(0xFF22222E)

enum class PlayerPlaybackStatus(val label: String, val color: Color) {
    WAITING("Aguardando", Color(0xFF9E9E9E)),
    LOADING("Carregando", Color(0xFFFFA726)),
    READY("Pronto", Color(0xFF42A5F5)),
    PLAYING("Reproduzindo", Color(0xFF66BB6A)),
    BUFFERING("Bufferizando", Color(0xFFAB47BC)),
    ERROR("Erro", Color(0xFFEF5350))
}

data class TmdbPreset(val title: String, val tmdbId: Int, val type: String, val season: Int = 1, val episode: Int = 1)

private val MOVIE_PRESETS = listOf(
    TmdbPreset("Clube da Luta", 550, "movie"),
    TmdbPreset("A Origem", 27205, "movie"),
    TmdbPreset("Interestelar", 157336, "movie"),
    TmdbPreset("Vingadores: Ultimato", 299534, "movie"),
    TmdbPreset("Matrix", 603, "movie")
)

private val TV_PRESETS = listOf(
    TmdbPreset("Game of Thrones", 1399, "tv", 1, 1),
    TmdbPreset("Breaking Bad", 1396, "tv", 1, 1),
    TmdbPreset("Stranger Things", 66732, "tv", 1, 1),
    TmdbPreset("The Last of Us", 100088, "tv", 1, 1),
    TmdbPreset("Loki", 84958, "tv", 1, 1)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPlayersScreen(viewModel: AdminViewModel) {
    val playerSources by viewModel.playerSources.collectAsState()
    val megaEmbedConfig by viewModel.megaEmbedConfig.collectAsState()
    val playerConfig by viewModel.playerConfig.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Testing Bench Form State
    var selectedPlayerId by remember { mutableStateOf("") }
    var selectedMediaType by remember { mutableStateOf("movie") } // "movie" or "tv"
    var tmdbIdInput by remember { mutableStateOf("550") }
    var seasonInput by remember { mutableStateOf("1") }
    var episodeInput by remember { mutableStateOf("1") }
    var generatedUrl by remember { mutableStateOf("") }
    var activeTestUrl by remember { mutableStateOf("") }
    var testPlayerKey by remember { mutableIntStateOf(0) }
    var currentPlaybackStatus by remember { mutableStateOf(PlayerPlaybackStatus.WAITING) }
    var lastErrorMessage by remember { mutableStateOf<String?>(null) }
    var isPlayerDropdownExpanded by remember { mutableStateOf(false) }

    // Auto-select first available player on launch if unselected
    LaunchedEffect(playerSources) {
        if (selectedPlayerId.isBlank() && playerSources.isNotEmpty()) {
            val defaultPlayer = playerSources.find { it.isDefault } ?: playerSources.first()
            selectedPlayerId = defaultPlayer.id
        }
    }

    val selectedSource = remember(selectedPlayerId, playerSources) {
        playerSources.find { it.id == selectedPlayerId } ?: playerSources.firstOrNull()
    }

    fun generateAndTestUrl(autoPlay: Boolean = false) {
        keyboardController?.hide()
        val source = selectedSource ?: return
        val tmdbId = tmdbIdInput.trim().toIntOrNull()
        if (tmdbId == null || tmdbId <= 0) {
            Toast.makeText(context, "Informe um TMDB ID válido maior que 0.", Toast.LENGTH_SHORT).show()
            return
        }

        val s = if (selectedMediaType == "tv") seasonInput.trim().toIntOrNull() ?: 1 else null
        val e = if (selectedMediaType == "tv") episodeInput.trim().toIntOrNull() ?: 1 else null

        val url = PlayerUtils.buildPlayerUrl(
            source = source,
            mediaType = selectedMediaType,
            tmdbId = tmdbId,
            season = s,
            episode = e,
            megaEmbedConfig = playerConfig.megaEmbed
        )

        if (url.isNotBlank()) {
            generatedUrl = url
            activeTestUrl = url
            testPlayerKey++
            currentPlaybackStatus = PlayerPlaybackStatus.LOADING
            lastErrorMessage = null
        } else {
            Toast.makeText(context, "Não foi possível gerar a URL para este player.", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. CABEÇALHO OFICIAL DO CENTRO DE TESTES ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(BrandRed.copy(alpha = 0.15f))
                            .border(1.dp, BrandRed.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleOutline,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CENTRO DE TESTES DE PLAYERS",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Teste os players configurados no sistema usando um filme ou série.",
                            color = Color(0xFF9E9EA8),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // --- 2. LISTA DE PLAYERS CONFIGURADOS NO SISTEMA (FONTE ÚNICA) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PLAYERS CONFIGURADOS",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Sincronizado diretamente com Configurações → Players",
                                color = Color(0xFF7E7E8C),
                                fontSize = 11.sp
                            )
                        }

                        Surface(
                            color = Color(0xFF1E1E28),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF333344))
                        ) {
                            Text(
                                text = "${playerSources.size} cadastrados",
                                color = Color(0xFFD0D0DE),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = DarkCardBorder)

                    if (playerSources.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhum player configurado no sistema.",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            playerSources.forEach { source ->
                                val isSelectedForTest = selectedPlayerId == source.id
                                val isMgeb = source.id.contains("mgeb", ignoreCase = true) || source.name.contains("mgeb", ignoreCase = true)

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            width = if (isSelectedForTest) 1.5.dp else 1.dp,
                                            color = if (isSelectedForTest) BrandRed else DarkCardBorder,
                                            shape = RoundedCornerShape(10.dp)
                                        ),
                                    color = if (isSelectedForTest) BrandRed.copy(alpha = 0.08f) else Color(0xFF161620)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(if (source.enabled) Color(0xFF4CAF50) else Color(0xFF757575))
                                                )

                                                Text(
                                                    text = source.name,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )

                                                // Status Badge
                                                Surface(
                                                    color = if (source.enabled) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFF37474F).copy(alpha = 0.4f),
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(
                                                        0.5.dp,
                                                        if (source.enabled) Color(0xFF4CAF50) else Color(0xFF78909C)
                                                    )
                                                ) {
                                                    Text(
                                                        text = if (source.enabled) "ATIVO" else "DESATIVADO",
                                                        color = if (source.enabled) Color(0xFF81C784) else Color(0xFFB0BEC5),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }

                                                if (source.isDefault) {
                                                    Surface(
                                                        color = BrandRed.copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(4.dp),
                                                        border = BorderStroke(0.5.dp, BrandRed.copy(alpha = 0.6f))
                                                    ) {
                                                        Text(
                                                            text = "PADRÃO",
                                                            color = BrandRed,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    selectedPlayerId = source.id
                                                    generateAndTestUrl()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSelectedForTest) BrandRed else Color(0xFF262634)
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "TESTAR",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Informações adicionais de suporte e aviso se desativado
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Suporte: ${if (source.supportedContent.contains("tv")) "Filmes e Séries" else "Filmes"} • Áudio: ${source.language}",
                                                color = Color(0xFF8E8EA0),
                                                fontSize = 11.sp
                                            )

                                            if (!source.enabled) {
                                                Text(
                                                    text = "Desativado em Configurações → Players",
                                                    color = Color(0xFFFFB74D),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium
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

        // --- 3. BANCADA DE TESTE DO PLAYER ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "TESTAR PLAYER",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )

                    // Seletor de Player (Dropdown)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "PLAYER",
                            color = Color(0xFFA0A0B0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        ExposedDropdownMenuBox(
                            expanded = isPlayerDropdownExpanded,
                            onExpandedChange = { isPlayerDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedSource?.name ?: "Selecione um player",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isPlayerDropdownExpanded) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF181822),
                                    unfocusedContainerColor = Color(0xFF181822),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = BrandRed,
                                    unfocusedBorderColor = DarkCardBorder
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .height(50.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = isPlayerDropdownExpanded,
                                onDismissRequest = { isPlayerDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF181822))
                            ) {
                                playerSources.forEach { source ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(source.name, color = Color.White, fontWeight = FontWeight.Bold)
                                                Text(
                                                    text = if (source.enabled) "Ativo" else "Desativado",
                                                    color = if (source.enabled) Color(0xFF81C784) else Color(0xFFE57373),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedPlayerId = source.id
                                            isPlayerDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Seletor de Tipo (Filme / Série)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "TIPO DE CONTEÚDO",
                            color = Color(0xFFA0A0B0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isMovie = selectedMediaType == "movie"
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedMediaType = "movie"
                                        if (tmdbIdInput == "1399" || tmdbIdInput == "1396") tmdbIdInput = "550"
                                    },
                                color = if (isMovie) BrandRed else Color(0xFF1A1A26),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isMovie) BrandRed else DarkCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "FILME",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            val isTv = selectedMediaType == "tv"
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedMediaType = "tv"
                                        if (tmdbIdInput == "550" || tmdbIdInput == "27205") tmdbIdInput = "1399"
                                    },
                                color = if (isTv) BrandRed else Color(0xFF1A1A26),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isTv) BrandRed else DarkCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SÉRIE",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Campo TMDB ID e Presets Rápidos
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TMDB ID",
                                color = Color(0xFFA0A0B0),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (selectedMediaType == "movie") "ex: 550 (Clube da Luta)" else "ex: 1399 (Game of Thrones)",
                                color = Color(0xFF6B6B7A),
                                fontSize = 10.5.sp
                            )
                        }

                        OutlinedTextField(
                            value = tmdbIdInput,
                            onValueChange = { tmdbIdInput = it.filter { ch -> ch.isDigit() } },
                            placeholder = { Text("Digite o TMDB ID...", color = Color.Gray, fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { generateAndTestUrl() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF181822),
                                unfocusedContainerColor = Color(0xFF181822),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = DarkCardBorder
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            trailingIcon = {
                                if (tmdbIdInput.isNotEmpty()) {
                                    IconButton(onClick = { tmdbIdInput = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Limpar", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        )

                        // Presets Chips Rápidos
                        val presets = if (selectedMediaType == "movie") MOVIE_PRESETS else TV_PRESETS
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presets.forEach { preset ->
                                val isCurrent = tmdbIdInput == preset.tmdbId.toString()
                                Surface(
                                    modifier = Modifier.clickable {
                                        tmdbIdInput = preset.tmdbId.toString()
                                        if (selectedMediaType == "tv") {
                                            seasonInput = preset.season.toString()
                                            episodeInput = preset.episode.toString()
                                        }
                                        generateAndTestUrl()
                                    },
                                    color = if (isCurrent) BrandRed.copy(alpha = 0.2f) else Color(0xFF1A1A24),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.8.dp, if (isCurrent) BrandRed else Color(0xFF2E2E3E))
                                ) {
                                    Text(
                                        text = "${preset.title} (#${preset.tmdbId})",
                                        color = if (isCurrent) Color.White else Color(0xFFB0B0C0),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Se for Série: Temporada e Episódio
                    if (selectedMediaType == "tv") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("TEMPORADA", color = Color(0xFFA0A0B0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = seasonInput,
                                    onValueChange = { seasonInput = it.filter { ch -> ch.isDigit() } },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF181822),
                                        unfocusedContainerColor = Color(0xFF181822),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = BrandRed,
                                        unfocusedBorderColor = DarkCardBorder
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("EPISÓDIO", color = Color(0xFFA0A0B0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = episodeInput,
                                    onValueChange = { episodeInput = it.filter { ch -> ch.isDigit() } },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF181822),
                                        unfocusedContainerColor = Color(0xFF181822),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = BrandRed,
                                        unfocusedBorderColor = DarkCardBorder
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                )
                            }
                        }
                    }

                    // Botão GERAR URL
                    Button(
                        onClick = { generateAndTestUrl() },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("btn_generate_player_url")
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "GERAR URL E CARREGAR TESTE",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // --- 4. EXIBIÇÃO DA URL GERADA ---
        if (generatedUrl.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "URL GERADA OFICIAL",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(generatedUrl))
                                        Toast.makeText(context, "URL copiada!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar URL", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                }

                                IconButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(generatedUrl))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Erro ao abrir navegador: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = "Abrir no navegador", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Surface(
                            color = Color(0xFF0C0C10),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E1E28)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = generatedUrl,
                                color = Color(0xFF80D8FF),
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- 5. PRÉ-VISUALIZAÇÃO / PLAYER INTERATIVO DE TESTE ---
        if (activeTestUrl.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PRÉ-VISUALIZAÇÃO",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )

                            // Status Indicator
                            Surface(
                                color = currentPlaybackStatus.color.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, currentPlaybackStatus.color.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(currentPlaybackStatus.color)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = currentPlaybackStatus.label,
                                        color = currentPlaybackStatus.color,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Container do Player Responsivo (16:9)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black)
                                .border(1.dp, Color(0xFF222230), RoundedCornerShape(10.dp))
                        ) {
                            key(activeTestUrl, testPlayerKey) {
                                UnifiedLiveTestPlayer(
                                    url = activeTestUrl,
                                    onStatusChanged = { status ->
                                        currentPlaybackStatus = status
                                    },
                                    onError = { err ->
                                        currentPlaybackStatus = PlayerPlaybackStatus.ERROR
                                        lastErrorMessage = err
                                    }
                                )
                            }
                        }

                        if (lastErrorMessage != null) {
                            Surface(
                                color = Color(0xFF2B1214),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFFB71C1C)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = lastErrorMessage ?: "Erro ao carregar o player.",
                                        color = Color(0xFFFFCDD2),
                                        fontSize = 11.sp
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

/**
 * Live Interactive Player for Testing:
 * - Instantiates a single WebView with isolated lifecycle
 * - Intercepts JS player events (playing, waiting, canplay, error) via injected Javascript Interface
 * - Removes loading overlay immediately on active playback
 * - Disposes previous instance safely on destruction
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UnifiedLiveTestPlayer(
    url: String,
    onStatusChanged: (PlayerPlaybackStatus) -> Unit,
    onError: (String) -> Unit
) {
    var isOverlayLoading by remember { mutableStateOf(true) }
    var activeWebViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(url) {
        onDispose {
            activeWebViewRef?.let { webView ->
                WebViewUtils.safeDestroy(webView)
            }
            activeWebViewRef = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    WebViewUtils.applySafeLayerType(this, forceSoftware = false)

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
                        allowContentAccess = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = false
                        displayZoomControls = false
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
                    }

                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onPlayerEvent(event: String) {
                            post {
                                val ev = event.lowercase()
                                when {
                                    ev == "playing" || ev == "play" -> {
                                        isOverlayLoading = false
                                        onStatusChanged(PlayerPlaybackStatus.PLAYING)
                                    }
                                    ev == "waiting" || ev == "buffering" -> {
                                        onStatusChanged(PlayerPlaybackStatus.BUFFERING)
                                    }
                                    ev == "canplay" || ev == "ready" || ev == "loadeddata" -> {
                                        isOverlayLoading = false
                                        onStatusChanged(PlayerPlaybackStatus.READY)
                                    }
                                    ev == "error" || ev == "failed" -> {
                                        isOverlayLoading = false
                                        onError("Erro reportado pelo player.")
                                    }
                                }
                            }
                        }
                    }, "TestPlayerBridge")

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            onStatusChanged(PlayerPlaybackStatus.LOADING)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Inject HTMLMediaElement listener to bridge video events automatically
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    function hookVideos() {
                                        var vids = document.querySelectorAll('video');
                                        for (var i = 0; i < vids.length; i++) {
                                            var v = vids[i];
                                            if (!v.__hooked) {
                                                v.__hooked = true;
                                                v.addEventListener('playing', function() { if(window.TestPlayerBridge) window.TestPlayerBridge.onPlayerEvent('playing'); });
                                                v.addEventListener('waiting', function() { if(window.TestPlayerBridge) window.TestPlayerBridge.onPlayerEvent('waiting'); });
                                                v.addEventListener('canplay', function() { if(window.TestPlayerBridge) window.TestPlayerBridge.onPlayerEvent('canplay'); });
                                                v.addEventListener('error', function() { if(window.TestPlayerBridge) window.TestPlayerBridge.onPlayerEvent('error'); });
                                            }
                                        }
                                    }
                                    hookVideos();
                                    setInterval(hookVideos, 1500);

                                    window.addEventListener('message', function(ev) {
                                        try {
                                            var d = ev.data;
                                            if (typeof d === 'string') { try { d = JSON.parse(d); } catch(e){} }
                                            if (d && (d.event || d.type)) {
                                                var t = (d.event || d.type).toLowerCase();
                                                if (window.TestPlayerBridge) window.TestPlayerBridge.onPlayerEvent(t);
                                            }
                                        } catch(e) {}
                                    });
                                })();
                                """.trimIndent(),
                                null
                            )
                            onStatusChanged(PlayerPlaybackStatus.READY)
                            isOverlayLoading = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                isOverlayLoading = false
                                val desc = error?.description?.toString() ?: "Falha ao carregar URL"
                                onError(desc)
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
                                isOverlayLoading = false
                                onError("Erro HTTP ${errorResponse?.statusCode}")
                            }
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?
                        ): Boolean {
                            WebViewUtils.safeDestroy(view, isDead = true)
                            activeWebViewRef = null
                            isOverlayLoading = false
                            onError("Processo do player foi encerrado.")
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
                    }

                    loadUrl(url)
                    activeWebViewRef = this
                }
            }
        )

        // Loading Overlay (only shown while initially resolving page)
        if (isOverlayLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = BrandRed,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = "Conectando ao player...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
