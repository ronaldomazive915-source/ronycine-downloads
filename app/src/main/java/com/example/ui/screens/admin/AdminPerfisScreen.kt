package com.example.ui.screens.admin

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.remote.UserProfile
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPerfisScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val profiles by adminViewModel.allGlobalProfiles.collectAsState()
    val users by adminViewModel.allUsers.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var profileToDelete by remember { mutableStateOf<UserProfile?>(null) }
    var selectedProfileDetails by remember { mutableStateOf<UserProfile?>(null) }

    val totalProfiles = profiles.size
    val recentProfiles = remember(profiles) {
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        profiles.count { it.createdAt >= sevenDaysAgo }
    }
    val defaultProfiles = remember(profiles) { profiles.count { it.isDefault } }

    val userMap = remember(users) {
        users.associateBy({ it.uid }, { it.email.ifBlank { it.displayName } })
    }

    val filteredProfiles = remember(profiles, searchQuery) {
        if (searchQuery.isBlank()) profiles
        else {
            val q = searchQuery.trim().lowercase()
            profiles.filter {
                it.name.lowercase().contains(q) ||
                        it.userId.lowercase().contains(q) ||
                        (userMap[it.userId]?.lowercase()?.contains(q) ?: false)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .testTag("admin_perfis_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. HEADER E MÉTRICAS ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "GESTÃO DE PERFIS",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    "Controle de perfis criados por usuários do RONYCINE",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricChip("TOTAL PERFIS", totalProfiles.toString(), Color.White, Modifier.weight(1f))
                    MetricChip("CRIADOS RECENTES", recentProfiles.toString(), Color(0xFF10B981), Modifier.weight(1f))
                    MetricChip("PERFIS PRINCIPAIS", defaultProfiles.toString(), Color(0xFF3B82F6), Modifier.weight(1f))
                }
            }
        }

        // --- 2. BUSCA ---
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar perfil por nome ou conta associada...", fontSize = 13.sp, color = Color.Gray) },
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
        }

        // --- 3. LISTA DE PERFIS ---
        if (filteredProfiles.isEmpty()) {
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
                        Icon(Icons.Default.AccountBox, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                        Text("Nenhum perfil encontrado", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Tente outro termo de pesquisa.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(filteredProfiles, key = { it.id.ifBlank { "${it.userId}_${it.name}" } }) { profile ->
                val associatedEmail = userMap[profile.userId] ?: "ID: ${profile.userId.take(8)}..."
                ProfileRowCard(
                    profile = profile,
                    associatedEmail = associatedEmail,
                    onViewDetails = { selectedProfileDetails = profile },
                    onDelete = { profileToDelete = profile }
                )
            }
        }
    }

    // Modal de detalhes
    selectedProfileDetails?.let { profile ->
        val associatedEmail = userMap[profile.userId] ?: "UID: ${profile.userId}"
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
fun ProfileRowCard(
    profile: UserProfile,
    associatedEmail: String,
    onViewDetails: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF262633)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!profile.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(profile.avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = profile.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            profile.name.firstOrNull()?.uppercase() ?: "P",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            profile.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (profile.isDefault) {
                            Surface(
                                color = Color(0xFF1E3A8A),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "PADRÃO",
                                    color = Color(0xFF93C5FD),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        "Conta: $associatedEmail",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        "Criado em: ${formatDate(profile.createdAt)}",
                        color = Color.DarkGray,
                        fontSize = 9.sp
                    )
                }
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

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
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
