package lol.mrrabbitson.fpmini.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import lol.mrrabbitson.fpmini.core.AppData
import lol.mrrabbitson.fpmini.core.ChatPage
import lol.mrrabbitson.fpmini.core.ChatPreview
import lol.mrrabbitson.fpmini.core.MyLotGroup
import lol.mrrabbitson.fpmini.core.Order
import lol.mrrabbitson.fpmini.core.OfferPage
import lol.mrrabbitson.fpmini.core.OrderPage
import lol.mrrabbitson.fpmini.core.TicketCategory
import lol.mrrabbitson.fpmini.core.TicketForm
import lol.mrrabbitson.fpmini.core.LotEditForm
import lol.mrrabbitson.fpmini.core.LotFormParser
import lol.mrrabbitson.fpmini.core.Parser
import lol.mrrabbitson.fpmini.core.ProfilePage
import lol.mrrabbitson.fpmini.core.SellerParser
import lol.mrrabbitson.fpmini.core.Ticket
import lol.mrrabbitson.fpmini.core.TicketPage
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** Сводка аккаунта с главной страницы. */
data class Me(
    val userId: Int,
    val name: String?,
    val csrf: String,
    val balance: String?,
    val activeSales: Int?,
    val nodeToGame: Map<Int, Int>,
)

data class RunnerState(val chats: List<ChatPreview>?, val sellerOrders: Int?)

data class RaiseResult(val game: String, val ok: Boolean, val message: String, val waitSec: Long?)

class Repository(private val client: FpClient, private val prefs: Prefs) {

    private val meLock = Mutex()
    @Volatile private var meCache: Pair<Long, Me>? = null
    @Volatile private var supportReadyAt = 0L

    // ---------- сессия ----------

    private fun parseMe(html: String): Me? {
        val d = Parser.doc(html)
        val ad: AppData = Parser.appData(d)
        val uid = ad.userId ?: return null
        val home = Parser.home(html)
        return Me(
            userId = uid,
            name = home.myName,
            csrf = ad.csrfToken.orEmpty(),
            balance = d.selectFirst(".badge-balance")?.text()?.trim()?.ifBlank { null },
            activeSales = d.selectFirst(".badge-trade")?.text()?.trim()?.toIntOrNull(),
            nodeToGame = SellerParser.nodeToGame(home),
        )
    }

    /** Данные текущего пользователя. Если FunPay отдал гостевую страницу — пробуем новую сессию по golden_key. */
    suspend fun me(force: Boolean = false): Me = meLock.withLock {
        if (!prefs.account.value.loggedIn) throw FpException("Не выполнен вход")
        val cached = meCache
        if (!force && cached != null && System.currentTimeMillis() - cached.first < 10 * 60_000) return cached.second
        var me = withContext(Dispatchers.Default) { parseMe(client.get("/")) }
        if (me == null) {
            client.resetSession()
            me = withContext(Dispatchers.Default) { parseMe(client.get("/")) }
        }
        me ?: throw FpException("FunPay не видит вход. Войдите заново.", 401)
        prefs.setMe(me.userId, me.name)
        meCache = System.currentTimeMillis() to me
        me
    }

    fun clear() {
        meCache = null
        supportReadyAt = 0
    }

    private fun tag() = (1..8).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")

    private suspend fun runner(objects: JSONArray, request: Any = false): JSONObject {
        val me = me()
        val body = client.post(
            "/runner/",
            listOf("objects" to objects.toString(), "request" to request.toString(), "csrf_token" to me.csrf),
        )
        return runCatching { JSONObject(body) }.getOrElse { throw FpException("Непонятный ответ FunPay") }
    }

    // ---------- чаты ----------

    /** Один лёгкий запрос: список чатов + число активных продаж. Так же делает сайт. */
    suspend fun poll(withChats: Boolean = true): RunnerState {
        val me = me()
        val objs = JSONArray()
        objs.put(JSONObject().put("type", "orders_counters").put("id", me.userId.toString()).put("tag", tag()).put("data", false))
        objs.put(JSONObject().put("type", "chat_counter").put("id", me.userId.toString()).put("tag", tag()).put("data", false))
        if (withChats) objs.put(JSONObject().put("type", "chat_bookmarks").put("id", me.userId.toString()).put("tag", tag()).put("data", false))
        val json = runner(objs)
        var chats: List<ChatPreview>? = null
        var seller: Int? = null
        val arr = json.optJSONArray("objects") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val data = o.opt("data") as? JSONObject ?: continue
            when (o.optString("type")) {
                "chat_bookmarks" -> chats = withContext(Dispatchers.Default) { SellerParser.chatList(data.optString("html")) }
                "orders_counters" -> seller = data.optInt("seller", -1).takeIf { it >= 0 }
            }
        }
        return RunnerState(chats, seller)
    }

    suspend fun chats(): List<ChatPreview> {
        poll(true).chats?.let { return it }
        val html = client.get("/chat/")
        return withContext(Dispatchers.Default) { SellerParser.chatList(html) }
    }

    suspend fun chat(node: String): ChatPage {
        val html = client.get("/chat/?node=$node")
        return withContext(Dispatchers.Default) { SellerParser.chat(html, node) }
    }

    /** Узел личного чата с пользователем: users-меньшийID-большийID. */
    suspend fun nodeWith(userId: Int): String {
        val me = me().userId
        return "users-${minOf(me, userId)}-${maxOf(me, userId)}"
    }

    suspend fun send(node: String, text: String) {
        val req = JSONObject()
            .put("action", "chat_message")
            .put("data", JSONObject().put("node", node).put("last_message", -1).put("content", text))
        val json = runner(JSONArray(), req)
        val resp = json.optJSONObject("response")
        val err = json.optBoolean("error", false) || resp?.optBoolean("error", false) == true
        if (err) {
            val msg = (resp?.optString("msg") ?: json.optString("msg")).ifBlank { "FunPay не отправил сообщение" }
            throw FpException(msg)
        }
    }

    // ---------- продажи ----------

    suspend fun orders(state: String? = null): List<Order> {
        val html = client.get("/orders/trade" + (state?.let { "?state=$it" } ?: ""))
        return withContext(Dispatchers.Default) { SellerParser.orders(html) }
    }

    // ---------- профиль и лоты ----------

    suspend fun myProfile(): Pair<ProfilePage, List<MyLotGroup>> {
        val me = me()
        val html = client.get("/users/${me.userId}/")
        return withContext(Dispatchers.Default) {
            Parser.profile(html, me.userId) to SellerParser.myLots(html, me.nodeToGame)
        }
    }

    private fun parseWait(msg: String): Long? {
        if (!msg.contains("подожд", true)) return null
        val n = Regex("(\\d+)").find(msg)?.groupValues?.get(1)?.toLongOrNull() ?: 1L
        return when {
            msg.contains("сек", true) -> n
            msg.contains("мин", true) -> n * 60
            msg.contains("час", true) -> n * 3600
            else -> 3600
        }
    }

    /** Поднять лоты одной игры. Если FunPay спрашивает, какие разделы, — поднимаем все предложенные. */
    suspend fun raiseGame(gameId: Int, nodeIds: List<Int>, title: String): RaiseResult {
        val first = nodeIds.first()
        val base = listOf("game_id" to gameId.toString(), "node_id" to first.toString())
        val firstBody = base + nodeIds.map { "node_ids[]" to it.toString() }
        var json = JSONObject(try {
            client.post("/lots/raise", firstBody, "${FpClient.BASE}/lots/$first/trade")
        } catch (e: FpException) {
            if (e.code != 428) throw e
            refreshSeal()
            client.post("/lots/raise", firstBody, "${FpClient.BASE}/lots/$first/trade")
        })
        val modal = json.optString("modal")
        if (modal.isNotBlank()) {
            val ids = Regex("value=\"(\\d+)\"").findAll(modal).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
                .ifEmpty { nodeIds }
            json = JSONObject(client.post("/lots/raise", base + ids.map { "node_ids[]" to it.toString() }, "${FpClient.BASE}/lots/$first/trade"))
        }
        val msg = json.optString("msg").ifBlank { if (json.optBoolean("error")) "Ошибка" else "Подняты" }
        val wait = parseWait(msg)
        val ok = !json.optBoolean("error", false) && wait == null
        return RaiseResult(title, ok, msg, wait)
    }

    /** Поднять все мои лоты (по играм). */
    suspend fun raiseAll(skipGames: Set<Int> = emptySet()): List<Pair<Int, RaiseResult>> {
        val groups = myProfile().second.filter { it.type == "lots" && it.gameId != null }
        val byGame = groups.groupBy { it.gameId!! }
        val out = mutableListOf<Pair<Int, RaiseResult>>()
        for ((game, gs) in byGame) {
            if (game in skipGames) continue
            val r = try {
                raiseGame(game, gs.map { it.nodeId }, gs.joinToString(", ") { it.title })
            } catch (e: FpException) {
                RaiseResult(gs.first().title, false, e.message ?: "Ошибка", null)
            }
            out += game to r
        }
        return out
    }

    // ---------- поддержка ----------

    /** Вход в support.funpay.com через FunPay (SSO). Хватает примерно на полчаса. */
    private suspend fun ensureSupport(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - supportReadyAt < 25 * 60_000) return
        client.get("/support/sso?return_to=%2Ftickets")
        supportReadyAt = System.currentTimeMillis()
    }

    suspend fun tickets(): List<Ticket> {
        ensureSupport()
        val out = mutableListOf<Ticket>()
        for (page in 1..3) {
            val html = client.get("${FpClient.SUPPORT}/tickets?status=all&order=last_answered&page=$page")
            val list = withContext(Dispatchers.Default) { SellerParser.tickets(html) }
            if (page == 1 && list.isEmpty() && !html.contains("ticket")) {
                // похоже, вход в поддержку слетел — пробуем ещё раз
                ensureSupport(force = true)
                return withContext(Dispatchers.Default) {
                    SellerParser.tickets(client.get("${FpClient.SUPPORT}/tickets?status=all&order=last_answered&page=1"))
                }
            }
            out += list
            if (!SellerParser.hasNextTicketsPage(html)) break
        }
        return out
    }

    suspend fun ticket(id: String): TicketPage {
        ensureSupport()
        val html = client.get("${FpClient.SUPPORT}/tickets/$id")
        return withContext(Dispatchers.Default) { SellerParser.ticket(html, id) }
    }

    suspend fun replyTicket(id: String, text: String) {
        val page = ticket(id)
        val token = page.replyToken ?: throw FpException("Заявка закрыта или форма ответа недоступна")
        val html = toHtml(text)
        client.post(
            "${FpClient.SUPPORT}/tickets/$id/comments/create",
            listOf(
                "add_comment[comment][body_html]" to html,
                "add_comment[comment][attachments]" to "",
                "add_comment[_token]" to token,
            ),
            "${FpClient.SUPPORT}/tickets/$id",
        )
    }

    // ---------- заказ и лот (внутри приложения) ----------

    suspend fun order(id: String): OrderPage {
        val html = client.get("/orders/$id/")
        return withContext(Dispatchers.Default) { SellerParser.order(html, id) }
    }

    suspend fun offer(type: String, id: String): OfferPage {
        val html = client.get("/$type/offer?id=$id")
        return withContext(Dispatchers.Default) { Parser.offer(html, id) }
    }

    // ---------- новая заявка ----------

    suspend fun ticketCategories(): List<TicketCategory> {
        ensureSupport()
        val html = client.get("${FpClient.SUPPORT}/tickets/new")
        return withContext(Dispatchers.Default) { SellerParser.ticketCategories(html) }
    }

    suspend fun ticketForm(categoryId: String): TicketForm {
        ensureSupport()
        val html = client.get("${FpClient.SUPPORT}/tickets/new/$categoryId")
        return withContext(Dispatchers.Default) { SellerParser.ticketForm(html, categoryId) }
    }

    private fun toHtml(text: String) =
        "<p>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</p>"

    /** Создать заявку. Возвращает id новой заявки, если FunPay его вернул. */
    suspend fun createTicket(form: TicketForm, values: Map<String, String>): String? {
        val token = form.token ?: ticketForm(form.categoryId).token ?: throw FpException("Форма заявки недоступна")
        val fields = mutableListOf<Pair<String, String>>()
        form.fields.forEach { f ->
            val v = values[f.name].orEmpty().trim()
            if (f.required && v.isEmpty()) throw FpException("Заполните поле «${f.label.ifBlank { "Сообщение" }}»")
            if (v.isEmpty()) return@forEach
            fields += f.name to (if (f.kind == "textarea") toHtml(v) else v)
        }
        fields += "ticket[comment][attachments]" to ""
        fields += "ticket[_token]" to token
        val body = client.post("${FpClient.SUPPORT}/tickets/create/${form.categoryId}", fields, "${FpClient.SUPPORT}/tickets/new/${form.categoryId}")
        val json = runCatching { JSONObject(body) }.getOrNull()
        json?.optJSONObject("errors")?.let { e ->
            val msg = e.keys().asSequence().mapNotNull { k -> e.optJSONArray(k)?.optString(0) ?: e.optString(k) }.firstOrNull()
            throw FpException(msg ?: "FunPay не принял заявку")
        }
        return json?.optJSONObject("action")?.optString("url")?.trimEnd('/')?.substringAfterLast('/')?.ifBlank { null }
    }

    // ---------- профиль другого пользователя ----------

    suspend fun profile(id: Int): ProfilePage {
        val html = client.get("/users/$id/")
        return withContext(Dispatchers.Default) { Parser.profile(html, id) }
    }

    // ---------- вся история продаж ----------

    @Volatile private var ordersCache: Pair<Long, List<Order>>? = null

    /**
     * Все продажи (страница за страницей, как кнопка «Показать ещё» на сайте).
     * [onProgress] — сколько уже загружено. Кэш 5 минут.
     */
    suspend fun allOrders(force: Boolean = false, maxPages: Int = 60, onProgress: (Int) -> Unit = {}): List<Order> {
        val cached = ordersCache
        if (!force && cached != null && System.currentTimeMillis() - cached.first < 5 * 60_000) return cached.second
        val out = linkedMapOf<String, Order>()
        var html = client.get("/orders/trade")
        for (page in 1..maxPages) {
            val p = withContext(Dispatchers.Default) { SellerParser.ordersPage(html) }
            p.orders.forEach { out.putIfAbsent(it.id, it) }
            onProgress(out.size)
            val token = p.continueToken ?: break
            if (p.orders.isEmpty()) break
            html = client.post("/orders/trade", listOf("continue" to token), "${FpClient.BASE}/orders/trade")
        }
        val list = out.values.toList()
        ordersCache = System.currentTimeMillis() to list
        return list
    }

    // ---------- редактирование лота ----------

    /** Обновить куку golden_seal (её требуют сохранение лотов и поднятие). */
    suspend fun refreshSeal() {
        val me = me()
        val objs = JSONArray().put(JSONObject().put("type", "chat_counter").put("id", me.userId.toString()).put("tag", tag()).put("data", false))
        runCatching { runner(objs) }
    }

    suspend fun lotForm(offerId: String): LotEditForm {
        me()
        val html = client.get("/lots/offerEdit?offer=$offerId")
        return withContext(Dispatchers.Default) {
            try { LotFormParser.parse(html, offerId) } catch (e: IllegalStateException) { throw FpException(e.message ?: "Форма не загрузилась") }
        }
    }

    /** Сохранить лот. Ошибки FunPay (например, неверная цена) — в тексте исключения. */
    suspend fun saveLot(form: LotEditForm, values: Map<String, String>) {
        val body = form.buildSave(values, me().csrf)
        val referer = "${FpClient.BASE}/lots/offerEdit?offer=${form.offerId}"
        val text = try {
            client.post("/lots/offerSave", body, referer)
        } catch (e: FpException) {
            if (e.code != 428) throw e
            refreshSeal()
            client.post("/lots/offerSave", body, referer)
        }
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        val err = when (val e = json.opt("error")) {
            is Boolean -> e
            is Number -> e.toInt() != 0
            is String -> e.isNotBlank() && e != "0" && e != "false"
            else -> false
        }
        val errors = json.optJSONArray("errors")
        val errObj = json.optJSONObject("errors")
        if (!err && (errors == null || errors.length() == 0) && (errObj == null || errObj.length() == 0)) return
        val parts = mutableListOf<String>()
        json.optString("msg").takeIf { it.isNotBlank() }?.let { parts += it }
        if (errors != null) for (k in 0 until errors.length()) {
            val v = errors.opt(k)
            parts += if (v is JSONArray) v.optString(1, v.optString(0)) else v.toString()
        }
        errObj?.keys()?.forEach { k -> parts += errObj.optString(k) }
        throw FpException(parts.filter { it.isNotBlank() }.joinToString("\n").ifBlank { "FunPay не сохранил лот" })
    }
}
