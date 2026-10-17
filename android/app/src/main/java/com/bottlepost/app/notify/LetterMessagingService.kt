package com.bottlepost.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bottlepost.app.BottlepostApp
import com.bottlepost.app.MainActivity
import com.bottlepost.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/**
 * Pushes are data-only and contain no letter text. On receipt we pull the letter from the relay
 * (which also deletes it there) and show a content-free, localized notification.
 */
class LetterMessagingService : FirebaseMessagingService() {

    private val container get() = (application as BottlepostApp).container

    override fun onNewToken(token: String) {
        container.appScope.launch { runCatching { container.registerPushToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "letter") return
        val kind = message.data["kind"]
        container.appScope.launch {
            val uid = container.auth.uid ?: return@launch
            runCatching { container.mail.syncOnce(uid) }
            show(this@LetterMessagingService, kind == "reply")
        }
    }

    companion object {
        const val CHANNEL_ID = "letters"
        private const val NOTIFICATION_ID = 1

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_letters),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun show(context: Context, isReply: Boolean) {
            // POST_NOTIFICATIONS is a runtime permission only from Android 13.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(if (isReply) R.string.notification_reply_title else R.string.notification_letter_title))
                .setContentText(context.getString(R.string.notification_body))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
