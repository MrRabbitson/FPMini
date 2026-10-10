package lol.mrrabbitson.fpmini.auto

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import lol.mrrabbitson.fpmini.App

/** Фоновая работа с постоянным уведомлением. Отключается переключателем на вкладке «Автоматизация». */
class AutomationService : Service() {

    companion object {
        private const val ACTION_STOP = "stop"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, AutomationService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AutomationService::class.java))
        }

        /** Запустить или остановить в зависимости от настроек. */
        fun sync(ctx: Context) {
            val app = ctx.applicationContext as App
            if (app.prefs.auto.value.background && app.prefs.account.value.loggedIn) start(ctx) else stop(ctx)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as App
        val notif = Notifier.serviceNotification(this, "Запуск…")
        ServiceCompat.startForeground(
            this, Notifier.SERVICE_ID, notif,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (intent?.action == ACTION_STOP || !app.prefs.auto.value.background) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop == null) {
            loop = scope.launch { Automation(this@AutomationService, app.repo, app.prefs, app.log).run() }
            // текст постоянного уведомления = текущий статус
            scope.launch {
                app.log.status.collectLatest { s ->
                    runCatching {
                        NotificationManagerCompat.from(this@AutomationService)
                            .notify(Notifier.SERVICE_ID, Notifier.serviceNotification(this@AutomationService, s))
                    }
                }
            }
            // выключили в настройках или вышли из аккаунта — останавливаемся
            scope.launch {
                app.prefs.auto.map { it.background }.distinctUntilChanged().collectLatest { if (!it) stopSelf() }
            }
            scope.launch {
                app.prefs.account.map { it.loggedIn }.distinctUntilChanged().collectLatest { if (!it) stopSelf() }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        (application as App).log.apply { add(EventKind.INFO, "Автоматизация остановлена"); status("Остановлено") }
        scope.cancel()
        super.onDestroy()
    }
}

/** После перезагрузки телефона — снова запускаем, если фон включён. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            runCatching { AutomationService.sync(context) }
        }
    }
}
