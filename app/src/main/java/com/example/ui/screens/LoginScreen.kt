package com.example.ui.screens

import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.UsernameCheckState
import com.example.util.UsernameUtils

private data class AmbientParticle(
    val initialX: Float,
    val initialY: Float,
    val radiusDp: Float,
    val speed: Float,
    val baseAlpha: Float
)

// Fallback high-res posters for instant rich cinematic backdrop
private val FALLBACK_CINEMATIC_POSTERS = listOf(
    "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg", // Oppenheimer
    "https://image.tmdb.org/t/p/w500/r2J02Z2OpNTctfOSN2Ydg39OF8n.jpg", // Dune 2
    "https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911r6m7haRef0WH.jpg", // The Dark Knight
    "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg", // Interstellar
    "https://image.tmdb.org/t/p/w500/ggFHVNu6YYI5L9pCfOacjizRGt.jpg", // Breaking Bad
    "https://image.tmdb.org/t/p/w500/49WJfeN0moxb9IPfGn8AIqMGskD.jpg", // Stranger Things
    "https://image.tmdb.org/t/p/w500/q6y0Go1tsGEsmtFryDOJo3dEmqu.jpg", // Shawshank Redemption
    "https://image.tmdb.org/t/p/w500/3bhkrj58Vtu7enYsRolD1fZdja1.jpg", // The Godfather
    "https://image.tmdb.org/t/p/w500/7WsyChQLEftFiDOVTGkv3hFpyyt.jpg", // Avengers Infinity War
    "https://image.tmdb.org/t/p/w500/A4j8S6mo02StdoHf8MNfTSssPM0.jpg", // Spider-Man
    "https://image.tmdb.org/t/p/w500/d5NXSklXo0qyIYkgV94XAgMIckC.jpg", // Dune
    "https://image.tmdb.org/t/p/w500/1E5baAaEse26fej7uHcjOgEE2t2.jpg"  // Fast X
)

private enum class LoginStep {
    IDENTIFIER,
    PASSWORD
}

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var isSignUpMode by remember { mutableStateOf(false) }
    var loginStep by remember { mutableStateOf(LoginStep.IDENTIFIER) }

    // State for Login
    var userIdentifier by remember { mutableStateOf("") }
    var resolvedEmail by remember { mutableStateOf("") }
    var displayIdentifier by remember { mutableStateOf("") }
    var isUsernameDetected by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isResolvingIdentifier by remember { mutableStateOf(false) }

    // State for Sign Up
    var signUpName by remember { mutableStateOf("") }
    var signUpUsername by remember { mutableStateOf("") }
    var signUpEmail by remember { mutableStateOf("") }
    var signUpPassword by remember { mutableStateOf("") }
    var signUpConfirmPassword by remember { mutableStateOf("") }
    var signUpPasswordVisible by remember { mutableStateOf(false) }
    var signUpConfirmVisible by remember { mutableStateOf(false) }

    // Forgot Password
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotPasswordInput by remember { mutableStateOf("") }

    val authState by viewModel.authState.collectAsState()
    val usernameCheckState by viewModel.usernameCheckState.collectAsState()
    val catalogPosters by viewModel.catalogPosters.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Check system preference for reduced motion
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

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onLoginSuccess()
        }
    }

    // Combine real catalog posters with fallbacks for immediate rich backdrop
    val activePosters = remember(catalogPosters) {
        if (catalogPosters.isNotEmpty()) {
            (catalogPosters + FALLBACK_CINEMATIC_POSTERS).distinct()
        } else {
            FALLBACK_CINEMATIC_POSTERS
        }
    }

    // Centralized Infinite Animation Controller
    val infiniteTransition = rememberInfiniteTransition(label = "login_ambient_anim")

    // Slow ambient breathing glow
    val pulseGlowAlpha by if (prefersReducedMotion) {
        remember { mutableFloatStateOf(0.30f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0.20f,
            targetValue = 0.42f,
            animationSpec = infiniteRepeatable(
                animation = tween(4500, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_glow"
        )
    }

    // Moving light streak / ambient beam position (0f to 1f)
    val lightBeamProgress by if (prefersReducedMotion) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(12000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "light_beam"
        )
    }

    // Continuous looping progress for poster continuous streaming motion (0f to 1f)
    val scrollProgressCol1 by if (prefersReducedMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(28000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "scroll_col1"
        )
    }

    val scrollProgressCol2 by if (prefersReducedMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(34000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "scroll_col2"
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
            label = "scroll_col3"
        )
    }

    // Slow horizontal rear panning (0f to 1f)
    val rearPanProgress by if (prefersReducedMotion) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(40000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rear_pan"
        )
    }

    // Floating particles offset
    val particleOffsetFraction by if (prefersReducedMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(16000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ambient_particles"
        )
    }

    // Parallax float
    val frontParallaxFloat by if (prefersReducedMotion) {
        remember { mutableFloatStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = -12f,
            targetValue = 12f,
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "front_parallax"
        )
    }

    val particles = remember {
        listOf(
            AmbientParticle(0.12f, 0.18f, 2.2f, 0.6f, 0.35f),
            AmbientParticle(0.85f, 0.14f, 3.0f, 0.8f, 0.28f),
            AmbientParticle(0.20f, 0.70f, 1.8f, 0.5f, 0.25f),
            AmbientParticle(0.80f, 0.82f, 2.6f, 0.7f, 0.30f),
            AmbientParticle(0.48f, 0.32f, 2.0f, 0.4f, 0.22f),
            AmbientParticle(0.92f, 0.50f, 2.5f, 0.9f, 0.20f),
            AmbientParticle(0.06f, 0.86f, 2.8f, 0.65f, 0.24f),
            AmbientParticle(0.38f, 0.94f, 2.2f, 0.75f, 0.28f)
        )
    }

    // Handle continuing from Identifier to Password step
    val onContinueIdentifier: () -> Unit = {
        val input = userIdentifier.trim()
        if (input.isBlank()) {
            Toast.makeText(context, "Digite seu e-mail ou @nome de usuário.", Toast.LENGTH_SHORT).show()
        } else {
            isResolvingIdentifier = true
            viewModel.clearError()
            viewModel.resolveIdentifier(
                identifier = input,
                onSuccess = { email, display, isUsername ->
                    isResolvingIdentifier = false
                    resolvedEmail = email
                    displayIdentifier = display
                    isUsernameDetected = isUsername
                    loginStep = LoginStep.PASSWORD
                },
                onError = { err ->
                    isResolvingIdentifier = false
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // =========================================================================
        // 1. DYNAMIC CINEMATIC POSTER BACKGROUND
        // =========================================================================
        AnimatedCinematicPosterCanvas(
            posters = activePosters,
            scrollCol1 = scrollProgressCol1,
            scrollCol2 = scrollProgressCol2,
            scrollCol3 = scrollProgressCol3,
            rearPan = rearPanProgress,
            frontFloat = frontParallaxFloat,
            modifier = Modifier.fillMaxSize()
        )

        // =========================================================================
        // 2. CINEMATIC GRADIENT MASKS, VIGNETTE & MOVING LIGHT
        // =========================================================================
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val beamX = width * (0.2f + (lightBeamProgress * 0.6f))
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrandRed.copy(alpha = pulseGlowAlpha * 0.85f),
                        BrandRed.copy(alpha = pulseGlowAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(beamX, height * 0.12f),
                    radius = width * 0.75f
                )
            )

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        DarkBackground.copy(alpha = 0.94f),
                        DarkBackground.copy(alpha = 0.80f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.5f, height * 0.53f),
                    radius = width * 0.88f
                )
            )

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        DarkBackground.copy(alpha = 0.75f),
                        DarkBackground.copy(alpha = 0.98f)
                    ),
                    startY = height * 0.60f,
                    endY = height
                )
            )

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DarkBackground.copy(alpha = 0.80f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = height * 0.20f
                )
            )

            particles.forEach { p ->
                val dynamicY = (p.initialY - (particleOffsetFraction * p.speed)) % 1f
                val normalizedY = if (dynamicY < 0f) dynamicY + 1f else dynamicY
                val px = p.initialX * width
                val py = normalizedY * height

                drawCircle(
                    color = BrandRed.copy(alpha = p.baseAlpha * (0.7f + (pulseGlowAlpha * 0.5f))),
                    radius = p.radiusDp.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        // =========================================================================
        // 3. MAIN FOREGROUND INTERACTION COLUMN
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ---------------------------------------------------------------------
            // LOGO RONYCINE WITH [ RC ] MINIMALIST BRAND MARK
            // ---------------------------------------------------------------------
            RonycineHeaderLogo(
                pulseAlpha = pulseGlowAlpha,
                modifier = Modifier.testTag("app_brand_logo")
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ---------------------------------------------------------------------
            // AUTHENTICATION CONTAINER
            // ---------------------------------------------------------------------
            Surface(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = BrandRed.copy(alpha = 0.18f),
                        spotColor = BrandRed.copy(alpha = 0.30f)
                    ),
                shape = RoundedCornerShape(18.dp),
                color = DarkSurface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.22f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Navigation Switch: [ ENTRAR ] [ CRIAR CONTA ]
                    AuthNavigationTabs(
                        isSignUpMode = isSignUpMode,
                        onTabSelected = { signUp ->
                            if (isSignUpMode != signUp) {
                                isSignUpMode = signUp
                                loginStep = LoginStep.IDENTIFIER
                                viewModel.clearError()
                                viewModel.clearUsernameCheckState()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    if (isSignUpMode) {
                        // =========================================================
                        // SIGN UP MODE (CADASTRO COM @NOME DE USUÁRIO)
                        // =========================================================
                        Text(
                            text = "CRIAR CONTA NO RONYCINE",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp
                        )

                        Text(
                            text = "Escolha seu @nome de usuário e acesse seus conteúdos.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 3.dp, bottom = 14.dp)
                        )

                        // 1. Nome Completo
                        LoginTextField(
                            value = signUpName,
                            onValueChange = {
                                signUpName = it
                                viewModel.clearError()
                            },
                            label = "Nome Completo",
                            leadingIcon = Icons.Default.Badge,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                            testTag = "signup_fullname_input"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2. @Nome de Usuário (Com validação e debounce em tempo real)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LoginTextField(
                                value = signUpUsername,
                                onValueChange = { raw ->
                                    val cleaned = raw.trim()
                                    signUpUsername = cleaned
                                    viewModel.clearError()
                                    viewModel.checkUsernameAvailability(cleaned)
                                },
                                label = "@Nome de usuário",
                                leadingIcon = Icons.Default.AlternateEmail,
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next,
                                testTag = "signup_username_input"
                            )

                            // Status em tempo real do username
                            when (val state = usernameCheckState) {
                                is UsernameCheckState.Checking -> {
                                    Row(
                                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            color = BrandRed,
                                            strokeWidth = 1.5.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Verificando disponibilidade...",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                is UsernameCheckState.Available -> {
                                    Row(
                                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "${state.formattedUsername} disponível",
                                            color = Color(0xFF10B981),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                is UsernameCheckState.Unavailable -> {
                                    Row(
                                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = state.reason,
                                            color = Color(0xFFEF4444),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                else -> {}
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. E-mail
                        LoginTextField(
                            value = signUpEmail,
                            onValueChange = {
                                signUpEmail = it
                                viewModel.clearError()
                            },
                            label = "E-mail",
                            leadingIcon = Icons.Default.Email,
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                            testTag = "signup_email_input"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 4. Senha
                        LoginTextField(
                            value = signUpPassword,
                            onValueChange = {
                                signUpPassword = it
                                viewModel.clearError()
                            },
                            label = "Senha (mínimo 6 caracteres)",
                            leadingIcon = Icons.Default.Lock,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                            isPassword = true,
                            passwordVisible = signUpPasswordVisible,
                            onTogglePassword = { signUpPasswordVisible = !signUpPasswordVisible },
                            testTag = "signup_password_input"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 5. Confirmar Senha
                        LoginTextField(
                            value = signUpConfirmPassword,
                            onValueChange = {
                                signUpConfirmPassword = it
                                viewModel.clearError()
                            },
                            label = "Confirmar Senha",
                            leadingIcon = Icons.Default.LockReset,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                            isPassword = true,
                            passwordVisible = signUpConfirmVisible,
                            onTogglePassword = { signUpConfirmVisible = !signUpConfirmVisible },
                            testTag = "signup_confirm_password_input",
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.signUp(signUpName, signUpUsername, signUpEmail, signUpPassword, signUpConfirmPassword)
                            }
                        )

                        // Auth Error Banner
                        if (authState is AuthState.Error) {
                            val errorMsg = (authState as AuthState.Error).message
                            Spacer(modifier = Modifier.height(12.dp))
                            AuthErrorBanner(errorMsg)
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        MainAuthActionButton(
                            buttonText = "CRIAR CONTA",
                            loadingText = "CRIANDO CONTA...",
                            successText = "CONTA CRIADA",
                            leadingIcon = Icons.Default.PersonAdd,
                            isLoading = authState is AuthState.Loading,
                            isSuccess = authState is AuthState.Success,
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.signUp(signUpName, signUpUsername, signUpEmail, signUpPassword, signUpConfirmPassword)
                            },
                            testTag = "create_account_button"
                        )

                    } else {
                        // =========================================================
                        // 2-STEP LOGIN FLOW (ENTRAR NO RONYCINE -> CONFIRME SUA SENHA)
                        // =========================================================
                        AnimatedContent(
                            targetState = loginStep,
                            transitionSpec = {
                                if (targetState == LoginStep.PASSWORD) {
                                    (slideInHorizontally { width -> width / 2 } + fadeIn(tween(250)))
                                        .togetherWith(slideOutHorizontally { width -> -width / 2 } + fadeOut(tween(200)))
                                } else {
                                    (slideInHorizontally { width -> -width / 2 } + fadeIn(tween(250)))
                                        .togetherWith(slideOutHorizontally { width -> width / 2 } + fadeOut(tween(200)))
                                }
                            },
                            label = "login_steps_anim"
                        ) { step ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                when (step) {
                                    LoginStep.IDENTIFIER -> {
                                        // -----------------------------------------
                                        // STEP 1: ENTRAR NO RONYCINE (E-mail ou nome de usuário)
                                        // -----------------------------------------
                                        Text(
                                            text = "ENTRAR NO RONYCINE",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.4.sp
                                        )

                                        Text(
                                            text = "Digite seu e-mail ou @nome de usuário.",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 3.dp, bottom = 16.dp)
                                        )

                                        LoginTextField(
                                            value = userIdentifier,
                                            onValueChange = {
                                                userIdentifier = it
                                                viewModel.clearError()
                                            },
                                            label = "E-mail ou @nome de usuário",
                                            leadingIcon = Icons.Default.AlternateEmail,
                                            keyboardType = KeyboardType.Email,
                                            imeAction = ImeAction.Done,
                                            testTag = "login_identifier_input",
                                            onDone = {
                                                focusManager.clearFocus()
                                                onContinueIdentifier()
                                            }
                                        )

                                        // Auth Error Banner
                                        if (authState is AuthState.Error) {
                                            val errorMsg = (authState as AuthState.Error).message
                                            Spacer(modifier = Modifier.height(12.dp))
                                            AuthErrorBanner(errorMsg)
                                        }

                                        Spacer(modifier = Modifier.height(18.dp))

                                        // CONTINUAR Button
                                        MainAuthActionButton(
                                            buttonText = "CONTINUAR",
                                            loadingText = "VERIFICANDO...",
                                            successText = "CONTINUANDO...",
                                            leadingIcon = Icons.Default.ArrowForward,
                                            isLoading = isResolvingIdentifier || (authState is AuthState.Loading && loginStep == LoginStep.IDENTIFIER),
                                            isSuccess = false,
                                            onClick = {
                                                focusManager.clearFocus()
                                                onContinueIdentifier()
                                            },
                                            testTag = "login_continue_button"
                                        )
                                    }

                                    LoginStep.PASSWORD -> {
                                        // -----------------------------------------
                                        // STEP 2: CONFIRME SUA SENHA
                                        // -----------------------------------------
                                        Text(
                                            text = "CONFIRME SUA SENHA",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.4.sp
                                        )

                                        Text(
                                            text = "Digite sua senha para continuar.",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 3.dp, bottom = 12.dp)
                                        )

                                        // Elegant Identifier Card Badge
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    viewModel.clearError()
                                                    loginStep = LoginStep.IDENTIFIER
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            color = DarkBackground.copy(alpha = 0.85f),
                                            border = BorderStroke(1.dp, CardBorder)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isUsernameDetected) Icons.Default.AccountCircle else Icons.Default.Email,
                                                    contentDescription = null,
                                                    tint = BrandRed,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (isUsernameDetected) displayIdentifier else resolvedEmail,
                                                        color = Color.White,
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (isUsernameDetected && resolvedEmail.isNotBlank()) {
                                                        Text(
                                                            text = resolvedEmail,
                                                            color = TextSecondary,
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Alterar",
                                                    color = BrandRed,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Password Field
                                        LoginTextField(
                                            value = password,
                                            onValueChange = {
                                                password = it
                                                viewModel.clearError()
                                            },
                                            label = "Senha",
                                            leadingIcon = Icons.Default.Lock,
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done,
                                            isPassword = true,
                                            passwordVisible = passwordVisible,
                                            onTogglePassword = { passwordVisible = !passwordVisible },
                                            testTag = "login_password_input",
                                            onDone = {
                                                focusManager.clearFocus()
                                                viewModel.signIn(resolvedEmail, password)
                                            }
                                        )

                                        // Forgot password link
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp, bottom = 2.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Text(
                                                text = "Esqueci minha senha",
                                                color = BrandRed.copy(alpha = 0.95f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        forgotPasswordInput = if (isUsernameDetected) displayIdentifier else resolvedEmail
                                                        showForgotPasswordDialog = true
                                                    }
                                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                                                    .testTag("forgot_password_button")
                                            )
                                        }

                                        // Auth Error Banner
                                        if (authState is AuthState.Error) {
                                            val errorMsg = (authState as AuthState.Error).message
                                            Spacer(modifier = Modifier.height(10.dp))
                                            AuthErrorBanner(errorMsg)
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // ENTRAR Button
                                        MainAuthActionButton(
                                            buttonText = "ENTRAR",
                                            loadingText = "ENTRANDO...",
                                            successText = "LOGIN REALIZADO",
                                            leadingIcon = Icons.Default.Login,
                                            isLoading = authState is AuthState.Loading,
                                            isSuccess = authState is AuthState.Success,
                                            onClick = {
                                                focusManager.clearFocus()
                                                viewModel.signIn(resolvedEmail, password)
                                            },
                                            testTag = "login_button"
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Back to identifier step
                                        TextButton(
                                            onClick = {
                                                viewModel.clearError()
                                                loginStep = LoginStep.IDENTIFIER
                                            },
                                            modifier = Modifier.testTag("back_to_identifier_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Voltar",
                                                color = TextSecondary,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ---------------------------------------------------------------------
            // CONTINUAR COMO VISITANTE (GUEST ACCESS)
            // ---------------------------------------------------------------------
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable {
                        focusManager.clearFocus()
                        onNavigateBack()
                    }
                    .testTag("continue_as_guest_button"),
                color = DarkSurface.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = Color.LightGray.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Continuar como Visitante",
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // =========================================================================
    // 4. PASSWORD RECOVERY DIALOG (SUPPORTS EMAIL OR @USERNAME)
    // =========================================================================
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LockReset,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RECUPERAR SENHA",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Digite seu e-mail ou @nome de usuário cadastrado para receber o link seguro de redefinição de senha.",
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )
                    LoginTextField(
                        value = forgotPasswordInput,
                        onValueChange = { forgotPasswordInput = it },
                        label = "E-mail ou @nome de usuário",
                        leadingIcon = Icons.Default.AlternateEmail,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                        testTag = "forgot_password_input"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = forgotPasswordInput.trim()
                        if (target.isBlank()) {
                            Toast.makeText(context, "Informe seu e-mail ou @nome de usuário.", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.resetPassword(target) {
                                showForgotPasswordDialog = false
                                Toast.makeText(context, "Link de recuperação enviado com sucesso!", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_reset_password_button")
                ) {
                    Text("ENVIAR LINK", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForgotPasswordDialog = false },
                    modifier = Modifier.testTag("cancel_reset_password_button")
                ) {
                    Text("CANCELAR", color = Color.Gray, fontSize = 13.sp)
                }
            }
        )
    }
}

// =========================================================================
// ERROR BANNER COMPONENT
// =========================================================================
@Composable
private fun AuthErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BrandRed.copy(alpha = 0.16f))
            .border(1.dp, BrandRed.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = BrandRed,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = message,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp
        )
    }
}

// =========================================================================
// CONTINUOUS ANIMATED CINEMATIC POSTER CANVAS (3 DEPTH LAYERS)
// =========================================================================
@Composable
private fun AnimatedCinematicPosterCanvas(
    posters: List<String>,
    scrollCol1: Float,
    scrollCol2: Float,
    scrollCol3: Float,
    rearPan: Float,
    frontFloat: Float,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    val rearPosters = remember(posters) { posters.take(4) }
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

    Box(modifier = modifier.fillMaxSize()) {
        // LAYER 1: CAMADA TRASEIRA
        Row(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 0.18f
                    translationX = (rearPan - 0.5f) * (screenWidthPx * 0.25f)
                    scaleX = 1.15f
                    scaleY = 1.15f
                }
                .blur(8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            rearPosters.forEach { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp)
                )
            }
        }

        // LAYER 2: CAMADA CENTRAL
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ContinuousRollingColumn(
                posters = col1Posters,
                progress = scrollCol1,
                tiltDegrees = -5f,
                alpha = 0.38f,
                itemHeightDp = 150.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp)
            )

            ContinuousRollingColumn(
                posters = col2Posters,
                progress = scrollCol2,
                tiltDegrees = 0f,
                alpha = 0.44f,
                itemHeightDp = 160.dp,
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp)
            )

            ContinuousRollingColumn(
                posters = col3Posters,
                progress = scrollCol3,
                tiltDegrees = 5f,
                alpha = 0.38f,
                itemHeightDp = 150.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp)
            )
        }

        // LAYER 3: CAMADA FRONTAL DESTAQUES
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 45.dp, end = 12.dp)
                .graphicsLayer {
                    translationY = frontFloat
                    rotationZ = 7f
                    alpha = 0.22f
                }
        ) {
            PosterThumbnailCard(
                url = posters[0 % posters.size],
                width = 85.dp,
                height = 125.dp
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 60.dp, start = 12.dp)
                .graphicsLayer {
                    translationY = -frontFloat
                    rotationZ = -8f
                    alpha = 0.22f
                }
        ) {
            PosterThumbnailCard(
                url = posters[1 % posters.size],
                width = 80.dp,
                height = 115.dp
            )
        }
    }
}

// Seamless Continuous Looping Column
@Composable
private fun ContinuousRollingColumn(
    posters: List<String>,
    progress: Float,
    tiltDegrees: Float,
    alpha: Float,
    itemHeightDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val spacingDp = 14.dp
    val totalItemHeightDp = itemHeightDp + spacingDp
    val density = LocalDensity.current
    val totalItemHeightPx = with(density) { totalItemHeightDp.toPx() }
    
    val extendedPosters = remember(posters) { posters + posters + posters }
    val cycleHeightPx = posters.size * totalItemHeightPx

    Column(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                rotationZ = tiltDegrees
                this.alpha = alpha
                translationY = - (progress * cycleHeightPx) % cycleHeightPx
            },
        verticalArrangement = Arrangement.spacedBy(spacingDp)
    ) {
        extendedPosters.forEach { url ->
            PosterThumbnailCard(
                url = url,
                width = null,
                height = itemHeightDp
            )
        }
    }
}

@Composable
private fun PosterThumbnailCard(
    url: String,
    width: androidx.compose.ui.unit.Dp?,
    height: androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    val modifier = if (width != null) {
        Modifier
            .width(width)
            .height(height)
    } else {
        Modifier
            .fillMaxWidth()
            .height(height)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shadowElevation = 6.dp
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// =========================================================================
// RONYCINE BRAND LOGO WITH DISCREET [ RC ] BRAND MARK
// =========================================================================
@Composable
fun RonycineHeaderLogo(
    pulseAlpha: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = DarkSurface.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.40f + (pulseAlpha * 0.35f))),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "[ ",
                    color = BrandRed.copy(alpha = 0.70f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "R",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "C",
                    color = BrandRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = " ]",
                    color = BrandRed.copy(alpha = 0.70f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Black)) {
                    append("RONY")
                }
                withStyle(SpanStyle(color = BrandRed, fontWeight = FontWeight.Black)) {
                    append("CINE")
                }
            },
            fontSize = 34.sp,
            letterSpacing = 3.5.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "SEU CINEMA PARTICULAR",
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.2.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = "SEMPRE COM VOCÊ",
            color = BrandRed,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.6.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

// =========================================================================
// AUTHENTICATION NAVIGATION TABS: [ ENTRAR ] [ CRIAR CONTA ]
// =========================================================================
@Composable
private fun AuthNavigationTabs(
    isSignUpMode: Boolean,
    onTabSelected: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = DarkBackground.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val isEntrarActive = !isSignUpMode
            val entrarBg by animateColorAsState(
                targetValue = if (isEntrarActive) BrandRed else Color.Transparent,
                label = "entrar_tab_bg"
            )
            val entrarTextColor by animateColorAsState(
                targetValue = if (isEntrarActive) Color.White else Color.Gray,
                label = "entrar_tab_text"
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { onTabSelected(false) }
                    .testTag("switch_to_login_button"),
                shape = RoundedCornerShape(7.dp),
                color = entrarBg
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Login,
                        contentDescription = null,
                        tint = entrarTextColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "ENTRAR",
                        color = entrarTextColor,
                        fontSize = 12.5.sp,
                        fontWeight = if (isEntrarActive) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.6.sp
                    )
                }
            }

            val isCriarActive = isSignUpMode
            val criarBg by animateColorAsState(
                targetValue = if (isCriarActive) BrandRed else Color.Transparent,
                label = "criar_tab_bg"
            )
            val criarTextColor by animateColorAsState(
                targetValue = if (isCriarActive) Color.White else Color.Gray,
                label = "criar_tab_text"
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { onTabSelected(true) }
                    .testTag("switch_to_signup_button"),
                shape = RoundedCornerShape(7.dp),
                color = criarBg
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = criarTextColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "CRIAR CONTA",
                        color = criarTextColor,
                        fontSize = 12.5.sp,
                        fontWeight = if (isCriarActive) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.6.sp
                    )
                }
            }
        }
    }
}

// =========================================================================
// MAIN ACTION BUTTON
// =========================================================================
@Composable
private fun MainAuthActionButton(
    buttonText: String,
    loadingText: String,
    successText: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean,
    isSuccess: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "main_auth_action_button"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "auth_btn_scale"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .scale(animatedScale)
            .testTag(testTag),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSuccess) Color(0xFF10B981) else BrandRed,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(9.dp),
        enabled = !isLoading,
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = loadingText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        } else if (isSuccess) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = successText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buttonText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// =========================================================================
// CUSTOM TEXT FIELD
// =========================================================================
@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    testTag: String,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = DarkBackground.copy(alpha = 0.85f),
            unfocusedContainerColor = DarkBackground.copy(alpha = 0.65f),
            focusedBorderColor = BrandRed,
            unfocusedBorderColor = CardBorder,
            focusedLabelColor = BrandRed,
            unfocusedLabelColor = TextSecondary,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = BrandRed
        ),
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (value.isNotEmpty()) BrandRed else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (isPassword && onTogglePassword != null) {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone?.invoke() }
        )
    )
}
