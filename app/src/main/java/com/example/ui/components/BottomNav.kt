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
    CALENDAR("calendar", "Calendário"),
    AI_SUPPORT("ai_support", "Suporte AI"),
    TV_LIVE("tv", "TV ao Vivo"),
    PROFILE("profile", "Perfil"),
    TRENDING("trending", "Em Alta"),
    MOVIES("movies", "Filmes"),
    MY_LIST("my-list", "Minha Lista"),
    COMMUNITY("community", "Comunidade"),
    SERIES("series", "Séries"),
    REQUEST("request", "Pedidos"),
    ANIMES_DORAMAS("animes_doramas", "ANIMES & DORAMAS"),
    LOGIN("login", "Entrar"),
    PROFILE_SELECTION("profile_selection", "Quem está assistindo?"),
    CREATE_PROFILE("create_profile", "Criar Perfil"),
    UPDATE_SCREEN("update_screen", "Atualizações"),
    DOWNLOADS("downloads", "Downloads")
}

private data class NavItemData(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val isCenterButton: Boolean = false,
    val testTag: String
)

@Composable
fun BottomNav(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val titleHome = com.example.util.stringI18n("nav.home")
    val titleExplore = com.example.util.stringI18n("nav.explore")
    val titleCalendar = com.example.util.stringI18n("nav.calendar")
    val titleLiveTv = com.example.util.stringI18n("nav.liveTV")
    val titleProfile = com.example.util.stringI18n("nav.profile")

    val navItems = remember(titleHome, titleExplore, titleCalendar, titleLiveTv, titleProfile) {
        listOf(
            NavItemData(
                route = ScreenRoute.HOME.route,
                title = titleHome,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                testTag = "nav_item_home"
            ),
            NavItemData(
                route = ScreenRoute.SEARCH.route,
                title = titleExplore,
                selectedIcon = Icons.Filled.Explore,
                unselectedIcon = Icons.Outlined.Explore,
                testTag = "nav_item_explore"
            ),
            NavItemData(
                route = ScreenRoute.CALENDAR.route,
                title = titleCalendar,
                selectedIcon = Icons.Filled.CalendarMonth,
                unselectedIcon = Icons.Outlined.CalendarMonth,
                isCenterButton = true,
                testTag = "nav_item_calendar"
            ),
            NavItemData(
                route = ScreenRoute.TV_LIVE.route,
                title = titleLiveTv,
                selectedIcon = Icons.Filled.LiveTv,
                unselectedIcon = Icons.Outlined.LiveTv,
                testTag = "nav_item_tv"
            ),
            NavItemData(
                route = ScreenRoute.PROFILE.route,
                title = titleProfile,
                selectedIcon = Icons.Filled.Person,
                unselectedIcon = Icons.Outlined.Person,
                testTag = "nav_item_profile"
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = Color.Black,
                spotColor = BrandRed.copy(alpha = 0.3f)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xF208080E)) // Deep dark com leve translucidez
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.03f)
                    )
                ),
                shape = RoundedCornerShape(28.dp)
            )
            .testTag("bottom_navigation")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                val isSelected = currentRoute == item.route ||
                        (item.route == ScreenRoute.SEARCH.route && currentRoute == "search") ||
                        (item.route == ScreenRoute.CALENDAR.route && (currentRoute == "calendar" || currentRoute == ScreenRoute.CALENDAR.route)) ||
                        (item.route == ScreenRoute.TV_LIVE.route && (currentRoute == "tv" || currentRoute.startsWith("tv/"))) ||
                        (item.route == ScreenRoute.PROFILE.route && (currentRoute == "profile" || currentRoute == ScreenRoute.PROFILE.route))

                if (item.isCenterButton) {
                    CenterCalendarNavItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onNavigate(item.route) },
                        modifier = Modifier.weight(1.15f)
                    )
                } else {
                    StandardBottomNavItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onNavigate(item.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterCalendarNavItem(
    item: NavItemData,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else if (isSelected) 1.05f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "center_press_scale"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.5f else 0.2f,
        animationSpec = tween(durationMillis = 200),
        label = "glow_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = BrandRed.copy(alpha = 0.3f)),
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
            // Botão Circular Central
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .shadow(
                        elevation = if (isSelected) 10.dp else 4.dp,
                        shape = CircleShape,
                        ambientColor = BrandRed,
                        spotColor = BrandRed.copy(alpha = glowAlpha)
                    )
                    .background(
                        brush = if (isSelected) {
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFE50914), Color(0xFF9E0B0F))
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF221417), Color(0xFF140D0F))
                            )
                        },
                        shape = CircleShape
                    )
                    .border(
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) Color(0xFFFFB3B3) else Color(0x55E50914)
                        ),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.title,
                    tint = if (isSelected) Color.White else Color(0xFFFFAEAE),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.5.dp))

            Text(
                text = item.title,
                color = if (isSelected) Color.White else Color(0xFFFFA4A4),
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                letterSpacing = 0.1.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StandardBottomNavItem(
    item: NavItemData,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "nav_press_scale"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF9494A3),
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "iconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF80808F),
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
            // Container do Ícone
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(28.dp)
                    .width(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(
                                    color = BrandRed.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .border(
                                    border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.45f)),
                                    shape = RoundedCornerShape(14.dp)
                                )
                        } else {
                            Modifier
                        }
                    )
            ) {
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.title,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = item.title,
                color = textColor,
                fontSize = 10.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
