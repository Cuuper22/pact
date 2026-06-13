package app.pact.android.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.pact.android.MainActivity
import app.pact.android.R

/**
 * Builds the app's notifications. The "ask" notification is the actionable one:
 * Allow / Not now buttons whose PendingIntents fire [AskActionReceiver], which
 * POSTs the vote to the relay so a locked phone votes in one tap.
 */
object PactNotifications {

    const val CHANNEL_ASKS = "pact_asks"
    const val CHANNEL_SHIELD = "pact_shield"
    private const val ASK_NOTIFICATION_ID = 4201

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ASKS,
                context.getString(R.string.notif_channel_asks_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_asks_desc)
            },
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SHIELD,
                context.getString(R.string.notif_channel_shield_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notif_channel_shield_desc)
                setShowBadge(false)
            },
        )
    }

    fun showAsk(context: Context, pactId: String, title: String, reason: String) {
        ensureChannels(context)
        if (!hasPostPermission(context)) return

        val allow = voteIntent(context, pactId, allow = true, requestCode = 1)
        val notNow = voteIntent(context, pactId, allow = false, requestCode = 2)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            pendingFlags(),
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ASKS)
            .setSmallIcon(R.drawable.ic_pact_flame)
            .setContentTitle(title)
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(
                R.drawable.ic_check,
                context.getString(R.string.action_allow),
                allow,
            )
            .addAction(
                R.drawable.ic_close,
                context.getString(R.string.action_not_now),
                notNow,
            )
            .build()

        NotificationManagerCompat.from(context).notify(ASK_NOTIFICATION_ID, notif)
    }

    fun showInfo(context: Context, title: String, body: String?) {
        ensureChannels(context)
        if (!hasPostPermission(context)) return
        val notif = NotificationCompat.Builder(context, CHANNEL_ASKS)
            .setSmallIcon(R.drawable.ic_pact_flame)
            .setContentTitle(title)
            .setContentText(body.orEmpty())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ASK_NOTIFICATION_ID + 1, notif)
    }

    fun cancelAsk(context: Context) {
        NotificationManagerCompat.from(context).cancel(ASK_NOTIFICATION_ID)
    }

    private fun voteIntent(
        context: Context,
        pactId: String,
        allow: Boolean,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, AskActionReceiver::class.java).apply {
            action = AskActionReceiver.ACTION_VOTE
            putExtra(AskActionReceiver.EXTRA_PACT_ID, pactId)
            putExtra(AskActionReceiver.EXTRA_ALLOW, allow)
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, pendingFlags())
    }

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun hasPostPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
