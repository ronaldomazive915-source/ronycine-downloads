package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.MediaEntity
import com.example.data.remote.Top10ConfigEntity
import com.example.data.remote.Top10ItemEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminTop10Screen(adminViewModel: AdminViewModel) {
    val config by adminViewModel.top10Config.collectAsState()
    val isGenerating by adminViewModel.isGeneratingTop10.collectAsState()
    val generatingMessage by adminViewModel.top10GeneratingMessage.collectAsState()
    
    var showAddItemDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // local copy for editing
    var localItems by remember(config.items) { mutableStateOf(config.items) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Header
            Text(
                "TOP 10 HOJE",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Text(
                "Escolha os filmes e séries que aparecem no TOP 10 da Home",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Mode Selection Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                listOf("manual" to "MODO MANUAL", "automatic" to "AUTOMÁTICO TMDB").forEach { (mode, label) ->
                    val isSelected = config.mode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) BrandRed else Color.Transparent)
                            .clickable { 
                                adminViewModel.saveTop10Config(config.copy(mode = mode))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (isSelected) Color.White else Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Status Info Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (config.mode == "automatic") "MODO AUTOMÁTICO — TMDB" else "MODO MANUAL",
                                color = BrandRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Configuração atual do ranking",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        
                        // Badge de status das posições
                        Surface(
                            color = if (localItems.size == 10) Color(0xFF2E7D32) else BrandRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "${localItems.size}/10 POSIÇÕES",
                                color = if (localItems.size == 10) Color.White else BrandRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (config.mode == "automatic") {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.White.copy(alpha = 0.05f)
                        )
                        
                        Text(
                            "ATUALIZAÇÃO AUTOMÁTICA",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0 to "OFF", 1 to "1h", 6 to "6h", 12 to "12h", 24 to "24h").forEach { (hours, label) ->
                                val isSelected = config.autoUpdateIntervalHours == hours
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) BrandRed else Color.White.copy(alpha = 0.05f))
                                        .clickable {
                                            adminViewModel.saveTop10Config(config.copy(autoUpdateIntervalHours = hours))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        color = if (isSelected) Color.White else Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (config.mode == "manual") {
                    Button(
                        onClick = { showAddItemDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(8.dp),
                        enabled = localItems.size < 10,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ADICIONAR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (config.mode == "automatic") {
                    Column(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { adminViewModel.generateTop10Automatic() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isGenerating) Color.Transparent else Color.DarkGray),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isGenerating,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            border = if (isGenerating) BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f)) else null
                        ) {
                            if (isGenerating) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = BrandRed, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(generatingMessage, color = Color.White, fontSize = 13.sp)
                                }
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ATUALIZAR AGORA", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = { 
                            adminViewModel.saveTop10Config(config.copy(items = localItems, lastUpdatedAt = System.currentTimeMillis(), source = "admin"))
                            scope.launch { snackbarHostState.showSnackbar("Configuração salva com sucesso!") }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SALVAR TOP 10", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (config.mode == "automatic") {
                Spacer(modifier = Modifier.height(12.dp))
                
                var showIntervalMenu by remember { mutableStateOf(false) }
                val intervals = listOf(
                    0 to "Desativado",
                    1 to "A cada 1 hora",
                    6 to "A cada 6 horas",
                    12 to "A cada 12 horas",
                    24 to "A cada 24 horas"
                )

                Surface(
                    onClick = { showIntervalMenu = true },
                    color = DarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Intervalo de Atualização", color = Color.Gray, fontSize = 10.sp)
                                val currentLabel = intervals.find { it.first == config.autoUpdateIntervalHours }?.second ?: "A cada 12 horas"
                                Text(currentLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                    }

                    DropdownMenu(
                        expanded = showIntervalMenu,
                        onDismissRequest = { showIntervalMenu = false },
                        modifier = Modifier.background(DarkSurface).border(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        intervals.forEach { (hours, label) ->
                            DropdownMenuItem(
                                text = { Text(label, color = Color.White) },
                                onClick = {
                                    adminViewModel.saveTop10Config(config.copy(autoUpdateIntervalHours = hours))
                                    showIntervalMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                "LISTA ATUAL (1-10)",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (localItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Leaderboard, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Nenhum item configurado", color = Color.Gray)
                        if (config.mode == "manual") {
                            Text("Adicione conteúdos do catálogo manualmente.", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(localItems.sortedBy { it.rank }) { item ->
                        Top10AdminCard(
                            item = item,
                            onRemove = {
                                localItems = localItems.filter { it.tmdbId != item.tmdbId || it.mediaType != item.mediaType }
                                    .mapIndexed { index, top10Item -> top10Item.copy(rank = index + 1) }
                                if (config.mode == "manual") {
                                    adminViewModel.saveTop10Config(config.copy(items = localItems))
                                }
                            },
                            onMoveUp = {
                                if (item.rank > 1) {
                                    val newList = localItems.toMutableList()
                                    val currentIndex = newList.indexOfFirst { it.rank == item.rank }
                                    val prevIndex = newList.indexOfFirst { it.rank == item.rank - 1 }
                                    if (currentIndex != -1 && prevIndex != -1) {
                                        val current = newList[currentIndex]
                                        val prev = newList[prevIndex]
                                        newList[currentIndex] = prev.copy(rank = item.rank)
                                        newList[prevIndex] = current.copy(rank = item.rank - 1)
                                        localItems = newList
                                        if (config.mode == "manual") {
                                            adminViewModel.saveTop10Config(config.copy(items = localItems))
                                        }
                                    }
                                }
                            },
                            onMoveDown = {
                                if (item.rank < localItems.size) {
                                    val newList = localItems.toMutableList()
                                    val currentIndex = newList.indexOfFirst { it.rank == item.rank }
                                    val nextIndex = newList.indexOfFirst { it.rank == item.rank + 1 }
                                    if (currentIndex != -1 && nextIndex != -1) {
                                        val current = newList[currentIndex]
                                        val next = newList[nextIndex]
                                        newList[currentIndex] = next.copy(rank = item.rank)
                                        newList[nextIndex] = current.copy(rank = item.rank + 1)
                                        localItems = newList
                                        if (config.mode == "manual") {
                                            adminViewModel.saveTop10Config(config.copy(items = localItems))
                                        }
                                    }
                                }
                            }
                        )
                    }
                    
                    item {
                        if (localItems.size < 10 && config.mode == "manual") {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = BrandRed.copy(alpha = 0.1f)),
                                border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = BrandRed, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "TOP 10 incompleto: ${localItems.size}/10 posições preenchidas",
                                        color = BrandRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddItemDialog) {
        Top10SearchDialog(
            adminViewModel = adminViewModel,
            onDismiss = { showAddItemDialog = false },
            onItemSelected = { media ->
                if (localItems.any { it.tmdbId == media.tmdbId && it.mediaType == media.mediaType }) {
                    scope.launch { snackbarHostState.showSnackbar("Este conteúdo já está no Top 10") }
                } else if (localItems.size >= 10) {
                    scope.launch { snackbarHostState.showSnackbar("Limite de 10 itens atingido") }
                } else {
                    val newItem = Top10ItemEntity(
                        rank = localItems.size + 1,
                        tmdbId = media.tmdbId,
                        mediaType = media.mediaType,
                        title = media.title,
                        posterPath = media.posterPath
                    )
                    localItems = localItems + newItem
                    if (config.mode == "manual") {
                        adminViewModel.saveTop10Config(config.copy(items = localItems))
                    }
                    showAddItemDialog = false
                }
            }
        )
    }
}

@Composable
fun Top10AdminCard(
    item: Top10ItemEntity,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(BrandRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.rank.toString(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.posterPath)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(42.dp)
                    .height(63.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (item.mediaType == "tv") Color(0xFF2196F3) else Color(0xFF607D8B),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            if (item.mediaType == "tv") "SÉRIE" else "FILME",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "TMDB: ${item.tmdbId}",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMoveUp,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color.LightGray)
                }
                IconButton(
                    onClick = onMoveDown,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.LightGray)
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = BrandRed.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Top10SearchDialog(
    adminViewModel: AdminViewModel,
    onDismiss: () -> Unit,
    onItemSelected: (MediaEntity) -> Unit
) {
    val searchQuery by adminViewModel.top10SearchQuery.collectAsState()
    val searchResults by adminViewModel.top10SearchResults.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = BrandRed)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Adicionar ao Top 10", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkSurface,
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)) {
                Text(
                    "Pesquise no catálogo do RonyCine pelo título ou TMDB ID.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { adminViewModel.searchCatalogForTop10(it) },
                    placeholder = { Text("Nome do filme ou série...", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        cursorColor = BrandRed
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    trailingIcon = { 
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { adminViewModel.searchCatalogForTop10("") }) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray)
                            }
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (searchQuery.length < 2) "Aguardando pesquisa..." else "Nenhum conteúdo encontrado",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(searchResults) { media ->
                            ListItem(
                                headlineContent = { Text(media.title, color = Color.White, fontWeight = FontWeight.Bold) },
                                supportingContent = { 
                                    Text(
                                        "${if (media.mediaType == "tv") "Série" else "Filme"} • ${media.releaseYear.take(4)}",
                                        color = Color.Gray
                                    ) 
                                },
                                leadingContent = {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(media.posterPath)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 40.dp, height = 60.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { onItemSelected(media) }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
    )
}
