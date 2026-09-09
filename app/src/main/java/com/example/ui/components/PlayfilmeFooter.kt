package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PlayfilmeFooter(
    onNavigate: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .border(
                BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(BrandRed.copy(alpha = 0.35f), Color.Transparent)
                    )
                )
            )
            .padding(horizontal = 14.dp, vertical = 16.dp)
            .testTag("playfilme_footer")
    ) {
        val isWide = maxWidth >= 600.dp

        if (isWide) {
            // Desktop / Tablet Layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 1200.dp)
                    .align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Col 1: Identity & About
                    Column(modifier = Modifier.weight(1.3f).padding(end = 20.dp)) {
                        BrandHeader()
                        Spacer(modifier = Modifier.height(10.dp))
                        AboutText()
                    }

                    // Col 2: Navigation Links
                    if (onNavigate != null) {
                        Column(modifier = Modifier.weight(0.9f).padding(horizontal = 12.dp)) {
                            Text(
                                text = "NAVEGAÇÃO",
                                color = BrandRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FooterNavLink("Início") { onNavigate(ScreenRoute.HOME.route) }
                            FooterNavLink("Explorar") { onNavigate(ScreenRoute.SEARCH.route) }
                            FooterNavLink("Filmes") { onNavigate(ScreenRoute.MOVIES.route) }
                            FooterNavLink("Séries") { onNavigate(ScreenRoute.SERIES.route) }
                            FooterNavLink("TV ao Vivo") { onNavigate(ScreenRoute.TV_LIVE.route) }
                            FooterNavLink("Em Alta") { onNavigate(ScreenRoute.TRENDING.route) }
                            FooterNavLink("Minha Lista") { onNavigate(ScreenRoute.MY_LIST.route) }
                        }
                    }

                    // Col 3: Social Contacts
                    Column(modifier = Modifier.weight(1.2f).padding(horizontal = 12.dp)) {
                        Text(
                            text = "FALE CONOSCO",
                            color = BrandRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ContactCard(
                            title = "WhatsApp",
                            subtitle = "Fale conosco",
                            icon = Icons.Default.Chat,
                            iconTint = Color(0xFF25D366),
                            onClick = { openUrl("https://wa.me/qr/T2SBXRX6HAB4D1") }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ContactCard(
                            title = "Telegram",
                            subtitle = "Canal oficial",
                            icon = Icons.AutoMirrored.Filled.Send,
                            iconTint = Color(0xFF229ED9),
                            onClick = { openUrl("https://t.me/ronycine") }
                        )
                    }

                    // Col 4: Website & Founder
                    Column(modifier = Modifier.weight(1.1f).padding(start = 12.dp)) {
                        Text(
                            text = "NOSSO SITE",
                            color = BrandRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SiteCard(onVisit = { openUrl("https://filmora.ai.studio") })
                        Spacer(modifier = Modifier.height(10.dp))
                        FounderCard()
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = CardBorder, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(12.dp))
                CopyrightRow()
            }
        } else {
            // Compact Mobile Vertical / Grid Flow
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BrandHeader()

                // Compact About Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Sobre o RONYCINE",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AboutText()
                    }
                }

                // Row 1: WhatsApp & Telegram side-by-side (Compact)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ContactCard(
                        title = "WhatsApp",
                        subtitle = "Fale conosco →",
                        icon = Icons.Default.Chat,
                        iconTint = Color(0xFF25D366),
                        onClick = { openUrl("https://wa.me/qr/T2SBXRX6HAB4D1") },
                        modifier = Modifier.weight(1f)
                    )

                    ContactCard(
                        title = "Telegram",
                        subtitle = "Canal oficial →",
                        icon = Icons.AutoMirrored.Filled.Send,
                        iconTint = Color(0xFF229ED9),
                        onClick = { openUrl("https://t.me/ronycine") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Nosso Site & Precisa de Ajuda? side-by-side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactSiteCard(
                        onVisit = { openUrl("https://filmora.ai.studio") },
                        modifier = Modifier.weight(1f)
                    )

                    CompactSupportCard(
                        onSupport = { openUrl("https://wa.me/qr/T2SBXRX6HAB4D1") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Compact Founder Card
                FounderCard(modifier = Modifier.fillMaxWidth())

                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                CopyrightRow()
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BrandRed),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "RONY",
                    color = BrandRed,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "CINE",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "ENTRETENIMENTO PREMIUM",
            color = Color(0xFFA1A1AA),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.5.sp
        )
    }
}

@Composable
private fun AboutText() {
    Text(
        text = "RONYCINE é uma plataforma de entretenimento dedicada a proporcionar uma experiência moderna, simples e organizada para descobrir filmes, séries e conteúdos de entretenimento.",
        color = Color(0xFFCCCCCC),
        fontSize = 13.sp,
        lineHeight = 19.sp
    )
}

@Composable
private fun ContactCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("contact_card_${title.lowercase()}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Acessar $title",
                tint = Color.Gray,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun CompactSiteCard(
    onVisit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onVisit() }
            .testTag("site_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(BrandRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🌐", fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "NOSSO SITE",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Visitar site →",
                        color = BrandRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSupportCard(
    onSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSupport() }
            .testTag("support_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(BrandRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HeadsetMic,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "AJUDA?",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Falar com suporte →",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteCard(
    onVisit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onVisit() }
            .testTag("site_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🌐",
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "NOSSO SITE",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Conheça nosso site oficial",
                color = Color.Gray,
                fontSize = 10.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onVisit,
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    text = "Visitar site →",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FounderCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("founder_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.7f)),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SOBRE O PROJETO",
                color = BrandRed,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Fundador: ",
                    color = Color.LightGray,
                    fontSize = 10.5.sp
                )
                Text(
                    text = "RONALDO MAZIVE",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun FooterNavLink(
    label: String,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = Color(0xFFA1A1AA),
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun CopyrightRow() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "RONYCINE © 2026",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Todos os direitos reservados.",
            color = Color.Gray,
            fontSize = 11.sp
        )
    }
}
