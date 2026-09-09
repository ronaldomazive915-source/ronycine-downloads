package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandRed
import com.example.ui.theme.TextSecondary
import com.example.util.stringI18n

private const val WHATSAPP_GROUP_LINK = "https://chat.whatsapp.com/GxpONcioT1p1fSROKRHau3"

// Session tracker to show only once per session
object WhatsAppInviteSession {
    var hasShown = false
}

@Composable
fun WhatsAppGroupInviteModal(
    visible: Boolean,
    onDismiss: (Boolean) -> Unit // Updated signature
) {
    if (!visible) return

    val context = LocalContext.current
    var isChecked by remember { mutableStateOf(false) } // Checkbox state

    // Sem animação de pulsação


    // Using Box for inline overlay
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Backdrop overlay with blur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) Modifier.blur(radius = 8.dp) else Modifier)
                .background(Color.Black.copy(alpha = 0.50f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onDismiss(false) } // Default dismiss
                )
        )

        // Card content - MUST be completely nítido
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.90f)
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* consume click inside modal */ }
                )
                .testTag("whatsapp_invite_modal"),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF141418),
            border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.35f)),
            shadowElevation = 16.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Close button (X) at top right
                IconButton(
                    onClick = { onDismiss(false) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .testTag("whatsapp_invite_close_icon")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Premium Black & Red RC Logo Badge
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = CircleShape,
                                spotColor = Color(0xFFE50914),
                                ambientColor = Color(0xFFE50914)
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF222222),
                                        Color(0xFF0D0D0D)
                                    )
                                )
                            )
                            .border(1.5.dp, Color(0xFFE50914).copy(alpha = 0.85f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.White)) {
                                    append("R")
                                }
                                withStyle(SpanStyle(color = Color(0xFFE50914))) {
                                    append("C")
                                }
                            },
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Title
                    Text(
                        text = stringI18n("whatsapp_invite.title"),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Subtitle
                    Text(
                        text = stringI18n("whatsapp_invite.subtitle"),
                        color = Color(0xFF25D366),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description
                    Text(
                        text = stringI18n("whatsapp_invite.description"),
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary Action: ENTRAR NO GRUPO
                    Button(
                        onClick = {
                            openWhatsAppGroup(context)
                            onDismiss(false)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("whatsapp_invite_join_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringI18n("whatsapp_invite.button"),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dismiss: Agora não
                    TextButton(
                        onClick = { onDismiss(isChecked) }, // Use checkbox state
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("whatsapp_invite_dismiss_button")
                    ) {
                        Text(
                            text = stringI18n("whatsapp_invite.dismiss"),
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Checkbox "Não mostrar novamente"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isChecked = !isChecked }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { isChecked = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = BrandRed,
                                uncheckedColor = Color.Gray
                            )
                        )
                        Text(
                            text = "Não mostrar novamente",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun openWhatsAppGroup(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WHATSAPP_GROUP_LINK)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Não foi possível abrir o link do grupo", Toast.LENGTH_SHORT).show()
    }
}
