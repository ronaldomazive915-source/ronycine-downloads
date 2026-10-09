package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.MediaEntity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.DarkBackground
import com.example.ui.viewmodel.MainViewModel

@Composable
fun WhatsNewScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Int, String) -> Unit
) {
    val recentlyAdded by viewModel.recentlyAddedMedia.collectAsState()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "NOVIDADES NO RONYCINE",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
                
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    color = BrandRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Confira os últimos lançamentos adicionados ao nosso catálogo.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        if (recentlyAdded.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Buscando novidades...", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(recentlyAdded) { media ->
                    WhatsNewCard(
                        media = media,
                        onClick = { onNavigateToDetail(media.tmdbId, media.mediaType) }
                    )
                }
            }
        }
    }
}

@Composable
fun WhatsNewCard(
    media: MediaEntity,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1D23))
        ) {
            AsyncImage(
                model = media.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                contentDescription = media.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Badge Novo
            Surface(
                modifier = Modifier.padding(6.dp).align(Alignment.TopEnd),
                color = BrandRed,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "NOVO",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Overlay Gradiente
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 100f
                        )
                    )
            )
            
            // Tipo
            Surface(
                modifier = Modifier.padding(6.dp).align(Alignment.BottomStart),
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = if (media.mediaType == "movie") "FILME" else "SÉRIE",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = media.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}
