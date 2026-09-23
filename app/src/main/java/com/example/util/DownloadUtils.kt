package com.example.util

import java.text.DecimalFormat

object DownloadUtils {
    
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
    
    fun formatDownloadSpeed(bytesPerSecond: Long): String {
        return "${formatFileSize(bytesPerSecond)}/s"
    }

    fun getFileNameFromUrl(url: String, defaultName: String): String {
        var name = url.substringAfterLast("/")
        if (name.contains("?")) name = name.substringBefore("?")
        if (!name.contains(".")) name = "$defaultName.mp4"
        return name
    }
}
