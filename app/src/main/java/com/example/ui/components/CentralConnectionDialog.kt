package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.CentralConnectionState
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary

@Composable
fun CentralConnectionDialog(
    connectionState: CentralConnectionState,
    onDismissRequest: () -> Unit,
    onPair: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(enabled = false) { },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 350.dp)
                    .padding(20.dp)
                    .testTag("central_connection_dialog"),
                shape = RoundedCornerShape(16.dp),
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Cloud Sync Icon
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFACC15).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = Color(0xFFFACC15),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "RONYCINE CENTRAL",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AnimatedContent(
                        targetState = connectionState,
                        label = "central_dialog_content"
                    ) { state ->
                        when (state) {
                            is CentralConnectionState.Disconnected, is CentralConnectionState.Error -> {
                                PairContent(
                                    error = (state as? CentralConnectionState.Error)?.message,
                                    onPair = onPair
                                )
                            }
                            is CentralConnectionState.Pairing -> {
                                PairingLoadingContent()
                            }
                            is CentralConnectionState.Connected -> {
                                ConnectedContent(
                                    accountName = state.accountName,
                                    onDisconnect = onDisconnect
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PairContent(
    error: String?,
    onPair: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Conecte seu aplicativo ao painel RONYCINE CENTRAL para sincronizar sua conta e preferências.",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "DIGITE O CÓDIGO DE PAREAMENTO",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it.uppercase() },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag("central_pairing_input"),
            textStyle = LocalTextStyle.current.copy(
                textAlign = TextAlign.Center,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = BrandRed
            ),
            placeholder = {
                Text(
                    "000000",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = Color.DarkGray,
                    fontSize = 22.sp,
                    letterSpacing = 4.sp
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (code.length == 6) {
                        onPair(code)
                        focusManager.clearFocus()
                    }
                }
            ),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandRed,
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = BrandRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onPair(code) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
            enabled = code.length == 6
        ) {
            Text("CONECTAR AGORA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PairingLoadingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 20.dp)
    ) {
        CircularProgressIndicator(color = BrandRed, strokeWidth = 3.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Validando código...",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Aguarde um momento enquanto conectamos com a Central.",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ConnectedContent(
    accountName: String?,
    onDisconnect: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF10B981).copy(alpha = 0.1f))
                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CONECTADO",
                    color = Color(0xFF10B981),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Conta Vinculada:",
            color = TextSecondary,
            fontSize = 12.sp
        )
        Text(
            text = accountName ?: "Usuário Central",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Seu aplicativo está sincronizado com a Central. Alterações feitas no painel serão refletidas aqui automaticamente.",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(30.dp))

        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Text("DESCONECTAR", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
