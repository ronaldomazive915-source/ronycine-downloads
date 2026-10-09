package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.remote.AppNotification
import com.example.data.remote.AutoNotificationConfig
import com.example.data.remote.FirebaseService
import com.example.data.remote.NotificationSettings
import com.example.data.remote.UserNotificationState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

class NotificationRepository(private val context: Context) {
    private val TAG = "NotificationRepo"
    private val firebaseService = FirebaseService.getInstance(context)
    private val db: FirebaseFirestore? get() = firebaseService.firestoreInstance

    /**
     * Observa notificações globais ordenadas por timestamp.
     */
    fun observeGlobalNotifications(limit: Int = 50): Flow<List<AppNotification>> = callbackFlow {
        val firestore = db ?: run {
            close(IllegalStateException("Firestore is not available"))
            return@callbackFlow
        }

        val listener = firestore.collection("public_notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing notifications: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val notifications = snapshot.documents.mapNotNull { it.toObject(AppNotification::class.java)?.copy(id = it.id) }
                    trySend(notifications)
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Observa o estado de leitura das notificações do usuário logado.
     */
    fun observeUserNotificationStates(userId: String): Flow<Map<String, Boolean>> = callbackFlow {
        val firestore = db ?: run {
            close(IllegalStateException("Firestore is not available"))
            return@callbackFlow
        }

        val listener = firestore.collection("users").document(userId)
            .collection("notification_states")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val states = snapshot.documents.associate { 
                        it.id to (it.getBoolean("read") ?: false)
                    }
                    trySend(states)
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Marca uma notificação específica como lida para o usuário.
     */
    suspend fun markAsRead(userId: String, notificationId: String) = withContext(Dispatchers.IO) {
        val firestore = db ?: return@withContext
        val state = UserNotificationState(
            notificationId = notificationId,
            userId = userId,
            read = true,
            readAt = System.currentTimeMillis()
        )
        try {
            firestore.collection("users").document(userId)
                .collection("notification_states").document(notificationId)
                .set(state, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error marking notification as read: ${e.message}")
        }
    }

    /**
     * Marca todas as notificações como lidas para o usuário.
     */
    suspend fun markAllAsRead(userId: String, notificationIds: List<String>) = withContext(Dispatchers.IO) {
        val firestore = db ?: return@withContext
        if (notificationIds.isEmpty()) return@withContext
        
        try {
            val batch = firestore.batch()
            val now = System.currentTimeMillis()
            notificationIds.forEach { id ->
                val ref = firestore.collection("users").document(userId)
                    .collection("notification_states").document(id)
                batch.set(ref, mapOf("read" to true, "readAt" to now), SetOptions.merge())
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e(TAG, "Error marking all as read: ${e.message}")
        }
    }

    /**
     * Cria uma nova notificação global (Somente Admin).
     */
    suspend fun createGlobalNotification(notification: AppNotification) = withContext(Dispatchers.IO) {
        val firestore = db ?: return@withContext
        val id = notification.id.ifBlank { UUID.randomUUID().toString() }
        val finalNotif = notification.copy(id = id)
        try {
            firestore.collection("public_notifications").document(id)
                .set(finalNotif).await()
            Log.d(TAG, "Global notification created: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification: ${e.message}")
        }
    }

    /**
     * Obtém configurações de notificações do usuário.
     */
    suspend fun getNotificationSettings(userId: String): NotificationSettings = withContext(Dispatchers.IO) {
        val firestore = db ?: return@withContext NotificationSettings()
        try {
            val doc = firestore.collection("users").document(userId)
                .collection("preferences").document("notifications")
                .get().await()
            doc.toObject(NotificationSettings::class.java) ?: NotificationSettings()
        } catch (e: Exception) {
            NotificationSettings()
        }
    }

    /**
     * Salva configurações de notificações do usuário.
     */
    suspend fun saveNotificationSettings(userId: String, settings: NotificationSettings) = withContext(Dispatchers.IO) {
        val firestore = db ?: return@withContext
        try {
            firestore.collection("users").document(userId)
                .collection("preferences").document("notifications")
                .set(settings, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving settings: ${e.message}")
        }
    }

    /**
     * Obtém configuração global de notificações automáticas (Somente Admin).
     */
    suspend fun getAutoNotificationConfig(): AutoNotificationConfig = withContext(Dispatchers.IO) {
        val firestore = db ?: return@withContext AutoNotificationConfig()
        try {
            val doc = firestore.collection("config").document("auto_notifications")
                .get().await()
            doc.toObject(AutoNotificationConfig::class.java) ?: AutoNotificationConfig()
        } catch (e: Exception) {
            AutoNotificationConfig()
        }
    }

    /**
     * Salva configuração global de notificações automáticas (Somente Admin).
     */
    suspend fun saveAutoNotificationConfig(config: AutoNotificationConfig) = withContext(Dispatchers.IO) {
        val firestore = db ?: return@withContext
        try {
            firestore.collection("config").document("auto_notifications")
                .set(config, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving auto config: ${e.message}")
        }
    }
}
