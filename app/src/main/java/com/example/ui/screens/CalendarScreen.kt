package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.CalendarDateRange
import com.example.data.remote.CalendarFilter
import com.example.data.remote.CalendarItem
import com.example.data.repository.CalendarUiState
import com.example.ui.components.CalendarMediaCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.CalendarViewModel

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedDateRange by viewModel.selectedDateRange.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val groupedItems by viewModel.filteredGroupedItems.collectAsStateWithLifecycle()

    var isSearchExpanded by remember { mutableStateOf(false) }

    val refreshRotation = remember { Animatable(0f) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            refreshRotation.animateTo(
                targetValue = refreshRotation.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            refreshRotation.snapTo(0f)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("calendar_screen")
    ) {
        val screenWidth = maxWidth
        val columnsCount = when {
            screenWidth < 360.dp -> 2
            screenWidth < 600.dp -> 3
            screenWidth < 840.dp -> 4
            else -> 6
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Top Bar
            Surface(
                color = DarkSurface,
                border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.3f)),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back Button
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("calendar_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = Color.White
                            )
                        }

                        // Title & Subtitle
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "CALENDÁRIO DE LANÇAMENTOS",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Veja o que já chegou e o que vem por aí.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Search Toggle Button
                        IconButton(
                            onClick = {
                                isSearchExpanded = !isSearchExpanded
                                if (!isSearchExpanded) {
                                    viewModel.clearSearch()
                                }
                            },
                            modifier = Modifier.testTag("calendar_search_toggle")
                        ) {
                            Icon(
                                imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Pesquisar no Calendário",
                                tint = if (searchQuery.isNotBlank()) BrandRed else Color.White
                            )
                        }

                        // Refresh Button ↻
                        IconButton(
                            onClick = { viewModel.loadData(forceRefresh = true) },
                            enabled = !isRefreshing,
                            modifier = Modifier.testTag("calendar_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Atualizar Calendário",
                                tint = if (isRefreshing) BrandRed else Color.White,
                                modifier = Modifier.rotate(refreshRotation.value)
                            )
                        }
                    }

                    // Search Input (Collapsible)
                    AnimatedVisibility(
                        visible = isSearchExpanded,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("calendar_search_input"),
                                placeholder = {
                                    Text(
                                        text = "Buscar título, episódio ou data...",
                                        color = Color.Gray,
                                        fontSize = 13.sp
                                    )
                                },
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.clearSearch() }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Limpar",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BrandRed,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedContainerColor = DarkBackground,
                                    unfocusedContainerColor = DarkBackground,
                                    cursorColor = BrandRed,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // 1. Filter Chips Bar - Category [Todos] [Filmes] [Séries] [Animes] [Doramas]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CalendarFilter.values().forEach { filter ->
                            val isSelected = selectedFilter == filter
                            val filterCount = when (filter) {
                                CalendarFilter.TODOS -> counts.total
                                CalendarFilter.FILMES -> counts.movies
                                CalendarFilter.SERIES -> counts.series
                                CalendarFilter.ANIMES -> counts.animes
                                CalendarFilter.DORAMAS -> counts.doramas
                            }

                            val chipColor = when (filter) {
                                CalendarFilter.TODOS -> BrandRed
                                CalendarFilter.FILMES -> BrandRed
                                CalendarFilter.SERIES -> Color(0xFF0284C7)
                                CalendarFilter.ANIMES -> Color(0xFFEA580C)
                                CalendarFilter.DORAMAS -> Color(0xFF9333EA)
                            }

                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { viewModel.setFilter(filter) }
                                    .testTag("calendar_filter_${filter.name.lowercase()}"),
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) chipColor else DarkSurfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) chipColor else CardBorder.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = filter.displayName,
                                        color = if (isSelected) Color.White else Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )

                                    if (filterCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) Color.Black.copy(alpha = 0.3f)
                                                    else Color.White.copy(alpha = 0.1f)
                                                )
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "$filterCount",
                                                color = if (isSelected) Color.White else Color.Gray,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Quick Date Range Chips [Todas as Datas] [Hoje] [Amanhã] [Esta Semana] [Próxima Semana] [Próximo Mês]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CalendarDateRange.values().forEach { range ->
                            val isSelected = selectedDateRange == range
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { viewModel.setDateRange(range) }
                                    .testTag("calendar_date_range_${range.name.lowercase()}"),
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) Color(0xFF374151) else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f)
                                )
                            ) {
                                Text(
                                    text = range.displayName,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Body Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (val state = uiState) {
                    is CalendarUiState.Loading -> {
                        CalendarLoadingSkeleton(columns = columnsCount)
                    }
                    is CalendarUiState.Error -> {
                        CalendarErrorView(
                            message = state.message,
                            onRetry = { viewModel.loadData(forceRefresh = true) }
                        )
                    }
                    is CalendarUiState.Empty -> {
                        CalendarEmptyView(onRetry = { viewModel.loadData(forceRefresh = true) })
                    }
                    is CalendarUiState.Success -> {
                        if (groupedItems.isEmpty()) {
                            CalendarEmptyFilteredView(
                                filterName = selectedFilter.displayName,
                                hasQuery = searchQuery.isNotBlank(),
                                onClearFilter = {
                                    viewModel.setFilter(CalendarFilter.TODOS)
                                    viewModel.setDateRange(CalendarDateRange.TODOS)
                                    viewModel.clearSearch()
                                }
                            )
                        } else {
                            CalendarGroupedContent(
                                grouped = groupedItems,
                                columns = columnsCount,
                                onNavigateToDetail = { item ->
                                    val routeType = item.mediaType.routeType
                                    val tmdbId = item.tmdbId ?: 0
                                    onNavigateToDetail(tmdbId, routeType)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarGroupedContent(
    grouped: Map<String, List<CalendarItem>>,
    columns: Int,
    onNavigateToDetail: (CalendarItem) -> Unit
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("calendar_list"),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        grouped.forEach { (dateHeader, itemsList) ->
            // Sticky-like Date Header
            item(key = "header_$dateHeader") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isTodayHeader = dateHeader.startsWith("HOJE")
                            val isTomorrowHeader = dateHeader.startsWith("AMANHÃ")

                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        when {
                                            isTodayHeader -> BrandRed
                                            isTomorrowHeader -> Color(0xFF0284C7)
                                            else -> Color.LightGray.copy(alpha = 0.6f)
                                        }
                                    )
                            )
                            Text(
                                text = dateHeader,
                                color = when {
                                    isTodayHeader -> Color.White
                                    isTomorrowHeader -> Color(0xFFE2E8F0)
                                    else -> Color(0xFFCBD5E1)
                                },
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Text(
                            text = "${itemsList.size} lançamentos",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        thickness = 1.dp
                    )
                }
            }

            // Grid items chunked by columns
            val rows = itemsList.chunked(columns)
            items(rows, key = { row -> "row_${dateHeader}_${row.firstOrNull()?.id ?: ""}" }) { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (i in 0 until columns) {
                        if (i < rowItems.size) {
                            val item = rowItems[i]
                            Box(modifier = Modifier.weight(1f)) {
                                CalendarMediaCard(
                                    item = item,
                                    onClick = { onNavigateToDetail(item) }
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarLoadingSkeleton(columns: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("calendar_loading_skeleton")
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkSurfaceVariant)
        )
        Spacer(modifier = Modifier.height(12.dp))

        for (r in 0 until 3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (c in 0 until columns) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(DarkSurfaceVariant)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(DarkSurfaceVariant)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("calendar_error_view"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = BrandRed,
                modifier = Modifier.size(54.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Não foi possível carregar o calendário.",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = message,
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("calendar_retry_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Tentar novamente", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CalendarEmptyView(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("calendar_empty_view"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(52.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Não há lançamentos disponíveis no momento.",
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onRetry,
                border = BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Atualizar", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CalendarEmptyFilteredView(
    filterName: String,
    hasQuery: Boolean,
    onClearFilter: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.FilterAltOff,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(46.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (hasQuery) "Nenhum resultado encontrado para a pesquisa."
                else "Nenhum lançamento encontrado em $filterName.",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            TextButton(
                onClick = onClearFilter,
                colors = ButtonDefaults.textButtonColors(contentColor = BrandRed)
            ) {
                Text(text = "Ver todos os lançamentos", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
