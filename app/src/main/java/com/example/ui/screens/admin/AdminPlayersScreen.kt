package com.example.ui.screens.admin

import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.PlayerSource
import com.example.ui.viewmodel.AdminViewModel
import com.example.util.PlayerUtils
import com.example.util.WebViewUtils
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.util.UUID

@Composable
fun AdminSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun AdminPlayersScreen(viewModel: AdminViewModel) {
    val playerSources by viewModel.playerSources.collectAsState()
    val playerConfig by viewModel.playerConfig.collectAsState()
    val isSavingPlayerConfig by viewModel.isSavingPlayerConfig.collectAsState()
    val playerSaveStatusMessage by viewModel.playerSaveStatusMessage.collectAsState()
    
    var showAddForm by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<PlayerSource?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<PlayerSource?>(null) }
    var selectedTestPlayerId by remember { mutableStateOf<String?>(null) }
    
    val activeCount = playerSources.count { it.enabled }
    val inactiveCount = playerSources.count { !it.enabled }
    val primaryPlayer = playerSources.find { it.id == playerConfig.defaultPlayerId }
    val isSettingDefaultPlayer by viewModel.isSettingDefaultPlayer.collectAsState()

    var showDefaultConfirm by remember { mutableStateOf<PlayerSource?>(null) }
    val context = LocalContext.current

    LaunchedEffect(playerSaveStatusMessage) {
        playerSaveStatusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AdminSectionHeader(
                title = "Gerenciador de Players",
                subtitle = "Configure, teste e gerencie as fontes de reprodução utilizadas pelo RONYCINE."
            )
        }

        // Status Feedback Banner
        if (playerSaveStatusMessage != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = playerSaveStatusMessage ?: "",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearPlayerStatusMessage() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Dashboard Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardSmallCard(
                    title = "ATIVOS",
                    value = "$activeCount",
                    label = "PLAYERS",
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
                DashboardSmallCard(
                    title = "INATIVOS",
                    value = "$inactiveCount",
                    label = "PLAYERS",
                    color = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
                DashboardSmallCard(
                    title = "PRINCIPAL",
                    value = primaryPlayer?.name ?: "Nenhum",
                    label = if (primaryPlayer != null) "MÉTODO ATIVO" else "ALERTA: NENHUM",
                    color = if (primaryPlayer != null) Color(0xFFfb542b) else Color(0xFFFFC107),
                    modifier = Modifier.weight(1.5f)
                )
            }
        }

        // Section: CONFIGURAÇÃO DE ÁUDIO (DUBLADO & LEGENDADO)
        item {
            AudioConfigCard(
                playerConfig = playerConfig,
                isSaving = isSavingPlayerConfig,
                onSaveSubtitled = { provider, defaultLanguage, enabled ->
                    viewModel.saveSubtitledSettings(provider, defaultLanguage, enabled)
                }
            )
        }

        // Section: Player Principal
        item {
            if (primaryPlayer != null) {
                PrimaryPlayerCard(
                    source = primaryPlayer,
                    playerConfig = playerConfig,
                    onEdit = { editingSource = primaryPlayer },
                    onTest = { selectedTestPlayerId = primaryPlayer.id }
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336).copy(alpha = 0.1f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF44336).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFF44336))
                        Text(
                            "Nenhum player principal definido. O aplicativo pode não reproduzir conteúdos corretamente.",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Section: MEGAEMBED
        item {
            MegaEmbedConfigCard(
                playerConfig = playerConfig,
                isSaving = isSavingPlayerConfig,
                onSave = { player, color, enabled ->
                    viewModel.saveMegaEmbedSettings(player, color, enabled)
                }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PLAYERS CONFIGURADOS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Button(
                    onClick = { showAddForm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFfb542b)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ADICIONAR PLAYER", fontSize = 11.sp)
                }
            }
        }

        // Players List
        items(playerSources.sortedBy { it.priority }) { source ->
            PlayerSourceCompactCard(
                source = source,
                isGlobalDefault = source.id == playerConfig.defaultPlayerId,
                isSettingDefault = isSettingDefaultPlayer == source.id,
                onEdit = { editingSource = source },
                onDelete = { showDeleteConfirm = source },
                onToggle = { viewModel.togglePlayerEnabled(source.id, !source.enabled) },
                onSetDefault = { showDefaultConfirm = source },
                onDuplicate = { viewModel.duplicatePlayerSource(source) },
                onTest = {
                    selectedTestPlayerId = source.id
                    Toast.makeText(context, "Player ${source.name} selecionado no Testador", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Test Section
        item {
            PlayerTestCard(
                sources = playerSources,
                defaultPlayerId = playerConfig.defaultPlayerId,
                playerConfig = playerConfig,
                selectedPlayerIdOverride = selectedTestPlayerId
            )
        }
        
        item { Spacer(Modifier.height(80.dp)) }
    }

    if (showAddForm || editingSource != null) {
        PlayerSourceFormDialog(
            source = editingSource,
            onDismiss = { 
                showAddForm = false
                editingSource = null
            },
            onSave = { 
                viewModel.savePlayerSource(it)
                showAddForm = false
                editingSource = null
            }
        )
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = Color(0xFF121212),
            title = { Text("Excluir Player?", color = Color.White) },
            text = { 
                Text(
                    "Esta ação removerá a configuração de \"${showDeleteConfirm?.name}\". " +
                    if (showDeleteConfirm?.id == playerConfig.defaultPlayerId) "\n\n⚠️ Este é o player principal. Defina outro como principal antes de excluir." else "",
                    color = Color.White.copy(alpha = 0.7f)
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm?.let { viewModel.deletePlayerSource(it.id) }
                        showDeleteConfirm = null
                    },
                    enabled = showDeleteConfirm?.id != playerConfig.defaultPlayerId,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF44336))
                ) {
                    Text("EXCLUIR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("CANCELAR", color = Color.White)
                }
            }
        )
    }

    if (showDefaultConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDefaultConfirm = null },
            containerColor = Color(0xFF121212),
            title = { Text("Definir Player Principal?", color = Color.White) },
            text = {
                Text(
                    "Deseja definir \"${showDefaultConfirm?.name}\" como o player principal do aplicativo? " +
                    "\n\nEsta alteração afetará todos os usuários imediatamente.",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDefaultConfirm?.let { viewModel.setDefaultPlayer(it.id) }
                        showDefaultConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFfb542b))
                ) {
                    Text("DEFINIR COMO PRINCIPAL")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDefaultConfirm = null }) {
                    Text("CANCELAR", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun DashboardSmallCard(
    title: String,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 8.sp, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
fun PrimaryPlayerCard(
    source: PlayerSource,
    playerConfig: com.example.data.remote.PlayerConfig,
    onEdit: () -> Unit,
    onTest: () -> Unit
) {
    val isMegaEmbed = source.id.contains("mgeb", ignoreCase = true) ||
            source.name.contains("mega", ignoreCase = true) ||
            source.movieTmdbUrl.contains("mgeb.top")

    val activeSubPlayerName = if (isMegaEmbed) {
        val playerCode = playerConfig.megaEmbed.player.ifBlank { source.internalPlayer.ifBlank { "megaplay" } }
        com.example.data.remote.MegaEmbedPlayerType.getDisplayName(playerCode)
    } else if (source.internalPlayer.isNotBlank()) {
        source.internalPlayer
    } else {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFfb542b).copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "PLAYER PRINCIPAL ATUAL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFfb542b),
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(source.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }

                    if (activeSubPlayerName != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activeSubPlayerName,
                                color = Color(0xFFfb542b),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            if (isMegaEmbed && playerConfig.megaEmbed.color.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            try {
                                                Color(android.graphics.Color.parseColor("#" + playerConfig.megaEmbed.color))
                                            } catch (e: Exception) {
                                                Color(0xFFfb542b)
                                            }
                                        )
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "#${playerConfig.megaEmbed.color}",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${source.language} • Prioridade ${source.priority}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("CONFIGURAR", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MegaEmbedConfigCard(
    playerConfig: com.example.data.remote.PlayerConfig,
    isSaving: Boolean,
    onSave: (player: String, color: String, enabled: Boolean) -> Unit
) {
    var selectedPlayer by remember(playerConfig.megaEmbed.player) {
        mutableStateOf(playerConfig.megaEmbed.player.ifBlank { "megaplay" })
    }
    var colorHex by remember(playerConfig.megaEmbed.color) {
        mutableStateOf(playerConfig.megaEmbed.color.ifBlank { "fb542b" })
    }
    var enabled by remember(playerConfig.megaEmbed.enabled) {
        mutableStateOf(playerConfig.megaEmbed.enabled)
    }

    val isValidHex = com.example.data.remote.MegaEmbedPlayerType.isValidHexColor(colorHex)
    val normalizedHex = com.example.data.remote.MegaEmbedPlayerType.normalizeColor(colorHex)
    val previewColor = if (isValidHex) {
        try {
            Color(android.graphics.Color.parseColor("#$normalizedHex"))
        } catch (e: Exception) {
            Color(0xFFfb542b)
        }
    } else {
        Color(0xFFfb542b)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFfb542b).copy(alpha = 0.25f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "MEGAEMBED",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Configure o player oficial e o estilo de reprodução",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { enabled = !enabled }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFFfb542b),
                            uncheckedColor = Color.White.copy(alpha = 0.5f)
                        )
                    )
                    Text(
                        "Ativo",
                        color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                "Player MegaEmbed:",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            // As 4 opções com RadioButton, nome, descrição curta
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.example.data.remote.MegaEmbedPlayerType.ALL_PLAYERS.forEach { option ->
                    val isSelected = selectedPlayer.equals(option.code, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedPlayer = option.code },
                        color = if (isSelected) Color(0xFFfb542b).copy(alpha = 0.12f) else Color(0xFF141414),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFFfb542b) else Color.White.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedPlayer = option.code },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFFfb542b),
                                    unselectedColor = Color.White.copy(alpha = 0.5f)
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.displayName,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = option.description,
                                    color = if (isSelected) Color(0xFFfb542b).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // COR DO PLAYER (HEX)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "COR DO PLAYER (HEX)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = colorHex,
                        onValueChange = { colorHex = it },
                        placeholder = { Text("#fb542b", color = Color.White.copy(alpha = 0.4f)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = !isValidHex,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFfb542b),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            errorBorderColor = Color(0xFFF44336)
                        )
                    )

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(previewColor)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isValidHex) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cor inválida",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (!isValidHex) {
                    Text(
                        "Formato HEX inválido. Use um formato como #fb542b ou fb542b",
                        color = Color(0xFFF44336),
                        fontSize = 11.sp
                    )
                }
            }

            Button(
                onClick = {
                    val finalColor = com.example.data.remote.MegaEmbedPlayerType.normalizeColor(colorHex)
                    onSave(selectedPlayer, finalColor, enabled)
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFfb542b)),
                shape = RoundedCornerShape(8.dp),
                enabled = isValidHex && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("SALVANDO CONFIGURAÇÃO...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("SALVAR CONFIGURAÇÃO", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AudioConfigCard(
    playerConfig: com.example.data.remote.PlayerConfig,
    isSaving: Boolean,
    onSaveSubtitled: (provider: String, defaultLanguage: String, enabled: Boolean) -> Unit
) {
    var subtitledProvider by remember(playerConfig.subtitledPlayer.provider) { 
        mutableStateOf(if (playerConfig.subtitledPlayer.provider.isNotBlank()) playerConfig.subtitledPlayer.provider else "vidsrc") 
    }
    var subtitledLanguage by remember(playerConfig.subtitledPlayer.defaultLanguage) { 
        mutableStateOf(if (playerConfig.subtitledPlayer.defaultLanguage.isNotBlank()) playerConfig.subtitledPlayer.defaultLanguage else "pt") 
    }
    var subtitledEnabled by remember(playerConfig.subtitledPlayer.enabled) { 
        mutableStateOf(playerConfig.subtitledPlayer.enabled) 
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "CONFIGURAÇÃO DE ÁUDIO",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        "Mapeamento oficial de provedores por tipo de áudio",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
                Badge(
                    containerColor = Color(0xFFfb542b).copy(alpha = 0.2f),
                    contentColor = Color(0xFFfb542b)
                ) {
                    Text("SISTEMA", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                }
            }

            // DUBLADO SECTION
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("DUBLADO", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Badge(containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f), contentColor = Color(0xFF4CAF50)) {
                                Text("ATIVO", fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                            }
                        }
                        Text("Provedor: MegaEmbed (mgeb.top)", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            }

            // LEGENDADO SECTION (VIDSRC)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFfb542b).copy(alpha = 0.3f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFfb542b).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color(0xFFfb542b), modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("LEGENDADO", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Badge(
                                        containerColor = if (subtitledEnabled) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFFF44336).copy(alpha = 0.2f),
                                        contentColor = if (subtitledEnabled) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    ) {
                                        Text(if (subtitledEnabled) "ATIVO" else "INATIVO", fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                                    }
                                }
                                Text("Provedor: VidSrc (vidsrc.tw)", color = Color(0xFFfb542b), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Switch(
                            checked = subtitledEnabled,
                            onCheckedChange = { subtitledEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFfb542b),
                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }

                    Text(
                        "O botão Legendado no player do app utiliza exclusivamente VidSrc para Filmes e Séries.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = subtitledLanguage,
                            onValueChange = { subtitledLanguage = it },
                            label = { Text("Idioma Legendas") },
                            placeholder = { Text("pt") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFfb542b),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                            )
                        )

                        Button(
                            onClick = {
                                onSaveSubtitled(subtitledProvider, subtitledLanguage, subtitledEnabled)
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFfb542b)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("SALVAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerSourceCompactCard(
    source: PlayerSource,
    isGlobalDefault: Boolean,
    isSettingDefault: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
    onSetDefault: () -> Unit,
    onDuplicate: () -> Unit,
    onTest: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (isGlobalDefault) Color(0xFFfb542b).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Status Indicator
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (source.enabled) Color(0xFF4CAF50) else Color(0xFFF44336))
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            source.name, 
                            color = if (source.enabled) Color.White else Color.White.copy(alpha = 0.4f), 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 14.sp
                        )
                        if (isGlobalDefault) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Star, "Principal", tint = Color(0xFFfb542b), modifier = Modifier.size(12.dp))
                            Text(" PRINCIPAL", color = Color(0xFFfb542b), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Text(
                        "${source.type} • ${source.language} • Prioridade ${source.priority}", 
                        color = Color.White.copy(alpha = 0.4f), 
                        fontSize = 11.sp
                    )
                    
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (source.movieTmdbUrl.isNotEmpty()) {
                            Badge(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White.copy(alpha = 0.6f)) {
                                Text("FILMES ✓", fontSize = 8.sp, modifier = Modifier.padding(2.dp))
                            }
                        }
                        if (source.tvTmdbUrl.isNotEmpty()) {
                            Badge(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White.copy(alpha = 0.6f)) {
                                Text("SÉRIES ✓", fontSize = 8.sp, modifier = Modifier.padding(2.dp))
                            }
                        }
                        if (source.language.contains("Legendado", ignoreCase = true) || source.name.contains("VidSrc", ignoreCase = true)) {
                            Badge(containerColor = Color(0xFFfb542b).copy(alpha = 0.15f), contentColor = Color(0xFFfb542b)) {
                                Text("VIDSRC", fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSettingDefault) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFfb542b)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    
                    IconButton(onClick = onTest, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.PlayArrow, "Testar", tint = Color(0xFFfb542b), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Edit, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.MoreVert, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color(0xFF1A1A1A))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Testar Player", color = Color(0xFFfb542b)) },
                            onClick = { onTest(); expanded = false },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = Color(0xFFfb542b)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Editar", color = Color.White) },
                            onClick = { onEdit(); expanded = false },
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color.White) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (source.enabled) "Desativar" else "Ativar", color = Color.White) },
                            onClick = { onToggle(); expanded = false },
                            leadingIcon = { Icon(if (source.enabled) Icons.Default.Block else Icons.Default.CheckCircle, null, tint = Color.White) }
                        )
                        if (!isGlobalDefault && source.enabled) {
                            DropdownMenuItem(
                                text = { Text(if (isSettingDefault) "Definindo..." else "Definir como principal", color = Color.White) },
                                onClick = { onSetDefault(); expanded = false },
                                leadingIcon = { 
                                    if (isSettingDefault) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Default.Star, null, tint = Color(0xFFfb542b)) 
                                },
                                enabled = !isSettingDefault
                            )
                        } else if (isGlobalDefault) {
                            DropdownMenuItem(
                                text = { Text("✓ Player principal", color = Color(0xFFfb542b)) },
                                onClick = { expanded = false },
                                leadingIcon = { Icon(Icons.Default.Star, null, tint = Color(0xFFfb542b)) },
                                enabled = false
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Duplicar", color = Color.White) },
                            onClick = { onDuplicate(); expanded = false },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = Color.White) }
                        )
                        Divider(color = Color.White.copy(alpha = 0.1f))
                        DropdownMenuItem(
                            text = { Text("Excluir", color = Color(0xFFF44336)) },
                            onClick = { onDelete(); expanded = false },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFF44336)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerSourceFormDialog(
    source: PlayerSource?,
    onDismiss: () -> Unit,
    onSave: (PlayerSource) -> Unit
) {
    var name by remember { mutableStateOf(source?.name ?: "") }
    var priority by remember { mutableStateOf(source?.priority?.toString() ?: "1") }
    var language by remember { mutableStateOf(source?.language ?: "Dublado") }
    
    var movieTmdbUrl by remember { mutableStateOf(source?.movieTmdbUrl ?: "") }
    var movieImdbUrl by remember { mutableStateOf(source?.movieImdbUrl ?: "") }
    var tvTmdbUrl by remember { mutableStateOf(source?.tvTmdbUrl ?: "") }
    var tvImdbUrl by remember { mutableStateOf(source?.tvImdbUrl ?: "") }
    
    var internalPlayer by remember { mutableStateOf(source?.internalPlayer ?: "") }
    var playerColor by remember { mutableStateOf(source?.playerColor ?: "") }
    
    var enabled by remember { mutableStateOf(source?.enabled ?: true) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121212),
        title = {
            Text(if (source == null) "Adicionar Player" else "Editar Player", color = Color.White)
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick Preset Selection
                item {
                    Text("PREDEFINIÇÕES RÁPIDAS", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                name = "VidSrc"
                                priority = "2"
                                language = "Legendado"
                                movieTmdbUrl = "https://vidsrc.tw/embed/movie/{tmdb_id}"
                                tvTmdbUrl = "https://vidsrc.tw/embed/tv/{tmdb_id}/{season_number}/{episode_number}"
                                internalPlayer = ""
                                playerColor = ""
                                enabled = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFfb542b).copy(alpha = 0.5f))
                        ) {
                            Text("VidSrc (Leg)", fontSize = 11.sp, color = Color(0xFFfb542b))
                        }

                        OutlinedButton(
                            onClick = {
                                name = "MegaEmbed"
                                priority = "1"
                                language = "Dublado"
                                movieTmdbUrl = "https://mgeb.top/embed/{tmdb_id}"
                                tvTmdbUrl = "https://mgeb.top/embed/{tmdb_id}/{season_number}/{episode_number}"
                                internalPlayer = "megaplay"
                                playerColor = "#fb542b"
                                enabled = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Text("MegaEmbed (Dub)", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedBorderColor = Color(0xFFfb542b)
                        )
                    )
                }
                
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = priority,
                            onValueChange = { priority = it },
                            label = { Text("Prioridade") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedTextColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedBorderColor = Color(0xFFfb542b)
                            )
                        )
                        
                        var langExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = language,
                                onValueChange = { },
                                label = { Text("Idioma") },
                                readOnly = true,
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = Color.White) },
                                modifier = Modifier.fillMaxWidth().clickable { langExpanded = true },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedTextColor = Color.White,
                                    focusedTextColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                    focusedBorderColor = Color(0xFFfb542b)
                                )
                            )
                            DropdownMenu(
                                expanded = langExpanded,
                                onDismissRequest = { langExpanded = false },
                                modifier = Modifier.background(Color(0xFF1A1A1A))
                            ) {
                                listOf("Dublado", "Legendado", "Original").forEach {
                                    DropdownMenuItem(
                                        text = { Text(it, color = Color.White) },
                                        onClick = { 
                                            language = it
                                            langExpanded = false 
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item { Text("FILMES", color = Color(0xFFfb542b), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                
                item {
                    OutlinedTextField(
                        value = movieTmdbUrl,
                        onValueChange = { movieTmdbUrl = it },
                        label = { Text("URL TMDB") },
                        placeholder = { Text("https://.../{tmdb_id}") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedBorderColor = Color(0xFFfb542b)
                        )
                    )
                }

                item { Text("SÉRIES", color = Color(0xFFfb542b), fontSize = 11.sp, fontWeight = FontWeight.Bold) }

                item {
                    OutlinedTextField(
                        value = tvTmdbUrl,
                        onValueChange = { tvTmdbUrl = it },
                        label = { Text("URL TMDB") },
                        placeholder = { Text("https://.../{tmdb_id}/{season_number}/{episode_number}") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedBorderColor = Color(0xFFfb542b)
                        )
                    )
                }

                item { Text("AVANÇADO", color = Color(0xFFfb542b), fontSize = 11.sp, fontWeight = FontWeight.Bold) }

                item {
                    var playerTypeExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = internalPlayer,
                            onValueChange = { internalPlayer = it },
                            label = { Text("Player Interno (Mgeb)") },
                            placeholder = { Text("Ex: megaplay, vidstack") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = Color.White) },
                            modifier = Modifier.fillMaxWidth().clickable { playerTypeExpanded = true },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedTextColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedBorderColor = Color(0xFFfb542b)
                            )
                        )
                        DropdownMenu(
                            expanded = playerTypeExpanded,
                            onDismissRequest = { playerTypeExpanded = false },
                            modifier = Modifier.background(Color(0xFF1A1A1A))
                        ) {
                            listOf("megaplay", "megatube", "vidstack", "clappr").forEach {
                                DropdownMenuItem(
                                    text = { Text(it, color = Color.White) },
                                    onClick = { 
                                        internalPlayer = it
                                        playerTypeExpanded = false 
                                    }
                                )
                            }
                        }
                    }
                }
                
                item {
                    OutlinedTextField(
                        value = playerColor,
                        onValueChange = { playerColor = it },
                        label = { Text("Cor (Hex)") },
                        placeholder = { Text("#fb542b") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedBorderColor = Color(0xFFfb542b)
                        )
                    )
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { enabled = !enabled },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFFfb542b))
                        )
                        Text("Habilitado para o Aplicativo", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    val newSource = (source ?: PlayerSource()).copy(
                        id = if (source == null) UUID.randomUUID().toString() else source.id,
                        name = name,
                        priority = priority.toIntOrNull() ?: 1,
                        language = language,
                        movieTmdbUrl = movieTmdbUrl,
                        movieImdbUrl = movieImdbUrl,
                        tvTmdbUrl = tvTmdbUrl,
                        tvImdbUrl = tvImdbUrl,
                        internalPlayer = internalPlayer,
                        playerColor = playerColor,
                        enabled = enabled,
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(newSource)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFfb542b)),
                enabled = name.isNotBlank() && !isSaving
            ) {
                Text(if (isSaving) "SALVANDO..." else "SALVAR ALTERAÇÕES")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("CANCELAR", color = Color.White.copy(alpha = 0.5f))
            }
        }
    )
}

@Composable
fun PlayerTestCard(
    sources: List<PlayerSource>,
    defaultPlayerId: String? = null,
    playerConfig: com.example.data.remote.PlayerConfig? = null,
    selectedPlayerIdOverride: String? = null
) {
    var selectedSource by remember { mutableStateOf<PlayerSource?>(null) }
    var mediaType by remember { mutableStateOf("movie") }
    var tmdbId by remember { mutableStateOf("1439930") }
    var season by remember { mutableStateOf("1") }
    var episode by remember { mutableStateOf("1") }
    
    var testPlayerType by remember { mutableStateOf("megaplay") }
    var testColor by remember { mutableStateOf("#fb542b") }

    var generatedUrl by remember { mutableStateOf("") }
    var previewReloadCount by remember { mutableIntStateOf(0) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // React to external player selection for test
    LaunchedEffect(selectedPlayerIdOverride) {
        if (selectedPlayerIdOverride != null) {
            val matched = sources.find { it.id == selectedPlayerIdOverride }
            if (matched != null) {
                selectedSource = matched
            }
        }
    }

    // Auto-select primary player on first load if sources are available
    LaunchedEffect(sources, defaultPlayerId) {
        if (selectedSource == null && sources.isNotEmpty()) {
            selectedSource = sources.find { it.id == defaultPlayerId && it.enabled } 
                ?: sources.firstOrNull { it.enabled }
        }
    }

    LaunchedEffect(playerConfig) {
        if (playerConfig != null) {
            if (playerConfig.megaEmbed.player.isNotBlank()) {
                testPlayerType = playerConfig.megaEmbed.player
            }
            if (playerConfig.megaEmbed.color.isNotBlank()) {
                testColor = "#" + playerConfig.megaEmbed.color.removePrefix("#")
            }
        }
    }

    val isMegaEmbed = selectedSource?.let { src ->
        src.id.contains("mgeb", ignoreCase = true) ||
        src.name.contains("mega", ignoreCase = true) ||
        src.movieTmdbUrl.contains("mgeb.top")
    } ?: false

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("TESTAR GERADOR DE URL", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            
            var sourceExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                // OutlinedTextField styled as a Dropdown
                OutlinedTextField(
                    value = selectedSource?.name ?: "Selecionar Player",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false, // We use the Box overlay to capture clicks reliably on mobile
                    trailingIcon = { 
                        Icon(
                            if (sourceExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, 
                            null, 
                            tint = Color.White
                        ) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = if (selectedSource != null) Color.White else Color.White.copy(alpha = 0.5f),
                        disabledBorderColor = Color.White.copy(alpha = 0.1f),
                        disabledLabelColor = Color.White.copy(alpha = 0.5f),
                        disabledTrailingIconColor = Color.White
                    )
                )
                
                // Invisible click overlay to ensure touch events are captured correctly on Android
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { sourceExpanded = true }
                )

                DropdownMenu(
                    expanded = sourceExpanded,
                    onDismissRequest = { sourceExpanded = false },
                    modifier = Modifier.background(Color(0xFF1A1A1A)).widthIn(min = 200.dp)
                ) {
                    val activeSources = sources.filter { it.enabled }
                    if (activeSources.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Nenhum player ativo", color = Color.White.copy(alpha = 0.5f)) },
                            onClick = { sourceExpanded = false },
                            enabled = false
                        )
                    } else {
                        activeSources.forEach { source ->
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(source.name, color = Color.White)
                                        if (source.id == defaultPlayerId) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(Icons.Default.Star, "Principal", tint = Color(0xFFfb542b), modifier = Modifier.size(12.dp))
                                        }
                                    }
                                },
                                onClick = { 
                                    selectedSource = source
                                    sourceExpanded = false 
                                }
                            )
                        }
                    }
                }
            }

            // Se for MegaEmbed, permite selecionar o Player específico de teste
            if (isMegaEmbed) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Player de Teste (MegaEmbed):",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.example.data.remote.MegaEmbedPlayerType.ALL_PLAYERS.forEach { p ->
                            val isSelected = testPlayerType.equals(p.code, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { testPlayerType = p.code },
                                label = { Text(p.displayName.substringBefore(" "), fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFfb542b),
                                    selectedLabelColor = Color.White,
                                    labelColor = Color.White.copy(alpha = 0.7f),
                                    containerColor = Color(0xFF141414)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Campo Cor para o teste
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = testColor,
                        onValueChange = { testColor = it },
                        label = { Text("Cor do Player") },
                        placeholder = { Text("#fb542b") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedBorderColor = Color(0xFFfb542b)
                        )
                    )
                    val previewColor = try {
                        val clean = com.example.data.remote.MegaEmbedPlayerType.normalizeColor(testColor)
                        Color(android.graphics.Color.parseColor("#$clean"))
                    } catch (_: Exception) {
                        Color(0xFFfb542b)
                    }
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(previewColor)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("movie" to "Filme", "tv" to "Série").forEach { (id, label) ->
                    FilterChip(
                        selected = mediaType == id,
                        onClick = { mediaType = id },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFfb542b).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFFfb542b),
                            labelColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                }
            }

            OutlinedTextField(
                value = tmdbId,
                onValueChange = { tmdbId = it },
                label = { Text("TMDB ID") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedTextColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedBorderColor = Color(0xFFfb542b)
                )
            )

            if (mediaType == "tv") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = season,
                        onValueChange = { season = it },
                        label = { Text("Temp") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedBorderColor = Color(0xFFfb542b)
                        )
                    )
                    OutlinedTextField(
                        value = episode,
                        onValueChange = { episode = it },
                        label = { Text("Ep") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedBorderColor = Color(0xFFfb542b)
                        )
                    )
                }
            }

            Button(
                onClick = {
                    val src = selectedSource
                    if (src != null) {
                        android.util.Log.d("PLAYER_TEST", "--- INICIANDO TESTE ---")
                        android.util.Log.d("PLAYER_TEST", "Player: ${src.name} (ID: ${src.id})")
                        android.util.Log.d("PLAYER_TEST", "Tipo: $mediaType")
                        android.util.Log.d("PLAYER_TEST", "TMDB ID: $tmdbId")
                        if (mediaType == "tv") {
                            android.util.Log.d("PLAYER_TEST", "Temp: $season, Ep: $episode")
                        }

                        val dummyMedia = com.example.data.local.MediaEntity(
                            tmdbId = tmdbId.toIntOrNull() ?: 0,
                            mediaType = mediaType,
                            title = "Teste",
                            posterPath = null,
                            backdropPath = null,
                            overview = "",
                            releaseYear = "2024",
                            rating = 0.0,
                            genres = ""
                        )

                        // Clona fonte apenas para o teste com o player e cor selecionados no painel de teste
                        val effectiveSrc = if (isMegaEmbed) {
                            src.copy(
                                internalPlayer = testPlayerType,
                                playerColor = testColor
                            )
                        } else {
                            src
                        }

                        generatedUrl = PlayerUtils.buildPlayerUrl(
                            effectiveSrc, 
                            dummyMedia, 
                            season.toIntOrNull(), 
                            episode.toIntOrNull()
                        )
                        previewReloadCount++
                        android.util.Log.d("PLAYER_TEST", "URL Gerada: $generatedUrl")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFfb542b)),
                enabled = selectedSource != null && tmdbId.isNotBlank()
            ) {
                Text("GERAR URL")
            }

            if (generatedUrl.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("URL GERADA:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFfb542b))
                        Spacer(Modifier.height(4.dp))
                        Text(generatedUrl, color = Color.White, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { 
                                    clipboardManager.setText(AnnotatedString(generatedUrl))
                                    Toast.makeText(context, "✓ URL copiada", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("COPIAR", fontSize = 10.sp)
                            }
                            Button(
                                onClick = { 
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(generatedUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erro ao abrir player", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFfb542b).copy(alpha = 0.2f), contentColor = Color(0xFFfb542b)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("ABRIR TESTE", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SEÇÃO: PRÉ-VISUALIZAÇÃO DO PLAYER DE TESTE
            // ==========================================
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "PRÉ-VISUALIZAÇÃO",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 12.sp
                )
                if (generatedUrl.isNotBlank()) {
                    IconButton(
                        onClick = { previewReloadCount++ },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Recarregar pré-visualização",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (generatedUrl.isBlank()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayCircleOutline,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Selecione um player e gere uma URL para testar.",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    AdminPlayerTestPreview(
                        url = generatedUrl,
                        sources = sources,
                        reloadTrigger = previewReloadCount,
                        onReloadRequest = { previewReloadCount++ }
                    )
                }
            }
        }
    }
}

/**
 * Pré-visualização real e segura do player de teste dentro do painel administrativo.
 * Carrega a URL exatamente gerada em iframe responsivo com WebChromeClient e WebViewClient otimizados.
 */
@Composable
fun AdminPlayerTestPreview(
    url: String,
    sources: List<PlayerSource>,
    reloadTrigger: Int,
    onReloadRequest: () -> Unit
) {
    // 1. Validação de segurança: apenas HTTPS
    val isHttps = url.startsWith("https://", ignoreCase = true)

    // 2. Validação de hosts permitidos (configurados nos players ou hosts de embed reconhecidos)
    val isAllowedHost = remember(url, sources) {
        if (!isHttps) return@remember false
        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return@remember false
            val configuredHosts = sources.flatMap { src ->
                listOf(src.movieTmdbUrl, src.movieImdbUrl, src.tvTmdbUrl, src.tvImdbUrl).mapNotNull { template ->
                    try {
                        val clean = template.replace("{tmdb_id}", "1").replace("{season_number}", "1").replace("{episode_number}", "1")
                        Uri.parse(clean).host?.lowercase()
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            val standardEmbedHosts = listOf(
                "mgeb.top", "nhdapi.com", "vidsrc.tw", "vidsrc.to", "vidsrc.me", "vidsrc.xyz", 
                "vidsrc.cc", "vidsrc.pm", "vidsrc.net", "vidsrc.in", "vidsrc.pro",
                "superembed.stream", "embed.su", "player.nhdapi.com"
            )
            val allAllowed = (configuredHosts + standardEmbedHosts).distinct()
            allAllowed.any { allowed -> host == allowed || host.endsWith(".$allowed") }
        } catch (_: Exception) {
            false
        }
    }

    if (!isHttps) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text("URL insegura. Apenas conexões HTTPS são permitidas.", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        return
    }

    if (!isAllowedHost) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text("Domínio não configurado na lista de players.", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        return
    }

    var isLoading by remember(url, reloadTrigger) { mutableStateOf(true) }
    var hasError by remember(url, reloadTrigger) { mutableStateOf(false) }
    var forceSoftwareRenderer by remember(url, reloadTrigger) { mutableStateOf(false) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val isHttpUrl = remember(url) {
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    }

    // Timeout de carregamento razoável (18 segundos) para evitar carregamento infinito
    LaunchedEffect(url, reloadTrigger) {
        isLoading = true
        hasError = false
        delay(18000)
        if (isLoading) {
            isLoading = false
            hasError = true
        }
    }

    // HTML wrapper caso a URL seja um snippet de iframe
    val iframeHtml = remember(url) {
        val escapedUrl = url.replace("\"", "&quot;")
        """
        <!DOCTYPE html>
        <html lang="pt-BR">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                html, body {
                    width: 100%;
                    height: 100%;
                    background-color: #000000;
                    overflow: hidden;
                }
                iframe {
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
            <iframe
                src="$escapedUrl"
                width="100%"
                height="100%"
                frameborder="0"
                scrolling="no"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen"
                allowfullscreen="true"
                webkitallowfullscreen="true"
                mozallowfullscreen="true">
            </iframe>
        </body>
        </html>
        """.trimIndent()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!hasError) {
            // key garante que o WebView anterior é completamente destruído antes de instanciar o novo
            key(url, reloadTrigger) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.BLACK)

                            // Previne crashes de rendernode da Mesa e Chromium aw_browser_terminator
                            WebViewUtils.applySafeLayerType(this, forceSoftware = forceSoftwareRenderer)

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
                                allowContentAccess = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                builtInZoomControls = false
                                displayZoomControls = false
                                setSupportMultipleWindows(false)
                                javaScriptCanOpenWindowsAutomatically = false
                                cacheMode = WebSettings.LOAD_DEFAULT
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
                                    val reqUrl = request?.url?.toString()?.lowercase() ?: return null
                                    for (kw in blockedAdKeywords) {
                                        if (reqUrl.contains(kw)) {
                                            return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val targetUrl = request?.url?.toString() ?: return false
                                    if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                                        return true
                                    }
                                    val lower = targetUrl.lowercase()
                                    if (lower.contains("redirect") || lower.contains("popunder") || lower.contains("click") || lower.contains("bet")) {
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        isLoading = false
                                        hasError = true
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
                                        "ADMIN_PREVIEW",
                                        "Preview renderer terminated (didCrash=${detail?.didCrash()}). Recovering safely..."
                                    )
                                    WebViewUtils.safeDestroy(view)
                                    if (!forceSoftwareRenderer) {
                                        forceSoftwareRenderer = true
                                        onReloadRequest()
                                    } else {
                                        hasError = true
                                        isLoading = false
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
                                ): Boolean = false

                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    if (newProgress >= 80) {
                                        isLoading = false
                                    }
                                }

                                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                    super.onShowCustomView(view, callback)
                                    customView = view
                                    customViewCallback = callback
                                }

                                override fun onHideCustomView() {
                                    super.onHideCustomView()
                                    customView = null
                                    customViewCallback?.onCustomViewHidden()
                                    customViewCallback = null
                                }
                            }

                            if (isHttpUrl) {
                                loadUrl(url)
                            } else {
                                loadDataWithBaseURL(null, iframeHtml, "text/html", "UTF-8", null)
                            }
                        }
                    },
                    onRelease = { webView ->
                        WebViewUtils.safeDestroy(webView)
                    }
                )
            }
        }

        // Overlay de Loading discreto
        if (isLoading && !hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color(0xFFfb542b),
                        strokeWidth = 2.dp
                    )
                    Text(
                        "CARREGANDO PLAYER...",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Overlay de Erro
        if (hasError) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFF44336),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Não foi possível carregar este player.",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Verifique a URL ou tente outro player.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onReloadRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFfb542b)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tentar novamente", fontSize = 11.sp)
                }
            }
        }

        // Diálogo para reprodução em Tela Cheia acionada pelo iframe/player
        if (customView != null) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = {
                    customViewCallback?.onCustomViewHidden()
                    customView = null
                },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        factory = { ctx ->
                            android.widget.FrameLayout(ctx).apply {
                                (customView?.parent as? ViewGroup)?.removeView(customView)
                                addView(
                                    customView,
                                    ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = {
                            customViewCallback?.onCustomViewHidden()
                            customView = null
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar Tela Cheia", tint = Color.White)
                    }
                }
            }
        }
    }
}
