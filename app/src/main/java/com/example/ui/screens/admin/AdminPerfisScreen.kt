package com.example.ui.screens.admin

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.UserProfile
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.VerifiedBadge
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPerfisScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profiles by adminViewModel.allGlobalProfiles.collectAsState()
    val users by adminViewModel.allUsers.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("TODOS") } // TODOS, VERIFICADOS, NAO_VERIFICADOS
    var currentPage by remember { mutableStateOf(1) }
    val itemsPerPage = 12

    var profileToDelete by remember { mutableStateOf<UserProfile?>(null) }
    var profileToVerifyToggle by remember { mutableStateOf<UserProfile?>(null) }
    var selectedProfileDetails by remember { mutableStateOf<UserProfile?>(null) }
    var verifyingProfileId by remember { mutableStateOf<String?>(null) }

    val userMap = remember(users) {
        users.associateBy({ it.uid }, { it.email.ifBlank { it.displayName } })
    }

    // Métricas reais de verificação
    val totalProfiles = profiles.size
    val verifiedCount = remember(profiles) { profiles.count { it.isVerified } }
    val pendingCount = totalProfiles - verifiedCount

    // Filtro e Busca combinados
    val filteredProfiles = remember(profiles, searchQuery, selectedFilter, userMap) {
        profiles.filter { profile ->
            // Filtro por status
            val matchesFilter = when (selectedFilter) {
                "VERIFICADOS" -> profile.isVerified
                "NAO_VERIFICADOS" -> !profile.isVerified
                else -> true
            }
            if (!matchesFilter) return@filter false

            // Busca por texto
            if (searchQuery.isBlank()) return@filter true
            val q = searchQuery.trim().lowercase()
            val assocEmail = userMap[profile.userId]?.lowercase() ?: ""
            val pName = profile.name.lowercase()
            val pUsername = profile.username.lowercase()
            val fallbackUsername = "@${com.example.util.UsernameUtils.normalize(profile.name)}".lowercase()
            val pUid = profile.userId.lowercase()

            pName.contains(q) || pUsername.contains(q) || fallbackUsername.contains(q) || assocEmail.contains(q) || pUid.contains(q)
        }
    }

    // Controle de página
    val totalPages = maxOf(1, ceil(filteredProfiles.size.toDouble() / itemsPerPage).toInt())
    
    // Reseta página ao mudar busca ou filtro
    LaunchedEffect(searchQuery, selectedFilter) {
        currentPage = 1
    }

    val pagedProfiles = remember(filteredProfiles, currentPage) {
        val startIndex = (currentPage - 1) * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, filteredProfiles.size)
        if (startIndex >= filteredProfiles.size) emptyList()
        else filteredProfiles.subList(startIndex, endIndex)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("admin_perfis_screen"),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- 1. CABEÇALHO (Span total) ---
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "SELOS DE VERIFICAÇÃO",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            "Gerencie selos azuis oficiais de verificação vinculados às contas e perfis.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                Toast.makeText(context, "Lista atualizada com sucesso", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(DarkSurface, CircleShape)
                                .border(1.dp, CardBorder, CircleShape)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = Color(0xFF0095F6), modifier = Modifier.size(18.dp))
                        }
                        VerifiedBadge(size = 28.dp, showToastOnClick = true)
                    }
                }

                // Cards de métricas superiores
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard("TOTAL PERFIS", totalProfiles.toString(), Color.White, Modifier.weight(1f))
                    MetricCard("VERIFICADOS", verifiedCount.toString(), Color(0xFF0095F6), Modifier.weight(1f))
                    MetricCard("PENDENTES", pendingCount.toString(), Color.Gray, Modifier.weight(1f))
                }
            }
        }

        // --- 2. ÁREA DE FILTROS E BUSCA (Span total) ---
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Barra de pesquisa
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Pesquisar por nome, username, e-mail ou UID...", fontSize = 13.sp, color = Color.Gray) },
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
                        focusedBorderColor = Color(0xFF0095F6),
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                // Chips de filtro horizontal
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChipItem("Todos", selectedFilter == "TODOS") { selectedFilter = "TODOS" }
                    FilterChipItem("✓ Verificados", selectedFilter == "VERIFICADOS") { selectedFilter = "VERIFICADOS" }
                    FilterChipItem("Não Verificados", selectedFilter == "NAO_VERIFICADOS") { selectedFilter = "NAO_VERIFICADOS" }
                }
            }
        }

        // --- 3. LISTAGEM DE CARDS ---
        if (pagedProfiles.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AccountBox, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                        Text("Nenhum perfil encontrado", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Tente mudar os filtros ou o termo de pesquisa.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(pagedProfiles, key = { it.id.ifBlank { "${it.userId}_${it.name}" } }) { profile ->
                val assocEmail = userMap[profile.userId] ?: "Sem e-mail registrado"
                val displayUsername = profile.username.ifBlank { "@${com.example.util.UsernameUtils.normalize(profile.name)}" }
                
                // Determina se o perfil pertence ao fundador (ronaldomazive915@gmail.com)
                val isFounderAccount = assocEmail.equals("ronaldomazive915@gmail.com", ignoreCase = true) || profile.userId == "FOUNDER"

                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (profile.isVerified) Color(0x330095F6) else CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedProfileDetails = profile }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ProfileAvatar(
                                profile = profile,
                                size = 44.dp,
                                borderWidth = 1.dp,
                                borderColor = if (profile.isVerified) Color(0xFF0095F6) else CardBorder
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = profile.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (profile.isVerified) {
                                        VerifiedBadge(size = 14.dp, showToastOnClick = false)
                                    }
                                }

                                Text(
                                    text = displayUsername,
                                    color = BrandRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = assocEmail,
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(CardBorder)
                        )

                        // Info de rodapé & Ações
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Badge status do selo
                            if (profile.isVerified) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF0095F6), modifier = Modifier.size(12.dp))
                                    Text("✓ VERIFICADO", color = Color(0xFF0095F6), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text("NÃO VERIFICADO", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }

                            // Ação rápida
                            if (isFounderAccount) {
                                // O perfil do fundador possui proteção absoluta: não pode ser desativado
                                Surface(
                                    color = Color(0xFF1E3A8A),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "FUNDADOR",
                                        color = Color(0xFF93C5FD),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { profileToVerifyToggle = profile },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (profile.isVerified) Color(0x22EF4444) else Color(0xFF0095F6),
                                        contentColor = if (profile.isVerified) Color(0xFFEF4444) else Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = if (profile.isVerified) "REMOVER" else "VERIFICAR",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. PAGINAÇÃO (Span total) ---
        if (totalPages > 1) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (currentPage > 1) currentPage-- },
                        enabled = currentPage > 1
                    ) {
                        Icon(
                            Icons.Default.ArrowBackIos,
                            contentDescription = "Anterior",
                            tint = if (currentPage > 1) Color.White else Color.DarkGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "$currentPage / $totalPages",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    IconButton(
                        onClick = { if (currentPage < totalPages) currentPage++ },
                        enabled = currentPage < totalPages
                    ) {
                        Icon(
                            Icons.Default.ArrowForwardIos,
                            contentDescription = "Próxima",
                            tint = if (currentPage < totalPages) Color.White else Color.DarkGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Modal de detalhes
    selectedProfileDetails?.let { profile ->
        val associatedEmail = userMap[profile.userId] ?: "UID: ${profile.userId}"
        val isFounderAccount = associatedEmail.equals("ronaldomazive915@gmail.com", ignoreCase = true)

        AlertDialog(
            onDismissRequest = { selectedProfileDetails = null },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = BrandRed)
                    Text("Detalhes do Perfil", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailField("Nome:", profile.name)
                    DetailField("Conta Associada:", associatedEmail)
                    DetailField("UID do Dono:", profile.userId)
                    DetailField("Perfil ID:", profile.id)
                    DetailField("Tipo de Avatar:", profile.avatarType)
                    DetailField("Perfil Padrão:", if (profile.isDefault) "Sim" else "Não")

                    // Toggle switch for Verification Status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isFounderAccount) {
                                adminViewModel.toggleProfileVerification(profile.userId, profile.id, profile.isVerified)
                                selectedProfileDetails = profile.copy(isVerified = !profile.isVerified)
                                Toast.makeText(context, "Sinal de verificação alterado com sucesso!", Toast.LENGTH_SHORT).show()
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Selo de Verificação:", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (profile.isVerified) {
                                VerifiedBadge(size = 14.dp, showToastOnClick = false)
                                Text(if (isFounderAccount) "VERIFICADO (Fundador)" else "VERIFICADO (Mudar)", color = Color(0xFF0095F6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("NÃO VERIFICADO (Mudar)", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Normal)
                            }
                        }
                    }

                    DetailField("Criado em:", formatDate(profile.createdAt))
                    DetailField("Última Atualização:", formatDate(profile.updatedAt))
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedProfileDetails = null }) {
                    Text("FECHAR", color = BrandRed, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Diálogo de confirmação para verificar/remover verificação
    profileToVerifyToggle?.let { profile ->
        val actionText = if (profile.isVerified) "Remover Verificação" else "Conceder Verificação"
        val descText = if (profile.isVerified) {
            "Tem certeza de que deseja remover o selo azul de verificação do perfil \"${profile.name}\"? Ele perderá os privilégios estéticos correspondentes imediatamente."
        } else {
            "Deseja conceder o selo azul de verificação oficial ao perfil \"${profile.name}\"? O selo aparecerá imediatamente ao lado de seu nome."
        }

        AlertDialog(
            onDismissRequest = { profileToVerifyToggle = null },
            containerColor = DarkSurface,
            title = {
                Text("$actionText?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(descText, color = Color.LightGray, fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        verifyingProfileId = profile.id
                        adminViewModel.toggleProfileVerification(profile.userId, profile.id, profile.isVerified) { success, errorMsg ->
                            verifyingProfileId = null
                            profileToVerifyToggle = null
                            if (success) {
                                val msg = if (profile.isVerified) "Verificação removida com sucesso." else "Usuário verificado com sucesso."
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                val err = errorMsg ?: "Não foi possível verificar este usuário. Tente novamente."
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = verifyingProfileId != profile.id,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (profile.isVerified) Color(0xFFEF4444) else Color(0xFF0095F6)
                    )
                ) {
                    if (verifyingProfileId == profile.id) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            Text("Salvando...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Text("CONFIRMAR", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToVerifyToggle = null }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        )
    }

    // Diálogo de confirmação de exclusão
    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            containerColor = DarkSurface,
            title = {
                Text("Excluir Perfil?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Tem certeza de que deseja excluir o perfil \"${profile.name}\"? Esta ação não pode ser desfeita.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.deleteUserProfile(profile.userId, profile.id)
                        profileToDelete = null
                        Toast.makeText(context, "Perfil excluído com sucesso!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("CONFIRMAR EXCLUSÃO", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = valueColor, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) Color(0x220095F6) else DarkSurface,
        border = BorderStroke(1.dp, if (isSelected) Color(0xFF0095F6) else CardBorder),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (isSelected) Color(0xFF0095F6) else Color.Gray,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
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
