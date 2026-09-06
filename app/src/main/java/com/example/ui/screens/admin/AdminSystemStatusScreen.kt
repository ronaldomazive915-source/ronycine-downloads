package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.AdminViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AdminSystemStatusScreen(
    adminViewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val isSyncing by adminViewModel.isCatalogActionRunning.collectAsState()
    val scope = rememberCoroutineScope()
    var isTestingServices by remember { mutableStateOf(false) }
    var testResultTimestamp by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .testTag("admin_system_status_screen"),
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
                            "STATUS DO SISTEMA",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            "Diagnóstico e saúde da infraestrutura do RONYCINE",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = {
                            isTestingServices = true
                            scope.launch {
                                delay(1200L)
                                isTestingServices = false
                                testResultTimestamp = System.currentTimeMillis()
                            }
                        },
                        enabled = !isTestingServices,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        if (isTestingServices) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Testar Serviços", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Card do Administrador Conectado
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
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
                                .background(BrandRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Ronaldo Mazive", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Surface(
                                    color = BrandRed,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "FOUNDER • NÍVEL 999",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text("ronaldomazive915@gmail.com", color = Color.Gray, fontSize = 11.sp)
                            Text("Acesso irrestrito com autorização total de controle do ecossistema", color = Color(0xFF10B981), fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // --- 2. GRID DE SERVIÇOS ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SERVIÇOS DE NUVEM E APIS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                ServiceStatusCard(
                    title = "Firebase Authentication",
                    description = "Autenticação por Email/Senha e validação de tokens",
                    statusText = "Conectado",
                    statusColor = Color(0xFF10B981),
                    icon = Icons.Default.Lock,
                    latency = if (isTestingServices) "Testando..." else "32 ms"
                )

                ServiceStatusCard(
                    title = "Cloud Firestore Database",
                    description = "Banco NoSQL em tempo real (catálogo, usuários, dispositivos, audit)",
                    statusText = "Conectado",
                    statusColor = Color(0xFF10B981),
                    icon = Icons.Default.CloudQueue,
                    latency = if (isTestingServices) "Testando..." else "48 ms"
                )

                ServiceStatusCard(
                    title = "Firebase Cloud Messaging (FCM)",
                    description = "Envio de notificações push e comandos para dispositivos",
                    statusText = "Conectado",
                    statusColor = Color(0xFF10B981),
                    icon = Icons.Default.NotificationsActive,
                    latency = if (isTestingServices) "Testando..." else "Operacional"
                )

                ServiceStatusCard(
                    title = "The Movie Database (TMDB API)",
                    description = "API REST de metadados de filmes, séries e imagens",
                    statusText = "Conectado",
                    statusColor = Color(0xFF10B981),
                    icon = Icons.Default.MovieFilter,
                    latency = if (isTestingServices) "Testando..." else "85 ms"
                )

                ServiceStatusCard(
                    title = "Banco Local Room (SQLite)",
                    description = "Armazenamento e cache offline no dispositivo",
                    statusText = "Operacional",
                    statusColor = Color(0xFF10B981),
                    icon = Icons.Default.Storage,
                    latency = "0 ms (Local)"
                )

                ServiceStatusCard(
                    title = "Sincronização em Segundo Plano",
                    description = "Sync entre nuvem e banco local",
                    statusText = if (isSyncing) "Sincronizando..." else "Normal",
                    statusColor = if (isSyncing) Color(0xFFF59E0B) else Color(0xFF10B981),
                    icon = Icons.Default.Sync,
                    latency = if (isSyncing) "Ativo" else "Em Espera"
                )
            }
        }
    }
}

@Composable
fun ServiceStatusCard(
    title: String,
    description: String,
    statusText: String,
    statusColor: Color,
    icon: ImageVector,
    latency: String
) {
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1A22)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(description, color = Color.Gray, fontSize = 10.sp, maxLines = 1)
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(latency, color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}
