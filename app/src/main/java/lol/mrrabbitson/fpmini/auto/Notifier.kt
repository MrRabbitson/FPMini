package lol.mrrabbitson.fpmini.auto

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
import lol.mrrabbitson.fpmini.MainActivity
import lol.mrrabbitson.fpmini.R

object Notifier {
    const val CH_SERVICE = "service"
    const val CH_MESSAGES = "messages"
    const val CH_ORDERS = "orders"
    const val SERVICE_ID = 1

    const val EXTRA_TAB = "tab"
    const val EXTRA_NODE = "node"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "Работа в фоне", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Постоянное уведомление, пока работает автоматизация"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_MESSAGES, "Сообщения", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_ORDERS, "Заказы", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun openIntent(ctx: Context, tab: String, node: String? = null, req: Int = 0): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_TAB, tab)
        if (node != null) i.putExtra(EXTRA_NODE, node)
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun serviceNotification(ctx: Context, text: String) =
        NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("FPMini работает")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent(ctx, "auto"))
            .build()

    private fun canPost(ctx: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    fun message(ctx: Context, node: String, from: String, text: String) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(from)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openIntent(ctx, "chats", node, node.hashCode()))
            .build()
        NotificationManagerCompat.from(ctx).notify(("m$node").hashCode(), n)
    }

    @Suppress("MissingPermission")
    fun order(ctx: Context, id: String, text: String) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_ORDERS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Новый заказ #$id")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, "sales", req = id.hashCode()))
            .build()
        NotificationManagerCompat.from(ctx).notify(("o$id").hashCode(), n)
    }
}
