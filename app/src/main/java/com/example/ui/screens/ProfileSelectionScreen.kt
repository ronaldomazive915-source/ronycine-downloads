package com.example.ui.screens

import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.remote.UserProfile
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.VerifiedBadge
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Fallback high-res posters for instant rich cinematic backdrop
private val FALLBACK_CINEMATIC_POSTERS = listOf(
    "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
    "https://image.tmdb.org/t/p/w500/r2J02Z2OpNTctfOSN2Ydg39OF8n.jpg",
    "https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911r6m7haRef0WH.jpg",
    "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
    "https://image.tmdb.org/t/p/w500/ggFHVNu6YYI5L9pCfOacjizRGt.jpg",
    "https://image.tmdb.org/t/p/w500/49WJfeN0moxb9IPfGn8AIqMGskD.jpg",
    "https://image.tmdb.org/t/p/w500/q6y0Go1tsGEsmtFryDOJo3dEmqu.jpg",
    "https://image.tmdb.org/t/p/w500/3bhkrj58Vtu7enYsRolD1fZdja1.jpg",
    "https://image.tmdb.org/t/p/w500/7WsyChQLEftFiDOVTGkv3hFpyyt.jpg",
    "https://image.tmdb.org/t/p/w500/A4j8S6mo02StdoHf8MNfTSssPM0.jpg",
    "https://image.tmdb.org/t/p/w500/d5NXSklXo0qyIYkgV94XAgMIckC.jpg",
    "https://image.tmdb.org/t/p/w500/1E5baAaEse26fej7uHcjOgEE2t2.jpg"
)

@Composable
fun ProfileSelectionScreen(
    authViewModel: AuthViewModel,
    onNavigateToCreateProfile: () -> Unit,
    onNavigateToEditProfile: (UserProfile) -> Unit,
    onProfileSelected: () -> Unit
) {
    val profiles by authViewModel.userProfiles.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val catalogPosters by authViewModel.catalogPosters.collectAsState()
    var isEditMode by remember { mutableStateOf(false) }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val prefersReducedMotion = remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
            )
            val duration = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0f || duration == 0f
        } catch (_: Exception) {
            false
        }
    }

    val activePosters = remember(catalogPosters) {
        if (catalogPosters.isNotEmpty()) {
            (catalogPosters + FALLBACK_CINEMATIC_POSTERS).distinct()
        } else {
            FALLBACK_CINEMATIC_POSTERS
        }
    }

    // Infinite animation controller
    val infiniteTransition = rememberInfiniteTransition(label = "profiles_ambient_anim")

    val pulseGlowAlpha by if (prefersReducedMotion) {
        remember { mutableFloatStateOf(0.25f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0.18f,
            targetValue = 0.38f,
            animationSpec = infiniteRepeatable(
                animation = tween(5000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "profiles_pulse_glow"
        )
    }

    val scrollProgressCol1 by if (prefersReducedMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(32000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "profiles_scroll_col1"
        )
    }

    val scrollProgressCol2 by if (prefersReducedMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(36000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "profiles_scroll_col2"
        )
    }

    val scrollProgressCol3 by if (prefersReducedMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(30000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "profiles_scroll_col3"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // =========================================================================
        // 1. CINEMATIC POSTERS BACKGROUND (Very Subtle & Moving)
        // =========================================================================
        ProfilesAnimatedBackground(
            posters = activePosters,
            scrollCol1 = scrollProgressCol1,
            scrollCol2 = scrollProgressCol2,
            scrollCol3 = scrollProgressCol3,
            modifier = Modifier.fillMaxSize()
        )

        // =========================================================================
        // 2. RADIAL VIGNETTE & CRIMSON GLOW
        // =========================================================================
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Top-center crimson ambient light
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrandRed.copy(alpha = pulseGlowAlpha * 0.70f),
                        BrandRed.copy(alpha = pulseGlowAlpha * 0.25f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.5f, height * 0.15f),
                    radius = width * 0.75f
                )
            )

            // Center dark radial mask for high legibility
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        DarkBackground.copy(alpha = 0.94f),
                        DarkBackground.copy(alpha = 0.82f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.5f, height * 0.50f),
                    radius = width * 0.85f
                )
            )

            // Top and bottom gradients
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DarkBackground.copy(alpha = 0.85f),
                        Color.Transparent,
                        DarkBackground.copy(alpha = 0.95f)
                    )
                )
            )
        }

        // =========================================================================
        // 3. MAIN FOREGROUND CONTENT (Centered, Compact & Cinematic)
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .wrapContentHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // ---------------------------------------------------------------------
                // LOGO RONYCINE WITH [ RC ] MINIMALIST MARK
                // ---------------------------------------------------------------------
                ProfileHeaderLogo(pulseAlpha = pulseGlowAlpha)

                Spacer(modifier = Modifier.height(20.dp))

                // ---------------------------------------------------------------------
                // TITLE: QUEM ESTÁ ASSISTINDO?
                // ---------------------------------------------------------------------
                AnimatedContent(
                    targetState = isEditMode,
                    transitionSpec = {
                        (fadeIn(tween(250)) + scaleIn(initialScale = 0.96f))
                            .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 1.04f))
                    },
                    label = "profiles_title_anim"
                ) { editMode ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (editMode) "GERENCIAR PERFIS" else "QUEM ESTÁ ASSISTINDO?",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (editMode) "Toque em um perfil para editar" else "Escolha seu perfil para continuar",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        if (!editMode && currentUser != null && !currentUser!!.username.isNullOrBlank()) {
                            Text(
                                text = currentUser!!.username!!,
                                color = BrandRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // ---------------------------------------------------------------------
                // COMPACT & RESPONSIVE PROFILES GRID (2 to 4 per row, no vertical stretching)
                // ---------------------------------------------------------------------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 92.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        items(profiles, key = { it.id }) { profile ->
                            val isSelected = selectedProfileId == profile.id
                            CompactProfileCard(
                                profile = profile,
                                isEditMode = isEditMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isEditMode) {
                                        onNavigateToEditProfile(profile)
                                    } else {
                                        if (selectedProfileId == null) {
                                            selectedProfileId = profile.id
                                            coroutineScope.launch {
                                                delay(220) // Fast and delightful selection animation
                                                authViewModel.selectProfile(profile)
                                                onProfileSelected()
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        // "+ Adicionar" Profile Card (if less than 5 profiles)
                        if (profiles.size < 5) {
                            item(key = "add_profile_card") {
                                AddProfileCompactCard(
                                    onClick = {
                                        if (!isEditMode) {
                                            onNavigateToCreateProfile()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // ---------------------------------------------------------------------
                // GERENCIAR PERFIS / CONCLUÍDO BUTTON (Compact & Elegant)
                // ---------------------------------------------------------------------
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isEditMode = !isEditMode }
                        .testTag("manage_profiles_toggle_button"),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isEditMode) BrandRed else DarkSurface.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, if (isEditMode) BrandRed else Color.White.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEditMode) "CONCLUÍDO" else "GERENCIAR PERFIS",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// COMPACT PROFILE CARD
// =========================================================================
@Composable
private fun CompactProfileCard(
    profile: UserProfile,
    isEditMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isSelected -> 1.08f
            isPressed -> 0.94f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "profile_card_scale"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> BrandRed
            isEditMode -> BrandRed.copy(alpha = 0.7f)
            else -> Color.Transparent
        },
        label = "profile_border_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("profile_card_${profile.id}")
    ) {
        Box(
            modifier = Modifier.size(86.dp),
            contentAlignment = Alignment.Center
        ) {
            // Profile Avatar Container
            ProfileAvatar(
                profile = profile,
                size = 82.dp,
                borderWidth = 2.dp,
                borderColor = if (isSelected || isEditMode) borderColor else CardBorder,
                showCameraBadge = false,
                testTag = "profile_avatar_img_${profile.id}"
            )

            // Edit Mode Overlay Icon
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BrandRed,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Selected checkmark feedback
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BrandRed.copy(alpha = 0.30f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Text(
                text = profile.name,
                color = if (isSelected) BrandRed else Color.White,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (profile.isVerified) {
                Spacer(modifier = Modifier.width(3.dp))
                VerifiedBadge(size = 14.dp, showToastOnClick = false)
            }
        }
    }
}

// =========================================================================
// ADD PROFILE COMPACT CARD
// =========================================================================
@Composable
private fun AddProfileCompactCard(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        label = "add_profile_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("add_profile_button")
    ) {
        Surface(
            modifier = Modifier.size(82.dp),
            shape = RoundedCornerShape(14.dp),
            color = DarkSurface.copy(alpha = 0.5f),
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.20f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Adicionar Perfil",
                    tint = Color.LightGray.copy(alpha = 0.8f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Adicionar",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// =========================================================================
// HEADER LOGO FOR PROFILES
// =========================================================================
@Composable
private fun ProfileHeaderLogo(pulseAlpha: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = DarkSurface.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.40f + (pulseAlpha * 0.35f))),
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[ ",
                    color = BrandRed.copy(alpha = 0.70f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "R",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "C",
                    color = BrandRed,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = " ]",
                    color = BrandRed.copy(alpha = 0.70f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Black)) {
                    append("RONY")
                }
                withStyle(SpanStyle(color = BrandRed, fontWeight = FontWeight.Black)) {
                    append("CINE")
                }
            },
            fontSize = 26.sp,
            letterSpacing = 3.sp
        )

        Text(
            text = "SEU CINEMA PARTICULAR",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

// =========================================================================
// BACKGROUND POSTERS ANIMATION FOR PROFILES SCREEN
// =========================================================================
@Composable
private fun ProfilesAnimatedBackground(
    posters: List<String>,
    scrollCol1: Float,
    scrollCol2: Float,
    scrollCol3: Float,
    modifier: Modifier = Modifier
) {
    val col1Posters = remember(posters) {
        val list = posters.filterIndexed { i, _ -> i % 3 == 0 }.take(4)
        if (list.size < 4) list + list else list
    }
    val col2Posters = remember(posters) {
        val list = posters.filterIndexed { i, _ -> i % 3 == 1 }.take(4)
        if (list.size < 4) list + list else list
    }
    val col3Posters = remember(posters) {
        val list = posters.filterIndexed { i, _ -> i % 3 == 2 }.take(4)
        if (list.size < 4) list + list else list
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .blur(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ProfileBgColumn(posters = col1Posters, progress = scrollCol1, tilt = -4f, alpha = 0.18f, modifier = Modifier.weight(1f))
        ProfileBgColumn(posters = col2Posters, progress = scrollCol2, tilt = 2f, alpha = 0.14f, modifier = Modifier.weight(1.05f))
        ProfileBgColumn(posters = col3Posters, progress = scrollCol3, tilt = 4f, alpha = 0.18f, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ProfileBgColumn(
    posters: List<String>,
    progress: Float,
    tilt: Float,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val itemHeightDp = 180.dp
    val spacingDp = 12.dp
    val totalHeightDp = itemHeightDp + spacingDp
    val density = LocalDensity.current
    val totalHeightPx = with(density) { totalHeightDp.toPx() }
    val cycleHeightPx = posters.size * totalHeightPx
    val extendedPosters = remember(posters) { posters + posters + posters }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                rotationZ = tilt
                this.alpha = alpha
                translationY = - (progress * cycleHeightPx) % cycleHeightPx
            },
        verticalArrangement = Arrangement.spacedBy(spacingDp)
    ) {
        extendedPosters.forEach { url ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeightDp),
                shape = RoundedCornerShape(10.dp),
                color = DarkSurface
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
