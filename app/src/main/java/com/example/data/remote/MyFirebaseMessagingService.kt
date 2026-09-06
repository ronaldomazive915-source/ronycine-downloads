package com.example.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "MyFCMService"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM Token generated: $token")
        val prefs = getSharedPreferences("playfilme_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()

        serviceScope.launch {
            try {
                val firebaseService = FirebaseService.getInstance(applicationContext)
                firebaseService.updateFcmToken(token)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating token on new token callback: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        val prefs = getSharedPreferences("playfilme_prefs", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notificationsEnabled", true)
        if (!notificationsEnabled) {
            Log.d(TAG, "Notifications disabled by user in Settings, ignoring FCM push.")
            return
        }

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: $data")
            val type = data["type"] ?: ""
            if (type.contains("UPDATE", ignoreCase = true) || type.contains("ATUALIZACAO", ignoreCase = true)) {
                val service = FirebaseService.getInstance(applicationContext)
                val control = service.updateControl.value
                if (!control.enabled || control.activeVersionCode == null) {
                    Log.d(TAG, "Update notification ignored because updateControl is DISABLED by admin.")
                    return
                }
            }
            if ((type.contains("MOVIE", ignoreCase = true) || type.contains("FILME", ignoreCase = true)) && !prefs.getBoolean("newMoviesEnabled", true)) {
                Log.d(TAG, "Movie notifications disabled by user in Settings.")
                return
            }
            if ((type.contains("SERIE", ignoreCase = true) || type.contains("TV", ignoreCase = true)) && !type.contains("EPISODE", ignoreCase = true) && !type.contains("EPISODIO", ignoreCase = true) && !prefs.getBoolean("newSeriesEnabled", true)) {
                Log.d(TAG, "Series notifications disabled by user in Settings.")
                return
            }
            if ((type.contains("EPISODE", ignoreCase = true) || type.contains("EPISODIO", ignoreCase = true)) && !prefs.getBoolean("newEpisodesEnabled", true)) {
                Log.d(TAG, "Episode notifications disabled by user in Settings.")
                return
            }

            val title = data["title"] ?: remoteMessage.notification?.title ?: "Novo conteúdo no RONYCINE"
            val message = data["message"] ?: data["body"] ?: remoteMessage.notification?.body ?: "Confira as novidades no catálogo!"
            val actionUrl = data["actionUrl"]
            val imageUrl = data["imageUrl"] ?: remoteMessage.notification?.imageUrl?.toString()
            
            sendNotification(title, message, actionUrl, imageUrl)
        } else {
            remoteMessage.notification?.let {
                Log.d(TAG, "Message Notification Body: ${it.body}")
                sendNotification(it.title ?: "Novo conteúdo no RONYCINE", it.body ?: "Confira as novidades no catálogo!", null, it.imageUrl?.toString())
            }
        }
    }

    private fun sendNotification(title: String, messageBody: String, actionUrl: String?, imageUrl: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (actionUrl != null) {
                putExtra("actionUrl", actionUrl)
            }
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 
            System.currentTimeMillis().toInt(), 
            intent,
            pendingIntentFlags
        )

        val channelId = "ronycine_news"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // Optional Bitmap loading for imageUrl
        if (!imageUrl.isNullOrBlank()) {
            try {
                val url = java.net.URL(imageUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.doInput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()
                val input = connection.inputStream
                val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                if (bitmap != null) {
                    notificationBuilder.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .bigLargeIcon(null as android.graphics.Bitmap?)
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load image for notification push: ${e.message}")
            }
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "RONYCINE",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Novidades do RONYCINE"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}
