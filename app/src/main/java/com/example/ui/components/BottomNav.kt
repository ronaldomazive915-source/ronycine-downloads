package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkSurface

enum class ScreenRoute(val route: String, val title: String) {
    HOME("home", "Início"),
    SEARCH("search", "Explorar"),
    TV_LIVE("tv", "TV ao Vivo"),
    TRENDING("trending", "Em Alta"),
    MOVIES("movies", "Filmes"),
    MY_LIST("my-list", "Minha Lista"),
    SERIES("series", "Séries"),
    PROFILE("profile", "Perfil"),
    REQUEST("request", "Pedidos"),
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
    modifier: Modifier = Modifier
) {
    // Exact 5 items in strict required order: Início | Explorar | TV ao Vivo | Em Alta | Minha Lista
    val navItems = remember {
        listOf(
            NavItemData(
                route = ScreenRoute.HOME.route,
                title = "Início",
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                accentColor = BrandRed,
                testTag = "nav_item_home"
            ),
            NavItemData(
                route = ScreenRoute.SEARCH.route,
                title = "Explorar",
                selectedIcon = Icons.Filled.Search,
                unselectedIcon = Icons.Outlined.Search,
                accentColor = Color(0xFF60A5FA),
                testTag = "nav_item_explore"
            ),
            NavItemData(
                route = ScreenRoute.TV_LIVE.route,
                title = "TV ao Vivo",
                selectedIcon = Icons.Filled.LiveTv,
                unselectedIcon = Icons.Outlined.LiveTv,
                accentColor = Color(0xFFEF4444),
                testTag = "nav_item_tv"
            ),
            NavItemData(
                route = ScreenRoute.TRENDING.route,
                title = "Em Alta",
                selectedIcon = Icons.Filled.Whatshot,
                unselectedIcon = Icons.Outlined.Whatshot,
                accentColor = Color(0xFFFB923C),
                testTag = "nav_item_trending"
            ),
            NavItemData(
                route = ScreenRoute.MY_LIST.route,
                title = "Minha Lista",
                selectedIcon = Icons.Filled.Favorite,
                unselectedIcon = Icons.Outlined.FavoriteBorder,
                accentColor = Color(0xFFA78BFA),
                testTag = "nav_item_my_list"
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = BrandRed.copy(alpha = 0.2f),
                spotColor = Color.Black
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE60A0A0C)) // Deep dark with subtle transparency
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.15f),
                        Color.Transparent,
                        BrandRed.copy(alpha = 0.15f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .testTag("bottom_navigation")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
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
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) item.accentColor else Color(0xFF8E8E93),
        animationSpec = tween(durationMillis = 300),
        label = "iconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF8E8E93),
        animationSpec = tween(durationMillis = 300),
        label = "textColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val indicatorWidth by animateDpAsState(
        targetValue = if (isSelected) 24.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "indicatorWidth"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = item.accentColor.copy(alpha = 0.15f)),
                onClick = onClick
            )
            .testTag(item.testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(32.dp)
                    .width(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        color = if (isSelected) item.accentColor.copy(alpha = 0.12f) else Color.Transparent
                    )
            ) {
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.title,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.title,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Selection Indicator
            Box(
                modifier = Modifier
                    .height(3.dp)
                    .width(indicatorWidth)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(item.accentColor, item.accentColor.copy(alpha = 0.6f))
                        )
                    )
            )
        }
    }
}
