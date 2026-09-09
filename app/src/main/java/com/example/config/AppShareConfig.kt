package com.example.config

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap

/**
 * Global Configuration for App Sharing, Installation Deep Links, and Versioning.
 *
 * Configurable URLs:
 * - [defaultInstallUrl]: The official web landing page link (e.g. https://ronycine.app/download).
 * - [defaultApkUrl]: Direct URL pointing to the official compiled APK file.
 * - [defaultPlayStoreUrl]: Google Play Store package link (if published).
 */
object AppShareConfig {
    const val APP_NAME = "RONYCINE"
    
    // Configuração Centralizada da URL de Atualização (Requisito 7)
    const val UPDATE_APK_URL = "https://github.com/ronaldomazive915-source/ronycine-downloads/releases/download/v1.1.0/RONYCINE.apk"
    
    // Default URLs - Official APK release URL
    var defaultInstallUrl: String = UPDATE_APK_URL
    var defaultApkUrl: String = UPDATE_APK_URL
    var defaultPlayStoreUrl: String = "https://play.google.com/store/apps/details?id=com.aistudio.playfilmeplus.app"

    const val CURRENT_VERSION_NAME = "1.1.1"
    const val CURRENT_VERSION_CODE = 12
    const val MIN_SUPPORTED_VERSION_CODE = 11

    /**
     * Formats the official share text message as specified in the exact user requirements.
     */
    fun getShareTextMessage(installUrl: String = defaultInstallUrl): String {
        return """
            🎬 RONYCINE

            Assista seus filmes e séries favoritos em um só lugar.

            📲 Baixe o aplicativo RONYCINE:
            $installUrl

            🚀 Instale e aproveite!
        """.trimIndent()
    }

    /**
     * Launches the official Android Sharesheet (Intent.ACTION_SEND).
     * Does NOT create a custom app chooser list; relies on system Android Sharesheet.
     */
    fun shareAppViaAndroidSharesheet(context: Context, customInstallUrl: String = defaultInstallUrl) {
        val message = getShareTextMessage(customInstallUrl)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Instalar $APP_NAME")
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(shareIntent, "Compartilhar $APP_NAME via")
        context.startActivity(chooser)
    }

    /**
     * Copies the installation link to the device Clipboard and displays a Toast "Link copiado!".
     */
    fun copyLinkToClipboard(context: Context, customInstallUrl: String = defaultInstallUrl) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Link $APP_NAME", customInstallUrl)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Link copiado!", Toast.LENGTH_SHORT).show()
    }

    /**
     * Generates a QR Code ImageBitmap from the installation URL for display in Compose.
     */
    fun generateQrCodeBitmap(content: String, sizePx: Int = 512): ImageBitmap? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 1)
            }
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
