package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.remote.FirebaseService
import com.example.data.remote.UserEntity
import com.example.data.remote.UserProfile
import com.example.util.UsernameUtils
import com.example.util.UsernameValidationResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: UserEntity) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class UsernameCheckState {
    object Idle : UsernameCheckState()
    object Checking : UsernameCheckState()
    data class Available(val formattedUsername: String) : UsernameCheckState()
    data class Unavailable(val reason: String) : UsernameCheckState()
}

sealed class ProfileOpState {
    object Idle : ProfileOpState()
    object Loading : ProfileOpState()
    data class Success(val message: String, val profile: UserProfile) : ProfileOpState()
    data class Error(val message: String) : ProfileOpState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val firebaseService = FirebaseService.getInstance(application)
    private val database = AppDatabase.getInstance(application)
    
    val currentUser = firebaseService.currentUser
    val activeProfile = firebaseService.activeProfile
    val userProfiles = firebaseService.userProfiles
    val profilesLoaded = firebaseService.profilesLoaded

    private val _profileOpState = MutableStateFlow<ProfileOpState>(ProfileOpState.Idle)
    val profileOpState: StateFlow<ProfileOpState> = _profileOpState.asStateFlow()

    fun clearProfileOpState() {
        _profileOpState.value = ProfileOpState.Idle
    }

    val catalogPosters: StateFlow<List<String>> = database.playFilmeDao()
        .getAllMedia()
        .map { list ->
            list.mapNotNull { it.posterPath ?: it.backdropPath }
                .filter { it.isNotBlank() }
                .map { path ->
                    if (path.startsWith("http")) path else "https://image.tmdb.org/t/p/w500$path"
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentProfileToEdit = MutableStateFlow<UserProfile?>(null)
    val currentProfileToEdit: StateFlow<UserProfile?> = _currentProfileToEdit.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _usernameCheckState = MutableStateFlow<UsernameCheckState>(UsernameCheckState.Idle)
    val usernameCheckState: StateFlow<UsernameCheckState> = _usernameCheckState.asStateFlow()

    private var usernameCheckJob: Job? = null

    fun checkUsernameAvailability(rawInput: String) {
        usernameCheckJob?.cancel()
        val trimmed = rawInput.trim()

        if (trimmed.isBlank()) {
            _usernameCheckState.value = UsernameCheckState.Idle
            return
        }

        val validation = UsernameUtils.validate(trimmed)
        if (validation is UsernameValidationResult.Invalid) {
            _usernameCheckState.value = UsernameCheckState.Unavailable(validation.reason)
            return
        }

        val formatted = UsernameUtils.formatDisplay(trimmed)
        _usernameCheckState.value = UsernameCheckState.Checking

        usernameCheckJob = viewModelScope.launch {
            delay(350) // Debounce profissional para evitar excesso de requisições
            firebaseService.checkUsernameAvailability(trimmed, currentUser.value?.uid)
                .onSuccess { available ->
                    if (available) {
                        _usernameCheckState.value = UsernameCheckState.Available(formatted)
                    } else {
                        _usernameCheckState.value = UsernameCheckState.Unavailable("Este nome de usuário já está em uso.")
                    }
                }
                .onFailure { error ->
                    _usernameCheckState.value = UsernameCheckState.Unavailable(
                        error.message ?: "Não foi possível verificar. Tente novamente."
                    )
                }
        }
    }

    fun clearUsernameCheckState() {
        usernameCheckJob?.cancel()
        _usernameCheckState.value = UsernameCheckState.Idle
    }

    fun setProfileToEdit(profile: UserProfile?) {
        _currentProfileToEdit.value = profile
    }

    fun selectProfile(profile: UserProfile?) {
        firebaseService.selectProfile(profile)
    }

    fun createProfile(name: String, presetUrl: String? = null, image: ByteArray? = null) {
        viewModelScope.launch {
            val trimmedName = name.trim()
            val currentProfiles = userProfiles.value

            val currentFbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentFbUser == null) {
                _profileOpState.value = ProfileOpState.Error("Faça login para criar um perfil.")
                return@launch
            }

            if (trimmedName.isBlank()) {
                _profileOpState.value = ProfileOpState.Error("Digite um nome para o perfil.")
                return@launch
            }

            if (trimmedName.length < 2) {
                _profileOpState.value = ProfileOpState.Error("Digite um nome com pelo menos 2 caracteres.")
                return@launch
            }

            if (currentProfiles.size >= 5) {
                _profileOpState.value = ProfileOpState.Error("Você já possui o número máximo de perfis (5).")
                return@launch
            }

            if (currentProfiles.any { it.name.equals(trimmedName, ignoreCase = true) }) {
                _profileOpState.value = ProfileOpState.Error("Já existe um perfil com esse nome.")
                return@launch
            }

            _profileOpState.value = ProfileOpState.Loading

            android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] Iniciando criação de perfil: $trimmedName")
            var avatarUrl: String? = presetUrl
            var photoUrl: String? = null
            var avatarType = if (presetUrl != null) "PRESET" else "DEFAULT"
            var avatarId = ""

            firebaseService.createProfile(trimmedName, avatarUrl, avatarType, photoUrl, avatarId)
                .onSuccess { profile ->
                    android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] Perfil criado no Firestore: ${profile.id}")
                    if (image != null) {
                        android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] Enviando foto para Firebase Storage...")
                        firebaseService.uploadProfileAvatar(profile.id, image)
                            .onSuccess { url ->
                                android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] Upload concluído. URL: $url")
                                avatarUrl = url
                                photoUrl = url
                                avatarType = "CUSTOM"
                                val finalProfile = profile.copy(
                                    avatarUrl = avatarUrl,
                                    photoUrl = photoUrl,
                                    avatarType = avatarType,
                                    updatedAt = System.currentTimeMillis()
                                )
                                android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] Salvando URL no Firestore...")
                                firebaseService.updateProfile(finalProfile)
                                    .onSuccess {
                                        android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] ✓ Perfil atualizado com foto.")
                                        firebaseService.selectProfile(finalProfile)
                                        _profileOpState.value = ProfileOpState.Success("Perfil criado com sucesso!", finalProfile)
                                    }
                                    .onFailure { error ->
                                        android.util.Log.e("PROFILE_PHOTO", "[PROFILE PHOTO ERROR] stage: FIRESTORE_UPDATE, code: ${error.hashCode()}, message: ${error.message}")
                                        firebaseService.selectProfile(profile)
                                        _profileOpState.value = ProfileOpState.Error("Foto enviada, mas não foi possível atualizar o perfil. Tente novamente.")
                                    }
                            }
                            .onFailure { error ->
                                android.util.Log.e("PROFILE_PHOTO", "[PROFILE PHOTO ERROR] stage: STORAGE_UPLOAD, code: ${error.hashCode()}, message: ${error.message}")
                                _profileOpState.value = ProfileOpState.Error("Não foi possível enviar a foto. Verifique sua conexão e tente novamente.")
                            }
                    } else {
                        android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] ✓ Perfil criado (sem foto customizada).")
                        firebaseService.selectProfile(profile)
                        _profileOpState.value = ProfileOpState.Success("Perfil criado com sucesso!", profile)
                    }
                }
                .onFailure { error ->
                    android.util.Log.e("PROFILE_PHOTO", "[PROFILE PHOTO ERROR] stage: CREATE_PROFILE_FIRESTORE, message: ${error.message}")
                    _profileOpState.value = ProfileOpState.Error(error.message ?: "Não foi possível criar o perfil. Tente novamente.")
                }
        }
    }

    fun updateProfile(
        profile: UserProfile,
        newName: String,
        presetUrl: String? = null,
        newImage: ByteArray? = null
    ) {
        viewModelScope.launch {
            val trimmedName = newName.trim()
            val currentProfiles = userProfiles.value

            if (trimmedName.isBlank()) {
                _profileOpState.value = ProfileOpState.Error("Digite um nome para o perfil.")
                return@launch
            }

            if (trimmedName.length < 2) {
                _profileOpState.value = ProfileOpState.Error("Digite um nome com pelo menos 2 caracteres.")
                return@launch
            }

            if (currentProfiles.any { it.id != profile.id && it.name.equals(trimmedName, ignoreCase = true) }) {
                _profileOpState.value = ProfileOpState.Error("Já existe um perfil com esse nome.")
                return@launch
            }

            _profileOpState.value = ProfileOpState.Loading
            android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] Atualizando perfil: ${profile.id}")

            var finalAvatarUrl = presetUrl ?: profile.avatarUrl
            var finalPhotoUrl = if (presetUrl != null) null else profile.photoUrl
            var finalAvatarType = if (presetUrl != null) "PRESET" else profile.avatarType

            if (newImage != null) {
                android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] Enviando foto para Firebase Storage...")
                val uploadRes = firebaseService.uploadProfileAvatar(profile.id, newImage)
                uploadRes.onSuccess { url ->
                    android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] Upload concluído. URL: $url")
                    finalAvatarUrl = url
                    finalPhotoUrl = url
                    finalAvatarType = "CUSTOM"
                }.onFailure { error ->
                    android.util.Log.e("PROFILE_PHOTO", "[PROFILE PHOTO ERROR] stage: STORAGE_UPLOAD, message: ${error.message}")
                    _profileOpState.value = ProfileOpState.Error("Não foi possível enviar a foto. Verifique sua conexão e tente novamente.")
                    return@launch
                }
            }

            val updated = profile.copy(
                name = trimmedName,
                avatarUrl = finalAvatarUrl,
                photoUrl = finalPhotoUrl,
                avatarType = finalAvatarType,
                updatedAt = System.currentTimeMillis()
            )

            android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] Salvando URL no Firestore...")
            firebaseService.updateProfile(updated)
                .onSuccess {
                    android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] ✓ Perfil atualizado.")
                    firebaseService.selectProfile(updated)
                    _profileOpState.value = ProfileOpState.Success("Perfil atualizado com sucesso!", updated)
                }
                .onFailure { error ->
                    android.util.Log.e("PROFILE_PHOTO", "[PROFILE PHOTO ERROR] stage: FIRESTORE_UPDATE, message: ${error.message}")
                    _profileOpState.value = ProfileOpState.Error("Não foi possível salvar os dados do perfil. Tente novamente.")
                }
        }
    }

    fun updateActiveProfileAvatar(presetUrl: String?, imageBytes: ByteArray?, avatarId: String = "") {
        val active = activeProfile.value ?: return
        viewModelScope.launch {
            _profileOpState.value = ProfileOpState.Loading
            android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] Atualizando avatar do perfil ativo: ${active.id}")
            
            var finalAvatarUrl = presetUrl ?: active.avatarUrl
            var finalPhotoUrl = if (presetUrl != null) null else active.photoUrl
            var finalAvatarType = if (presetUrl != null) "PRESET" else active.avatarType

            if (imageBytes != null) {
                android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] Enviando foto para Firebase Storage...")
                val uploadRes = firebaseService.uploadProfileAvatar(active.id, imageBytes)
                uploadRes.onSuccess { url ->
                    android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] Upload concluído. URL: $url")
                    finalAvatarUrl = url
                    finalPhotoUrl = url
                    finalAvatarType = "CUSTOM"
                }.onFailure { error ->
                    android.util.Log.e("PROFILE_PHOTO", "[PROFILE PHOTO ERROR] stage: STORAGE_UPLOAD, message: ${error.message}")
                    _profileOpState.value = ProfileOpState.Error("Não foi possível enviar a foto. Verifique sua conexão e tente novamente.")
                    return@launch
                }
            }

            val updated = active.copy(
                avatarUrl = finalAvatarUrl,
                photoUrl = finalPhotoUrl,
                avatarType = finalAvatarType,
                avatarId = if (presetUrl != null) avatarId else active.avatarId,
                updatedAt = System.currentTimeMillis()
            )

            android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] Salvando URL no Firestore...")
            firebaseService.updateProfile(updated)
                .onSuccess {
                    android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] ✓ Perfil atualizado.")
                    firebaseService.selectProfile(updated)
                    _profileOpState.value = ProfileOpState.Success("Foto salva com sucesso!", updated)
                }
                .onFailure { error ->
                    android.util.Log.e("PROFILE_PHOTO", "[PROFILE PHOTO ERROR] stage: FIRESTORE_UPDATE, message: ${error.message}")
                    _profileOpState.value = ProfileOpState.Error("Foto enviada, mas não foi possível atualizar o perfil. Tente novamente.")
                }
        }
    }

    fun removeActiveProfilePhoto() {
        val active = activeProfile.value ?: return
        viewModelScope.launch {
            _profileOpState.value = ProfileOpState.Loading
            android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] Removendo foto do perfil ativo: ${active.id}")
            firebaseService.removeProfilePhoto(active)
                .onSuccess { updated ->
                    _profileOpState.value = ProfileOpState.Success("Foto de perfil removida com sucesso!", updated)
                }
                .onFailure { error ->
                    _profileOpState.value = ProfileOpState.Error(error.message ?: "Não foi possível remover a foto.")
                }
        }
    }

    fun removeProfilePhoto(profile: UserProfile) {
        viewModelScope.launch {
            _profileOpState.value = ProfileOpState.Loading
            android.util.Log.d("PROFILE_PHOTO", "[PROFILE PHOTO] Removendo foto do perfil: ${profile.id}")
            firebaseService.removeProfilePhoto(profile)
                .onSuccess { updated ->
                    _profileOpState.value = ProfileOpState.Success("Foto de perfil removida com sucesso!", updated)
                }
                .onFailure { error ->
                    _profileOpState.value = ProfileOpState.Error(error.message ?: "Não foi possível remover a foto.")
                }
        }
    }

    fun downloadProfilePhoto(context: android.content.Context, profile: UserProfile, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = com.example.util.ProfilePhotoUtils.savePhotoToDeviceGallery(
                context = context,
                imageUrl = profile.photoUrl ?: profile.avatarUrl,
                imageBytes = null,
                profileName = profile.name
            )
            result.onSuccess { msg ->
                onResult(msg)
            }.onFailure { err ->
                onResult("Erro: ${err.message ?: "Não foi possível baixar a foto."}")
            }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            _profileOpState.value = ProfileOpState.Loading
            firebaseService.deleteProfile(profileId)
                .onSuccess {
                    _profileOpState.value = ProfileOpState.Success("Perfil excluído.", UserProfile())
                }
                .onFailure { error ->
                    _profileOpState.value = ProfileOpState.Error(error.message ?: "Não foi possível excluir o perfil.")
                }
        }
    }

    fun claimOrUpdateUsername(
        rawUsername: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            firebaseService.claimOrUpdateUsername(rawUsername)
                .onSuccess {
                    _authState.value = AuthState.Idle
                    onSuccess()
                }
                .onFailure { error ->
                    val msg = error.message ?: "Não foi possível definir o nome de usuário."
                    _authState.value = AuthState.Error(msg)
                    onError(msg)
                }
        }
    }

    fun resolveIdentifier(
        identifier: String,
        onSuccess: (resolvedEmail: String, displayIdentifier: String, isUsername: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmed = identifier.trim()
        if (trimmed.isBlank()) {
            val err = "Digite seu e-mail ou @nome de usuário."
            _authState.value = AuthState.Error(err)
            onError(err)
            return
        }

        // Se for e-mail válido
        if (UsernameUtils.isEmailAddress(trimmed)) {
            _authState.value = AuthState.Idle
            onSuccess(trimmed.lowercase(), trimmed.lowercase(), false)
            return
        }

        // Se tiver arroba mas não for e-mail válido nem começar com @
        if (trimmed.contains("@") && !trimmed.startsWith("@")) {
            val err = "Formato de e-mail inválido."
            _authState.value = AuthState.Error(err)
            onError(err)
            return
        }

        // É um username
        val displayUsername = UsernameUtils.formatDisplay(trimmed)
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            firebaseService.resolveEmailFromIdentifier(trimmed)
                .onSuccess { resolvedEmail ->
                    _authState.value = AuthState.Idle
                    onSuccess(resolvedEmail, displayUsername, true)
                }
                .onFailure { error ->
                    val msg = error.message ?: "Usuário não encontrado."
                    _authState.value = AuthState.Error(msg)
                    onError(msg)
                }
        }
    }

    fun signIn(emailOrUsername: String, pass: String) {
        if (emailOrUsername.isBlank() || pass.isBlank()) {
            _authState.value = AuthState.Error("Preencha todos os campos.")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            firebaseService.signIn(emailOrUsername, pass)
                .onSuccess { user ->
                    _authState.value = AuthState.Success(user)
                }
                .onFailure { error ->
                    _authState.value = AuthState.Error(mapAuthError(error))
                }
        }
    }

    fun signUp(name: String, username: String, email: String, pass: String, confirmPass: String) {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()

        if (trimmedName.isBlank()) {
            _authState.value = AuthState.Error("Informe seu nome completo.")
            return
        }

        val usernameVal = UsernameUtils.validate(username)
        if (usernameVal is UsernameValidationResult.Invalid) {
            _authState.value = AuthState.Error(usernameVal.reason)
            return
        }

        if (!UsernameUtils.isEmailAddress(trimmedEmail)) {
            _authState.value = AuthState.Error("Informe um endereço de e-mail válido.")
            return
        }

        if (pass.length < 6) {
            _authState.value = AuthState.Error("A senha deve ter pelo menos 6 caracteres.")
            return
        }

        if (pass != confirmPass) {
            _authState.value = AuthState.Error("As senhas não coincidem.")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            firebaseService.signUpWithUsername(trimmedName, username, trimmedEmail, pass)
                .onSuccess { user ->
                    _authState.value = AuthState.Success(user)
                }
                .onFailure { error ->
                    _authState.value = AuthState.Error(mapAuthError(error))
                }
        }
    }

    fun resetPassword(emailOrUsername: String, onSent: () -> Unit = {}) {
        val trimmed = emailOrUsername.trim()
        if (trimmed.isBlank()) {
            _authState.value = AuthState.Error("Informe seu e-mail ou @nome de usuário.")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            firebaseService.resetPassword(trimmed)
                .onSuccess {
                    _authState.value = AuthState.Idle
                    onSent()
                }
                .onFailure { error ->
                    _authState.value = AuthState.Error(mapAuthError(error))
                }
        }
    }

    fun signOut() {
        firebaseService.signOut()
    }

    fun clearError() {
        _authState.value = AuthState.Idle
    }

    private fun mapAuthError(error: Throwable): String {
        val message = error.message ?: ""
        return when {
            message.contains("user-not-found") || 
            message.contains("wrong-password") || 
            message.contains("INVALID_LOGIN_CREDENTIALS") ||
            message.contains("invalid-credential") ||
            message.contains("incorrect, malformed or has expired") -> "E-mail/usuário ou senha incorretos."
            message.contains("email-already-in-use") -> "Este e-mail já possui uma conta cadastrada."
            message.contains("invalid-email") || message.contains("badly formatted") -> "E-mail com formato inválido."
            message.contains("network-request-failed") -> "Sem conexão com a internet."
            message.contains("weak-password") -> "A senha é muito fraca (mínimo 6 caracteres)."
            message.contains("too-many-requests") -> "Muitas tentativas malsucedidas. Tente novamente mais tarde."
            message.contains("user-disabled") -> "Esta conta foi desativada."
            message.contains("operation-not-allowed") -> "O login com e-mail e senha não está habilitado no Firebase Console."
            message.contains("reservado") || message.contains("já está em uso") || message.contains("caracteres") -> message
            else -> error.localizedMessage ?: "Não foi possível completar a operação."
        }
    }
}
