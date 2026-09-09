package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandRedDark

enum class ScreenRoute(val route: String, val title: String) {
    HOME("home", "Início"),
    SEARCH("search", "Explorar"),
    TV_LIVE("tv", "TV ao Vivo"),
    TRENDING("trending", "Em Alta"),
    MOVIES("movies", "Filmes"),
    MY_LIST("my-list", "Minha Lista"),
    COMMUNITY("community", "Comunidade"),
    SERIES("series", "Séries"),
    PROFILE("profile", "Perfil"),
    REQUEST("request", "Pedidos"),
    ANIMES_DORAMAS("animes_doramas", "ANIMES & DORAMAS"),
    LOGIN("login", "Entrar"),
    PROFILE_SELECTION("profile_selection", "Quem está assistindo?"),
    CREATE_PROFILE("create_profile", "Criar Perfil")
}

private data class NavItemData(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val accentColor: Color,
    val testTag: String
)

@Composable
fun BottomNav(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    hasCommunityNotification: Boolean = false,
    communityUnreadCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val titleHome = com.example.util.stringI18n("nav.home")
    val titleExplore = com.example.util.stringI18n("nav.explore")
    val titleLiveTv = com.example.util.stringI18n("nav.liveTV")
    val titleTrending = com.example.util.stringI18n("nav.trending")
    val titleCommunity = com.example.util.stringI18n("nav.community")

    val navItems = remember(titleHome, titleExplore, titleLiveTv, titleTrending, titleCommunity) {
        listOf(
            NavItemData(
                route = ScreenRoute.HOME.route,
                title = titleHome,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                accentColor = BrandRed,
                testTag = "nav_item_home"
            ),
            NavItemData(
                route = ScreenRoute.SEARCH.route,
                title = titleExplore,
                selectedIcon = Icons.Filled.Explore,
                unselectedIcon = Icons.Outlined.Explore,
                accentColor = BrandRed,
                testTag = "nav_item_explore"
            ),
            NavItemData(
                route = ScreenRoute.COMMUNITY.route,
                title = titleCommunity,
                selectedIcon = Icons.Filled.Groups,
                unselectedIcon = Icons.Outlined.Groups,
                accentColor = BrandRed,
                testTag = "nav_item_community"
            ),
            NavItemData(
                route = ScreenRoute.TV_LIVE.route,
                title = titleLiveTv,
                selectedIcon = Icons.Filled.LiveTv,
                unselectedIcon = Icons.Outlined.LiveTv,
                accentColor = BrandRed,
                testTag = "nav_item_tv"
            ),
            NavItemData(
                route = ScreenRoute.TRENDING.route,
                title = titleTrending,
                selectedIcon = Icons.Filled.Whatshot,
                unselectedIcon = Icons.Outlined.Whatshot,
                accentColor = BrandRed,
                testTag = "nav_item_trending"
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Color.Black,
                spotColor = BrandRed.copy(alpha = 0.25f)
            )
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xE608080C)) // Deep dark com leve glass
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.02f)
                    )
                ),
                shape = RoundedCornerShape(26.dp)
            )
            .testTag("bottom_navigation")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                val isSelected = currentRoute == item.route ||
                        (item.route == ScreenRoute.TRENDING.route && (currentRoute == "trending" || currentRoute == "movies" || currentRoute == "series")) ||
                        (item.route == ScreenRoute.SEARCH.route && currentRoute == "search") ||
                        (item.route == ScreenRoute.TV_LIVE.route && (currentRoute == "tv" || currentRoute.startsWith("tv/")))

                BottomNavItem(
                    item = item,
                    isSelected = isSelected,
                    onClick = { onNavigate(item.route) },
                    hasCommunityNotification = hasCommunityNotification,
                    communityUnreadCount = communityUnreadCount,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    item: NavItemData,
    isSelected: Boolean,
    onClick: () -> Unit,
    hasCommunityNotification: Boolean = false,
    communityUnreadCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val isCommunity = item.route == ScreenRoute.COMMUNITY.route
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animação rápida de escala ao tocar (feedback tátil e responsivo)
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "nav_press_scale"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color.White
            isCommunity -> Color(0xFFC4C4D0)
            else -> Color(0xFF9E9EAF)
        },
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "iconColor"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color.White
            isCommunity -> Color(0xFFB0B0C0)
            else -> Color(0xFF8E8E93)
        },
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "textColor"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = BrandRed.copy(alpha = 0.2f)),
                onClick = onClick
            )
            .testTag(item.testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
        ) {
            // Container do Ícone (Botão Normal vs Ativo)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(28.dp)
                    .width(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (isSelected) {
                            if (isCommunity) {
                                // Destaque especial vermelho RONYCINE para a Comunidade ativa
                                Modifier
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = RoundedCornerShape(14.dp),
                                        ambientColor = BrandRed,
                                        spotColor = BrandRed
                                    )
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(BrandRed, BrandRedDark)
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .border(
                                        border = BorderStroke(1.dp, Color(0x66FFA4A4)),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            } else {
                                // Itens ativos normais com fundo sutil vermelho
                                Modifier
                                    .background(
                                        color = BrandRed.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .border(
                                        border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            }
                        } else {
                            if (isCommunity) {
                                // Comunidade inativa: fundo escuro sutil com borda discreta
                                Modifier
                                    .background(
                                        color = Color(0x1AFFFFFF),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .border(
                                        border = BorderStroke(0.7.dp, Color(0x24FFFFFF)),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            } else {
                                Modifier
                            }
                        }
                    )
            ) {
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.title,
                    tint = iconColor,
                    modifier = Modifier.size(if (isCommunity) 21.dp else 20.dp)
                )

                // Badge de Notificação da Comunidade
                if (isCommunity && (hasCommunityNotification || communityUnreadCount > 0)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-2).dp)
                    ) {
                        if (communityUnreadCount > 0) {
                            val displayText = if (communityUnreadCount > 99) "99+" else "$communityUnreadCount"
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 14.dp, minHeight = 14.dp)
                                    .background(BrandRed, RoundedCornerShape(7.dp))
                                    .border(1.dp, Color(0xFF08080C), RoundedCornerShape(7.dp))
                                    .padding(horizontal = 3.dp, vertical = 0.5.dp)
                            ) {
                                Text(
                                    text = displayText,
                                    color = Color.White,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 10.sp
                                )
                            }
                        } else {
                            // Ponto vermelho sutil
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(BrandRed, CircleShape)
                                    .border(1.dp, Color(0xFF08080C), CircleShape)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.5.dp))

            Text(
                text = item.title,
                color = textColor,
                fontSize = 10.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = if (isSelected) 0.1.sp else 0.sp,
                maxLines = 1
            )
        }
    }
}
