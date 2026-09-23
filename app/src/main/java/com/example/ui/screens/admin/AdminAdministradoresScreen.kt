package com.example.ui.screens.admin

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import com.example.data.remote.UserEntity
import com.example.data.remote.UserPermissions
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.draw.scale

import com.example.ui.theme.RatingYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAdministradoresScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val users by adminViewModel.allUsers.collectAsState()
    val playerConfig by adminViewModel.playerConfig.collectAsState()
    
    val accentColor = remember(playerConfig) {
        try {
            Color(android.graphics.Color.parseColor("#" + playerConfig.megaEmbed.color))
        } catch (e: Exception) {
            BrandRed
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var showAddAdminDialog by remember { mutableStateOf(false) }
    var userToChangeRole by remember { mutableStateOf<Pair<UserEntity, String>?>(null) }
    var selectedAdminForPermissions by remember { mutableStateOf<UserEntity?>(null) }
    var selectedAdminForDetails by remember { mutableStateOf<UserEntity?>(null) }

    // Admin users only
    val adminList = remember(users) {
        users.filter { it.role.equals("ADMIN", ignoreCase = true) || it.role.equals("FOUNDER", ignoreCase = true) }
    }

    val regularUsers = remember(users) {
        users.filter { !it.role.equals("ADMIN", ignoreCase = true) && !it.role.equals("FOUNDER", ignoreCase = true) }
    }

    val currentUser by adminViewModel.currentUser.collectAsState()
    val canManageAdmins = currentUser?.hasPermission("manageAdmins") == true
    val canManagePermissions = currentUser?.hasPermission("managePermissions") == true

    val filteredAdmins = remember(adminList, searchQuery) {
        if (searchQuery.isBlank()) adminList
        else {
            val q = searchQuery.trim().lowercase()
            adminList.filter { it.email.lowercase().contains(q) || it.displayName.lowercase().contains(q) }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
            .testTag("admin_administradores_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // --- 1. HEADER COMPACTO ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, null, tint = accentColor, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text(
                            text = "ADMINISTRADORES",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Gestão de papéis e permissões",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = { if (canManageAdmins) showAddAdminDialog = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (canManageAdmins) accentColor else Color.Gray.copy(alpha = 0.2f))
                        .testTag("btn_adicionar_administrador"),
                    enabled = canManageAdmins
                ) {
                    Icon(
                        if (canManageAdmins) Icons.Default.Add else Icons.Default.Lock,
                        contentDescription = "Adicionar",
                        tint = if (canManageAdmins) Color.White else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // --- 2. ESTATÍSTICAS COMPACTAS ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactMetricCard(
                    label = "Administr.",
                    value = adminList.size.toString(),
                    icon = Icons.Default.Person,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                )
                CompactMetricCard(
                    label = "Founders",
                    value = adminList.count { it.role.equals("FOUNDER", ignoreCase = true) }.toString(),
                    icon = Icons.Default.Shield,
                    accentColor = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
                CompactMetricCard(
                    label = "Usuários",
                    value = regularUsers.size.toString(),
                    icon = Icons.Default.People,
                    accentColor = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- 3. BUSCA COMPACTA ---
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar por nome ou e-mail...", color = Color.Gray, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = accentColor.copy(alpha = 0.5f),
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("admin_administradores_search_input"),
                singleLine = true
            )
        }

        // --- 4. LISTA DE ADMINISTRADORES ---
        if (filteredAdmins.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.PersonSearch, null, tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Nenhum resultado para \"$searchQuery\"" else "Nenhum administrador encontrado.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(filteredAdmins, key = { it.uid.ifBlank { it.email } }) { admin ->
                AdminCompactUserCard(
                    user = admin,
                    accentColor = accentColor,
                    canManageAdmins = canManageAdmins,
                    canManagePermissions = canManagePermissions,
                    onRevokeAdmin = { userToChangeRole = Pair(admin, "USER") },
                    onManagePermissions = { selectedAdminForPermissions = admin },
                    onShowDetails = { selectedAdminForDetails = admin }
                )
            }
        }
    }

    // --- MODAIS ---

    // Gerenciar Permissões
    selectedAdminForPermissions?.let { admin ->
        ManagePermissionsDialog(
            admin = admin,
            accentColor = accentColor,
            onDismiss = { selectedAdminForPermissions = null },
            onSave = { newPermissions ->
                adminViewModel.updateUserPermissions(admin.uid, newPermissions)
                selectedAdminForPermissions = null
            }
        )
    }

    // Detalhes do Administrador
    selectedAdminForDetails?.let { admin ->
        AdminDetailsCompactDialog(
            admin = admin,
            accentColor = accentColor,
            onDismiss = { selectedAdminForDetails = null }
        )
    }

    // Confirmação de Remoção
    userToChangeRole?.let { (user, newRole) ->
        ConfirmActionDialog(
            title = "Remover administrador?",
            message = "O usuário ${user.email} perderá o acesso administrativo.",
            confirmLabel = "Remover",
            accentColor = Color(0xFFEF4444),
            onConfirm = {
                adminViewModel.setUserRole(user.uid, newRole)
                userToChangeRole = null
            },
            onDismiss = { userToChangeRole = null }
        )
    }

    // Adicionar Administrador (Promover)
    if (showAddAdminDialog) {
        PromoteUserCompactDialog(
            regularUsers = regularUsers,
            accentColor = accentColor,
            onDismiss = { showAddAdminDialog = false },
            onSelectUser = { user ->
                userToChangeRole = Pair(user, "ADMIN")
                showAddAdminDialog = false
            }
        )
    }
}

@Composable
private fun CompactMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(72.dp),
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
            Column {
                Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(label, color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun AdminCompactUserCard(
    user: UserEntity,
    accentColor: Color,
    canManageAdmins: Boolean,
    canManagePermissions: Boolean,
    onRevokeAdmin: () -> Unit,
    onManagePermissions: () -> Unit,
    onShowDetails: () -> Unit
) {
    val isFounder = user.role.equals("FOUNDER", ignoreCase = true)
    val roleColor = if (isFounder) Color(0xFFF59E0B) else accentColor
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar Placeholder
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(roleColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isFounder) Icons.Default.Shield else Icons.Default.AdminPanelSettings,
                    null,
                    tint = roleColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        user.displayName.ifBlank { "Administrador" },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Small Badge
                    Surface(
                        color = roleColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.5.dp, roleColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            user.role.uppercase(),
                            color = roleColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(user.email, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                
                val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
                Text(
                    "Cadastrado em ${if (user.createdAt > 0) sdf.format(Date(user.createdAt)) else "N/A"}",
                    color = Color.DarkGray,
                    fontSize = 10.sp
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(DarkSurface).border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Ver Detalhes", fontSize = 14.sp) },
                        onClick = {
                            showMenu = false
                            onShowDetails()
                        },
                        leadingIcon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp)) }
                    )
                    
                    if (!isFounder) {
                        DropdownMenuItem(
                            text = { Text("Permissões", fontSize = 14.sp) },
                            onClick = {
                                if (canManagePermissions) {
                                    showMenu = false
                                    onManagePermissions()
                                }
                            },
                            leadingIcon = { 
                                Icon(
                                    if (canManagePermissions) Icons.Default.VpnKey else Icons.Default.Lock, 
                                    null, 
                                    modifier = Modifier.size(18.dp),
                                    tint = if (canManagePermissions) Color.Unspecified else Color.Gray
                                ) 
                            },
                            enabled = canManagePermissions
                        )
                        DropdownMenuItem(
                            text = { Text("Remover", color = if (canManageAdmins) Color(0xFFEF4444) else Color.Gray, fontSize = 14.sp) },
                            onClick = {
                                if (canManageAdmins) {
                                    showMenu = false
                                    onRevokeAdmin()
                                }
                            },
                            leadingIcon = { 
                                Icon(
                                    if (canManageAdmins) Icons.Default.PersonRemove else Icons.Default.Lock, 
                                    null, 
                                    tint = if (canManageAdmins) Color(0xFFEF4444) else Color.Gray, 
                                    modifier = Modifier.size(18.dp)
                                ) 
                            },
                            enabled = canManageAdmins
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Acesso Founder", fontSize = 14.sp) },
                            onClick = { showMenu = false },
                            leadingIcon = { Icon(Icons.Default.VerifiedUser, null, modifier = Modifier.size(18.dp)) },
                            enabled = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagePermissionsDialog(
    admin: UserEntity,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSave: (UserPermissions) -> Unit
) {
    var permissions by remember { mutableStateOf(admin.permissions) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            color = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("PERMISSÕES", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text(admin.displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color.Gray)
                    }
                }

                Divider(color = CardBorder)

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PermissionSection("ACESSO", accentColor) {
                        PermissionSwitch("Dashboard", permissions.dashboard) { permissions = permissions.copy(dashboard = it) }
                        PermissionSwitch("Administradores", permissions.administrators) { permissions = permissions.copy(administrators = it) }
                        PermissionSwitch("Usuários", permissions.users) { permissions = permissions.copy(users = it) }
                    }

                    PermissionSection("CATÁLOGO", accentColor) {
                        PermissionSwitch("Catálogo Completo", permissions.catalog) { permissions = permissions.copy(catalog = it) }
                        PermissionSwitch("Importar Filme", permissions.importFilme) { permissions = permissions.copy(importFilme = it) }
                        PermissionSwitch("Importar Série", permissions.importSerie) { permissions = permissions.copy(importSerie = it) }
                        PermissionSwitch("Importação em Massa", permissions.importacaoMassa) { permissions = permissions.copy(importacaoMassa = it) }
                        PermissionSwitch("TOP 10 Hoje", permissions.top10) { permissions = permissions.copy(top10 = it) }
                        PermissionSwitch("Destaques Home", permissions.destaques) { permissions = permissions.copy(destaques = it) }
                        PermissionSwitch("TV ao Vivo", permissions.tvAoVivo) { permissions = permissions.copy(tvAoVivo = it) }
                        PermissionSwitch("Pedidos TMDB", permissions.pedidosTmdb) { permissions = permissions.copy(pedidosTmdb = it) }
                        PermissionSwitch("Sincronizar Catálogo", permissions.sincronizarCatalogo) { permissions = permissions.copy(sincronizarCatalogo = it) }
                    }

                    PermissionSection("REPRODUÇÃO", accentColor) {
                        PermissionSwitch("Players", permissions.players) { permissions = permissions.copy(players = it) }
                        PermissionSwitch("Configurar Players", permissions.configurarPlayers) { permissions = permissions.copy(configurarPlayers = it) }
                    }

                    PermissionSection("SISTEMA", accentColor) {
                        PermissionSwitch("Dispositivos", permissions.dispositivos) { permissions = permissions.copy(dispositivos = it) }
                        PermissionSwitch("Atualizações", permissions.atualizacoes) { permissions = permissions.copy(atualizacoes = it) }
                        PermissionSwitch("Notificações", permissions.notificacoes) { permissions = permissions.copy(notificacoes = it) }
                        PermissionSwitch("Configurações", permissions.configuracoes) { permissions = permissions.copy(configuracoes = it) }
                    }

                    PermissionSection("ADMINISTRAÇÃO", Color(0xFFEF4444)) {
                        PermissionSwitch("Gerenciar Admins", permissions.manageAdmins, Color(0xFFEF4444)) { permissions = permissions.copy(manageAdmins = it) }
                        PermissionSwitch("Gerenciar Permissões", permissions.managePermissions, Color(0xFFEF4444)) { permissions = permissions.copy(managePermissions = it) }
                        PermissionSwitch("Excluir Conteúdo", permissions.deleteContent, Color(0xFFEF4444)) { permissions = permissions.copy(deleteContent = it) }
                        PermissionSwitch("Excluir Usuários", permissions.deleteUsers, Color(0xFFEF4444)) { permissions = permissions.copy(deleteUsers = it) }
                    }
                }

                Divider(color = CardBorder)

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancelar", color = Color.Gray)
                    }
                    Button(
                        onClick = { onSave(permissions) },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Salvar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionSection(title: String, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = color.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkBackground.copy(alpha = 0.5f))
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

@Composable
private fun PermissionSwitch(label: String, checked: Boolean, activeColor: Color = BrandRed, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.7f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = activeColor,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = DarkSurface
            )
        )
    }
}

@Composable
private fun PermissionSectionSub(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    PermissionSwitch(label, checked, onCheckedChange = onCheckedChange)
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    accentColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        title = { Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = Color.Gray, fontSize = 14.sp) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(confirmLabel, color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.Gray)
            }
        }
    )
}

@Composable
private fun AdminDetailsCompactDialog(
    admin: UserEntity,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.AccountCircle, null, tint = accentColor, modifier = Modifier.size(24.dp))
                    Text("DETALHES", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailCompactRow("Nome", admin.displayName.ifBlank { "N/A" })
                    DetailCompactRow("E-mail", admin.email)
                    DetailCompactRow("Papel", admin.role.uppercase())
                    DetailCompactRow("UID", admin.uid)
                    DetailCompactRow("Criado em", if (admin.createdAt > 0) sdf.format(Date(admin.createdAt)) else "N/A")
                    DetailCompactRow("Último login", if (admin.lastLoginAt > 0) sdf.format(Date(admin.lastLoginAt)) else "N/A")
                }
                
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("FECHAR", color = accentColor)
                }
            }
        }
    }
}

@Composable
private fun DetailCompactRow(label: String, value: String) {
    Column {
        Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PromoteUserCompactDialog(
    regularUsers: List<UserEntity>,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSelectUser: (UserEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(regularUsers, query) {
        if (query.isBlank()) regularUsers
        else {
            val q = query.trim().lowercase()
            regularUsers.filter { it.email.lowercase().contains(q) || it.displayName.lowercase().contains(q) }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp),
            color = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("ADICIONAR ADMIN", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar usuário...", color = Color.Gray, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground,
                        focusedBorderColor = accentColor.copy(alpha = 0.5f),
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Nenhum usuário encontrado.", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered) { user ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onSelectUser(user) },
                                color = DarkBackground,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.DarkGray.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(user.displayName.ifBlank { "Sem nome" }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(user.email, color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.AutoMirrored.Default.ArrowForward, null, tint = accentColor, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        }
    }
}
