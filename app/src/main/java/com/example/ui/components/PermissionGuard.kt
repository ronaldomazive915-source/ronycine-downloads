package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.UserEntity
import com.example.data.remote.UserPermissions
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import kotlinx.coroutines.delay

/**
 * Higher-order component to protect admin screens and actions.
 * Prevents infinite spinners by enforcing a safety timeout and fallback options.
 */
@Composable
fun PermissionGuard(
    currentUser: UserEntity?,
    permissionKey: String,
    onRetry: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var isTimedOut by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            isTimedOut = false
            delay(8000) // 8 second safety timeout
            if (currentUser == null) {
                isTimedOut = true
                android.util.Log.w("PermissionGuard", "[AUTH_DIAGNOSTIC] ADMIN_LOAD_ERROR: Timeout waiting for user profile ($permissionKey)")
            }
        } else {
            isTimedOut = false
        }
    }

    if (currentUser == null) {
        if (isTimedOut) {
            // Safety timeout reached - display error screen with Retry option instead of infinite spinner
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Text(
                            text = "Não foi possível verificar as permissões administrativas.",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "A resposta do servidor demorou mais que o esperado. Verifique sua conexão com a internet ou tente novamente.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (onNavigateBack != null) {
                                OutlinedButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, CardBorder),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Voltar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    isTimedOut = false
                                    onRetry?.invoke()
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tentar novamente", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // Still within the 8-second window: show clean loader with explanatory text
            Box(modifier = Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RonycineSmileLoader(color = BrandRed, size = 32.dp)
                    Text(
                        text = "Verificando permissões administrativas...",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        return
    }

    if (currentUser.hasPermission(permissionKey)) {
        content()
    } else {
        AccessDeniedUI(permissionName = UserPermissions.getLabel(permissionKey))
    }
}

@Composable
fun AccessDeniedUI(permissionName: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                color = BrandRed.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Acesso Negado",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Você não possui permissão para acessar esta área:\n\"$permissionName\"",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Solicite acesso ao Founder do RONYCINE para gerenciar esta funcionalidade.",
                color = Color.Gray.copy(alpha = 0.5f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}
