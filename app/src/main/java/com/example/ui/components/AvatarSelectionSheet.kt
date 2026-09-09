package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.BuildConfig
import com.example.data.remote.TmdbNetwork
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextSecondary
import com.example.util.AvatarCatalog
import com.example.util.LanguageManager
import com.example.util.PresetAvatar
import com.example.util.stringI18n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLEncoder

data class AvatarCharacterItem(
    val id: String,
    val characterName: String,
    val actorName: String?,
    val showName: String,
    val url: String
)

data class WorkAvatarGroup(
    val workId: String,
    val workTitle: String,
    val workType: String,
    val characters: List<AvatarCharacterItem>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarSelectionSheet(
    currentAvatarUrl: String?,
    onDismissRequest: () -> Unit,
    onAvatarSelected: (presetUrl: String?, imageBytes: ByteArray?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        stringI18n("avatar_picker.tab_characters"),
        stringI18n("avatar_picker.tab_my_photo"),
        "Avatar+",
        "Especiais"
    )

    var searchQuery by remember { mutableStateOf("") }
    var chosenPresetUrl by remember { mutableStateOf(currentAvatarUrl) }
    var chosenPresetId by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var processedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var processingError by remember { mutableStateOf<String?>(null) }
    var isProcessingImage by remember { mutableStateOf(false) }

    // Dynamic TMDB Search States
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<WorkAvatarGroup>>(emptyList()) }
    val searchCache = remember { mutableMapOf<String, List<WorkAvatarGroup>>() }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Default Grouped Avatars from Catalog
    val defaultCatalogGroups = remember {
        AvatarCatalog.avatars
            .groupBy { it.showName }
            .map { (showName, avatars) ->
                WorkAvatarGroup(
                    workId = showName,
                    workTitle = showName,
                    workType = if (avatars.firstOrNull()?.subCategory?.contains("Anime", ignoreCase = true) == true) "ANIME" else "SÉRIE",
                    characters = avatars.map { p ->
                        AvatarCharacterItem(
                            id = p.id,
                            characterName = p.characterName ?: p.name,
                            actorName = p.actorName,
                            showName = p.showName,
                            url = p.url
                        )
                    }
                )
            }
    }

    // Dynamic search logic
    LaunchedEffect(searchQuery, refreshTrigger) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }

        val cacheKey = query.lowercase()
        if (refreshTrigger == 0 && searchCache.containsKey(cacheKey)) {
            searchResults = searchCache[cacheKey] ?: emptyList()
            isSearching = false
            return@LaunchedEffect
        }

        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            delay(350) // Debounce
            isSearching = true
            val results = withContext(Dispatchers.IO) {
                try {
                    val apiKey = BuildConfig.TMDB_API_KEY.ifEmpty { "d60f5b5a778fe74b5cf4a371f008725b" }
                    val api = TmdbNetwork.apiService
                    
                    val tmdbResponse = api.searchMulti(apiKey = apiKey, query = query, language = "pt-BR")
                    val items = tmdbResponse.results ?: emptyList()

                    val groups = mutableListOf<WorkAvatarGroup>()

                    // Filter movies and TV shows
                    val mediaList = items.filter { it.mediaType == "tv" || it.mediaType == "movie" }.take(6)

                    for (media in mediaList) {
                        val isTv = media.mediaType == "tv"
                        val title = media.title ?: media.name ?: media.originalTitle ?: media.originalName ?: query
                        val workType = if (isTv) "SÉRIE" else "FILME"

                        val castList = try {
                            if (isTv) {
                                val credits = api.getSeriesCredits(seriesId = media.id, apiKey = apiKey, language = "pt-BR")
                                credits.cast ?: emptyList()
                            } else {
                                val credits = api.getMovieCredits(movieId = media.id, apiKey = apiKey, language = "pt-BR")
                                credits.cast ?: emptyList()
                            }
                        } catch (_: Exception) {
                            emptyList()
                        }

                        val validCast = castList.filter { !it.profilePath.isNullOrBlank() }.take(12)
                        if (validCast.isNotEmpty()) {
                            val characters = validCast.map { cast ->
                                val profileUrl = if (cast.profilePath!!.startsWith("http")) {
                                    cast.profilePath
                                } else {
                                    "https://image.tmdb.org/t/p/w185${cast.profilePath}"
                                }
                                AvatarCharacterItem(
                                    id = "${media.id}_${cast.id}",
                                    characterName = if (!cast.character.isNullOrBlank()) cast.character else cast.name,
                                    actorName = cast.name,
                                    showName = title,
                                    url = profileUrl
                                )
                            }
                            groups.add(
                                WorkAvatarGroup(
                                    workId = media.id.toString(),
                                    workTitle = title,
                                    workType = workType,
                                    characters = characters
                                )
                            )
                        }
                    }

                    // Also search in local Catalog for instant fallback
                    val localMatches = AvatarCatalog.avatars.filter {
                        it.name.contains(query, ignoreCase = true) ||
                        it.showName.contains(query, ignoreCase = true) ||
                        (it.characterName?.contains(query, ignoreCase = true) == true) ||
                        (it.actorName?.contains(query, ignoreCase = true) == true)
                    }

                    if (localMatches.isNotEmpty()) {
                        val localGroups = localMatches.groupBy { it.showName }.map { (show, list) ->
                            WorkAvatarGroup(
                                workId = "local_$show",
                                workTitle = show,
                                workType = "CATÁLOGO",
                                characters = list.map { p ->
                                    AvatarCharacterItem(
                                        id = p.id,
                                        characterName = p.characterName ?: p.name,
                                        actorName = p.actorName,
                                        showName = p.showName,
                                        url = p.url
                                    )
                                }
                            )
                        }
                        // Merge local groups avoiding duplicate show titles
                        for (lg in localGroups) {
                            if (groups.none { it.workTitle.equals(lg.workTitle, ignoreCase = true) }) {
                                groups.add(lg)
                            }
                        }
                    }

                    groups
                } catch (e: Exception) {
                    emptyList()
                }
            }

            searchCache[cacheKey] = results
            searchResults = results
            isSearching = false
        }
    }

    // Process gallery image
    val processUri: (Uri) -> Unit = { uri ->
        isProcessingImage = true
        processingError = null
        selectedImageUri = uri
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                var rotationAngle = 0f
                try {
                    val exifStream = context.contentResolver.openInputStream(uri)
                    if (exifStream != null) {
                        val exif = ExifInterface(exifStream)
                        val orientation = exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                        rotationAngle = when (orientation) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                        exifStream.close()
                    }
                } catch (_: Exception) {}

                var orientedBitmap = originalBitmap
                if (rotationAngle != 0f) {
                    val matrix = Matrix()
                    matrix.postRotate(rotationAngle)
                    orientedBitmap = Bitmap.createBitmap(
                        originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                    )
                }

                val dimension = minOf(orientedBitmap.width, orientedBitmap.height)
                val xOffset = (orientedBitmap.width - dimension) / 2
                val yOffset = (orientedBitmap.height - dimension) / 2
                val croppedBitmap = Bitmap.createBitmap(orientedBitmap, xOffset, yOffset, dimension, dimension)
                val finalScaled = Bitmap.createScaledBitmap(croppedBitmap, 512, 512, true)

                val outputStream = ByteArrayOutputStream()
                finalScaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                val bytes = outputStream.toByteArray()

                processedBytes = bytes
                chosenPresetUrl = null
                chosenPresetId = ""
                isProcessingImage = false
            } else {
                processingError = "Não foi possível carregar a imagem selecionada."
                isProcessingImage = false
            }
        } catch (e: Exception) {
            processingError = "Erro: ${e.localizedMessage ?: "Tente outra imagem."}"
            isProcessingImage = false
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let { processUri(it) } }

    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { processUri(it) } }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = DarkSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f))
        },
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrandRed.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringI18n("avatar_picker.title"),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = stringI18n("avatar_picker.subtitle"),
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { refreshTrigger++ },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recarregar",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tab navigation
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkBackground,
                contentColor = BrandRed,
                edgePadding = 0.dp,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = BrandRed,
                        height = 2.5.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                color = if (selectedTab == index) Color.White else TextSecondary,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // PERSONAGENS TAB (TMDB Dynamic + Presets)
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Search Bar
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        text = stringI18n("avatar_picker.search_placeholder"),
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = {
                                    if (isSearching) {
                                        CircularProgressIndicator(
                                            color = BrandRed,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Buscar",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Limpar",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = DarkBackground,
                                    unfocusedContainerColor = DarkBackground,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Display Option: Use My Initials
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkBackground)
                                    .clickable {
                                        val encoded = URLEncoder.encode("Rony Cine", "UTF-8")
                                        val initialsUrl = "https://ui-avatars.com/api/?name=$encoded&background=E50914&color=fff&size=512&bold=true"
                                        chosenPresetUrl = initialsUrl
                                        chosenPresetId = "initials"
                                        processedBytes = null
                                        selectedImageUri = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(BrandRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aa",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = stringI18n("avatar_picker.use_initials"),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Gerar avatar circular simples com seu nome",
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val groupsToDisplay = if (searchQuery.isNotBlank()) searchResults else defaultCatalogGroups

                            if (groupsToDisplay.isEmpty() && !isSearching) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringI18n("avatar_picker.no_characters"),
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    contentPadding = PaddingValues(bottom = 8.dp)
                                ) {
                                    items(groupsToDisplay, key = { it.workId }) { workGroup ->
                                        WorkCharacterGroupSection(
                                            workGroup = workGroup,
                                            chosenUrl = chosenPresetUrl,
                                            onSelectCharacter = { charItem ->
                                                chosenPresetUrl = charItem.url
                                                chosenPresetId = charItem.id
                                                processedBytes = null
                                                selectedImageUri = null
                                                processingError = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // PERSONAL PHOTO TAB
                        PersonalPhotoSection(
                            selectedUri = selectedImageUri,
                            hasProcessedBytes = processedBytes != null,
                            isProcessing = isProcessingImage,
                            errorMessage = processingError,
                            onPickFromGallery = {
                                try {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } catch (_: Exception) {
                                    getContentLauncher.launch("image/*")
                                }
                            }
                        )
                    }

                    2, 3 -> {
                        // AVATAR+ and SPECIALS PRESET TABS
                        val categoryName = if (selectedTab == 2) AvatarCatalog.CATEGORY_AVATAR_PLUS else AvatarCatalog.CATEGORY_VIP
                        val presetList = remember(categoryName) { AvatarCatalog.filter(categoryName, "") }

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 78.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(presetList, key = { it.id }) { preset ->
                                val isSelected = chosenPresetUrl == preset.url && processedBytes == null
                                PresetAvatarCard(
                                    preset = preset,
                                    isSelected = isSelected,
                                    onClick = {
                                        chosenPresetUrl = preset.url
                                        chosenPresetId = preset.id
                                        processedBytes = null
                                        selectedImageUri = null
                                        processingError = null
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Confirm Save Avatar Button
            val hasSelection = chosenPresetUrl != null || processedBytes != null
            Button(
                onClick = {
                    if (hasSelection) {
                        onAvatarSelected(chosenPresetUrl, processedBytes)
                        onDismissRequest()
                    }
                },
                enabled = hasSelection && !isProcessingImage,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandRed,
                    disabledContainerColor = DarkBackground
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("confirm_avatar_selection_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringI18n("avatar_picker.save"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun WorkCharacterGroupSection(
    workGroup: WorkAvatarGroup,
    chosenUrl: String?,
    onSelectCharacter: (AvatarCharacterItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = workGroup.workTitle.uppercase(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Surface(
                color = BrandRed.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.5.dp, BrandRed.copy(alpha = 0.4f))
            ) {
                Text(
                    text = workGroup.workType,
                    color = BrandRed,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(workGroup.characters, key = { it.id }) { charItem ->
                val isSelected = chosenUrl == charItem.url
                CharacterAvatarCard(
                    item = charItem,
                    isSelected = isSelected,
                    onClick = { onSelectCharacter(charItem) }
                )
            }
        }
    }
}

@Composable
private fun CharacterAvatarCard(
    item: AvatarCharacterItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(DarkBackground)
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) BrandRed else CardBorder.copy(alpha = 0.6f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.url)
                    .crossfade(true)
                    .build(),
                contentDescription = item.characterName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkSurfaceVariant)
                    )
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BrandRed.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selecionado",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = item.characterName,
            color = if (isSelected) BrandRed else Color.White,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (!item.actorName.isNullOrBlank()) {
            Text(
                text = item.actorName,
                color = TextSecondary,
                fontSize = 8.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PresetAvatarCard(
    preset: PresetAvatar,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(82.dp)
            .clickable(onClick = onClick)
            .testTag("preset_avatar_${preset.id}")
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(DarkBackground)
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) BrandRed else CardBorder.copy(alpha = 0.6f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(preset.url)
                    .crossfade(true)
                    .build(),
                contentDescription = preset.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkSurfaceVariant)
                    )
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BrandRed.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selecionado",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = preset.name,
            color = if (isSelected) BrandRed else Color.White,
            fontSize = 10.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (preset.showName.isNotBlank()) {
            Text(
                text = preset.showName,
                color = TextSecondary,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PersonalPhotoSection(
    selectedUri: Uri?,
    hasProcessedBytes: Boolean,
    isProcessing: Boolean,
    errorMessage: String?,
    onPickFromGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(DarkBackground)
                .border(2.dp, if (hasProcessedBytes) BrandRed else CardBorder, CircleShape)
                .clickable(enabled = !isProcessing, onClick = onPickFromGallery),
            contentAlignment = Alignment.Center
        ) {
            if (selectedUri != null) {
                SubcomposeAsyncImage(
                    model = selectedUri,
                    contentDescription = "Foto selecionada",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Galeria",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = BrandRed,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onPickFromGallery,
            colors = ButtonDefaults.buttonColors(containerColor = DarkBackground),
            border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.8f)),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            modifier = Modifier.height(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = BrandRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (hasProcessedBytes) "Trocar Foto da Galeria" else "Escolher Foto da Galeria",
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = Color(0xFFF87171),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Corte circular e otimização automática de alta definição.",
                color = TextSecondary,
                fontSize = 10.5.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
