package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log

object BadgeUtils {
    private const val TAG = "BadgeUtils"

    fun updateBadge(context: Context, count: Int) {
        try {
            val safeCount = maxOf(0, count)
            // Broadcast for Samsung, Sony, HTC, Xiaomi, etc. launchers
            val intent = Intent("android.intent.action.BADGE_COUNT_UPDATE").apply {
                putExtra("badge_count", safeCount)
                putExtra("badge_count_package_name", context.packageName)
                putExtra("badge_count_class_name", getLauncherClassName(context))
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "[BADGE] Updated app launcher badge count to: $safeCount")
        } catch (e: Exception) {
            Log.e(TAG, "[BADGE] Error updating launcher badge: ${e.message}")
        }
    }

    private fun getLauncherClassName(context: Context): String {
        try {
            val packageName = context.packageName
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            return intent?.component?.className ?: ""
        } catch (e: Exception) {
            return ""
        }
    }
}
