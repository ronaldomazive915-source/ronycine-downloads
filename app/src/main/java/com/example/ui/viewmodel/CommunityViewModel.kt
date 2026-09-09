package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.MediaEntity
import com.example.data.remote.CommunityMessage
import com.example.data.remote.FirebaseService
import com.example.data.repository.CatalogAIService
import com.example.util.CineIntentDetector
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CommunityViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val dao = AppDatabase.getInstance(application).playFilmeDao()
    private val firebaseService = FirebaseService.getInstance(application)
    private val catalogAIService = CatalogAIService.getInstance(application)

    private val _messages = MutableStateFlow<List<CommunityMessage>>(emptyList())
    val messages: StateFlow<List<CommunityMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _replyingTo = MutableStateFlow<CommunityMessage?>(null)
    val replyingTo: StateFlow<CommunityMessage?> = _replyingTo.asStateFlow()

    private val _isBanned = MutableStateFlow(false)
    val isBanned: StateFlow<Boolean> = _isBanned.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _muteUntil = MutableStateFlow(0L)
    val muteUntil: StateFlow<Long> = _muteUntil.asStateFlow()

    private val _isSpamBlocked = MutableStateFlow(false)
    val isSpamBlocked: StateFlow<Boolean> = _isSpamBlocked.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _onlineCount = MutableStateFlow(24)
    val onlineCount: StateFlow<Int> = _onlineCount.asStateFlow()

    private var messagesListener: ListenerRegistration? = null
    private var banListener: ListenerRegistration? = null
    private var muteListener: ListenerRegistration? = null

    private val pendingOptimisticMessages = Collections.synchronizedMap(mutableMapOf<String, CommunityMessage>())
    private val processedAiMessageIds = Collections.synchronizedSet(mutableSetOf<String>())

    private val _sentTimestamps = MutableStateFlow<List<Long>>(emptyList())

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Base offensive words list for instant client-side moderation
    private val badWords = listOf(
        "caralho", "puta", "viado", "fdp", "bosta", "merda", "fuck", "shit",
        "cu", "cuzão", "arrombado", "foder", "foda", "bostas", "putas"
    )

    init {
        setupListeners()
        
        // Timeout check (Requirement 5): Ensure interface is unblocked in 4.5 seconds max
        viewModelScope.launch {
            kotlinx.coroutines.delay(4500)
            if (_isLoading.value) {
                _isLoading.value = false
                _isOffline.value = true
            }
        }
    }

    private fun getSafeLong(doc: com.google.firebase.firestore.DocumentSnapshot, field: String): Long {
        return try {
            when (val value = doc.get(field)) {
                is Number -> value.toLong()
                is com.google.firebase.Timestamp -> value.seconds * 1000 + (value.nanoseconds / 1000000)
                is String -> value.toLongOrNull() ?: System.currentTimeMillis()
                else -> System.currentTimeMillis()
            }
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun setupListeners() {
        // 1. Listen to community messages (unconditional/public - Requirement 9)
        messagesListener = db.collection("community_messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(50) // Paginated count: 50 messages (Requirement 6)
            .addSnapshotListener { snapshot, error ->
                _isLoading.value = false
                if (error != null) {
                    Log.e("CommunityViewModel", "Error listening to messages: ${error.message}")
                    _isOffline.value = true
                    return@addSnapshotListener
                }

                _isOffline.value = false
                if (snapshot != null) {
                    val list = mutableListOf<CommunityMessage>()
                    val remoteIds = mutableSetOf<String>()
                    for (doc in snapshot) {
                        try {
                            val reactionsRaw = (doc.get("reactions") as? Map<*, *>)?.mapNotNull { (k, v) ->
                                if (k is String && v is String) k to v else null
                            }?.toMap() ?: emptyMap()

                            val msg = CommunityMessage(
                                id = doc.id,
                                userId = doc.getString("userId") ?: "",
                                profileId = doc.getString("profileId") ?: "",
                                username = doc.getString("username") ?: "",
                                displayName = doc.getString("displayName") ?: "",
                                avatarUrl = doc.getString("avatarUrl"),
                                isVerified = doc.getBoolean("isVerified") ?: false,
                                text = doc.getString("text") ?: "",
                                createdAt = getSafeLong(doc, "createdAt"),
                                updatedAt = getSafeLong(doc, "updatedAt"),
                                status = doc.getString("status") ?: "ACTIVE",
                                replyToId = doc.getString("replyToId"),
                                replyToUsername = doc.getString("replyToUsername"),
                                replyToText = doc.getString("replyToText"),
                                isAiResponse = doc.getBoolean("isAiResponse") ?: false,
                                recommendedMediaId = doc.getLong("recommendedMediaId")?.toInt(),
                                recommendedMediaType = doc.getString("recommendedMediaType"),
                                recommendedMediaTitle = doc.getString("recommendedMediaTitle"),
                                recommendedMediaPoster = doc.getString("recommendedMediaPoster"),
                                aiProcessed = doc.getBoolean("aiProcessed") ?: false,
                                reactions = reactionsRaw,
                                deliveryStatus = "SENT"
                            )
                            list.add(msg)
                            remoteIds.add(msg.id)
                        } catch (e: Exception) {
                            Log.e("CommunityViewModel", "Error parsing message: ${e.message}")
                        }
                    }

                    // Remove confirmed messages from pendingOptimisticMessages
                    remoteIds.forEach { id ->
                        pendingOptimisticMessages.remove(id)
                    }

                    // Preserve any local in-flight messages that haven't arrived in snapshot yet
                    val pendingList = pendingOptimisticMessages.values.filter { !remoteIds.contains(it.id) }
                    val mergedList = (list + pendingList).sortedBy { it.createdAt }
                    _messages.value = mergedList

                    // Check for automatic recommendation intent on newly arrived messages
                    for (change in snapshot.documentChanges) {
                        if (change.type == DocumentChange.Type.ADDED) {
                            val changeDoc = change.document
                            val createdAt = getSafeLong(changeDoc, "createdAt")
                            val isRecent = System.currentTimeMillis() - createdAt < 120_000
                            val isAi = changeDoc.getBoolean("isAiResponse") ?: false
                            val uId = changeDoc.getString("userId") ?: ""
                            val text = changeDoc.getString("text") ?: ""
                            val mId = changeDoc.id

                            if (isRecent && !isAi && uId != "cine_bot" && uId != "ronycine_ia_bot" && !processedAiMessageIds.contains(mId)) {
                                if (CineIntentDetector.shouldCineRespond(text)) {
                                    processedAiMessageIds.add(mId)
                                    viewModelScope.launch(Dispatchers.IO) {
                                        handleRecommendationTrigger(mId, text)
                                    }
                                }
                            }
                        }
                    }

                    // Dynamic online count simulation based on unique chatters
                    val uniqueChatters = mergedList.filter { System.currentTimeMillis() - it.createdAt < 3600_000 }
                        .map { it.profileId }
                        .distinct()
                        .size
                    _onlineCount.value = 15 + uniqueChatters + (1..10).random()
                }
            }

        // 2. Setup user specific moderation observers (conditional - Requirement 9)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            setupUserModerationListeners(uid)
        }
    }

    private fun setupUserModerationListeners(uid: String) {
        // Observe Ban status
        banListener = db.collection("community_moderation")
            .document("bans")
            .collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, _ ->
                _isBanned.value = snapshot != null && snapshot.exists()
            }

        // Observe Mute status
        muteListener = db.collection("community_moderation")
            .document("mutes")
            .collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val until = snapshot.getLong("until") ?: 0L
                    if (until > System.currentTimeMillis()) {
                        _isMuted.value = true
                        _muteUntil.value = until
                    } else {
                        _isMuted.value = false
                        _muteUntil.value = 0L
                    }
                } else {
                    _isMuted.value = false
                    _muteUntil.value = 0L
                }
            }
    }

    fun setReplyingTo(message: CommunityMessage?) {
        _replyingTo.value = message
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun sendMessage(text: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            _errorMessage.value = "Você precisa estar logado para enviar mensagens."
            return
        }

        val activeProfile = firebaseService.activeProfile.value
        if (activeProfile == null) {
            _errorMessage.value = "Nenhum perfil ativo selecionado."
            return
        }

        if (text.isBlank()) return

        if (_isBanned.value) {
            _errorMessage.value = "Você está banido da comunidade."
            return
        }

        if (_isMuted.value) {
            if (_muteUntil.value > System.currentTimeMillis()) {
                val remainingSeconds = (_muteUntil.value - System.currentTimeMillis()) / 1000
                _errorMessage.value = "Você está silenciado por mais $remainingSeconds segundos."
                return
            } else {
                _isMuted.value = false
            }
        }

        // Spam Protection
        val now = System.currentTimeMillis()
        val filteredTimestamps = _sentTimestamps.value.filter { now - it < 10_000 }
        if (filteredTimestamps.size >= 5) {
            _errorMessage.value = "Você está enviando mensagens muito rápido! Bloqueado por 30s."
            viewModelScope.launch {
                _isSpamBlocked.value = true
                _sentTimestamps.value = emptyList()
                kotlinx.coroutines.delay(30000)
                _isSpamBlocked.value = false
            }
            return
        }
        _sentTimestamps.value = filteredTimestamps + now

        // Link Moderation (except if contains ronycine or playfilme)
        val linkPattern = Regex("(https?://|www\\.)[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        if (linkPattern.containsMatchIn(text)) {
            if (!text.contains("ronycine", ignoreCase = true) && !text.contains("playfilme", ignoreCase = true)) {
                _errorMessage.value = "Links externos não são permitidos na comunidade."
                return
            }
        }

        // Term Moderation
        var processedText = text
        var hadOffensive = false
        for (word in badWords) {
            val regex = Regex("\\b${word}\\b", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(processedText)) {
                processedText = processedText.replace(regex, "[Mensagem moderada: linguagem imprópria]")
                hadOffensive = true
            }
        }

        if (hadOffensive) {
            _errorMessage.value = "Sua mensagem foi moderada por conter termos impróprios."
        }

        // If activeProfile is the founder account, ensure isVerified is true
        val isFounder = currentUser.email?.lowercase() == "ronaldomazive915@gmail.com"
        val forceVerified = isFounder || activeProfile.isVerified

        // Build Firestore message
        val msgId = db.collection("community_messages").document().id
        val reply = _replyingTo.value

        val messageData = mutableMapOf<String, Any>(
            "id" to msgId,
            "userId" to currentUser.uid,
            "profileId" to activeProfile.id,
            "username" to activeProfile.username,
            "displayName" to activeProfile.name,
            "avatarUrl" to (activeProfile.avatarUrl ?: ""),
            "isVerified" to forceVerified,
            "text" to processedText,
            "createdAt" to now,
            "updatedAt" to now,
            "status" to "ACTIVE"
        )

        if (reply != null) {
            messageData["replyToId"] = reply.id
            messageData["replyToUsername"] = reply.username
            messageData["replyToText"] = reply.text
        }

        val optimisticMessage = CommunityMessage(
            id = msgId,
            userId = currentUser.uid,
            profileId = activeProfile.id,
            username = activeProfile.username,
            displayName = activeProfile.name,
            avatarUrl = activeProfile.avatarUrl ?: "",
            isVerified = forceVerified,
            text = processedText,
            createdAt = now,
            updatedAt = now,
            status = "ACTIVE",
            replyToId = reply?.id,
            replyToUsername = reply?.username,
            replyToText = reply?.text,
            isAiResponse = false,
            deliveryStatus = "SENDING"
        )

        // 1. Instant Optimistic UI Update: Insert message in UI immediately (< 1ms)
        pendingOptimisticMessages[msgId] = optimisticMessage
        _messages.value = (_messages.value + optimisticMessage).sortedBy { it.createdAt }
        _replyingTo.value = null

        // 2. Persist to Firestore in detached background thread
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.collection("community_messages").document(msgId).set(messageData).awaitOrNull()

                // Mark local optimistic message as confirmed (SENT)
                val confirmed = optimisticMessage.copy(deliveryStatus = "SENT")
                pendingOptimisticMessages[msgId] = confirmed
                _messages.value = _messages.value.map { if (it.id == msgId) confirmed else it }

                // Check AI response intent in background queue
                if (CineIntentDetector.shouldCineRespond(processedText)) {
                    if (!processedAiMessageIds.contains(msgId)) {
                        processedAiMessageIds.add(msgId)
                        launch(Dispatchers.IO) {
                            handleRecommendationTrigger(msgId, processedText)
                        }
                    }
                }
                launch(Dispatchers.IO) {
                    handleMentions(processedText, activeProfile.username)
                }
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "Error saving message to Firestore: ${e.message}")
                val failed = optimisticMessage.copy(deliveryStatus = "FAILED")
                pendingOptimisticMessages[msgId] = failed
                _messages.value = _messages.value.map { if (it.id == msgId) failed else it }
            }
        }
    }

    fun retrySendMessage(messageId: String) {
        val msg = pendingOptimisticMessages[messageId] ?: _messages.value.find { it.id == messageId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val retrying = msg.copy(deliveryStatus = "SENDING")
            pendingOptimisticMessages[messageId] = retrying
            _messages.value = _messages.value.map { if (it.id == messageId) retrying else it }

            try {
                val messageData = mutableMapOf<String, Any>(
                    "id" to msg.id,
                    "userId" to msg.userId,
                    "profileId" to msg.profileId,
                    "username" to msg.username,
                    "displayName" to msg.displayName,
                    "avatarUrl" to (msg.avatarUrl ?: ""),
                    "isVerified" to msg.isVerified,
                    "text" to msg.text,
                    "createdAt" to msg.createdAt,
                    "updatedAt" to System.currentTimeMillis(),
                    "status" to "ACTIVE"
                )
                if (msg.replyToId != null) {
                    messageData["replyToId"] = msg.replyToId
                    messageData["replyToUsername"] = msg.replyToUsername ?: ""
                    messageData["replyToText"] = msg.replyToText ?: ""
                }
                db.collection("community_messages").document(msg.id).set(messageData).awaitOrNull()
                val sent = msg.copy(deliveryStatus = "SENT")
                pendingOptimisticMessages[messageId] = sent
                _messages.value = _messages.value.map { if (it.id == messageId) sent else it }
            } catch (e: Exception) {
                val failed = msg.copy(deliveryStatus = "FAILED")
                pendingOptimisticMessages[messageId] = failed
                _messages.value = _messages.value.map { if (it.id == messageId) failed else it }
            }
        }
    }

    private suspend fun handleRecommendationTrigger(messageId: String, text: String) {
        withContext(Dispatchers.IO) {
            try {
                val eventRef = db.collection("community_ai_events").document(messageId)
                val eventDoc = eventRef.get().awaitOrNull()
                if (eventDoc != null && eventDoc.exists()) {
                    return@withContext
                }

                eventRef.set(mapOf(
                    "messageId" to messageId,
                    "claimedAt" to System.currentTimeMillis()
                )).awaitOrNull()

                val cineDoc = try {
                    db.collection("admin_config").document("cine_config").get().awaitOrNull()
                } catch (_: Exception) { null }

                val cineActive = cineDoc?.getBoolean("isActive") ?: true
                if (cineActive) {
                    val activeProfileId = firebaseService.activeProfile.value?.id
                    respondAsAi(text, cineDoc, activeProfileId)
                }
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "Error handling recommendation trigger: ${e.message}")
            }
        }
    }

    private suspend fun handleMentions(text: String, senderUsername: String) {
        // User Mention Check
        val mentionPattern = Regex("@(\\w+)")
        val matches = mentionPattern.findAll(text)
        for (match in matches) {
            val mentionedUsername = "@" + match.groups[1]?.value?.lowercase()
            if (mentionedUsername == "@cine" || mentionedUsername == "@ronycineia" || mentionedUsername == "@ia") continue
            if (mentionedUsername.equals(senderUsername, ignoreCase = true)) continue

            // Create community notification in Firestore for mentioned user
            createMentionNotification(mentionedUsername, text)
        }
    }

    private suspend fun createMentionNotification(username: String, text: String) {
        withContext(Dispatchers.IO) {
            try {
                // Find target profile by username normalized in Firestore
                val targetDocs = db.collection("profiles")
                    .whereEqualTo("username", username)
                    .get()
                    .awaitOrNull()

                val firstDoc = targetDocs?.documents?.firstOrNull() ?: return@withContext
                val profileId = firstDoc.id
                val userId = firstDoc.getString("userId") ?: return@withContext

                // Create a standard system notification or write to user's notifications
                val notifId = "mention_${System.currentTimeMillis()}"
                val notificationMap = mapOf(
                    "id" to notifId,
                    "title" to "Menção na Comunidade 💬",
                    "message" to "Você foi mencionado na comunidade: \"$text\"",
                    "type" to "INFORMACAO",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false,
                    "isActive" to true,
                    "isSent" to true,
                    "targetSegment" to "PROFILE_$profileId", // For target segment isolation
                    "profileId" to profileId,
                    "userId" to userId
                )

                db.collection("notifications").document(notifId).set(notificationMap)
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "Error creating mention notification: ${e.message}")
            }
        }
    }

    private fun respondAsAi(userPrompt: String, cineDoc: com.google.firebase.firestore.DocumentSnapshot?, profileId: String?) {
        viewModelScope.launch {
            _isAiThinking.value = true
            try {
                val name = cineDoc?.getString("name") ?: "Cine"
                val personality = cineDoc?.getString("personality") ?: "amigável, cinéfilo e profissional"
                val avatarUrl = cineDoc?.getString("avatarUrl") ?: ""

                val cineResponse = catalogAIService.generateResponse(
                    userPrompt = userPrompt,
                    profileId = profileId,
                    cineName = name,
                    cinePersonality = personality
                )

                if (cineResponse.text.isBlank()) return@launch

                // Save AI response to Firestore
                val msgId = db.collection("community_messages").document().id
                val aiData = mapOf<String, Any>(
                    "id" to msgId,
                    "userId" to "cine_bot",
                    "profileId" to "cine_profile",
                    "username" to "@${name.lowercase().replace("\\s".toRegex(), "")}",
                    "displayName" to "$name ✓",
                    "avatarUrl" to avatarUrl,
                    "isVerified" to true,
                    "text" to cineResponse.text,
                    "createdAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis(),
                    "status" to "ACTIVE",
                    "isAiResponse" to true,
                    "recommendedMediaId" to (cineResponse.recommendedMediaId ?: -1),
                    "recommendedMediaType" to (cineResponse.recommendedMediaType ?: ""),
                    "recommendedMediaTitle" to (cineResponse.recommendedMediaTitle ?: ""),
                    "recommendedMediaPoster" to (cineResponse.recommendedMediaPoster ?: "")
                ).filter { it.value != "" && (it.value as? Int) != -1 }

                db.collection("community_messages").document(msgId).set(aiData)

            } catch (e: Exception) {
                Log.e("CommunityViewModel", "Cine response generation error: ${e.message}")
            } finally {
                _isAiThinking.value = false
            }
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            _errorMessage.value = "Você precisa estar logado para reagir."
            return
        }
        val activeProfile = firebaseService.activeProfile.value
        val userId = currentUser.uid
        val profileId = activeProfile?.id ?: userId

        viewModelScope.launch {
            try {
                val msgRef = db.collection("community_messages").document(messageId)
                val subReactionRef = msgRef.collection("reactions").document(userId)

                val snapshot = msgRef.get().awaitOrNull() ?: return@launch
                val rawReactions = snapshot.get("reactions") as? Map<*, *>
                val currentReactions = mutableMapOf<String, String>()
                rawReactions?.forEach { (k, v) ->
                    if (k is String && v is String) {
                        currentReactions[k] = v
                    }
                }

                val existingReaction = currentReactions[userId]

                if (existingReaction == emoji) {
                    // Remove reaction (toggle off)
                    msgRef.update("reactions.$userId", FieldValue.delete()).awaitOrNull()
                    subReactionRef.delete().awaitOrNull()
                } else {
                    // Add or update reaction
                    msgRef.set(
                        mapOf("reactions" to mapOf(userId to emoji)),
                        SetOptions.merge()
                    ).awaitOrNull()

                    val reactionData = mapOf(
                        "emoji" to emoji,
                        "userId" to userId,
                        "profileId" to profileId,
                        "createdAt" to System.currentTimeMillis(),
                        "updatedAt" to System.currentTimeMillis()
                    )
                    subReactionRef.set(reactionData).awaitOrNull()
                }
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "Error toggling reaction: ${e.message}")
            }
        }
    }

    // --- Administration Moderation Commands ---

    fun deleteMessage(messageId: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser?.email?.lowercase() != "ronaldomazive915@gmail.com") return

        viewModelScope.launch {
            try {
                db.collection("community_messages").document(messageId).update("status", "DELETED")
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "Error deleting message: ${e.message}")
            }
        }
    }

    fun muteUser(targetUserId: String, durationMinutes: Int) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser?.email?.lowercase() != "ronaldomazive915@gmail.com") return

        val untilTime = System.currentTimeMillis() + (durationMinutes * 60_000L)
        viewModelScope.launch {
            try {
                db.collection("community_moderation")
                    .document("mutes")
                    .collection("users")
                    .document(targetUserId)
                    .set(mapOf("until" to untilTime, "mutedBy" to currentUser.uid))
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "Error muting user: ${e.message}")
            }
        }
    }

    fun unmuteUser(targetUserId: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser?.email?.lowercase() != "ronaldomazive915@gmail.com") return

        viewModelScope.launch {
            try {
                db.collection("community_moderation")
                    .document("mutes")
                    .collection("users")
                    .document(targetUserId)
                    .delete()
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "Error unmuting user: ${e.message}")
            }
        }
    }

    fun banUser(targetUserId: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser?.email?.lowercase() != "ronaldomazive915@gmail.com") return

        viewModelScope.launch {
            try {
                db.collection("community_moderation")
                    .document("bans")
                    .collection("users")
                    .document(targetUserId)
                    .set(mapOf("bannedAt" to System.currentTimeMillis(), "bannedBy" to currentUser.uid))
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "Error banning user: ${e.message}")
            }
        }
    }

    fun unbanUser(targetUserId: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser?.email?.lowercase() != "ronaldomazive915@gmail.com") return

        viewModelScope.launch {
            try {
                db.collection("community_moderation")
                    .document("bans")
                    .collection("users")
                    .document(targetUserId)
                    .delete()
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "Error unbanning user: ${e.message}")
            }
        }
    }

    fun retry() {
        _isLoading.value = true
        _isOffline.value = false
        messagesListener?.remove()
        setupListeners()
    }

    override fun onCleared() {
        super.onCleared()
        messagesListener?.remove()
        banListener?.remove()
        muteListener?.remove()
    }

    // Helper extension to handle await smoothly
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitOrNull(): T? =
        withContext(Dispatchers.IO) {
            try {
                com.google.android.gms.tasks.Tasks.await(this@awaitOrNull)
            } catch (e: Exception) {
                null
            }
        }
}
