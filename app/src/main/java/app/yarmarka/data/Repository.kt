package app.yarmarka.data

import app.yarmarka.core.CategoryPage
import app.yarmarka.core.HomePage
import app.yarmarka.core.OfferPage
import app.yarmarka.core.Parser
import app.yarmarka.core.ProfilePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Всё, что приложение берёт с FunPay. Короткий кэш, чтобы «Назад» не грузил страницу заново. */
class Repository(private val client: FpClient, private val prefs: Prefs) {

    private val cache = ConcurrentHashMap<String, Pair<Long, Any>>()
    private val ttlMs = 2 * 60_000L

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, force: Boolean, load: suspend () -> T): T {
        if (!force) {
            cache[key]?.let { (at, v) -> if (System.currentTimeMillis() - at < ttlMs) return v as T }
        }
        val v = load()
        cache[key] = System.currentTimeMillis() to v
        return v
    }

    fun clearCache() = cache.clear()

    private suspend fun page(path: String): String = client.get(path)

    /** Запоминаем, кто вошёл, — по data-app-data любой страницы. */
    private fun rememberMe(html: String) {
        val ad = Parser.appData(Parser.doc(html))
        if (prefs.account.value.loggedIn) prefs.setMe(ad.userId, prefs.account.value.name)
    }

    suspend fun home(force: Boolean = false): HomePage = cached("home", force) {
        val html = page("/")
        withContext(Dispatchers.Default) {
            Parser.home(html).also { h ->
                if (prefs.account.value.loggedIn) prefs.setMe(h.myUserId, h.myName)
            }
        }
    }

    suspend fun category(type: String, id: Int, force: Boolean = false): CategoryPage =
        cached("cat/$type/$id", force) {
            val html = page("/$type/$id/")
            withContext(Dispatchers.Default) { Parser.category(html, id, type) }
        }

    suspend fun offer(type: String, id: String, force: Boolean = false): OfferPage =
        cached("offer/$type/$id", force) {
            val html = page("/$type/offer?id=$id")
            withContext(Dispatchers.Default) { rememberMe(html); Parser.offer(html, id) }
        }

    suspend fun profile(id: Int, force: Boolean = false): ProfilePage =
        cached("user/$id", force) {
            val html = page("/users/$id/")
            withContext(Dispatchers.Default) { rememberMe(html); Parser.profile(html, id) }
        }

    /** Проверка входа: грузим главную и смотрим, видит ли FunPay пользователя. */
    suspend fun verifyLogin(): Boolean {
        val h = home(force = true)
        return h.myUserId != null
    }

    /** Отправить сообщение пользователю (как поле «Написать...» на сайте). */
    suspend fun sendMessage(toUserId: Int, text: String) {
        if (!prefs.account.value.loggedIn) throw FpException("Войдите в аккаунт, чтобы писать продавцам.")
        val html = page("/")
        val ad = Parser.appData(Parser.doc(html))
        val me = ad.userId ?: throw FpException("FunPay не видит вход. Войдите заново в настройках.")
        val csrf = ad.csrfToken ?: throw FpException("Не удалось получить токен FunPay.")
        if (me == toUserId) throw FpException("Это ваш лот.")
        val node = "users-${minOf(me, toUserId)}-${maxOf(me, toUserId)}"
        val request = JSONObject()
            .put("action", "chat_message")
            .put("data", JSONObject().put("node", node).put("last_message", -1).put("content", text))
        val resp = client.runner("[]", request.toString(), csrf)
        val json = runCatching { JSONObject(resp) }.getOrNull()
            ?: throw FpException("Непонятный ответ FunPay.")
        if (json.optBoolean("error", false)) {
            val msg = json.optString("msg").ifBlank { "FunPay не отправил сообщение." }
            throw FpException(msg)
        }
    }
}
