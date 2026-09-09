package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary

/**
 * Modern, clean, professional streaming TopBar for RONYCINE.
 * Structure: ☰   [ 🔍 Pesquisar... ✕ ]   🔔   👤
 * Removed all title/logo text to keep a clean, modern streaming header.
 */
@Composable
fun TopBar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    currentRoute: String,
    onNavigateToProfile: () -> Unit,
    onNavigateToRequest: () -> Unit,
    activeProfile: com.example.data.remote.UserProfile? = null,
    onNavigateToNotifications: () -> Unit = {},
    onMenuClick: (() -> Unit)? = null,
    onClearSearch: (() -> Unit)? = null,
    unreadNotificationCount: Int = 0,
    syncStatus: com.example.data.remote.SyncStatus? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground.copy(alpha = 0.98f))
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Left: Hamburger Menu (☰)
        IconButton(
            onClick = { onMenuClick?.invoke() ?: onNavigateToRequest() },
            modifier = Modifier
                .size(40.dp)
                .testTag("topbar_menu_button")
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu Principal",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // 2. Center: Search Input
        TextField(
            value = searchQuery,
            onValueChange = { newQuery ->
                onSearchQueryChanged(newQuery)
            },
            placeholder = {
                Text(
                    text = com.example.util.stringI18n("search.placeholder"),
                    color = TextSecondary,
                    fontSize = 12.5.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Pesquisar",
                    tint = TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            if (onClearSearch != null) {
                                onClearSearch()
                            } else {
                                onSearchQueryChanged("")
                            }
                        },
                        modifier = Modifier.size(28.dp).testTag("topbar_clear_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpar Pesquisa",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(48.dp)
                .testTag("topbar_search_input")
        )

        // 3. Right Actions: Notifications (🔔) + Profile (👤)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Notifications with live badge
            IconButton(
                onClick = onNavigateToNotifications,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("topbar_notifications_button")
            ) {
                BadgedBox(
                    badge = {
                        if (unreadNotificationCount > 0) {
                            Badge(
                                containerColor = BrandRed,
                                contentColor = Color.White,
                                modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                            ) {
                                Text(
                                    text = if (unreadNotificationCount > 99) "99+" else "$unreadNotificationCount",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notificações",
                        tint = if (unreadNotificationCount > 0) BrandRed else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Profile Button (Avatar / 👤)
            ProfileAvatar(
                profile = activeProfile,
                size = 34.dp,
                borderWidth = 1.5.dp,
                borderColor = BrandRed,
                testTag = "topbar_profile_button",
                onClick = onNavigateToProfile
            )
        }
    }
}
