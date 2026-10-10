package lol.mrrabbitson.fpmini.auto

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import lol.mrrabbitson.fpmini.core.ChatPreview
import lol.mrrabbitson.fpmini.core.OrderStatus
import lol.mrrabbitson.fpmini.data.AutoSettings
import lol.mrrabbitson.fpmini.data.FpException
import lol.mrrabbitson.fpmini.data.Prefs
import lol.mrrabbitson.fpmini.data.Repository

/**
 * Цикл автоматизации: каждые несколько секунд один лёгкий запрос к FunPay
 * (список чатов + счётчик продаж), дальше — по ситуации:
 * уведомления, автоответы, проверка новых заказов, поднятие лотов.
 */
class Automation(
    private val ctx: Context,
    private val repo: Repository,
    private val prefs: Prefs,
    private val log: EventLog,
) {
    private var lastSeller: Int? = null
    private var lastOrdersCheck = 0L
    private var nextRaiseCheck = 0L

    suspend fun run() {
        log.add(EventKind.INFO, "Автоматизация запущена")
        var errors = 0
        while (currentCoroutineContext().isActive) {
            val a = prefs.auto.value
            try {
                val st = repo.poll(withChats = a.needsChats)
                val firstRun = prefs.getLong("init_done") == 0L

                if (a.needsChats && st.chats != null) handleChats(st.chats, a, firstRun)

                val now = System.currentTimeMillis()
                val sellerChanged = st.sellerOrders != null && st.sellerOrders != lastSeller
                if (a.notifyOrders && (sellerChanged || now - lastOrdersCheck > 3 * 60_000)) {
                    checkOrders(firstRun)
                    lastOrdersCheck = now
                }
                lastSeller = st.sellerOrders

                if (a.raise && now >= nextRaiseCheck) {
                    raise(a)
                }

                if (firstRun) prefs.putLong("init_done", 1)
                if (errors > 0) log.add(EventKind.OK, "Связь с FunPay восстановлена")
                errors = 0
                log.status(statusText(a, st.sellerOrders))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors++
                if (errors == 1 || errors % 10 == 0) log.add(EventKind.ERROR, e.message ?: "Ошибка")
                log.status("Нет связи с FunPay, повтор…")
                if ((e as? FpException)?.code == 401) {
                    log.add(EventKind.ERROR, "Сессия FunPay слетела — войдите заново в приложении")
                }
            }
            val pause = when {
                errors == 0 -> 6_000L
                errors < 5 -> 15_000L
                else -> 60_000L
            }
            delay(pause)
        }
    }

    private fun statusText(a: AutoSettings, seller: Int?): String {
        val parts = mutableListOf<String>()
        if (a.greet || a.rulesEnabled) parts += "автоответы"
        if (a.raise) parts += "поднятие"
        if (a.notifyMessages || a.notifyOrders) parts += "уведомления"
        val base = if (parts.isEmpty()) "Ничего не включено" else parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
        return if (seller != null && seller > 0) "$base · активных продаж: $seller" else base
    }

    // ---------- сообщения ----------

    private fun sig(c: ChatPreview) = c.lastMsgId?.toString() ?: (c.lastMessage + "|" + c.time)

    private suspend fun handleChats(chats: List<ChatPreview>, a: AutoSettings, firstRun: Boolean) {
        val me = repo.me().userId
        for (c in chats) {
            val key = "sig:${c.node}"
            val old = prefs.getString(key)
            val now = sig(c)
            if (old == now) continue
            prefs.putString(key, now)
            if (firstRun) {
                // при первом запуске только запоминаем, что уже было
                c.lastMsgId?.let { prefs.putLong("msg:${c.node}", it) }
                continue
            }
            processChat(c, a, me)
        }
    }

    private suspend fun processChat(c: ChatPreview, a: AutoSettings, me: Int) {
        val page = repo.chat(c.node)
        val lastDone = prefs.getLong("msg:${c.node}", 0L)
        val fresh = page.messages.filter { it.id > lastDone }
        page.messages.maxOfOrNull { it.id }?.let { prefs.putLong("msg:${c.node}", it) }
        val incoming = fresh.filter { it.authorId != null && it.authorId != me && it.authorId != 0 }
        if (incoming.isEmpty()) return

        val last = incoming.last()
        val who = page.interlocutor?.name ?: c.name
        if (a.notifyMessages) {
            Notifier.message(ctx, c.node, who, last.text.ifBlank { if (last.imageUrl != null) "📷 Картинка" else "Новое сообщение" })
        }
        log.add(EventKind.INFO, "Сообщение от $who: ${last.text.take(60)}")

        // приветствие — только если я ещё ни разу не писал в этот чат
        val greeted = prefs.getStringSet("greeted")
        if (a.greet && c.node !in greeted && page.messages.none { it.authorId == me }) {
            send(c.node, a.greetText, "Приветствие → $who")
            prefs.putStringSet("greeted", greeted + c.node)
        }

        // ответ по ключевым словам на последнее входящее
        if (a.rulesEnabled && last.text.isNotBlank()) {
            val text = last.text.lowercase()
            val rule = a.rules.firstOrNull { r ->
                r.enabled && r.keywords.any { k -> k.isNotBlank() && text.contains(k.trim().lowercase()) }
            }
            if (rule != null) send(c.node, rule.reply, "Автоответ → $who")
        }
    }

    private suspend fun send(node: String, text: String, what: String) {
        try {
            repo.send(node, text)
            log.add(EventKind.OK, what)
        } catch (e: Exception) {
            log.add(EventKind.ERROR, "$what: ${e.message}")
        }
    }

    // ---------- заказы ----------

    private suspend fun checkOrders(firstRun: Boolean) {
        val paid = repo.orders("paid").filter { it.status == OrderStatus.PAID }
        val known = prefs.getStringSet("orders_known")
        val init = firstRun || prefs.getLong("orders_init") == 0L
        val fresh = paid.filter { it.id !in known }
        if (!init) {
            fresh.forEach { o ->
                val text = listOfNotNull(o.description.ifBlank { null }, o.buyer?.name?.let { "Покупатель: $it" }, o.priceText.ifBlank { null })
                    .joinToString(" · ")
                Notifier.order(ctx, o.id, text)
                log.add(EventKind.OK, "Новый заказ #${o.id}: ${o.priceText}")
            }
        }
        prefs.putStringSet("orders_known", (known + paid.map { it.id }).toList().takeLast(500).toSet())
        prefs.putLong("orders_init", 1)
    }

    // ---------- поднятие ----------

    private suspend fun raise(a: AutoSettings) {
        val now = System.currentTimeMillis()
        val skip = prefs.getStringSet("raise_games").mapNotNull { e ->
            val (g, t) = e.split(':').let { it.getOrNull(0)?.toIntOrNull() to it.getOrNull(1)?.toLongOrNull() }
            if (g != null && t != null && t > now) g else null
        }.toSet()
        val results = repo.raiseAll(skip)
        val next = mutableMapOf<Int, Long>()
        prefs.getStringSet("raise_games").forEach { e ->
            val p = e.split(':'); val g = p.getOrNull(0)?.toIntOrNull(); val t = p.getOrNull(1)?.toLongOrNull()
            if (g != null && t != null && t > now) next[g] = t
        }
        for ((game, r) in results) {
            next[game] = when {
                r.ok -> now + a.raiseIntervalMin * 60_000L
                r.waitSec != null -> now + r.waitSec * 1000 + 30_000
                else -> now + 10 * 60_000L
            }
            when {
                r.ok -> log.add(EventKind.OK, "Подняты: ${r.game}")
                r.waitSec != null -> log.add(EventKind.INFO, "${r.game}: ${r.message}")
                else -> log.add(EventKind.WARN, "${r.game}: ${r.message}")
            }
        }
        prefs.putStringSet("raise_games", next.map { (g, t) -> "$g:$t" }.toSet())
        nextRaiseCheck = (next.values.minOrNull() ?: (now + a.raiseIntervalMin * 60_000L)).coerceAtLeast(now + 60_000)
    }
}
