package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.remote.AppNotification
import com.example.data.remote.FirebaseService
import com.example.data.repository.NotificationRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NotificationUiItem(
    val notification: AppNotification,
    val isRead: Boolean
)

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NotificationRepository(application)
    private val firebaseService = FirebaseService.getInstance(application)
    private val database = AppDatabase.getInstance(application)
    private val dao = database.playFilmeDao()
    
    private val _userId = MutableStateFlow<String?>(null)
    
    init {
        viewModelScope.launch {
            firebaseService.currentUser.collect { user ->
                _userId.value = user?.uid ?: FirebaseAuth.getInstance().currentUser?.uid
            }
        }
        
        // Sincroniza notificações globais com o banco local
        viewModelScope.launch {
            repository.observeGlobalNotifications().collect { globalList ->
                syncGlobalToLocal(globalList)
            }
        }
    }

    private suspend fun syncGlobalToLocal(globalList: List<AppNotification>) = withContext(Dispatchers.IO) {
        val uid = _userId.value
        val states = if (uid != null) repository.observeUserNotificationStates(uid).first() else emptyMap()
        
        globalList.forEach { notif ->
            val isRead = states[notif.id] ?: false
            val localEntity = notif.toLocalEntity(isRead = isRead)
            dao.insertNotification(localEntity)
        }
    }

    // Observa as notificações globais
    private val globalNotifications = repository.observeGlobalNotifications()

    // Observa os estados de leitura do usuário
    private val userStates = _userId.flatMapLatest { uid ->
        if (uid != null) repository.observeUserNotificationStates(uid)
        else flowOf(emptyMap())
    }

    // Combina os dois para gerar a lista final da UI
    val notifications: StateFlow<List<NotificationUiItem>> = combine(globalNotifications, userStates) { global, states ->
        global.map { notif ->
            NotificationUiItem(
                notification = notif,
                isRead = states[notif.id] ?: false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Contador de não lidas
    val unreadCount: StateFlow<Int> = notifications.map { list ->
        list.count { !it.isRead }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markAsRead(notificationId: String) {
        val uid = _userId.value ?: return
        viewModelScope.launch {
            repository.markAsRead(uid, notificationId)
        }
    }

    fun markAllAsRead() {
        val uid = _userId.value ?: return
        val unreadIds = notifications.value.filter { !it.isRead }.map { it.notification.id }
        if (unreadIds.isEmpty()) return
        
        viewModelScope.launch {
            repository.markAllAsRead(uid, unreadIds)
        }
    }
    
    /**
     * Função para o Admin criar notificações ao importar.
     * Pode ser chamada diretamente do AdminViewModel.
     */
    fun createNotification(notification: AppNotification) {
        viewModelScope.launch {
            repository.createGlobalNotification(notification)
        }
    }
}
