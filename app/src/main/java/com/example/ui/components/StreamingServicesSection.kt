package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.util.directionLockedHorizontalScroll

data class StreamingService(
    val name: String,
    val logoUrl: String,
    val backgroundColor: Color,
    val borderAccent: Color? = null,
    val textLogoFallback: @Composable () -> Unit
)

@Composable
fun StreamingServicesSection(
    onServiceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val streamingServices = remember {
        listOf(
            StreamingService(
                name = "Netflix",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/0/08/Netflix_2015_logo.svg/320px-Netflix_2015_logo.svg.png",
                backgroundColor = Color(0xFF0F0F0F),
                borderAccent = Color(0xFFE50914).copy(alpha = 0.5f),
                textLogoFallback = {
                    Text("NETFLIX", color = Color(0xFFE50914), fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            ),
            StreamingService(
                name = "Disney+",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Disney%2B_logo.svg/320px-Disney%2B_logo.svg.png",
                backgroundColor = Color(0xFF041933),
                borderAccent = Color(0xFF00D2FF).copy(alpha = 0.5f),
                textLogoFallback = {
                    Text("Disney+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                }
            ),
            StreamingService(
                name = "Globoplay",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a6/Globoplay_Logo.svg/320px-Globoplay_Logo.svg.png",
                backgroundColor = Color(0xFF18181B),
                borderAccent = Color(0xFFFF5216).copy(alpha = 0.5f),
                textLogoFallback = {
                    Text("globoplay", color = Color(0xFFFF5216), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            ),
            StreamingService(
                name = "Prime Video",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/11/Amazon_Prime_Video_logo.svg/320px-Amazon_Prime_Video_logo.svg.png",
                backgroundColor = Color(0xFF0F172A),
                borderAccent = Color(0xFF00A8E8).copy(alpha = 0.5f),
                textLogoFallback = {
                    Text("prime video", color = Color(0xFF00A8E8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            ),
            StreamingService(
                name = "Max",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ce/Max_logo.svg/320px-Max_logo.svg.png",
                backgroundColor = Color(0xFF001540),
                borderAccent = Color(0xFF0052FF).copy(alpha = 0.5f),
                textLogoFallback = {
                    Text("max", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                }
            ),
            StreamingService(
                name = "Apple TV+",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/2/28/Apple_TV_Plus_Logo.svg/320px-Apple_TV_Plus_Logo.svg.png",
                backgroundColor = Color(0xFF000000),
                borderAccent = Color(0xFFFFFFFF).copy(alpha = 0.3f),
                textLogoFallback = {
                    Text(" tv+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            ),
            StreamingService(
                name = "Paramount+",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a5/Paramount_Plus_logo.svg/320px-Paramount_Plus_logo.svg.png",
                backgroundColor = Color(0xFF001233),
                borderAccent = Color(0xFF0064FF).copy(alpha = 0.5f),
                textLogoFallback = {
                    Text("Paramount+", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            ),
            StreamingService(
                name = "Crunchyroll",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a4/Crunchyroll_Logo_2024.svg/320px-Crunchyroll_Logo_2024.svg.png",
                backgroundColor = Color(0xFF18181C),
                borderAccent = Color(0xFFF47521).copy(alpha = 0.6f),
                textLogoFallback = {
                    Text("crunchyroll", color = Color(0xFFF47521), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            ),
            StreamingService(
                name = "MUBI",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/60/Mubi_logo.svg/320px-Mubi_logo.svg.png",
                backgroundColor = Color(0xFF0E0E11),
                borderAccent = Color(0xFFCCCCCC).copy(alpha = 0.3f),
                textLogoFallback = {
                    Text("MUBI", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal, letterSpacing = 2.sp)
                }
            ),
            StreamingService(
                name = "Pluto TV",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3b/Pluto_TV_logo.svg/320px-Pluto_TV_logo.svg.png",
                backgroundColor = Color(0xFF0D0D0D),
                borderAccent = Color(0xFFFFCC00).copy(alpha = 0.4f),
                textLogoFallback = {
                    Text("pluto tv", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            ),
            StreamingService(
                name = "Telecine",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/16/Telecine_logo.svg/320px-Telecine_logo.svg.png",
                backgroundColor = Color(0xFF141416),
                borderAccent = Color(0xFFFF0000).copy(alpha = 0.5f),
                textLogoFallback = {
                    Text("TELECINE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            ),
            StreamingService(
                name = "Starz",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/52/Starz_2016.svg/320px-Starz_2016.svg.png",
                backgroundColor = Color(0xFF050505),
                borderAccent = Color(0xFFFFFFFF).copy(alpha = 0.25f),
                textLogoFallback = {
                    Text("STARZ", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                }
            ),
            StreamingService(
                name = "MGM+",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1a/MGM_Plus_logo.svg/320px-MGM_Plus_logo.svg.png",
                backgroundColor = Color(0xFF0A0A0C),
                borderAccent = Color(0xFFD4AF37).copy(alpha = 0.5f),
                textLogoFallback = {
                    Text("MGM+", color = Color(0xFFD4AF37), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Section Header (Title)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Accent brand color indicator
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .background(BrandRed, RoundedCornerShape(2.dp))
            )
            Text(
                text = "STREAMING",
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp
            )
        }

        // Horizontal row of services with customized scroll
        var isScrollEnabled by remember { mutableStateOf(true) }

        LazyRow(
            userScrollEnabled = isScrollEnabled,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(top = 2.dp, bottom = 6.dp)
                .directionLockedHorizontalScroll { enabled ->
                    isScrollEnabled = enabled
                }
        ) {
            items(
                items = streamingServices,
                key = { it.name }
            ) { service ->
                StreamingServiceCard(
                    service = service,
                    onClick = { onServiceClick(service.name) }
                )
            }
        }
    }
}

@Composable
fun StreamingServiceCard(
    service: StreamingService,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(service.logoUrl) {
        ImageRequest.Builder(context)
            .data(service.logoUrl)
            // Use desktop standard headers to bypass wikimedia robot block
            .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .crossfade(true)
            .build()
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else if (isHovered) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Card(
        modifier = modifier
            .width(110.dp)
            .height(55.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .border(
                width = 1.dp,
                color = service.borderAccent ?: CardBorder.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .testTag("streaming_card_${service.name.lowercase()}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = service.backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = service.name,
                contentScale = ContentScale.Fit,
                onState = { imageState = it },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.7f)
            )

            if (imageState is AsyncImagePainter.State.Loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = service.borderAccent?.copy(alpha = 1f) ?: Color.White,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else if (imageState is AsyncImagePainter.State.Error) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    service.textLogoFallback()
                }
            }
        }
    }
}
