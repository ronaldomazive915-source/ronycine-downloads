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
import com.example.data.remote.UserEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAdministradoresScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val users by adminViewModel.allUsers.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddAdminDialog by remember { mutableStateOf(false) }
    var userToChangeRole by remember { mutableStateOf<Pair<UserEntity, String>?>(null) }
    var selectedAdminForDetails by remember { mutableStateOf<UserEntity?>(null) }

    // Admin users only
    val adminList = remember(users) {
        users.filter { it.role.equals("ADMIN", ignoreCase = true) || it.role.equals("FOUNDER", ignoreCase = true) }
    }

    val regularUsers = remember(users) {
        users.filter { !it.role.equals("ADMIN", ignoreCase = true) && !it.role.equals("FOUNDER", ignoreCase = true) }
    }

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
            .padding(16.dp)
            .testTag("admin_administradores_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. HEADER ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ADMINISTRADORES",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Gestão de papéis administrativos, fundadores e permissões",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = { showAddAdminDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_adicionar_administrador")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Promover Admin", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Summary Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        title = "TOTAL ADMINS",
                        value = adminList.size.toString(),
                        icon = Icons.Default.AdminPanelSettings,
                        iconTint = BrandRed,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "FOUNDERS",
                        value = adminList.count { it.role.equals("FOUNDER", ignoreCase = true) }.toString(),
                        icon = Icons.Default.Shield,
                        iconTint = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "USUÁRIOS COMUNS",
                        value = regularUsers.size.toString(),
                        icon = Icons.Default.People,
                        iconTint = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar administrador por nome ou e-mail...", color = Color.Gray, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar", tint = Color.Gray)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_administradores_search_input")
                )
            }
        }

        // --- 2. LISTA DE ADMINISTRADORES ---
        if (filteredAdmins.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "Nenhum administrador encontrado para '$searchQuery'" else "Nenhum administrador cadastrado.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(filteredAdmins, key = { it.uid.ifBlank { it.email } }) { admin ->
                AdminUserRowCard(
                    user = admin,
                    onClick = { selectedAdminForDetails = admin },
                    onRevokeAdmin = { userToChangeRole = Pair(admin, "USER") }
                )
            }
        }
    }

    // Modal: Detalhes do Administrador
    selectedAdminForDetails?.let { admin ->
        AdminDetailsDialog(
            admin = admin,
            onDismiss = { selectedAdminForDetails = null },
            onRevokeAdmin = {
                userToChangeRole = Pair(admin, "USER")
                selectedAdminForDetails = null
            }
        )
    }

    // Diálogo de confirmação de alteração de papel
    userToChangeRole?.let { (user, newRole) ->
        AlertDialog(
            onDismissRequest = { userToChangeRole = null },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = if (newRole == "USER") "Revogar privilégios de Admin?" else "Promover para Administrador?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = if (newRole == "USER")
                        "O usuário ${user.email} perderá o acesso ao Painel Administrativo e voltará a ser um usuário padrão."
                    else
                        "O usuário ${user.email} terá acesso administrativo ao Painel do RONYCINE.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.setUserRole(user.uid, newRole)
                        userToChangeRole = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (newRole == "USER") Color(0xFFDC2626) else BrandRed)
                ) {
                    Text(if (newRole == "USER") "Revogar Acesso" else "Confirmar Promoção", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToChangeRole = null }) {
                    Text("Cancelar", color = Color.Gray)
                }
            }
        )
    }

    // Modal: Promover Usuário para Administrador
    if (showAddAdminDialog) {
        PromoteUserDialog(
            regularUsers = regularUsers,
            onDismiss = { showAddAdminDialog = false },
            onSelectUser = { user ->
                userToChangeRole = Pair(user, "ADMIN")
                showAddAdminDialog = false
            }
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(title, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AdminUserRowCard(
    user: UserEntity,
    onClick: () -> Unit,
    onRevokeAdmin: () -> Unit
) {
    val isFounder = user.role.equals("FOUNDER", ignoreCase = true)
    val roleColor = if (isFounder) Color(0xFFF59E0B) else BrandRed

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("admin_card_${user.uid}"),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(roleColor.copy(alpha = 0.15f))
                    .border(1.dp, roleColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFounder) Icons.Default.Shield else Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = roleColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = user.displayName.ifBlank { "Administrador" },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        color = roleColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, roleColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (isFounder) "FOUNDER" else "ADMIN",
                            color = roleColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = user.email.ifBlank { "Sem e-mail" },
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
                val createdStr = remember(user.createdAt) {
                    if (user.createdAt > 0) sdf.format(Date(user.createdAt)) else "Data não registrada"
                }
                Text(
                    text = "Cadastrado em: $createdStr",
                    color = Color(0xFF757575),
                    fontSize = 10.sp
                )
            }

            if (!isFounder) {
                IconButton(
                    onClick = onRevokeAdmin,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF221111))
                        .testTag("btn_revoke_${user.uid}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonRemove,
                        contentDescription = "Revogar papel de administrador",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminDetailsDialog(
    admin: UserEntity,
    onDismiss: () -> Unit,
    onRevokeAdmin: () -> Unit
) {
    val isFounder = admin.role.equals("FOUNDER", ignoreCase = true)
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = BrandRed)
                Text("Detalhes do Administrador", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow("Nome:", admin.displayName.ifBlank { "Não informado" })
                DetailRow("E-mail:", admin.email)
                DetailRow("Papel:", admin.role.uppercase())
                DetailRow("Status de Acesso:", admin.accessStatus.uppercase())
                DetailRow("UID:", admin.uid)
                DetailRow("Cadastro:", if (admin.createdAt > 0) sdf.format(Date(admin.createdAt)) else "N/A")
                DetailRow("Último Acesso:", if (admin.lastLoginAt > 0) sdf.format(Date(admin.lastLoginAt)) else "N/A")
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isFounder) {
                    Button(
                        onClick = onRevokeAdmin,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Text("Revogar Admin", color = Color.White, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Fechar", color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }
    )
}

@Composable
private fun PromoteUserDialog(
    regularUsers: List<UserEntity>,
    onDismiss: () -> Unit,
    onSelectUser: (UserEntity) -> Unit
) {
    var searchUserQuery by remember { mutableStateOf("") }
    val filtered = remember(regularUsers, searchUserQuery) {
        if (searchUserQuery.isBlank()) regularUsers
        else {
            val q = searchUserQuery.trim().lowercase()
            regularUsers.filter { it.email.lowercase().contains(q) || it.displayName.lowercase().contains(q) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text("Promover Usuário para Administrador", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchUserQuery,
                    onValueChange = { searchUserQuery = it },
                    placeholder = { Text("Filtrar por nome ou e-mail...", color = Color.Gray, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (filtered.isEmpty()) {
                    Text("Nenhum usuário disponível para promoção.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered) { user ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectUser(user) },
                                color = DarkBackground,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(user.displayName.ifBlank { "Sem nome" }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(user.email, color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = BrandRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.Gray)
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
