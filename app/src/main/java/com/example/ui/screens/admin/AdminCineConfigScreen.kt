package com.example.ui.screens.admin

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.SystemAvatar
import com.example.ui.viewmodel.AdminViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCineConfigScreen(adminViewModel: AdminViewModel) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val coroutineScope = rememberCoroutineScope()

    // Identity
    var cineName by remember { mutableStateOf("Cine") }
    var cineDescription by remember { mutableStateOf("Assistente oficial de recomendação") }
    var isActive by remember { mutableStateOf(true) }

    // Personality
    var cinePersonality by remember { mutableStateOf("amigável e cinéfilo") }
    var welcomeMessage by remember { mutableStateOf("Olá! Sou o Cine.") }
    var recommendationsEnabled by remember { mutableStateOf(true) }
    var moderationEnabled by remember { mutableStateOf(true) }
    var linkBlockingEnabled by remember { mutableStateOf(true) }

    // Avatar
    var avatarId by remember { mutableStateOf("") }
    
    // System Avatars
    var systemAvatars by remember { mutableStateOf<List<SystemAvatar>>(emptyList()) }
    var showAvatarManager by remember { mutableStateOf(false) }
    var showAvatarSelector by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    // Load initial config and avatars
    LaunchedEffect(Unit) {
        try {
            val doc = db.collection("admin_config").document("cine_config").get().await()
            if (doc.exists()) {
                cineName = doc.getString("name") ?: "Cine"
                cineDescription = doc.getString("description") ?: "Assistente oficial de recomendação"
                cinePersonality = doc.getString("personality") ?: "amigável"
                avatarId = doc.getString("avatarId") ?: ""
                isActive = doc.getBoolean("isActive") ?: true
                welcomeMessage = doc.getString("welcomeMessage") ?: "Olá! Sou o Cine."
                recommendationsEnabled = doc.getBoolean("recommendationsEnabled") ?: true
                moderationEnabled = doc.getBoolean("moderationEnabled") ?: true
                linkBlockingEnabled = doc.getBoolean("linkBlockingEnabled") ?: true
            }

            val avatarsSnapshot = db.collection("systemAvatars").orderBy("order").get().await()
            if (avatarsSnapshot.isEmpty) {
                // Seed database
                val seed = listOf(
                    SystemAvatar("avatar_cine_01", "Robô Clássico", "https://api.dicebear.com/7.x/bottts/png?seed=Cine1&backgroundColor=e50914", true, 1),
                    SystemAvatar("avatar_cine_02", "Robô Moderno", "https://api.dicebear.com/7.x/bottts/png?seed=Cine2&backgroundColor=0f0f12", true, 2),
                    SystemAvatar("avatar_cine_03", "Assistente Fofo", "https://api.dicebear.com/7.x/bottts/png?seed=Cine3&backgroundColor=131b2e", true, 3),
                    SystemAvatar("avatar_cine_04", "Forma Abstrata", "https://api.dicebear.com/7.x/shapes/png?seed=Cine4&backgroundColor=e50914", true, 4),
                    SystemAvatar("avatar_cine_05", "Cineasta", "https://api.dicebear.com/7.x/fun-emoji/png?seed=Cine5&backgroundColor=131317", true, 5),
                    SystemAvatar("avatar_cine_06", "Pipoca", "https://api.dicebear.com/7.x/icons/png?seed=popcorn&backgroundColor=e50914", true, 6),
                    SystemAvatar("avatar_cine_07", "Claquete", "https://api.dicebear.com/7.x/icons/png?seed=movie&backgroundColor=0f0f12", true, 7),
                    SystemAvatar("avatar_cine_08", "Estrela", "https://api.dicebear.com/7.x/icons/png?seed=star&backgroundColor=e50914", true, 8),
                    SystemAvatar("avatar_cine_09", "IA Holograma", "https://api.dicebear.com/7.x/bottts/png?seed=Cine9&backgroundColor=1a1a24", true, 9),
                    SystemAvatar("avatar_cine_10", "Minimalista", "https://api.dicebear.com/7.x/initials/png?seed=CN&backgroundColor=e50914", true, 10)
                )
                val batch = db.batch()
                seed.forEach { avatar ->
                    val ref = db.collection("systemAvatars").document(avatar.id)
                    batch.set(ref, avatar)
                }
                batch.commit().await()
                systemAvatars = seed
                if (avatarId.isEmpty()) avatarId = "avatar_cine_01"
            } else {
                systemAvatars = avatarsSnapshot.toObjects(SystemAvatar::class.java)
                if (avatarId.isEmpty() && systemAvatars.isNotEmpty()) {
                    avatarId = systemAvatars.first { it.active }.id
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao carregar", Toast.LENGTH_SHORT).show()
        } finally {
            isLoading = false
        }
    }

    val saveConfig = {
        if (!isSaving) {
            isSaving = true
            
            // Derive avatarUrl for backward compatibility and fast access by other components
            val selectedAvatarUrl = systemAvatars.find { it.id == avatarId }?.imageUrl ?: ""
            
            val data = mapOf(
                "name" to cineName.ifBlank { "Cine" },
                "description" to cineDescription,
                "personality" to cinePersonality,
                "avatarId" to avatarId,
                "avatarUrl" to selectedAvatarUrl, // Saved for backward compatibility
                "isActive" to isActive,
                "welcomeMessage" to welcomeMessage,
                "recommendationsEnabled" to recommendationsEnabled,
                "moderationEnabled" to moderationEnabled,
                "linkBlockingEnabled" to linkBlockingEnabled,
                "updatedAt" to System.currentTimeMillis()
            )
            
            db.collection("admin_config").document("cine_config").set(data)
                .addOnSuccessListener {
                    isSaving = false
                    Toast.makeText(context, "Salvo com sucesso!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    isSaving = false
                    Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    val DarkBackground = Color(0xFF0B0F19)
    val DarkSurface = Color(0xFF131B2E)
    val PrimaryRed = Color(0xFFE50914)
    val CardBorder = Color(0xFF1E293B)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = PrimaryRed,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(PrimaryRed.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, PrimaryRed.copy(alpha=0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val currentAvatar = systemAvatars.find { it.id == avatarId }
                                if (currentAvatar != null && currentAvatar.imageUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = currentAvatar.imageUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = PrimaryRed)
                                }
                            }
                            Column {
                                Text("CONFIGURAÇÕES DO CINE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Gerencie a inteligência artificial do app", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkSurface,
                    contentColor = PrimaryRed,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = PrimaryRed
                        )
                    },
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Geral", color = if (selectedTab == 0) PrimaryRed else Color.Gray, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Personalidade", color = if (selectedTab == 1) PrimaryRed else Color.Gray, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Avatares", color = if (selectedTab == 2) PrimaryRed else Color.Gray, fontWeight = FontWeight.Bold) }
                    )
                }

                when (selectedTab) {
                    0 -> {
                        // IDENTIDADE
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("IDENTIDADE DO CINE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Status do Sistema", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(if (isActive) "ATIVO" else "DESATIVADO", color = if (isActive) Color(0xFF4CAF50) else Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Switch(
                                        checked = isActive,
                                        onCheckedChange = { isActive = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryRed)
                                    )
                                }
                                
                                OutlinedTextField(
                                    value = cineName,
                                    onValueChange = { cineName = it },
                                    label = { Text("Nome do Assistente") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryRed,
                                        unfocusedBorderColor = CardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                OutlinedTextField(
                                    value = cineDescription,
                                    onValueChange = { cineDescription = it },
                                    label = { Text("Descrição Curta") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryRed,
                                        unfocusedBorderColor = CardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                    1 -> {
                        // PERSONALIDADE
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("PERSONALIDADE & COMPORTAMENTO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                
                                OutlinedTextField(
                                    value = welcomeMessage,
                                    onValueChange = { welcomeMessage = it },
                                    label = { Text("Mensagem de Apresentação") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryRed,
                                        unfocusedBorderColor = CardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                
                                OutlinedTextField(
                                    value = cinePersonality,
                                    onValueChange = { cinePersonality = it },
                                    label = { Text("Tom da Comunicação") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryRed,
                                        unfocusedBorderColor = CardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                HorizontalDivider(color = CardBorder)
                                
                                ToggleRow(
                                    title = "Ativar Recomendações",
                                    subtitle = "O Cine pode recomendar filmes e séries",
                                    checked = recommendationsEnabled,
                                    onCheckedChange = { recommendationsEnabled = it }
                                )
                                ToggleRow(
                                    title = "Ativar Moderação",
                                    subtitle = "O Cine ajudará a moderar o chat",
                                    checked = moderationEnabled,
                                    onCheckedChange = { moderationEnabled = it }
                                )
                                ToggleRow(
                                    title = "Bloqueio de Links",
                                    subtitle = "O Cine avisa sobre links externos",
                                    checked = linkBlockingEnabled,
                                    onCheckedChange = { linkBlockingEnabled = it }
                                )
                            }
                        }
                    }
                    2 -> {
                        // AVATARES
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("AVATAR DO CINE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    TextButton(onClick = { showAvatarManager = true }) {
                                        Text("Gerenciar Biblioteca", color = PrimaryRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                val currentAvatar = systemAvatars.find { it.id == avatarId }
                                if (currentAvatar != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .padding(12.dp)
                                    ) {
                                        AsyncImage(
                                            model = currentAvatar.imageUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .border(2.dp, PrimaryRed, CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Avatar Atual", color = Color.Gray, fontSize = 12.sp)
                                            Text(currentAvatar.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                        Button(
                                            onClick = { showAvatarSelector = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Trocar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = saveConfig,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SALVANDO...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SALVAR CONFIGURAÇÕES", fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showAvatarSelector) {
        AvatarSelectorModal(
            avatars = systemAvatars.filter { it.active },
            currentAvatarId = avatarId,
            onDismiss = { showAvatarSelector = false },
            onSelect = { selectedId ->
                avatarId = selectedId
                showAvatarSelector = false
                saveConfig()
            }
        )
    }

    if (showAvatarManager) {
        AvatarManagerDialog(
            avatars = systemAvatars,
            currentAvatarId = avatarId,
            onDismiss = { showAvatarManager = false },
            onUpdateAvatars = { updatedList ->
                systemAvatars = updatedList
                val batch = db.batch()
                updatedList.forEach { avatar ->
                    batch.set(db.collection("systemAvatars").document(avatar.id), avatar)
                }
                batch.commit().addOnSuccessListener {
                    Toast.makeText(context, "Biblioteca atualizada!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFE50914))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarSelectorModal(
    avatars: List<SystemAvatar>,
    currentAvatarId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var selectedId by remember { mutableStateOf(currentAvatarId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131B2E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        modifier = Modifier.fillMaxWidth(),
        title = { Text("Escolha seu avatar", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                val selectedAvatar = avatars.find { it.id == selectedId }
                if (selectedAvatar != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Selecionado: ${selectedAvatar.name}", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 350.dp)
                ) {
                    items(avatars.sortedBy { it.order }) { avatar ->
                        val isSelected = selectedId == avatar.id
                        Box(
                            modifier = Modifier
                                .aspectRatio(0.8f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFFE50914).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color(0xFFE50914) else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedId = avatar.id }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                AsyncImage(
                                    model = avatar.imageUrl,
                                    contentDescription = avatar.name,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = avatar.name,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFFE50914),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(16.dp)
                                        .background(Color(0xFF131B2E), CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelect(selectedId) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
            ) {
                Text("Salvar avatar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.Gray)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarManagerDialog(
    avatars: List<SystemAvatar>,
    currentAvatarId: String,
    onDismiss: () -> Unit,
    onUpdateAvatars: (List<SystemAvatar>) -> Unit
) {
    var editableList by remember { mutableStateOf(avatars) }
    val activeCount = editableList.count { it.active }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131B2E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        modifier = Modifier.fillMaxWidth(),
        title = { Text("Biblioteca de Avatares", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Total: ${editableList.size} | Ativos: $activeCount | Inativos: ${editableList.size - activeCount}", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(editableList.sortedBy { it.order }) { avatar ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = avatar.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(avatar.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                if (avatar.id == currentAvatarId) {
                                    Text("Em uso pelo Cine", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Switch(
                                checked = avatar.active,
                                onCheckedChange = { isActive ->
                                    if (!isActive && avatar.id == currentAvatarId) {
                                        // Handled silently
                                    } else {
                                        editableList = editableList.map {
                                            if (it.id == avatar.id) it.copy(active = isActive) else it
                                        }
                                    }
                                },
                                enabled = avatar.id != currentAvatarId
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onUpdateAvatars(editableList)
                onDismiss()
            }) {
                Text("SALVAR", color = Color(0xFFE50914), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = Color.Gray)
            }
        }
    )
}
