package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.remote.AppVersionEntity
import com.example.data.remote.FirebaseService
import com.example.data.remote.UpdateEventEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.Locale

data class ApkMetadata(
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Int = 0,
    val sha256: String = "",
    val fileSizeBytes: Long = 0L,
    val fileSizeFormatted: String = "",
    val isValid: Boolean = false,
    val errorMessage: String? = null
)

data class ApkVerificationResult(
    val isValid: Boolean,
    val message: String,
    val fileSizeBytes: Long = 0L,
    val fileSizeFormatted: String = "",
    val httpStatusCode: Int = 0,
    val contentType: String? = null,
    val resolvedUrl: String = ""
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateDownloadState()
    object Verifying : UpdateDownloadState()
    data class ReadyToInstall(val file: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

object UpdateManager {

    private const val TAG = "RONYCINE_UPDATE"
    const val TEMP_PART_EXT = ".ronycine.part"

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private val managerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private var activeDownloadJob: Job? = null

    fun resetState(force: Boolean = false) {
        if (force || (_downloadState.value !is UpdateDownloadState.Downloading && _downloadState.value !is UpdateDownloadState.Verifying)) {
            activeDownloadJob?.cancel()
            _downloadState.value = UpdateDownloadState.Idle
        }
    }

    /**
     * Emite logs de diagnóstico com detalhes da versão atual e disponível (Requisito 8).
     * Não expõe tokens sensíveis de URLs completas para segurança.
     */
    fun logUpdateCheckDiagnostics(
        installedVersionName: String,
        installedVersionCode: Int,
        targetVersionName: String,
        targetVersionCode: Int,
        apkUrl: String,
        status: String,
        isMandatory: Boolean,
        minimumVersionCode: Int,
        sha256: String = "",
        fileSize: String = ""
    ) {
        val hostSafe = try {
            if (apkUrl.isNotBlank()) URL(apkUrl).host else "NENHUMA_URL"
        } catch (e: Exception) {
            "URL_MALFORMADA"
        }
        val shaSafe = if (sha256.isNotBlank()) sha256.take(8) + "..." else "NÃO CONFIGURADO"
        Log.d(TAG, """
            |========== [RONYCINE UPDATE DIAGNÓSTICO] ==========
            | Versão Atual Instalada : v$installedVersionName (Build $installedVersionCode)
            | Versão Disponível      : v$targetVersionName (Build $targetVersionCode)
            | Status da Versão       : $status
            | Obrigatória (Mandatory): $isMandatory
            | Mínimo Requerido (Min) : Build $minimumVersionCode
            | APK URL Configurada    : ${if (apkUrl.isNotBlank()) "SIM [Host: $hostSafe]" else "NÃO (Vazia)"}
            | SHA-256                : $shaSafe
            | Tamanho Detectado      : ${fileSize.ifBlank { "Não informado" }}
            |===================================================
        """.trimMargin())
    }

    /**
     * Validação real da URL do APK antes da publicação ou teste no painel administrativo.
     * Suporta redirecionamentos HTTPS (301, 302, 303, 307, 308) e verifica se o
     * conteúdo é executável Android e não página HTML (Requisitos 3, 5, 6, 11, 12, 20).
     */
    suspend fun verifyApkUrl(rawUrl: String): ApkVerificationResult = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return@withContext ApkVerificationResult(
                isValid = false,
                message = "A URL do APK não foi fornecida."
            )
        }
        if (trimmed.contains(" ")) {
            return@withContext ApkVerificationResult(
                isValid = false,
                message = "A URL do APK não pode conter espaços."
            )
        }
        if (!trimmed.startsWith("https://", ignoreCase = true)) {
            return@withContext ApkVerificationResult(
                isValid = false,
                message = "A URL do APK deve utilizar protocolo seguro HTTPS."
            )
        }

        var currentUrl = trimmed
        var redirects = 0
        val maxRedirects = 5

        try {
            while (redirects < maxRedirects) {
                val urlObj = URL(currentUrl)
                var connection = (urlObj.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 12000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "RONYCINE-UpdateVerifier/1.0")
                    setRequestProperty("Accept", "*/*")
                    requestMethod = "HEAD"
                }

                var code = try {
                    connection.responseCode
                } catch (e: Exception) {
                    -1
                }

                // Se HEAD for proibido (HTTP 405) ou falhar, testa via GET com Range de 1KB
                if (code == 405 || code == -1) {
                    connection = (urlObj.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 12000
                        readTimeout = 12000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "RONYCINE-UpdateVerifier/1.0")
                        setRequestProperty("Range", "bytes=0-1024")
                        requestMethod = "GET"
                    }
                    code = try { connection.responseCode } catch (e: Exception) { -1 }
                }

                // Tratamento de redirecionamentos legítimos
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank()) {
                        return@withContext ApkVerificationResult(
                            isValid = false,
                            message = "Redirecionamento sem endereço de destino válido (HTTP $code)."
                        )
                    }
                    currentUrl = if (location.startsWith("http")) location else URL(urlObj, location).toString()
                    redirects++
                    continue
                }

                if (code !in 200..299) {
                    val errorDetail = when (code) {
                        404 -> "Arquivo APK não encontrado no servidor (HTTP 404)."
                        403 -> "Acesso negado ao arquivo APK no servidor (HTTP 403)."
                        500, 502, 503 -> "Servidor do APK temporariamente indisponível (HTTP $code)."
                        else -> "Servidor retornou código HTTP $code."
                    }
                    return@withContext ApkVerificationResult(
                        isValid = false,
                        message = errorDetail,
                        httpStatusCode = code,
                        resolvedUrl = currentUrl
                    )
                }

                val contentType = connection.contentType?.lowercase(Locale.ROOT) ?: ""
                if (contentType.contains("text/html")) {
                    return@withContext ApkVerificationResult(
                        isValid = false,
                        message = "Servidor retornou uma página web (HTML) em vez de um arquivo APK.",
                        httpStatusCode = code,
                        contentType = contentType,
                        resolvedUrl = currentUrl
                    )
                }

                // Tamanho do arquivo obtido via Content-Length ou Content-Range
                var length = connection.contentLengthLong
                if (length <= 0) {
                    val rangeHeader = connection.getHeaderField("Content-Range")
                    if (!rangeHeader.isNullOrBlank() && rangeHeader.contains("/")) {
                        val totalStr = rangeHeader.substringAfter("/")
                        length = totalStr.toLongOrNull() ?: 0L
                    }
                }

                val formatted = if (length > 0) formatFileSize(length) else ""

                return@withContext ApkVerificationResult(
                    isValid = true,
                    message = "✓ APK acessível\n✓ URL válida\n✓ Arquivo encontrado",
                    fileSizeBytes = length,
                    fileSizeFormatted = formatted,
                    httpStatusCode = code,
                    contentType = contentType,
                    resolvedUrl = currentUrl
                )
            }

            return@withContext ApkVerificationResult(
                isValid = false,
                message = "Excesso de redirecionamentos ao tentar acessar o APK."
            )
        } catch (e: java.net.UnknownHostException) {
            ApkVerificationResult(
                isValid = false,
                message = "Não foi possível acessar o servidor do APK (Servidor não encontrado ou sem internet)."
            )
        } catch (e: java.net.SocketTimeoutException) {
            ApkVerificationResult(
                isValid = false,
                message = "Tempo limite esgotado ao conectar ao servidor do APK."
            )
        } catch (e: Exception) {
            ApkVerificationResult(
                isValid = false,
                message = "Falha ao verificar APK: ${e.localizedMessage ?: "Erro desconhecido"}"
            )
        }
    }

    /**
     * Inspeciona um arquivo APK localmente extraindo metadados reais:
     * PackageName, VersionName, VersionCode, SHA-256 e Tamanho.
     */
    fun inspectApkFile(context: Context, file: File): ApkMetadata {
        return try {
            if (!file.exists() || file.length() <= 0) {
                return ApkMetadata(
                    isValid = false,
                    errorMessage = "Arquivo APK vazio ou inexistente."
                )
            }

            val pm = context.packageManager
            val pInfo = pm.getPackageArchiveInfo(file.absolutePath, 0)
            if (pInfo == null) {
                return ApkMetadata(
                    isValid = false,
                    errorMessage = "O arquivo selecionado não é um pacote APK válido do Android ou está corrompido."
                )
            }

            val pkg = pInfo.packageName ?: ""
            val verName = pInfo.versionName ?: "1.0.0"
            val verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION") pInfo.versionCode
            }

            val hash = calculateSha256(file)
            val sizeBytes = file.length()
            val sizeFormatted = formatFileSize(sizeBytes)

            val currentPkg = context.packageName
            if (pkg != currentPkg) {
                return ApkMetadata(
                    packageName = pkg,
                    versionName = verName,
                    versionCode = verCode,
                    sha256 = hash,
                    fileSizeBytes = sizeBytes,
                    fileSizeFormatted = sizeFormatted,
                    isValid = false,
                    errorMessage = "Este APK possui um package ID diferente do aplicativo instalado ($pkg vs $currentPkg)."
                )
            }

            ApkMetadata(
                packageName = pkg,
                versionName = verName,
                versionCode = verCode,
                sha256 = hash,
                fileSizeBytes = sizeBytes,
                fileSizeFormatted = sizeFormatted,
                isValid = true,
                errorMessage = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inspecionar APK: ${e.message}", e)
            ApkMetadata(
                isValid = false,
                errorMessage = "Falha ao ler o arquivo APK: ${e.message}"
            )
        }
    }

    /**
     * Copia um Uri vindo do SAF para a pasta temporária de trabalho do app.
     */
    fun copyUriToTempFile(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val stagingDir = File(context.cacheDir, "apk_staging")
            if (!stagingDir.exists()) stagingDir.mkdirs()
            val destFile = File(stagingDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao copiar APK da URI: ${e.message}")
            null
        }
    }

    /**
     * Calcula o hash SHA-256 de um arquivo de forma eficiente.
     */
    fun calculateSha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(32768)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao calcular SHA-256: ${e.message}")
            ""
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val df = DecimalFormat("#.##")
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${df.format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
            bytes >= 1024 * 1024 -> "${df.format(bytes.toDouble() / (1024 * 1024))} MB"
            bytes >= 1024 -> "${df.format(bytes.toDouble() / 1024)} KB"
            else -> "$bytes B"
        }
    }

    /**
     * Inicia o download real do APK utilizando arquivo temporário .ronycine.part,
     * medição precisa de bytes e porcentagem (0% a 100%), validação de Content-Type
     * e integridade SHA-256 antes da instalação oficial (Requisitos 7, 10, 11, 21, 23, 24, 25).
     */
    fun startUpdateFlow(
        context: Context,
        scope: CoroutineScope,
        apkUrl: String,
        expectedSha256: String,
        targetVersionName: String,
        targetVersionCode: Int,
        firebaseService: FirebaseService? = null,
        trigger: String = "in_app"
    ) {
        val currentState = _downloadState.value
        if (currentState is UpdateDownloadState.Downloading || currentState is UpdateDownloadState.Verifying) {
            Log.d(TAG, "Download já está em andamento. Ignorando requisição duplicada.")
            return
        }

        val trimmedUrl = apkUrl.trim()
        if (trimmedUrl.isBlank()) {
            _downloadState.value = UpdateDownloadState.Error("Esta versão não possui APK configurado.")
            return
        }
        if (trimmedUrl.contains(" ")) {
            _downloadState.value = UpdateDownloadState.Error("A URL do APK contém espaços inválidos.")
            return
        }
        if (!trimmedUrl.startsWith("https://", ignoreCase = true)) {
            _downloadState.value = UpdateDownloadState.Error("A URL do APK deve utilizar protocolo seguro HTTPS.")
            return
        }

        activeDownloadJob?.cancel()
        activeDownloadJob = managerScope.launch(Dispatchers.IO) {
            val currentPInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) { null }
            val currentVersionName = currentPInfo?.versionName ?: "1.0.0"

            val deviceId = firebaseService?.deviceId ?: firebaseService?.getOrGeneratePersistentDeviceId() ?: "device_unknown"
            val eventId = "evt_${deviceId}_${targetVersionCode}_${System.currentTimeMillis()}"

            // Registra início do evento no Firestore
            firebaseService?.recordUpdateEventInCloud(
                UpdateEventEntity(
                    id = eventId,
                    deviceId = deviceId,
                    deviceName = Build.MODEL ?: "Android",
                    fromVersion = currentVersionName,
                    toVersion = targetVersionName,
                    status = "DOWNLOADING",
                    startedAt = System.currentTimeMillis(),
                    trigger = trigger
                )
            )

            val updateDir = File(context.getExternalFilesDir(null), "updates")
            if (!updateDir.exists()) updateDir.mkdirs()

            // Arquivo temporário de download parcial (.ronycine.part)
            val partFile = File(updateDir, "ronycine_v${targetVersionCode}${TEMP_PART_EXT}")
            val finalApkFile = File(updateDir, "ronycine_v${targetVersionCode}.apk")

            if (partFile.exists()) partFile.delete()
            if (finalApkFile.exists()) finalApkFile.delete()

            try {
                _downloadState.value = UpdateDownloadState.Downloading(0, 0L, 0L)

                // Segue redirecionamentos HTTPS transparentemente
                var currentUrl = trimmedUrl
                var redirects = 0
                val maxRedirects = 5
                var connection: HttpURLConnection? = null

                while (redirects < maxRedirects) {
                    val url = URL(currentUrl)
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "RONYCINE-AppUpdateEngine/1.0")
                        setRequestProperty("Accept", "*/*")
                        setRequestProperty("Accept-Encoding", "identity")
                    }

                    connection.connect()
                    val responseCode = connection.responseCode

                    if (responseCode in 300..399) {
                        val location = connection.getHeaderField("Location")
                        if (location.isNullOrBlank()) {
                            throw IllegalStateException("Redirecionamento sem destino informado pelo servidor.")
                        }
                        currentUrl = if (location.startsWith("http")) location else URL(url, location).toString()
                        redirects++
                        connection.disconnect()
                        continue
                    }

                    if (responseCode !in 200..299) {
                        val message = when (responseCode) {
                            404 -> "Arquivo APK não encontrado no servidor (HTTP 404)."
                            403 -> "Acesso negado ao servidor do APK (HTTP 403)."
                            else -> "Servidor retornou código HTTP $responseCode."
                        }
                        throw IllegalStateException(message)
                    }

                    break
                }

                val conn = connection ?: throw IllegalStateException("Não foi possível estabelecer conexão com o servidor do APK.")

                // Validação de Content-Type
                val contentType = conn.contentType?.lowercase(Locale.ROOT) ?: ""
                if (contentType.contains("text/html")) {
                    throw IllegalStateException("Servidor não retornou um APK válido (retornou página HTML).")
                }

                val contentLength = conn.contentLengthLong
                var downloadedBytes = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(32768)
                        var bytesRead: Int
                        var isFirstBlock = true
                        var lastProgress = 0

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            // Validação dos primeiros 4 bytes (Magic Bytes do formato APK/ZIP: PK\x03\x04)
                            if (isFirstBlock && bytesRead >= 4) {
                                isFirstBlock = false
                                val isZipSignature = buffer[0] == 0x50.toByte() &&
                                        buffer[1] == 0x4B.toByte() &&
                                        (buffer[2] == 0x03.toByte() || buffer[2] == 0x05.toByte() || buffer[2] == 0x07.toByte())

                                // Se começar com '<!DO' ou '<htm', é HTML
                                val isHtml = buffer[0] == '<'.code.toByte() &&
                                        (buffer[1] == '!'.code.toByte() || buffer[1] == 'h'.code.toByte() || buffer[1] == 'H'.code.toByte())

                                if (isHtml || !isZipSignature) {
                                    throw IllegalStateException("Servidor não retornou um arquivo APK válido.")
                                }
                            }

                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val progress = if (contentLength > 0) {
                                ((downloadedBytes * 100) / contentLength).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }

                            if (progress != lastProgress) {
                                lastProgress = progress
                                _downloadState.value = UpdateDownloadState.Downloading(
                                    progressPercent = progress,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = contentLength
                                )
                            }
                        }
                    }
                }

                _downloadState.value = UpdateDownloadState.Verifying
                firebaseService?.updateSpecificEventStatus(eventId, "DOWNLOADED")

                // Verificação de Integridade SHA-256 (se configurado)
                if (expectedSha256.isNotBlank()) {
                    val calculatedSha = calculateSha256(partFile)
                    if (!calculatedSha.equals(expectedSha256.trim(), ignoreCase = true)) {
                        partFile.delete()
                        val errorMsg = "SHA-256 não corresponde ao arquivo."
                        firebaseService?.updateSpecificEventStatus(eventId, "FAILED", errorMsg)
                        _downloadState.value = UpdateDownloadState.Error(errorMsg)
                        return@launch
                    }
                    Log.d(TAG, "SHA-256 verificado com sucesso: $calculatedSha")
                } else {
                    Log.d(TAG, "SHA-256 não configurado na versão; prosseguindo com verificação padrão do pacote.")
                }

                // Renomeia o arquivo temporário .part para .apk definitivo
                if (finalApkFile.exists()) finalApkFile.delete()
                val renamed = partFile.renameTo(finalApkFile)
                val targetFileToInstall = if (renamed) finalApkFile else partFile

                // Pronto para instalar
                _downloadState.value = UpdateDownloadState.ReadyToInstall(targetFileToInstall)
                firebaseService?.updateSpecificEventStatus(eventId, "INSTALLING")

                // Dispara o instalador oficial do Android
                withContext(Dispatchers.Main) {
                    val success = installApk(context, targetFileToInstall)
                    if (!success) {
                        firebaseService?.updateSpecificEventStatus(eventId, "FAILED", "Android bloqueou a instalação.")
                        _downloadState.value = UpdateDownloadState.Error("Android bloqueou a instalação ou requer permissão de fontes desconhecidas.")
                    }
                }

            } catch (e: CancellationException) {
                if (partFile.exists()) partFile.delete()
                _downloadState.value = UpdateDownloadState.Idle
            } catch (e: Exception) {
                if (partFile.exists()) partFile.delete()
                Log.e(TAG, "Falha no download da atualização: ${e.message}", e)
                val msg = when {
                    e is java.net.UnknownHostException || e is java.net.SocketTimeoutException ->
                        "Não foi possível acessar o servidor do APK."
                    e.message?.contains("HTML", ignoreCase = true) == true ->
                        "O servidor não retornou um arquivo APK."
                    e.message?.contains("SHA-256", ignoreCase = true) == true ->
                        "SHA-256 não corresponde ao arquivo."
                    e.message?.contains("interrompido", ignoreCase = true) == true ->
                        "Download interrompido."
                    else -> e.localizedMessage ?: "Download interrompido."
                }
                firebaseService?.updateSpecificEventStatus(eventId, "FAILED", msg)
                _downloadState.value = UpdateDownloadState.Error(msg)
            }
        }
    }

    /**
     * Executa a instalação oficial do APK usando FileProvider e Intent.ACTION_VIEW
     * respeitando o controle oficial do sistema operacional Android.
     */
    fun installApk(context: Context, file: File): Boolean {
        return try {
            if (!file.exists() || file.length() <= 0) {
                Log.e(TAG, "Arquivo APK inexistente ou vazio para instalação: ${file.absolutePath}")
                return false
            }

            // No Android 8.0+ (Oreo), orienta e verifica permissão de fontes desconhecidas
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    _downloadState.value = UpdateDownloadState.Error(
                        "Para concluir a atualização, permita a instalação desta nova versão do RONYCINE nas configurações do Android. Após permitir, volte e clique em 'Instalar Agora' novamente."
                    )
                    return false
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar instalação do APK: ${e.message}", e)
            false
        }
    }
}
