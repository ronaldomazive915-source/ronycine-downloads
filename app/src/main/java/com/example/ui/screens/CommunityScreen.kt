package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.example.data.remote.CommunityMessage
import com.example.ui.theme.BrandRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.viewmodel.CommunityViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun VerifiedBadge(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(13.dp)
            .background(Color(0xFF2196F3), CircleShape)
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Verificado",
            tint = Color.White,
            modifier = Modifier.size(9.dp)
        )
    }
}

@Composable
fun DateSeparator(timestamp: Long) {
    val dateText = remember(timestamp) {
        try {
            val sdfStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val messageDate = sdfStr.format(Date(timestamp))
            val today = sdfStr.format(Date())
            val yesterday = sdfStr.format(Date(System.currentTimeMillis() - 86400000))
            
            when (messageDate) {
                today -> "HOJE"
                yesterday -> "ONTEM"
                else -> messageDate
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    if (dateText.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Text(
                text = "  ${dateText.uppercase()}  ",
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }
    }
}

fun formatTime(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        ""
    }
}

fun formatDateKey(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        ""
    }
}

// Translate helpers utilizing LanguageManager
@Composable
fun translateKey(key: String, fallback: String): String {
    val translated = com.example.util.stringI18n(key)
    return if (translated.isNotBlank()) translated else fallback
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommunityScreen(
    viewModel: CommunityViewModel,
    onNavigateToDetail: (Int, String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val isAiThinking by viewModel.isAiThinking.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val isBanned by viewModel.isBanned.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val muteUntil by viewModel.muteUntil.collectAsState()
    val isSpamBlocked by viewModel.isSpamBlocked.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val onlineCount by viewModel.onlineCount.collectAsState()

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Isolated prefill bridge: Only populated when user clicks a suggestion chip
    var composerPrefill by remember { mutableStateOf("") }

    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    val isAdmin = remember(currentUser) { currentUser?.email?.lowercase() == "ronaldomazive915@gmail.com" }

    var selectedMessageForMod by remember { mutableStateOf<CommunityMessage?>(null) }
    var showMuteDialog by remember { mutableStateOf(false) }
    var showOnlineMembersDialog by remember { mutableStateOf(false) }

    val iaHintText = translateKey("community.ia_hint", "Converse naturalmente com o Cine no chat")

    // Track scroll position to show "New Message" pill and determine safe auto-scroll
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (layoutInfo.totalItemsCount == 0) {
                true
            } else {
                val lastVisibleItem = visibleItemsInfo.lastOrNull()
                lastVisibleItem != null && lastVisibleItem.index >= layoutInfo.totalItemsCount - 2
            }
        }
    }

    // Show error toasts automatically without disrupting composer focus
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearErrorMessage()
        }
    }

    // Auto-scroll ONLY when new messages arrive and user is already near the bottom
    // Typing locally NEVER triggers scrolling
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && isAtBottom) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    // Scroll when AI thinking starts if at bottom
    LaunchedEffect(isAiThinking) {
        if (isAiThinking && messages.isNotEmpty() && isAtBottom) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size)
            }
        }
    }

    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val isKeyboardOpen = imeBottomPx > 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060609),
                        Color(0xFF0D0A0C),
                        Color(0xFF060609)
                    )
                )
            )
            .imePadding()
            .testTag("community_screen")
    ) {
        // 1. Decoupled Community Header: Does NOT re-render on keystrokes
        CommunityHeader(
            onlineCount = onlineCount,
            onNavigateBack = onNavigateBack,
            onShowOnlineMembers = { showOnlineMembersDialog = true },
            onShowInfo = { Toast.makeText(context, iaHintText, Toast.LENGTH_LONG).show() }
        )

        // 2. Chat Messages Area: Decoupled LazyColumn
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            MessageList(
                messages = messages,
                listState = listState,
                isAiThinking = isAiThinking,
                isLoading = isLoading,
                isOffline = isOffline,
                currentUserId = currentUser?.uid ?: "",
                isAdmin = isAdmin,
                onToggleReaction = { messageId, emoji ->
                    viewModel.toggleReaction(messageId, emoji)
                },
                onReply = { message -> viewModel.setReplyingTo(message) },
                onDelete = { messageId -> viewModel.deleteMessage(messageId) },
                onMute = { message ->
                    selectedMessageForMod = message
                    showMuteDialog = true
                },
                onBan = { targetUserId ->
                    viewModel.banUser(targetUserId)
                    Toast.makeText(context, "Usuário banido com sucesso", Toast.LENGTH_SHORT).show()
                },
                onRetryMessage = { messageId ->
                    viewModel.retrySendMessage(messageId)
                },
                onNavigateToDetail = onNavigateToDetail,
                onRetryConnection = { viewModel.retry() },
                onSelectSuggestion = { suggestion ->
                    composerPrefill = suggestion
                }
            )

            // Floating "New Message" / Scroll to bottom Button
            androidx.compose.animation.AnimatedVisibility(
                visible = !isAtBottom && messages.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 16.dp)
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = "Scroll down",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = translateKey("community.new_message", "Nova mensagem"),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Isolated Message Composer: Holds local text state; keystrokes NEVER re-render MessageList or Header
        val isInputLocked = isBanned || isSpamBlocked || (isMuted && muteUntil > System.currentTimeMillis())
        MessageComposer(
            isInputLocked = isInputLocked,
            isBanned = isBanned,
            isSpamBlocked = isSpamBlocked,
            isMuted = isMuted,
            muteUntil = muteUntil,
            replyingTo = replyingTo,
            onCancelReply = { viewModel.setReplyingTo(null) },
            onSendMessage = { text ->
                viewModel.sendMessage(text)
                // Auto scroll to bottom when user sends a message
                coroutineScope.launch {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
            },
            composerPrefillText = composerPrefill,
            onPrefillConsumed = { composerPrefill = "" },
            isKeyboardOpen = isKeyboardOpen
        )
    }

    // 4. Mute Time Selection Dialog
    if (showMuteDialog && selectedMessageForMod != null) {
        AlertDialog(
            onDismissRequest = { showMuteDialog = false },
            title = { Text("Silenciar Perfil") },
            text = { Text("Selecione por quanto tempo deseja silenciar o usuário ${selectedMessageForMod?.displayName}:") },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.muteUser(selectedMessageForMod!!.userId, 5)
                            showMuteDialog = false
                            Toast.makeText(context, "Silenciado por 5 minutos", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("5 minutos")
                    }
                    Button(
                        onClick = {
                            viewModel.muteUser(selectedMessageForMod!!.userId, 60)
                            showMuteDialog = false
                            Toast.makeText(context, "Silenciado por 1 hora", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1 hora")
                    }
                    Button(
                        onClick = {
                            viewModel.muteUser(selectedMessageForMod!!.userId, 1440)
                            showMuteDialog = false
                            Toast.makeText(context, "Silenciado por 24 horas", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("24 horas")
                    }
                    TextButton(
                        onClick = { showMuteDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancelar", color = Color.White)
                    }
                }
            }
        )
    }

    // 5. Online Members Dialog
    if (showOnlineMembersDialog) {
        AlertDialog(
            onDismissRequest = { showOnlineMembersDialog = false },
            title = { Text("Membros Online na Comunidade") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Há $onlineCount membros participando ativamente no chat global do Ronycine.", color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Ronycine (Fundador ✓)", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("• Cine (Assistente IA 🤖)", color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold)
                    Text("• Membros da Comunidade Global", color = Color.LightGray)
                }
            },
            confirmButton = {
                TextButton(onClick = { showOnlineMembersDialog = false }) {
                    Text("Fechar", color = BrandRed, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun CommunityHeader(
    onlineCount: Int,
    onNavigateBack: () -> Unit,
    onShowOnlineMembers: () -> Unit,
    onShowInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .statusBarsPadding()
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.03f), shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = translateKey("community.title", "Comunidade RONYCINE"),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onShowOnlineMembers() }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color.Green, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (onlineCount > 0) "$onlineCount " + translateKey("community.online", "membros online") else translateKey("community.online_fallback", "Comunidade online"),
                            color = Color.Green,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            IconButton(
                onClick = onShowInfo,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Informação",
                    tint = Color.LightGray.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageList(
    messages: List<CommunityMessage>,
    listState: LazyListState,
    isAiThinking: Boolean,
    isLoading: Boolean,
    isOffline: Boolean,
    currentUserId: String,
    isAdmin: Boolean,
    onToggleReaction: (String, String) -> Unit,
    onReply: (CommunityMessage) -> Unit,
    onDelete: (String) -> Unit,
    onMute: (CommunityMessage) -> Unit,
    onBan: (String) -> Unit,
    onRetryMessage: (String) -> Unit,
    onNavigateToDetail: (Int, String) -> Unit,
    onRetryConnection: () -> Unit,
    onSelectSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        isLoading -> {
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = translateKey("community.placeholder", "Carregando mensagens..."),
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }
        isOffline -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = "Offline",
                    tint = Color.Gray,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = translateKey("community.offline_message", "Não foi possível carregar as mensagens."),
                    color = Color.Gray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRetryConnection,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        translateKey("community.offline_retry", "Tentar novamente"),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        messages.isEmpty() -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.White.copy(alpha = 0.03f), CircleShape)
                ) {
                    Text(
                        text = "🌎",
                        fontSize = 32.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = translateKey("community.empty_title", "A comunidade está começando"),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = translateKey("community.empty_sub", "Converse com outros membros, compartilhe descobertas e encontre novos filmes e séries."),
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Quick-action pre-fill chips
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SuggestionChipButton(
                        text = translateKey("community.suggest_movie", "🎬 Recomende um filme"),
                        onClick = { onSelectSuggestion("Cine, me recomenda um filme de ") }
                    )
                    SuggestionChipButton(
                        text = translateKey("community.suggest_series", "📺 Fale sobre uma série"),
                        onClick = { onSelectSuggestion("Gostei muito da série ") }
                    )
                    SuggestionChipButton(
                        text = translateKey("community.suggest_chat", "💬 Comece uma conversa"),
                        onClick = { onSelectSuggestion("Olá pessoal! 👋") }
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                itemsIndexed(
                    items = messages,
                    key = { _, msg -> msg.id },
                    contentType = { _, _ -> "message_item" }
                ) { index, message ->
                    val previousMessage = if (index > 0) messages[index - 1] else null
                    val showDateSeparator = if (previousMessage == null) {
                        true
                    } else {
                        formatDateKey(previousMessage.createdAt) != formatDateKey(message.createdAt)
                    }

                    if (showDateSeparator) {
                        DateSeparator(message.createdAt)
                    }

                    val isDeleted = message.status == "DELETED"
                    val isOwnMessage = message.userId == currentUserId

                    if (isDeleted) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "🚫 Mensagem removida pelo administrador",
                                    color = Color.Gray.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    } else {
                        MessageBubble(
                            message = message,
                            isOwnMessage = isOwnMessage,
                            isAdmin = isAdmin,
                            currentUserId = currentUserId,
                            onToggleReaction = { emoji -> onToggleReaction(message.id, emoji) },
                            onReply = { onReply(message) },
                            onDelete = { onDelete(message.id) },
                            onMute = { onMute(message) },
                            onBan = { onBan(message.userId) },
                            onRetry = { onRetryMessage(message.id) },
                            onNavigateToDetail = onNavigateToDetail
                        )
                    }
                }
                if (isAiThinking) {
                    item(key = "ai_thinking_bubble") {
                        AiThinkingBubble()
                    }
                }
            }
        }
    }
}

@Composable
fun MessageComposer(
    isInputLocked: Boolean,
    isBanned: Boolean,
    isSpamBlocked: Boolean,
    isMuted: Boolean,
    muteUntil: Long,
    replyingTo: CommunityMessage?,
    onCancelReply: () -> Unit,
    onSendMessage: (String) -> Unit,
    composerPrefillText: String,
    onPrefillConsumed: () -> Unit,
    isKeyboardOpen: Boolean,
    modifier: Modifier = Modifier
) {
    // Isolated local state: Typing only causes recomposition of MessageComposer
    var textInput by rememberSaveable { mutableStateOf("") }

    // Synchronize prefill text from suggestion buttons if supplied
    LaunchedEffect(composerPrefillText) {
        if (composerPrefillText.isNotEmpty()) {
            textInput = composerPrefillText
            onPrefillConsumed()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!isKeyboardOpen) {
                    Modifier.navigationBarsPadding()
                } else {
                    Modifier
                }
            )
    ) {
        // Reply Preview Bar
        androidx.compose.animation.AnimatedVisibility(
            visible = replyingTo != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            replyingTo?.let { reply ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF131317))
                        .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Reply,
                            contentDescription = "Replying",
                            tint = BrandRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Respondendo a ${reply.displayName}",
                                color = BrandRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = reply.text,
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onCancelReply,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Cancelar",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Dedicated input container
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.03f)),
            color = DarkSurface
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                TextField(
                    value = textInput,
                    onValueChange = { if (!isInputLocked) textInput = it },
                    placeholder = {
                        Text(
                            text = when {
                                isBanned -> "Você foi banido da comunidade"
                                isSpamBlocked -> "Aguarde para enviar mensagens"
                                isMuted -> "Você está silenciado"
                                else -> "Escreva uma mensagem..."
                            },
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .testTag("chat_input_text_field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F0F12),
                        unfocusedContainerColor = Color(0xFF0F0F12),
                        disabledContainerColor = Color(0xFF050507),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.Gray
                    ),
                    maxLines = 4,
                    enabled = !isInputLocked
                )

                Spacer(modifier = Modifier.width(8.dp))

                val canSend = !isInputLocked && textInput.isNotBlank()
                IconButton(
                    onClick = {
                        val toSend = textInput.trim()
                        if (toSend.isNotBlank()) {
                            textInput = "" // Clear immediately in local state
                            onSendMessage(toSend) // Immediate optimistic UI insertion
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = if (canSend) BrandRed else Color.White.copy(alpha = 0.05f),
                            shape = CircleShape
                        )
                        .testTag("send_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Enviar",
                        tint = if (canSend) Color.White else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SuggestionChipButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = Color.LightGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: CommunityMessage,
    isOwnMessage: Boolean,
    isAdmin: Boolean,
    currentUserId: String = "",
    onToggleReaction: (String) -> Unit = {},
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onMute: () -> Unit,
    onBan: () -> Unit,
    onRetry: () -> Unit = {},
    onNavigateToDetail: (Int, String) -> Unit
) {
    var showModActions by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
    ) {
        if (!isOwnMessage) {
            // Received Avatar Circle
            val avatarLetter = message.displayName.firstOrNull()?.toString()?.uppercase() ?: "?"
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(BrandRed, Color(0xFF7A0707))
                        )
                    )
            ) {
                if (!message.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = message.avatarUrl,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = avatarLetter,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Sender name, badge, handle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = message.displayName,
                    color = if (message.isAiResponse) Color(0xFF64B5F6) else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (message.isVerified) {
                    VerifiedBadge()
                }
                if (message.username.isNotBlank()) {
                    Text(
                        text = if (message.username.startsWith("@")) message.username else "@${message.username}",
                        color = Color.Gray.copy(alpha = 0.8f),
                        fontSize = 9.sp
                    )
                }
            }

            // Reply Context Banner inside Column
            if (message.replyToId != null) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 3.dp, start = if (isOwnMessage) 0.dp else 4.dp, end = if (isOwnMessage) 4.dp else 0.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "↳ em resposta a ${message.replyToUsername}: \"${message.replyToText}\"",
                        color = Color.LightGray.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Message Body Bubble Shape with Reaction Picker Popup
            Box {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 14.dp,
                                topEnd = 14.dp,
                                bottomStart = if (isOwnMessage) 14.dp else 2.dp,
                                bottomEnd = if (isOwnMessage) 2.dp else 14.dp
                            )
                        )
                        .background(
                            when {
                                message.isAiResponse -> Brush.horizontalGradient(
                                    listOf(Color(0xFF0F2027), Color(0xFF1B2C33))
                                )
                                isOwnMessage -> Brush.horizontalGradient(
                                    listOf(BrandRed, Color(0xFF8A0606))
                                )
                                else -> Brush.horizontalGradient(
                                    listOf(Color(0xFF131317), Color(0xFF131317))
                                )
                            }
                        )
                        .then(
                            if (message.isAiResponse) {
                                Modifier.border(1.dp, Color(0xFF1976D2).copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                            } else Modifier.border(1.dp, Color.White.copy(alpha = 0.02f), RoundedCornerShape(14.dp))
                        )
                        .combinedClickable(
                            onClick = {
                                if (isAdmin && message.userId != "ronycine_ia_bot" && message.userId != "cine_bot" && !isOwnMessage) {
                                    showModActions = !showModActions
                                }
                            },
                            onLongClick = {
                                showReactionPicker = true
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column {
                        Text(
                            text = message.text,
                            color = Color.White,
                            fontSize = 13.5.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Small discrete timestamp and delivery status inside bubble
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = formatTime(message.createdAt),
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 9.sp
                            )
                            if (isOwnMessage) {
                                Spacer(modifier = Modifier.width(4.dp))
                                when (message.deliveryStatus) {
                                    "SENDING" -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(9.dp),
                                            color = Color.White.copy(alpha = 0.6f),
                                            strokeWidth = 1.2.dp
                                        )
                                    }
                                    "FAILED" -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable { onRetry() }
                                                .padding(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ErrorOutline,
                                                contentDescription = "Falha no envio. Toque para reenviar.",
                                                tint = Color(0xFFFF5252),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = "Reenviar",
                                                color = Color(0xFFFF5252),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Enviada",
                                            tint = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Embedded Recommendation Card
                        if (message.recommendedMediaId != null && message.recommendedMediaId != -1) {
                            Spacer(modifier = Modifier.height(8.dp))
                            InteractiveRecommendationCard(
                                title = message.recommendedMediaTitle ?: "Título recomendado",
                                posterUrl = message.recommendedMediaPoster,
                                mediaType = message.recommendedMediaType ?: "movie",
                                onClick = { onNavigateToDetail(message.recommendedMediaId, message.recommendedMediaType ?: "movie") }
                            )
                        }
                    }
                }

                // Reaction Picker Popup anchored above the message bubble
                if (showReactionPicker) {
                    Popup(
                        alignment = if (isOwnMessage) Alignment.TopEnd else Alignment.TopStart,
                        offset = IntOffset(x = 0, y = -115),
                        onDismissRequest = { showReactionPicker = false },
                        properties = PopupProperties(focusable = true, dismissOnClickOutside = true)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF1B1B22),
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                val availableEmojis = listOf("❤️", "😂", "👍", "😮", "😢", "🔥", "👏", "😍")
                                val currentUserReaction = message.reactions[currentUserId]

                                availableEmojis.forEach { emoji ->
                                    val isSelected = currentUserReaction == emoji
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) BrandRed.copy(alpha = 0.25f) else Color.Transparent)
                                            .border(
                                                width = if (isSelected) 1.dp else 0.dp,
                                                color = if (isSelected) BrandRed else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                onToggleReaction(emoji)
                                                showReactionPicker = false
                                            }
                                    ) {
                                        Text(text = emoji, fontSize = 17.sp)
                                    }
                                }

                                // Discrete divider and Reply shortcut
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(18.dp)
                                        .background(Color.White.copy(alpha = 0.15f))
                                )

                                IconButton(
                                    onClick = {
                                        onReply()
                                        showReactionPicker = false
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Reply,
                                        contentDescription = "Responder",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Display Active Reaction Badges below the message bubble
            val reactionCounts = remember(message.reactions) {
                message.reactions.values.groupingBy { it }.eachCount()
            }
            val userReaction = message.reactions[currentUserId]

            if (reactionCounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    reactionCounts.forEach { (emoji, count) ->
                        val isUserEmoji = userReaction == emoji
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isUserEmoji) BrandRed.copy(alpha = 0.22f) else Color(0xFF191920),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isUserEmoji) BrandRed.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onToggleReaction(emoji)
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(text = emoji, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = count.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUserEmoji) Color.White else Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }

            // Moderator Action sheet directly under bubble
            if (showModActions && isAdmin && !isOwnMessage) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    TextButton(
                        onClick = {
                            onDelete()
                            showModActions = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Deletar", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apagar", fontSize = 10.sp)
                    }

                    TextButton(
                        onClick = {
                            onMute()
                            showModActions = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Yellow),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.VolumeMute, contentDescription = "Mudar", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Silenciar", fontSize = 10.sp)
                    }

                    TextButton(
                        onClick = {
                            onBan()
                            showModActions = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Block, contentDescription = "Banir", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Banir", fontSize = 10.sp)
                    }
                }
            }
        }

        if (isOwnMessage) {
            Spacer(modifier = Modifier.width(8.dp))
            // Sent Message Avatar Circle
            val avatarLetter = message.displayName.firstOrNull()?.toString()?.uppercase() ?: "?"
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(BrandRed, Color(0xFF7A0707))
                        )
                    )
            ) {
                if (!message.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = message.avatarUrl,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = avatarLetter,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveRecommendationCard(
    title: String,
    posterUrl: String?,
    mediaType: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .testTag("interactive_recommendation_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = posterUrl ?: "https://image.tmdb.org/t/p/w185/placeholder.jpg",
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 44.dp, height = 60.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (mediaType == "movie") "🎬 Filme" else "📺 Série",
                    color = Color.Gray,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(
                        text = "Assistir Agora",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AiThinkingBubble() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // AI Avatar
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B))
        ) {
            Text(
                text = "🤖",
                fontSize = 18.sp
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column {
            // Sender details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "RONYCINE IA",
                    color = Color(0xFF64B5F6),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                VerifiedBadge()
                Text(
                    text = "@RonycineIA",
                    color = Color.Gray.copy(alpha = 0.8f),
                    fontSize = 9.sp
                )
            }
            
            // Bubble with elegant blinking/thinking message
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = 2.dp,
                            bottomEnd = 14.dp
                        )
                    )
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0F2027), Color(0xFF1B2C33))
                        )
                    )
                    .border(1.dp, Color(0xFF1976D2).copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🤖 Cine está digitando...",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic
                    )
                    CircularProgressIndicator(
                        color = Color(0xFF64B5F6),
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp
                    )
                }
            }
        }
    }
}
