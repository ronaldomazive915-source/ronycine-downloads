package com.example.ui.screens.admin

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
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
import com.example.ui.components.VerifiedBadge
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsuariosScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val users by adminViewModel.allUsers.collectAsState()
    val allProfiles by adminViewModel.allGlobalProfiles.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("TODOS") } // "TODOS", "ATIVO", "SUSPENSO", "ADMIN"
    var selectedUserForDetails by remember { mutableStateOf<UserEntity?>(null) }
    var userToChangeStatus by remember { mutableStateOf<Pair<UserEntity, String>?>(null) } // user to target status
    var verifyingUserId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Metrics calculation
    val totalUsers = users.size
    val activeUsers = remember(users) { users.count { it.accessStatus.equals("ACTIVE", ignoreCase = true) } }
    val suspendedUsers = remember(users) { users.count { !it.accessStatus.equals("ACTIVE", ignoreCase = true) } }
    val adminUsers = remember(users) { users.count { it.role == "ADMIN" || it.role == "FOUNDER" } }

    val filteredUsers = remember(users, searchQuery, selectedFilter) {
        users.filter { user ->
            val matchesFilter = when (selectedFilter) {
                "ATIVO" -> user.accessStatus.equals("ACTIVE", ignoreCase = true)
                "SUSPENSO" -> !user.accessStatus.equals("ACTIVE", ignoreCase = true)
                "ADMIN" -> user.role == "ADMIN" || user.role == "FOUNDER"
                else -> true
            }
            if (!matchesFilter) return@filter false

            if (searchQuery.isBlank()) return@filter true
            val q = searchQuery.trim().lowercase()
            user.email.lowercase().contains(q) ||
                    user.displayName.lowercase().contains(q) ||
                    user.uid.lowercase().contains(q) ||
                    user.role.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .testTag("admin_usuarios_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. HEADER E MÉTRICAS ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "GESTÃO DE USUÁRIOS",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    "Controle de contas cadastradas, status de acesso e perfis vinculados",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                // Cards de métricas compactos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricChip("TOTAL", totalUsers.toString(), Color.White, Modifier.weight(1f))
                    MetricChip("ATIVOS", activeUsers.toString(), Color(0xFF10B981), Modifier.weight(1f))
                    MetricChip("SUSPENSOS", suspendedUsers.toString(), Color(0xFFEF4444), Modifier.weight(1f))
                    MetricChip("ADMINS", adminUsers.toString(), Color(0xFFF59E0B), Modifier.weight(1f))
                }
            }
        }

        // --- 2. BARRA DE BUSCA E FILTROS ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por nome, e-mail ou UID...", fontSize = 13.sp, color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar", tint = Color.Gray)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                // Filtros de status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("TODOS", "ATIVO", "SUSPENSO", "ADMIN").forEach { filterName ->
                        val isSelected = selectedFilter == filterName
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = filterName },
                            label = { Text(filterName, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandRed,
                                selectedLabelColor = Color.White,
                                containerColor = DarkSurface,
                                labelColor = Color.Gray
                            ),
                            border = BorderStroke(1.dp, if (isSelected) BrandRed else CardBorder),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }

        // --- 3. LISTA DE USUÁRIOS ---
        if (filteredUsers.isEmpty()) {
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PersonOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                        Text(
                            "Nenhum usuário encontrado",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Tente outro termo de busca ou selecione outro filtro.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(filteredUsers, key = { it.uid.ifBlank { it.email } }) { user ->
                val userProfiles = remember(allProfiles, user.uid) {
                    allProfiles.filter { it.userId == user.uid }
                }
                UserRowCard(
                    user = user,
                    profilesCount = userProfiles.size,
                    onViewDetails = { selectedUserForDetails = user },
                    onChangeStatus = { targetStatus ->
                        userToChangeStatus = Pair(user, targetStatus)
                    }
                )
            }
        }
    }

    // Modal de detalhes do usuário
    selectedUserForDetails?.let { user ->
        val userProfiles = remember(allProfiles, user.uid) {
            allProfiles.filter { it.userId == user.uid }
        }
        AlertDialog(
            onDismissRequest = { selectedUserForDetails = null },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = BrandRed)
                    Text("Detalhes do Usuário", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailField("Nome:", user.displayName.ifBlank { "Não informado" })
                    DetailField("E-mail:", user.email.ifBlank { "Sem e-mail" })
                    DetailField("UID:", user.uid)
                    DetailField("Função / Role:", user.role)
                    DetailField("Status:", user.accessStatus)

                    // Toggle switch for Verification Status
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = verifyingUserId != user.uid) {
                            verifyingUserId = user.uid
                            adminViewModel.toggleUserVerification(user.uid, user.isVerified) { success, errorMsg ->
                                verifyingUserId = null
                                if (success) {
                                    val msg = if (user.isVerified) "Verificação removida com sucesso." else "Usuário verificado com sucesso."
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    selectedUserForDetails = user.copy(isVerified = !user.isVerified)
                                } else {
                                    val err = errorMsg ?: "Não foi possível verificar este usuário. Tente novamente."
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Selo de Verificação:", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (verifyingUserId == user.uid) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color(0xFF0095F6), strokeWidth = 2.dp)
                                Text("Salvando...", color = Color(0xFF0095F6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else if (user.isVerified) {
                                VerifiedBadge(size = 14.dp, showToastOnClick = false)
                                Text("VERIFICADO (Mudar)", color = Color(0xFF0095F6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("NÃO VERIFICADO (Mudar)", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Normal)
                            }
                        }
                    }

                    DetailField("Criado em:", formatDate(user.createdAt))
                    DetailField("Último Acesso:", formatDate(user.lastLoginAt))
                    DetailField("Perfis Cadastrados:", "${userProfiles.size} perfis (${userProfiles.joinToString { it.name }})")
                    if (user.deviceId.isNotBlank()) {
                        DetailField("Dispositivo:", user.deviceId)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedUserForDetails = null }) {
                    Text("FECHAR", color = BrandRed, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Diálogo de confirmação para suspender/ativar
    userToChangeStatus?.let { (user, newStatus) ->
        AlertDialog(
            onDismissRequest = { userToChangeStatus = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    if (newStatus == "ACTIVE") "Ativar Usuário?" else "Suspender Usuário?",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (newStatus == "ACTIVE")
                        "Tem certeza de que deseja reativar o acesso de ${user.email}?"
                    else
                        "Tem certeza de que deseja suspender o acesso de ${user.email}? O usuário não conseguirá acessar os conteúdos.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.updateUserStatus(user.uid, newStatus)
                        userToChangeStatus = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (newStatus == "ACTIVE") Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                ) {
                    Text(if (newStatus == "ACTIVE") "CONFIRMAR ATIVAÇÃO" else "CONFIRMAR SUSPENSÃO", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToChangeStatus = null }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun UserRowCard(
    user: UserEntity,
    profilesCount: Int,
    onViewDetails: () -> Unit,
    onChangeStatus: (String) -> Unit
) {
    val isFounder = user.role == "FOUNDER"
    val isAdmin = user.role == "ADMIN"
    val isActive = user.accessStatus.equals("ACTIVE", ignoreCase = true)

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isFounder -> BrandRed
                                    isAdmin -> Color(0xFFF59E0B)
                                    else -> Color(0xFF262633)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            user.displayName.firstOrNull()?.uppercase() ?: user.email.firstOrNull()?.uppercase() ?: "U",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                user.displayName.ifBlank { user.email.substringBefore("@") },
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            if (user.isVerified) {
                                VerifiedBadge(size = 13.dp, showToastOnClick = false)
                            }

                            if (isFounder) {
                                Surface(
                                    color = BrandRed,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "FOUNDER",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            } else if (isAdmin) {
                                Surface(
                                    color = Color(0xFFF59E0B),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "ADMIN",
                                        color = Color.Black,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            user.email,
                            color = Color.Gray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Status Chip
                Surface(
                    color = if (isActive) Color(0xFF064E3B) else Color(0xFF450A0A),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (isActive) Color(0xFF10B981) else Color(0xFFEF4444))
                ) {
                    Text(
                        if (isActive) "ATIVO" else "SUSPENSO",
                        color = if (isActive) Color(0xFF34D399) else Color(0xFFF87171),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Informações secundárias e ações
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "$profilesCount perfis",
                        color = Color.LightGray,
                        fontSize = 10.sp
                    )
                    Text("•", color = Color.Gray, fontSize = 10.sp)
                    Text(
                        "Último acesso: ${formatDate(user.lastLoginAt)}",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onViewDetails,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Text("Detalhes", fontSize = 10.sp, color = Color.White)
                    }

                    if (!isFounder) {
                        Button(
                            onClick = { onChangeStatus(if (isActive) "SUSPENDED" else "ACTIVE") },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isActive) Color(0xFF261214) else Color(0xFF0D291E),
                                contentColor = if (isActive) Color(0xFFEF4444) else Color(0xFF10B981)
                            ),
                            border = BorderStroke(1.dp, if (isActive) Color(0xFFEF4444).copy(alpha = 0.5f) else Color(0xFF10B981).copy(alpha = 0.5f))
                        ) {
                            Text(if (isActive) "Suspender" else "Ativar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp))
        Text(value, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Sem registro"
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
    return sdf.format(Date(timestamp))
}
