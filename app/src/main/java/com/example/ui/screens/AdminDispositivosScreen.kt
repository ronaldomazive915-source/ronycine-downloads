package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.DeviceEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AdminViewModel
import kotlinx.coroutines.delay

@Composable
fun AdminDispositivosScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val devices by adminViewModel.allDevices.collectAsState()
    
    // Ticker para atualizar periodicamente o cálculo de isOnline em tempo real
    var ticker by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000L) // Atualiza o relógio a cada 5s para refletir online/offline dinamicamente
            ticker = System.currentTimeMillis()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("TODOS") } // "TODOS", "ONLINE", "OFFLINE", "BLOQUEADOS"

    var selectedDevice by remember { mutableStateOf<DeviceEntity?>(null) }
    var deviceToBlock by remember { mutableStateOf<DeviceEntity?>(null) }
    var deviceToUnblock by remember { mutableStateOf<DeviceEntity?>(null) }
    var deviceToRemove by remember { mutableStateOf<DeviceEntity?>(null) }

    // Métricas reais baseadas no estado da lista
    val onlineDevices = devices.count { it.isOnline }
    val offlineDevices = devices.size - onlineDevices
    val blockedDevices = devices.count { it.isBlocked }
    val activeLast24h = devices.count { 
        (System.currentTimeMillis() - it.effectiveLastSeen) < 24 * 60 * 60 * 1000L 
    }

    // Filtragem de dispositivos
    val filteredDevices = remember(devices, searchQuery, selectedFilter, ticker) {
        devices.filter { device ->
            val matchesQuery = searchQuery.isBlank() ||
                    device.name.contains(searchQuery, ignoreCase = true) ||
                    device.deviceId.contains(searchQuery, ignoreCase = true) ||
                    device.platform.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "ONLINE" -> device.isOnline
                "OFFLINE" -> !device.isOnline && !device.isBlocked
                "BLOQUEADOS" -> device.isBlocked
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .testTag("admin_dispositivos_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Cabeçalho com Título e Botão de Atualizar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📱 GERENCIAMENTO DE DISPOSITIVOS",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Registro automático e monitoramento em tempo real",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = {
                        adminViewModel.refreshDevices()
                        Toast.makeText(context, "Atualizando lista de dispositivos...", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = Color.White)
                }
            }
        }

        // 2. Cards de Métricas em Tempo Real
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardCard(
                    title = "TOTAL",
                    value = "${devices.size}",
                    icon = "📱",
                    modifier = Modifier.weight(1f)
                )
                DashboardCard(
                    title = "ONLINE",
                    value = "$onlineDevices",
                    icon = "🟢",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardCard(
                    title = "OFFLINE",
                    value = "$offlineDevices",
                    icon = "⚪",
                    modifier = Modifier.weight(1f)
                )
                DashboardCard(
                    title = "ÚLTIMAS 24H",
                    value = "$activeLast24h",
                    icon = "📊",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Barra de Pesquisa
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("device_search_input"),
                placeholder = { Text("Buscar por modelo ou ID do dispositivo...", color = Color.Gray, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar", tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandRed,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // 4. Chips de Filtro
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf(
                    "TODOS" to "Todos (${devices.size})",
                    "ONLINE" to "🟢 Online ($onlineDevices)",
                    "OFFLINE" to "⚪ Offline ($offlineDevices)",
                    "BLOQUEADOS" to "🔒 Bloqueados ($blockedDevices)"
                )
                items(filters) { (key, label) ->
                    val isSelected = selectedFilter == key
                    Surface(
                        color = if (isSelected) BrandRed else DarkSurface,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (isSelected) BrandRed else CardBorder),
                        modifier = Modifier.clickable { selectedFilter = key }
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // 5. Lista de Dispositivos Conectados
        if (filteredDevices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (devices.isEmpty()) "Nenhum dispositivo registrado ainda" else "Nenhum dispositivo encontrado para este filtro",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (devices.isEmpty()) "Quando qualquer usuário abrir o aplicativo, seu dispositivo será registrado e aparecerá aqui em tempo real." else "Tente buscar com outro termo ou alterar o filtro.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredDevices, key = { it.deviceId }) { device ->
                DeviceRow(
                    device = device,
                    onClick = { selectedDevice = device },
                    onBlock = { deviceToBlock = device },
                    onUnblock = { deviceToUnblock = device },
                    onRemove = { deviceToRemove = device },
                    onToggleAdmin = { newAdminState ->
                        adminViewModel.setDeviceAdminAccess(device.deviceId, newAdminState)
                        val msg = if (newAdminState) "Acesso de Admin concedido ao dispositivo!" else "Acesso de Admin revogado!"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    onCopyId = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Device ID", device.deviceId)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "ID do dispositivo copiado!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // Diálogos de Detalhes e Ações
    if (selectedDevice != null) {
        DeviceDetailsDialog(
            device = selectedDevice!!,
            onDismiss = { selectedDevice = null },
            onToggleAdmin = { newAdminState ->
                adminViewModel.setDeviceAdminAccess(selectedDevice!!.deviceId, newAdminState)
                selectedDevice = selectedDevice!!.copy(adminAccess = newAdminState)
                val msg = if (newAdminState) "Acesso de Admin concedido!" else "Acesso de Admin revogado!"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
            onCopyId = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Device ID", selectedDevice!!.deviceId)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "ID do dispositivo copiado!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (deviceToBlock != null) {
        AlertDialog(
            onDismissRequest = { deviceToBlock = null },
            title = { Text("Bloquear Dispositivo", color = Color.White) },
            text = { Text("Tem certeza que deseja bloquear o dispositivo '${deviceToBlock?.name}'? Ele perderá acesso ao aplicativo.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.setDeviceBlockedStatus(deviceToBlock!!.deviceId, true)
                        Toast.makeText(context, "Dispositivo bloqueado!", Toast.LENGTH_SHORT).show()
                        deviceToBlock = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) { Text("Bloquear") }
            },
            dismissButton = {
                TextButton(onClick = { deviceToBlock = null }) { Text("Cancelar", color = Color.Gray) }
            },
            containerColor = DarkSurface
        )
    }

    if (deviceToUnblock != null) {
        AlertDialog(
            onDismissRequest = { deviceToUnblock = null },
            title = { Text("Desbloquear Dispositivo", color = Color.White) },
            text = { Text("Tem certeza que deseja desbloquear o dispositivo '${deviceToUnblock?.name}'?", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.setDeviceBlockedStatus(deviceToUnblock!!.deviceId, false)
                        Toast.makeText(context, "Dispositivo desbloqueado!", Toast.LENGTH_SHORT).show()
                        deviceToUnblock = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) { Text("Desbloquear") }
            },
            dismissButton = {
                TextButton(onClick = { deviceToUnblock = null }) { Text("Cancelar", color = Color.Gray) }
            },
            containerColor = DarkSurface
        )
    }

    if (deviceToRemove != null) {
        AlertDialog(
            onDismissRequest = { deviceToRemove = null },
            title = { Text("Remover Dispositivo", color = Color.White) },
            text = { Text("Tem certeza que deseja remover o dispositivo '${deviceToRemove?.name}' do sistema? Ele será registrado novamente caso abra o aplicativo.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.removeDevice(deviceToRemove!!.deviceId)
                        Toast.makeText(context, "Dispositivo removido!", Toast.LENGTH_SHORT).show()
                        deviceToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { deviceToRemove = null }) { Text("Cancelar", color = Color.Gray) }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun DashboardCard(title: String, value: String, icon: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(title, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DeviceRow(
    device: DeviceEntity,
    onClick: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onRemove: () -> Unit,
    onToggleAdmin: (Boolean) -> Unit = {},
    onCopyId: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicador visual de Status
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                device.isBlocked -> BrandRed
                                device.isOnline -> Color(0xFF34D399) // Verde vivo
                                else -> Color.Gray
                            }
                        )
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.name.ifBlank { "Dispositivo Android" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (device.isAdmin) {
                            Surface(
                                color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "👑 ADMIN",
                                    color = Color(0xFFA78BFA),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Surface(
                            color = when {
                                device.isBlocked -> BrandRed.copy(alpha = 0.2f)
                                device.isOnline -> Color(0xFF065F46)
                                else -> Color(0xFF374151)
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = when {
                                    device.isBlocked -> "🔒 BLOQUEADO"
                                    device.isOnline -> "🟢 ONLINE"
                                    else -> "⚪ OFFLINE"
                                },
                                color = when {
                                    device.isBlocked -> BrandRed
                                    device.isOnline -> Color(0xFF34D399)
                                    else -> Color.LightGray
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ID: ${device.maskedDeviceId} • ${device.platform} (v${device.appVersion})",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Última atividade: ${device.lastActivityFormatted}",
                        color = if (device.isOnline) Color(0xFF34D399) else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (device.isOnline) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }

            Box {
                IconButton(onClick = { expandedMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opções", tint = Color.White)
                }
                DropdownMenu(
                    expanded = expandedMenu,
                    onDismissRequest = { expandedMenu = false },
                    modifier = Modifier.background(DarkSurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Ver detalhes", color = Color.White) },
                        onClick = { expandedMenu = false; onClick() },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White) }
                    )
                    DropdownMenuItem(
                        text = { Text("Copiar Device ID", color = Color.White) },
                        onClick = { expandedMenu = false; onCopyId() },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (device.isAdmin) "Revogar Acesso Admin" else "Conceder Acesso Admin", color = Color(0xFFA78BFA)) },
                        onClick = { expandedMenu = false; onToggleAdmin(!device.isAdmin) },
                        leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color(0xFFA78BFA)) }
                    )
                    if (device.isBlocked) {
                        DropdownMenuItem(
                            text = { Text("Desbloquear", color = Color.White) },
                            onClick = { expandedMenu = false; onUnblock() },
                            leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White) }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Bloquear", color = BrandRed) },
                            onClick = { expandedMenu = false; onBlock() },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = BrandRed) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remover", color = BrandRed) },
                        onClick = { expandedMenu = false; onRemove() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = BrandRed) }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceDetailsDialog(
    device: DeviceEntity,
    onDismiss: () -> Unit,
    onToggleAdmin: (Boolean) -> Unit = {},
    onCopyId: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Smartphone, contentDescription = null, tint = BrandRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Detalhes do Dispositivo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow(
                    "Status",
                    when {
                        device.isBlocked -> "🔒 Bloqueado"
                        device.isOnline -> "🟢 Online (Ativo agora)"
                        else -> "⚪ Offline"
                    }
                )
                DetailRow(
                    "Painel Administrativo",
                    if (device.isAdmin) "👑 Autorizado (adminAccess = true)" else "❌ Não Autorizado (adminAccess = false)"
                )
                DetailRowWithAction("Device ID", device.deviceId, onCopy = onCopyId)
                DetailRow("Dispositivo / Modelo", device.name)
                DetailRow("Plataforma", device.platform)
                DetailRow(
                    "Versão do App",
                    "${device.appVersion} (${device.buildNumber})"
                )
                DetailRow(
                    "Estado do Push/FCM",
                    if (device.fcmToken.isNotBlank()) "✅ Ativo (${device.fcmStatus})" else "❌ Inativo/Indisponível"
                )
                DetailRow(
                    "Permissão de Notificação",
                    if (device.notificationsPermission == "CONCEDIDA") "✅ Concedida" else "❌ Negada/Inativo"
                )
                DetailRow("Primeira Conexão", device.firstConnectionFormatted)
                DetailRow("Última Atividade (Heartbeat)", device.lastActivityFormatted)

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onToggleAdmin(!device.isAdmin) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFA78BFA))
                ) {
                    Icon(
                        Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (device.isAdmin) "Revogar Acesso Admin" else "Conceder Acesso Admin",
                        color = Color(0xFFA78BFA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
            ) {
                Text("Fechar")
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Column {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DetailRowWithAction(label: String, value: String, onCopy: () -> Unit) {
    Column {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }
    }
}
