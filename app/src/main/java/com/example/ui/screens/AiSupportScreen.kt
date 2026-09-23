package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.repository.CatalogAIService
import com.example.data.repository.CineResponse
import com.example.ui.components.ScreenRoute
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandRedDark
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ContentShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private const val OFFICIAL_WHATSAPP_LINK = "https://chat.whatsapp.com/GxpONcioT1p1fSROKRHau3"

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaRecommendation: CineResponse? = null
)

private enum class HelpCategory(
    val title: String,
    val icon: ImageVector,
    val description: String
) {
    PLAYBACK("Problema para assistir", Icons.Filled.PlayCircleOutline, "Tela preta, sem áudio ou falha no player"),
    DOWNLOAD("Problema com download", Icons.Filled.DownloadDone, "Download travado ou não reproduz offline"),
    LINK("Problema com link", Icons.Filled.Link, "Testar e validar link de filme ou série"),
    ACCESS("Problema de acesso", Icons.Filled.VpnKey, "Login, senha, perfil e período de teste"),
    APP("Problema no aplicativo", Icons.Filled.Smartphone, "Travamento, botões ou erros visuais"),
    FAQ("Perguntas frequentes", Icons.Filled.HelpOutline, "Dúvidas comuns sobre como usar o RONYCINE")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSupportScreen(
    mainViewModel: MainViewModel,
    authViewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Int, String) -> Unit,
    onNavigateToWatch: (Int, String, Int?, Int?) -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val currentUser by authViewModel.currentUser.collectAsState()
    val activeProfile by authViewModel.activeProfile.collectAsState()

    var activeDialogCategory by remember { mutableStateOf<HelpCategory?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    var initialReportCategory by remember { mutableStateOf("Geral") }

    // Chat State
    var promptInput by remember { mutableStateOf("") }
    var isAiTyping by remember { mutableStateOf(false) }
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                isUser = false,
                text = "Olá! 👋 Sou o suporte inteligente do RONYCINE.\n\nComo posso ajudar você hoje? Tire dúvidas sobre filmes, séries, episódios, downloads, reprodução ou recursos do aplicativo."
            )
        )
    }

    // Voice recognition state
    var isListening by remember { mutableStateOf(false) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Dispose speech recognizer on leave
    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    // Microphone permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechRecognition(
                context = context,
                onListeningStateChange = { isListening = it },
                onResult = { text ->
                    promptInput = text
                    isListening = false
                },
                onError = { errorMsg ->
                    isListening = false
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                },
                onRecognizerCreated = { speechRecognizer = it }
            )
        } else {
            Toast.makeText(
                context,
                "Permissão de microfone necessária para perguntas por voz.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val handleVoiceClick: () -> Unit = {
        if (isListening) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                // ignore
            }
            isListening = false
        } else {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            )
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition(
                    context = context,
                    onListeningStateChange = { isListening = it },
                    onResult = { text ->
                        promptInput = text
                        isListening = false
                    },
                    onError = { errorMsg ->
                        isListening = false
                        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                    },
                    onRecognizerCreated = { speechRecognizer = it }
                )
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val handleSendMessage: (String) -> Unit = { textToSend ->
        val query = textToSend.trim()
        if (query.isNotBlank() && !isAiTyping) {
            focusManager.clearFocus()
            promptInput = ""
            messages.add(ChatMessage(isUser = true, text = query))
            isAiTyping = true

            coroutineScope.launch {
                try {
                    val aiService = CatalogAIService.getInstance(context)
                    val response = aiService.generateResponse(
                        userPrompt = query,
                        profileId = activeProfile?.id,
                        cineName = "Suporte RONYCINE",
                        cinePersonality = "prestativo, cinéfilo, técnico e direto"
                    )
                    messages.add(
                        ChatMessage(
                            isUser = false,
                            text = response.text,
                            mediaRecommendation = response
                        )
                    )
                } catch (e: Exception) {
                    messages.add(
                        ChatMessage(
                            isUser = false,
                            text = "Tivemos uma oscilação na consulta. Você também pode verificar a seção de Ajuda Rápida acima ou falar com a equipe no WhatsApp oficial."
                        )
                    )
                } finally {
                    isAiTyping = false
                }
            }
        }
    }

    val listState = rememberLazyListState()

    // Scroll down when new message is added
    LaunchedEffect(messages.size, isAiTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size + 4) // adjust to chat list items
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(BrandRed, BrandRedDark)
                                    ),
                                    shape = CircleShape
                                )
                                .border(1.dp, Color(0x66FFA4A4), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Headset,
                                contentDescription = "Ícone de Headset do Suporte AI",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "SUPORTE AI",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Central de Ajuda RONYCINE",
                                fontSize = 11.sp,
                                color = Color(0xFFB0B0C0)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("ai_support_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar para tela anterior",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            openWhatsAppSupport(context)
                        },
                        modifier = Modifier.testTag("ai_support_header_whatsapp")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Chat,
                            contentDescription = "Falar com suporte no WhatsApp",
                            tint = Color(0xFF25D366)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0C0C12),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF07070A)
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
        ) {
            // 1. HERO HEADER SECTION
            item(key = "hero_header") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black, spotColor = BrandRed.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13131D)),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x33E50914),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(BrandRed, BrandRedDark)
                                        ),
                                        CircleShape
                                    )
                                    .shadow(10.dp, CircleShape, spotColor = BrandRed)
                                    .border(1.5.dp, Color(0x88FFB4B4), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Headset,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Como podemos ajudar você?",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Tire suas dúvidas, consulte o catálogo e resolva problemas técnicos no RONYCINE.",
                                fontSize = 12.5.sp,
                                color = Color(0xFFC0C0D4),
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }

            // 2. QUICK HELP SECTION (ÁREA DE AJUDA RÁPIDA)
            item(key = "quick_help_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "AJUDA RÁPIDA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFE50914),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Toque para diagnosticar",
                        fontSize = 11.sp,
                        color = Color(0xFF88889A)
                    )
                }
            }

            item(key = "quick_help_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val categories = HelpCategory.values().toList()
                    val rows = categories.chunked(2)
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { cat ->
                                QuickHelpCard(
                                    category = cat,
                                    onClick = { activeDialogCategory = cat },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // 3. WHATSAPP SUPPORT CARD
            item(key = "whatsapp_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openWhatsAppSupport(context) }
                        .testTag("ai_support_whatsapp_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1B14)),
                    border = BorderStroke(1.dp, Color(0x3325D366))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color(0xFF25D366), CircleShape)
                                .shadow(6.dp, CircleShape, spotColor = Color(0xFF25D366))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Chat,
                                contentDescription = "WhatsApp",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "💬 Suporte no WhatsApp",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Fale diretamente com o suporte e comunidade oficial do RONYCINE.",
                                fontSize = 11.5.sp,
                                color = Color(0xFFA5D6A7),
                                lineHeight = 15.sp
                            )
                        }

                        Button(
                            onClick = { openWhatsAppSupport(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF25D366),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = "ABRIR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 4. CHAT AI SECTION (PERGUNTE À IA)
            item(key = "chat_ai_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🤖 PERGUNTE À IA DO RONYCINE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFE50914),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Respostas verificadas",
                        fontSize = 11.sp,
                        color = Color(0xFF88889A)
                    )
                }
            }

            // Suggested quick questions
            item(key = "quick_suggestions") {
                val suggestions = listOf(
                    "Meu filme está com tela preta",
                    "Como faço download?",
                    "Como escolher uma temporada?",
                    "Como trocar áudio para dublado?",
                    "Como compartilhar um filme?"
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(suggestions) { sug ->
                        SuggestionChip(
                            onClick = { handleSendMessage(sug) },
                            label = {
                                Text(
                                    text = sug,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFD6D6E6)
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFF161622)
                            ),
                            border = BorderStroke(1.dp, Color(0x2BFFFFFF)),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Messages history
            items(messages, key = { it.id }) { msg ->
                ChatMessageItem(
                    message = msg,
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToWatch = onNavigateToWatch
                )
            }

            // AI Typing Indicator
            if (isAiTyping) {
                item(key = "ai_typing_indicator") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFF221417), CircleShape)
                                .border(1.dp, Color(0x66E50914), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Headset,
                                contentDescription = null,
                                tint = BrandRed,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF181824)),
                            border = BorderStroke(1.dp, Color(0x22FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = BrandRed
                                )
                                Text(
                                    text = "Consultando catálogo e dados do RONYCINE...",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFAAAAAF)
                                )
                            }
                        }
                    }
                }
            }

            // 5. INPUT FIELD WITH VOICE AND SEND BUTTON
            item(key = "input_bar") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF12121C)),
                    border = BorderStroke(
                        1.dp,
                        if (isListening) Color(0xFFE50914) else Color(0x33FFFFFF)
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        if (isListening) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(BrandRed, CircleShape)
                                )
                                Text(
                                    text = "Ouvindo sua voz... Fale agora.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF8888)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = promptInput,
                                onValueChange = { promptInput = it },
                                placeholder = {
                                    Text(
                                        text = if (isListening) "Ouvindo..." else "Digite sua dúvida...",
                                        fontSize = 13.sp,
                                        color = Color(0xFF7E7E92)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_support_input_field"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = BrandRed
                                ),
                                singleLine = false,
                                maxLines = 3,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = { handleSendMessage(promptInput) }
                                )
                            )

                            // Voice Input Button 🎤
                            IconButton(
                                onClick = handleVoiceClick,
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        if (isListening) Color(0xFFE50914) else Color(0x1FFFFFFF),
                                        CircleShape
                                    )
                                    .testTag("ai_support_voice_button")
                            ) {
                                Icon(
                                    imageVector = if (isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                                    contentDescription = "Perguntar por voz",
                                    tint = if (isListening) Color.White else Color(0xFFFFB3B3),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Send Button ➤
                            IconButton(
                                onClick = { handleSendMessage(promptInput) },
                                enabled = promptInput.isNotBlank() && !isAiTyping,
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        if (promptInput.isNotBlank() && !isAiTyping) BrandRed else Color(0x33444455),
                                        CircleShape
                                    )
                                    .testTag("ai_support_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Enviar pergunta para o Suporte AI",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 6. ACTION FOOTER (ENVIAR RELATÓRIO & WHATSAPP)
            item(key = "footer_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            initialReportCategory = "Geral"
                            showReportDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("ai_support_send_report_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0x44FFFFFF)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Assignment,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFFFC107)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Enviar Relatório",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { openWhatsAppSupport(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("ai_support_whatsapp_bottom_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E7E34),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WhatsApp",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Modal dialog for active help category
    activeDialogCategory?.let { category ->
        HelpCategoryDetailDialog(
            category = category,
            authViewModel = authViewModel,
            mainViewModel = mainViewModel,
            onDismiss = { activeDialogCategory = null },
            onRequestReport = { catName ->
                activeDialogCategory = null
                initialReportCategory = catName
                showReportDialog = true
            },
            onNavigateToWatch = onNavigateToWatch,
            onNavigate = onNavigate
        )
    }

    // Technical Report Dialog
    if (showReportDialog) {
        TechnicalReportDialog(
            initialCategory = initialReportCategory,
            currentUserEmail = currentUser?.email,
            activeProfileName = activeProfile?.name,
            onDismiss = { showReportDialog = false }
        )
    }
}

// ----------------------------------------------------
// UI COMPONENTS
// ----------------------------------------------------

@Composable
private fun QuickHelpCard(
    category: HelpCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(84.dp)
            .clickable(onClick = onClick)
            .testTag("quick_help_card_${category.name.lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12121A)),
        border = BorderStroke(1.dp, Color(0x1FFFFFFF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .background(Color(0x33E50914), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = Color(0xFFFF6666),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = category.title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = category.description,
                fontSize = 10.sp,
                color = Color(0xFF9E9EAF),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    onNavigateToDetail: (Int, String) -> Unit,
    onNavigateToWatch: (Int, String, Int?, Int?) -> Unit
) {
    val isUser = message.isUser

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            if (!isUser) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(0xFF221417), CircleShape)
                        .border(1.dp, Color(0x66E50914), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Headset,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) Color(0xFFB71C1C) else Color(0xFF161622)
                ),
                border = BorderStroke(
                    1.dp,
                    if (isUser) Color(0x44FF8A80) else Color(0x22FFFFFF)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.text,
                        fontSize = 13.sp,
                        color = Color.White,
                        lineHeight = 18.sp
                    )

                    // If recommendation contains media
                    message.mediaRecommendation?.let { rec ->
                        if (rec.recommendedMediaId != null && !rec.recommendedMediaTitle.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onNavigateToDetail(
                                            rec.recommendedMediaId,
                                            rec.recommendedMediaType ?: "movie"
                                        )
                                    },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D14)),
                                border = BorderStroke(1.dp, Color(0x44E50914))
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (!rec.recommendedMediaPoster.isNullOrBlank()) {
                                        AsyncImage(
                                            model = "https://image.tmdb.org/t/p/w185${rec.recommendedMediaPoster}",
                                            contentDescription = rec.recommendedMediaTitle,
                                            modifier = Modifier
                                                .size(width = 36.dp, height = 52.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = rec.recommendedMediaTitle,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "Disponível no RONYCINE",
                                            fontSize = 10.sp,
                                            color = Color(0xFF81C784)
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            onNavigateToWatch(
                                                rec.recommendedMediaId,
                                                rec.recommendedMediaType ?: "movie",
                                                null,
                                                null
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text(
                                            text = "ASSISTIR",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// QUICK HELP DETAILED DIALOG
// ----------------------------------------------------

@Composable
private fun HelpCategoryDetailDialog(
    category: HelpCategory,
    authViewModel: AuthViewModel,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit,
    onRequestReport: (String) -> Unit,
    onNavigateToWatch: (Int, String, Int?, Int?) -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    var linkInput by remember { mutableStateOf("") }
    var linkDiagnosticResult by remember { mutableStateOf<String?>(null) }
    var parsedMediaAction by remember { mutableStateOf<ContentShareHelper.ParsedSharedContent?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            border = BorderStroke(1.dp, Color(0x33FFFFFF))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0x33E50914), CircleShape)
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = BrandRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Diagnóstico e Soluções Oficiais",
                                fontSize = 11.sp,
                                color = Color(0xFF9E9EAF)
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Fechar",
                                tint = Color.LightGray
                            )
                        }
                    }
                }

                when (category) {
                    HelpCategory.PLAYBACK -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                StepItem("1. Trocar Player", "Vá em Configurações e alterne entre o Player Nativo (ExoPlayer) e o Player Web.")
                                StepItem("2. Alternar Áudio", "No topo do player, clique no botão de idioma e selecione Dublado ou Legendado para carregar uma fonte alternativa.")
                                StepItem("3. Verificar Conexão", "Transmissões em HD requerem conexão estável de pelo menos 5 Mbps.")
                                StepItem("4. Limpar Cache", "Se o vídeo travar em loop, feche o app e limpe o cache nas configurações do Android.")
                            }
                        }
                    }

                    HelpCategory.DOWNLOAD -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                StepItem("1. Gerenciadores Externos", "O RONYCINE suporta download interno, 1DM e ADM com velocidade máxima.")
                                StepItem("2. Armazenamento", "Certifique-se de que o aparelho possui espaço livre suficiente para salvar o vídeo.")
                                StepItem("3. Onde Encontrar", "Seus downloads salvos ficam disponíveis na aba 'Meus Downloads' do menu.")
                            }
                        }
                        item {
                            Button(
                                onClick = {
                                    onDismiss()
                                    onNavigate(ScreenRoute.DOWNLOADS.route)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A3C)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Abrir Meus Downloads", fontSize = 12.sp)
                            }
                        }
                    }

                    HelpCategory.LINK -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Cole o link recebido para validar se ele é um link oficial do RONYCINE:",
                                    fontSize = 12.sp,
                                    color = Color.LightGray
                                )

                                OutlinedTextField(
                                    value = linkInput,
                                    onValueChange = { linkInput = it },
                                    placeholder = { Text("https://ronycine.app/filme/...", fontSize = 11.5.sp, color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = BrandRed,
                                        unfocusedBorderColor = Color(0x44FFFFFF)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )

                                Button(
                                    onClick = {
                                        val parsed = ContentShareHelper.handleSharedContentLink(linkInput)
                                        parsedMediaAction = parsed
                                        if (parsed != null) {
                                            val typeLabel = if (parsed.mediaType == "tv") "Série" else "Filme"
                                            val epInfo = if (parsed.seasonNumber != null && parsed.episodeNumber != null) " (T${parsed.seasonNumber}:E${parsed.episodeNumber})" else ""
                                            linkDiagnosticResult = "✅ Link válido! Conteúdo: $typeLabel [ID: ${parsed.tmdbId}]$epInfo"
                                        } else {
                                            linkDiagnosticResult = "❌ Link inválido ou formato não reconhecido. Certifique-se de colar um link no formato https://ronycine.app/filme/{id} ou https://ronycine.app/serie/{id}."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Validar e Testar Link", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                linkDiagnosticResult?.let { result ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F16)),
                                        border = BorderStroke(1.dp, if (parsedMediaAction != null) Color(0xFF4CAF50) else Color(0xFFE53935))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(text = result, fontSize = 11.5.sp, color = Color.White)
                                            parsedMediaAction?.let { act ->
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = {
                                                        onDismiss()
                                                        onNavigateToWatch(
                                                            act.tmdbId,
                                                            act.mediaType,
                                                            act.seasonNumber,
                                                            act.episodeNumber
                                                        )
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("▶️ Abrir Conteúdo Agora", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HelpCategory.ACCESS -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val user = authViewModel.currentUser.collectAsState().value
                                val profile = authViewModel.activeProfile.collectAsState().value

                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D14)),
                                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "Status da Sessão",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFB3B3)
                                        )
                                        Text(
                                            text = "Usuário: ${user?.email ?: "Não autenticado (Convidado)"}",
                                            fontSize = 11.sp,
                                            color = Color.LightGray
                                        )
                                        Text(
                                            text = "Perfil Ativo: ${profile?.name ?: "Padrão"}",
                                            fontSize = 11.sp,
                                            color = Color.LightGray
                                        )
                                    }
                                }

                                StepItem("1. Esqueci Minha Senha", "Use a opção 'Esqueci a senha' na tela de login informando seu e-mail cadastrado.")
                                StepItem("2. Trocar de Perfil", "Acesse 'Perfil' na barra inferior e toque em 'Quem está assistindo?' para alternar perfis.")
                                StepItem("3. Período de Teste", "O RONYCINE avalia automaticamente o acesso liberado para sua conta.")
                            }
                        }
                    }

                    HelpCategory.APP -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                StepItem("1. Atualizações", "Mantenha o app na versão mais recente disponível na tela de Atualizações.")
                                StepItem("2. Memória RAM", "Feche aplicativos em segundo plano se o aparelho apresentar lentidão.")
                                StepItem("3. Relatar Erro", "Envie um relatório técnico informando o que aconteceu para que a equipe corrija.")
                            }
                        }
                    }

                    HelpCategory.FAQ -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                FaqAccordion(
                                    question = "Como assistir a um filme ou série?",
                                    answer = "Navegue pela tela inicial ou use a busca em Explorar. Toque no pôster do título e clique em 'Assistir Agora'. Para séries, você pode escolher a temporada e episódio desejado."
                                )
                                FaqAccordion(
                                    question = "Como escolher temporada e episódio?",
                                    answer = "Na página de detalhes da série, role até a seção 'Temporadas', selecione o número da temporada e toque no card do episódio para iniciar."
                                )
                                FaqAccordion(
                                    question = "Como funciona o compartilhamento?",
                                    answer = "Ao tocar em 'Partilhar' em qualquer filme ou episódio, o RONYCINE gera um link real que, ao ser aberto em outro aparelho com o app, inicia diretamente a reprodução do título correspondente."
                                )
                                FaqAccordion(
                                    question = "Como trocar a fonte e áudio do player?",
                                    answer = "Durante a reprodução, toque na tela para exibir os controles e selecione o botão de áudio/engrenagem para alternar entre Dublado, Legendado e opções de fonte."
                                )
                                FaqAccordion(
                                    question = "Como assistir canais de TV ao vivo?",
                                    answer = "Toque na aba 'TV ao Vivo' na barra de navegação inferior. Selecione o canal na lista para iniciar a transmissão em tempo real."
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onRequestReport(category.title) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0x44FFA000)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFC107))
                        ) {
                            Icon(imageVector = Icons.Filled.Assignment, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Enviar Relatório", fontSize = 11.sp)
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(0.8f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E3E))
                        ) {
                            Text("Fechar", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepItem(step: String, detail: String) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101018)),
        border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = step, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = detail, fontSize = 11.sp, color = Color(0xFFB0B0C0), lineHeight = 15.sp)
        }
    }
}

@Composable
private fun FaqAccordion(question: String, answer: String) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101018)),
        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = question,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = answer,
                        fontSize = 11.5.sp,
                        color = Color(0xFFC0C0D4),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// TECHNICAL REPORT DIALOG
// ----------------------------------------------------

@Composable
private fun TechnicalReportDialog(
    initialCategory: String,
    currentUserEmail: String?,
    activeProfileName: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var problemDescription by remember { mutableStateOf("") }

    val appVersion = "1.1.0"
    val osVersion = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
    val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
    val timestampFormatted = remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
    }

    val generateReportText: () -> String = {
        """
            📋 RELATÓRIO TÉCNICO RONYCINE
            Data/Hora: $timestampFormatted
            Categoria: $selectedCategory
            Versão do App: $appVersion
            Sistema: Android $osVersion
            Dispositivo: $deviceModel
            Conta: ${currentUserEmail ?: "Convidado"}
            Perfil: ${activeProfileName ?: "Padrão"}
            
            Descrição do Problema:
            ${if (problemDescription.isBlank()) "Sem descrição adicional informada." else problemDescription.trim()}
        """.trimIndent()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141E)),
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Assignment,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enviar Relatório Técnico",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Coleta de dados técnicos para diagnóstico",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Fechar", tint = Color.LightGray)
                    }
                }

                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D14)),
                    border = BorderStroke(1.dp, Color(0x22FFFFFF))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("• Versão RONYCINE: $appVersion", fontSize = 11.sp, color = Color.LightGray)
                        Text("• Aparelho: $deviceModel", fontSize = 11.sp, color = Color.LightGray)
                        Text("• Android: $osVersion", fontSize = 11.sp, color = Color.LightGray)
                    }
                }

                OutlinedTextField(
                    value = problemDescription,
                    onValueChange = { problemDescription = it },
                    placeholder = { Text("Descreva o que aconteceu (opcional)...", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = Color(0x44FFFFFF)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 3,
                    maxLines = 5
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Copiar Relatório
                    OutlinedButton(
                        onClick = {
                            val text = generateReportText()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Relatório RONYCINE", text))
                            Toast.makeText(context, "Relatório copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0x44FFFFFF)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar", fontSize = 11.sp)
                    }

                    // Compartilhar / Enviar
                    Button(
                        onClick = {
                            val text = generateReportText()
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Enviar Relatório Técnico RONYCINE"))
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                    ) {
                        Icon(imageVector = Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Compartilhar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// UTILITY FUNCTIONS: WHATSAPP & SPEECH RECOGNITION
// ----------------------------------------------------

private fun openWhatsAppSupport(context: Context) {
    try {
        val uri = Uri.parse(OFFICIAL_WHATSAPP_LINK)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        // Set package to whatsapp if available
        intent.setPackage("com.whatsapp")
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            // Fallback: standard ACTION_VIEW via browser or chooser
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_WHATSAPP_LINK))
            context.startActivity(fallbackIntent)
        } catch (e2: Exception) {
            Toast.makeText(context, "Não foi possível abrir o link do WhatsApp.", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun startSpeechRecognition(
    context: Context,
    onListeningStateChange: (Boolean) -> Unit,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
    onRecognizerCreated: (SpeechRecognizer) -> Unit
) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        onError("Reconhecimento de voz não está disponível no dispositivo.")
        return
    }

    try {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        onRecognizerCreated(recognizer)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale sua dúvida sobre o RONYCINE...")
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onListeningStateChange(true)
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                onListeningStateChange(false)
            }

            override fun onError(error: Int) {
                onListeningStateChange(false)
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Erro de áudio."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Nenhuma fala detectada. Tente novamente."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Erro de conexão para reconhecimento de voz."
                    else -> "Não foi possível reconhecer a fala."
                }
                onError(msg)
            }

            override fun onResults(results: Bundle?) {
                onListeningStateChange(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onResult(matches[0])
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    } catch (e: Exception) {
        onListeningStateChange(false)
        onError("Falha ao inicializar o microfone: ${e.message}")
    }
}
