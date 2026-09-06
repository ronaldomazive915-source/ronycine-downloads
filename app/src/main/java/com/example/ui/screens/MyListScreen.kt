package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MediaCard
import com.example.ui.theme.DarkBackground
import com.example.ui.viewmodel.MainViewModel

@Composable
fun MyListScreen(
    viewModel: MainViewModel,
    onNavigateToDetail: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val myList by viewModel.myList.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
            .testTag("my_list_screen")
    ) {
        Text(
            text = "❤️ Minha Lista",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        if (myList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sua lista está vazia. Adicione filmes e séries para assistir mais tarde!",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                contentPadding = PaddingValues(bottom = 90.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(myList, key = { it.tmdbId }) { media ->
                    MediaCard(
                        media = media,
                        onClick = { onNavigateToDetail(media.tmdbId, media.mediaType) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
