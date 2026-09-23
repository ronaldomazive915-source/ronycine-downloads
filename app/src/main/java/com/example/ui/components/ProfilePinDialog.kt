package com.example.ui.components

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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.UserProfile
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary

@Composable
fun ProfilePinDialog(
    profile: UserProfile,
    onDismissRequest: () -> Unit,
    onPinVerified: () -> Unit,
    errorMessage: String? = null
) {
    var pin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(errorMessage) }
    val focusRequester = remember { FocusRequester() }

    fun submitPin() {
        if (pin.length == 4) {
            if (profile.verifyPin(pin)) {
                localError = null
                onPinVerified()
            } else {
                localError = "PIN incorreto"
                pin = ""
            }
        } else {
            localError = "Digite os 4 dígitos do PIN"
        }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

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
                    .widthIn(max = 330.dp)
                    .padding(20.dp)
                    .testTag("profile_pin_dialog"),
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
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("pin_dialog_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Voltar",
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Lock Icon
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(BrandRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Perfil protegido",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Digite o PIN de 4 dígitos para acessar este perfil.",
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Visual 4-Dots Display (● ● ● ●)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                try {
                                    focusRequester.requestFocus()
                                } catch (_: Exception) {}
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        for (i in 0 until 4) {
                            val isFilled = i < pin.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (isFilled) BrandRed else DarkBackground)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isFilled) BrandRed else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Hidden/Accessible Numeric Input Field
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { input ->
                            if (input.length <= 4 && input.all { it.isDigit() }) {
                                pin = input
                                localError = null
                                if (input.length == 4) {
                                    // Valida automaticamente ao digitar o 4º dígito ou pelo botão ENTRAR
                                    submitPin()
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitPin() }
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .width(160.dp)
                            .focusRequester(focusRequester)
                            .testTag("pin_input_field"),
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            fontSize = 20.sp,
                            letterSpacing = 6.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = BrandRed
                        ),
                        singleLine = true
                    )

                    if (localError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = localError!!,
                            color = BrandRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("pin_error_message")
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Botão ENTRAR
                    Button(
                        onClick = { submitPin() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("pin_submit_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandRed,
                            disabledContainerColor = BrandRed.copy(alpha = 0.4f)
                        ),
                        enabled = pin.length == 4
                    ) {
                        Text(
                            text = "ENTRAR",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Opção VOLTAR
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("pin_cancel_button")
                    ) {
                        Text(
                            text = "VOLTAR",
                            color = Color.LightGray,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}
