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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandRed
import com.example.ui.theme.TextSecondary
import com.example.util.stringI18n

private const val WHATSAPP_GROUP_LINK = "https://chat.whatsapp.com/GxpONcioT1p1fSROKRHau3"
private const val TELEGRAM_CHANNEL_LINK = "https://t.me/ronycine1"

// Session tracker to show only once per session
object WhatsAppInviteSession {
    var hasShown = false
}

@Composable
fun WhatsAppGroupInviteModal(
    visible: Boolean,
    onDismiss: (Boolean) -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    var isChecked by remember { mutableStateOf(false) }

    // Animations
    val transitionState = remember { MutableTransitionState(false).apply { targetState = true } }
    val transition = updateTransition(transitionState, label = "ModalTransition")
    
    val scale by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow) },
        label = "Scale"
    ) { if (it) 1f else 0.8f }
    
    val alpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300) },
        label = "Alpha"
    ) { if (it) 1f else 0f }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Backdrop overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f * alpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onDismiss(false) }
                )
        )

        // Card content
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.92f)
                .padding(16.dp)
                .scale(scale)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* consume click inside modal card to avoid dismissing via background scrim */ }
                )
                .testTag("community_invite_modal"),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF101014),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            shadowElevation = 20.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Main content column
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Logo
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF222222), Color(0xFF0D0D0D))
                                )
                            )
                            .border(1.dp, BrandRed.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.White)) { append("R") }
                                withStyle(SpanStyle(color = BrandRed)) { append("C") }
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringI18n("whatsapp_invite.header_title"),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringI18n("whatsapp_invite.header_desc"),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Options Container
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isWide = maxWidth > 480.dp
                        if (isWide) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CommunityCard(
                                    modifier = Modifier.weight(1f),
                                    title = stringI18n("whatsapp_invite.title"),
                                    subtitle = stringI18n("whatsapp_invite.subtitle"),
                                    buttonText = stringI18n("whatsapp_invite.button"),
                                    brandColor = Color(0xFF25D366),
                                    icon = Icons.Default.Group,
                                    buttonIcon = "👥",
                                    onClick = { 
                                        openWhatsAppGroup(context)
                                        onDismiss(false)
                                    }
                                )
                                CommunityCard(
                                    modifier = Modifier.weight(1f),
                                    title = stringI18n("telegram_invite.title"),
                                    subtitle = stringI18n("telegram_invite.subtitle"),
                                    buttonText = stringI18n("telegram_invite.button"),
                                    brandColor = Color(0xFF0088CC),
                                    icon = Icons.Default.Send,
                                    buttonIcon = "✈️",
                                    onClick = {
                                        openTelegramChannel(context)
                                        onDismiss(false)
                                    }
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CommunityCard(
                                    title = stringI18n("whatsapp_invite.title"),
                                    subtitle = stringI18n("whatsapp_invite.subtitle"),
                                    buttonText = stringI18n("whatsapp_invite.button"),
                                    brandColor = Color(0xFF25D366),
                                    icon = Icons.Default.Group,
                                    buttonIcon = "👥",
                                    onClick = { 
                                        openWhatsAppGroup(context)
                                        onDismiss(false)
                                    }
                                )
                                CommunityCard(
                                    title = stringI18n("telegram_invite.title"),
                                    subtitle = stringI18n("telegram_invite.subtitle"),
                                    buttonText = stringI18n("telegram_invite.button"),
                                    brandColor = Color(0xFF0088CC),
                                    icon = Icons.Default.Send,
                                    buttonIcon = "✈️",
                                    onClick = {
                                        openTelegramChannel(context)
                                        onDismiss(false)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringI18n("whatsapp_invite.footer"),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dismiss Footer Checkbox
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isChecked = !isChecked }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("community_modal_dont_show_again"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { isChecked = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = BrandRed,
                                uncheckedColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Não mostrar novamente",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.5.sp
                        )
                    }
                }

                // Close button positioned above all content (highest z-order in Box)
                IconButton(
                    onClick = { onDismiss(isChecked) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .size(48.dp)
                        .testTag("community_modal_close_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommunityCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    buttonText: String,
    brandColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    buttonIcon: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161D)),
        border = BorderStroke(1.dp, brandColor.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(brandColor.copy(alpha = 0.12f), CircleShape)
                        .border(0.8.dp, brandColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = brandColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(10.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = brandColor),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "$buttonIcon $buttonText",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
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
        Toast.makeText(context, "Não foi possível abrir o link do WhatsApp", Toast.LENGTH_SHORT).show()
    }
}

private fun openTelegramChannel(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_CHANNEL_LINK)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Não foi possível abrir o link do Telegram", Toast.LENGTH_SHORT).show()
    }
}
