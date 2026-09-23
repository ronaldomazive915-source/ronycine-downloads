package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object ExternalDownloadManager {

    const val PKG_1DM = "idm.internet.download.manager"
    const val PKG_1DM_PLUS = "idm.internet.download.manager.plus"
    const val PKG_1DM_LITE = "idm.internet.download.manager.lite"
    const val PKG_ADM = "com.dv.adm"
    const val PKG_ADM_PRO = "com.dv.adm.pay"

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun is1DmInstalled(context: Context): Boolean {
        return isAppInstalled(context, PKG_1DM) || 
               isAppInstalled(context, PKG_1DM_PLUS) || 
               isAppInstalled(context, PKG_1DM_LITE)
    }

    fun isAdmInstalled(context: Context): Boolean {
        return isAppInstalled(context, PKG_ADM) || 
               isAppInstalled(context, PKG_ADM_PRO)
    }

    fun downloadWith1DM(context: Context, url: String, fileName: String, mimeType: String? = null) {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = Uri.parse(url)
        val type = mimeType ?: if (url.contains(".m3u8")) "application/x-mpegURL" else "video/*"
        
        intent.setDataAndType(uri, type)
        intent.putExtra("extra_filename", fileName)
        intent.putExtra(Intent.EXTRA_TITLE, fileName)
        intent.putExtra("android.intent.extra.TEXT", url)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val pkg = when {
            isAppInstalled(context, PKG_1DM_PLUS) -> PKG_1DM_PLUS
            isAppInstalled(context, PKG_1DM_LITE) -> PKG_1DM_LITE
            else -> PKG_1DM
        }
        intent.setPackage(pkg)
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // If direct package launch fails, retry with general VIEW or system chooser
            try {
                intent.setPackage(null)
                context.startActivity(intent)
            } catch (e2: Exception) {
                downloadWithSystemChooser(context, url, fileName)
            }
        }
    }

    fun downloadWithADM(context: Context, url: String, fileName: String, mimeType: String? = null) {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = Uri.parse(url)
        val type = mimeType ?: if (url.contains(".m3u8")) "application/x-mpegURL" else "video/*"
        
        intent.setDataAndType(uri, type)
        intent.putExtra("android.intent.extra.TEXT", url)
        intent.putExtra("title", fileName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val pkg = if (isAppInstalled(context, PKG_ADM_PRO)) PKG_ADM_PRO else PKG_ADM
        intent.setPackage(pkg)
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                intent.setPackage(null)
                context.startActivity(intent)
            } catch (e2: Exception) {
                downloadWithSystemChooser(context, url, fileName)
            }
        }
    }

    fun downloadWithSystemChooser(context: Context, url: String, fileName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val uri = Uri.parse(url)
                if (url.contains(".m3u8")) {
                    setDataAndType(uri, "application/x-mpegURL")
                } else {
                    setDataAndType(uri, "video/*")
                }
                putExtra(Intent.EXTRA_TITLE, fileName)
                putExtra("android.intent.extra.TEXT", url)
            }
            val chooser = Intent.createChooser(intent, "Baixar com: $fileName")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Nenhum aplicativo compatível encontrado", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(context: Context, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Download Link", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Link copiado com sucesso!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao copiar link", Toast.LENGTH_SHORT).show()
        }
    }

    fun openPlayStore(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(webIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Não foi possível abrir a Play Store", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
