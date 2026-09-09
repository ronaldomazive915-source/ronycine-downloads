package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.remote.UserProfile
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary

/**
 * Modern, professional lateral navigation drawer for RONYCINE.
 * Replaces the bottom-sheet structure with a clean left-side panel.
 * 
 * Features:
 * - Content starts naturally from the top with status bar padding (no empty top gap).
 * - Full viewport height with smooth internal scrolling.
 * - Header with RONYCINE branding and close (×) button.
 * - Compact active profile card with real avatar/fallback.
 * - Vertical item hierarchy with active route highlighting.
 * - Smooth slide-in/slide-out horizontal animation (250ms).
 * - Dimmed backdrop scrim that dismisses on tap.
 * - Android BackHandler integration.
 */
@Composable
fun QuickMenuSheet(
    activeProfile: UserProfile?,
    isAdmin: Boolean,
    currentRoute: String? = null,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit
) {
    // Intercept back button to close drawer
    BackHandler(onBack = onDismiss)

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val drawerWidth = if (screenWidth < 400.dp) (screenWidth * 0.84f) else 320.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("quick_menu_drawer_overlay")
    ) {
        // 1. Dark Backdrop Scrim (click outside to dismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )

        // 2. Lateral Drawer Panel (Slide in from Left, Content aligned to Top)
        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(durationMillis = 260)
            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(durationMillis = 220)
            ) + fadeOut(animationSpec = tween(durationMillis = 180)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(drawerWidth)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Prevent taps inside drawer from dismissing
                    ),
                color = Color(0xFF101014),
                border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.4f)),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // --- FIXED TOP HEADER ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Branding
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "RONY",
                                color = BrandRed,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "CINE",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Close (×) Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(DarkSurface)
                                .testTag("drawer_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar Menu",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // --- ACTIVE USER PROFILE CARD ---
                    Surface(
                        onClick = {
                            onDismiss()
                            onNavigate(ScreenRoute.PROFILE.route)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurface,
                        border = BorderStroke(0.8.dp, CardBorder.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Avatar
                            ProfileAvatar(
                                profile = activeProfile,
                                size = 40.dp,
                                borderWidth = 1.5.dp,
                                borderColor = BrandRed,
                                testTag = "quick_menu_profile_avatar"
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = activeProfile?.name?.ifBlank { "RONYCINE" } ?: "RONYCINE",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (activeProfile?.isVerified == true) {
                                        VerifiedBadge(size = 14.dp, showToastOnClick = false)
                                    }
                                }
                                Text(
                                    text = "Meu Principal",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        color = CardBorder.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )

                    // --- SCROLLABLE MENU ITEMS ---
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        item {
                            DrawerMenuItem(
                                icon = Icons.Default.Home,
                                title = "Início",
                                isSelected = currentRoute == ScreenRoute.HOME.route,
                                onClick = {
                                    onDismiss()
                                    onNavigate(ScreenRoute.HOME.route)
                                }
                            )
                        }

                        item {
                            DrawerMenuItem(
                                icon = Icons.Default.Movie,
                                title = "Explorar Catálogo",
                                isSelected = currentRoute == ScreenRoute.SEARCH.route,
                                onClick = {
                                    onDismiss()
                                    onNavigate(ScreenRoute.SEARCH.route)
                                }
                            )
                        }

                        item {
                            DrawerMenuItem(
                                icon = Icons.Default.TrendingUp,
                                title = "Em Alta / Destaques",
                                isSelected = currentRoute == ScreenRoute.TRENDING.route,
                                onClick = {
                                    onDismiss()
                                    onNavigate(ScreenRoute.TRENDING.route)
                                }
                            )
                        }

                        item {
                            DrawerMenuItem(
                                icon = Icons.Default.VideoLibrary,
                                title = "ANIMES & DORAMAS",
                                isSelected = currentRoute == ScreenRoute.ANIMES_DORAMAS.route,
                                onClick = {
                                    onDismiss()
                                    onNavigate(ScreenRoute.ANIMES_DORAMAS.route)
                                }
                            )
                        }

                        item {
                            DrawerMenuItem(
                                icon = Icons.Default.Tv,
                                title = "TV ao Vivo",
                                isSelected = currentRoute == ScreenRoute.TV_LIVE.route,
                                isAccent = true,
                                onClick = {
                                    onDismiss()
                                    onNavigate(ScreenRoute.TV_LIVE.route)
                                }
                            )
                        }

                        item {
                            DrawerMenuItem(
                                icon = Icons.Default.Bookmark,
                                title = "Minha Lista",
                                isSelected = currentRoute == ScreenRoute.MY_LIST.route,
                                onClick = {
                                    onDismiss()
                                    onNavigate(ScreenRoute.MY_LIST.route)
                                }
                            )
                        }

                        item {
                            DrawerMenuItem(
                                icon = Icons.Default.AddCircleOutline,
                                title = "Pedir Filme ou Série",
                                isSelected = currentRoute == ScreenRoute.REQUEST.route,
                                onClick = {
                                    onDismiss()
                                    onNavigate(ScreenRoute.REQUEST.route)
                                }
                            )
                        }

                        item {
                            DrawerMenuItem(
                                icon = Icons.Default.Notifications,
                                title = "Notificações",
                                isSelected = currentRoute == "notifications",
                                onClick = {
                                    onDismiss()
                                    onNavigate("notifications")
                                }
                            )
                        }

                        item {
                            DrawerMenuItem(
                                icon = Icons.Default.Person,
                                title = "Meu Perfil",
                                isSelected = currentRoute == ScreenRoute.PROFILE.route,
                                onClick = {
                                    onDismiss()
                                    onNavigate(ScreenRoute.PROFILE.route)
                                }
                            )
                        }

                        item {
                            DrawerMenuItem(
                                icon = Icons.Default.Settings,
                                title = "Definições e Conta",
                                isSelected = currentRoute == "settings",
                                onClick = {
                                    onDismiss()
                                    onNavigate("settings")
                                }
                            )
                        }

                        item {
                            DrawerMenuItem(
                                icon = Icons.Default.Info,
                                title = "Informações e Contato",
                                isSelected = currentRoute == "info",
                                onClick = {
                                    onDismiss()
                                    onNavigate("info")
                                }
                            )
                        }

                        if (isAdmin) {
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                DrawerMenuItem(
                                    icon = Icons.Default.AdminPanelSettings,
                                    title = "Painel Administrativo",
                                    isSelected = currentRoute == "admin",
                                    isAccent = true,
                                    onClick = {
                                        onDismiss()
                                        onNavigate("admin")
                                    }
                                )
                            }
                        }
                    }

                    // --- BOTTOM FOOTER ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HorizontalDivider(
                            color = CardBorder.copy(alpha = 0.3f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "RONYCINE v1.0 • Streaming Premium",
                            color = TextSecondary.copy(alpha = 0.7f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    title: String,
    isSelected: Boolean = false,
    isAccent: Boolean = false,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> BrandRed.copy(alpha = 0.16f)
        isAccent -> BrandRed.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    val contentColor = when {
        isSelected -> BrandRed
        isAccent -> Color.White
        else -> Color.LightGray
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = if (isSelected) BorderStroke(0.8.dp, BrandRed.copy(alpha = 0.5f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected || isAccent) BrandRed else TextSecondary,
                modifier = Modifier.size(19.dp)
            )

            Text(
                text = title,
                color = contentColor,
                fontSize = 13.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
