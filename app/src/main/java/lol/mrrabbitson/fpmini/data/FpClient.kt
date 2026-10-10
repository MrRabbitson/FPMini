package lol.mrrabbitson.fpmini.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class FpException(message: String, val code: Int = 0) : IOException(message)

/** HTTP-клиент для funpay.com и support.funpay.com: свои куки + golden_key. */
class FpClient(private val prefs: Prefs) {

    companion object {
        const val BASE = "https://funpay.com"
        const val SUPPORT = "https://support.funpay.com"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"
    }

    private val jar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (!url.host.endsWith("funpay.com")) return
            val now = System.currentTimeMillis()
            cookies.forEach { c ->
                val scope = if (c.hostOnly) url.host else "*" + c.domain
                prefs.saveCookie(scope, c.name, if (c.expiresAt < now) null else c.value)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            if (!url.host.endsWith("funpay.com")) return emptyList()
            val all = prefs.cookies(url.host).toMutableMap()
            prefs.account.value.goldenKey?.let { all["golden_key"] = it }
            return all.map { (k, v) -> Cookie.Builder().domain(url.host).path("/").name(k).value(v).build() }
        }
    }

    private val http = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun url(path: String) = if (path.startsWith("http")) path else BASE + path

    /**
     * Выполнить запрос. На 429 (слишком много запросов) и 502/503 — ждём и повторяем,
     * учитывая Retry-After, до 5 попыток.
     */
    private fun exec(req: Request): String {
        var attempt = 0
        while (true) {
            val resp = try {
                http.newCall(req).execute()
            } catch (e: IOException) {
                if (attempt < 2) { attempt++; Thread.sleep(1500L * attempt); continue }
                throw FpException("Нет соединения с FunPay")
            }
            val code = resp.code
            val retryAfter = resp.header("Retry-After")?.trim()?.toLongOrNull()
            val body = resp.use { it.body?.string().orEmpty() }
            if ((code == 429 || code == 502 || code == 503) && attempt < 5) {
                attempt++
                val wait = (retryAfter?.times(1000) ?: (2000L shl (attempt - 1))).coerceIn(1000L, 30_000L)
                Thread.sleep(wait)
                continue
            }
            when {
                body.contains("challenge-platform") || body.contains("cf-browser-verification") ->
                    throw FpException("FunPay просит проверку браузера, попробуйте позже", 403)
                code == 428 -> throw FpException("FunPay требует обновить сессию", 428)
                code == 429 -> throw FpException("FunPay ограничил запросы, попробуйте через минуту", 429)
                code == 404 -> throw FpException("Не найдено", 404)
                code !in 200..299 -> throw FpException("FunPay ответил ошибкой $code", code)
            }
            return body
        }
    }

    suspend fun get(path: String, ajax: Boolean = false): String = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url(path))
            .header("User-Agent", UA)
            .header("Accept-Language", "ru-RU,ru;q=0.9")
        if (ajax) b.header("X-Requested-With", "XMLHttpRequest").header("Accept", "application/json, text/javascript, */*; q=0.01")
        else b.header("Accept", "text/html,application/xhtml+xml")
        exec(b.build())
    }

    /** POST формы. [fields] — пары, ключи могут повторяться (node_ids[]). */
    suspend fun post(path: String, fields: List<Pair<String, String>>, referer: String? = null): String =
        withContext(Dispatchers.IO) {
            val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
            val u = url(path)
            val origin = if (u.startsWith(SUPPORT)) SUPPORT else BASE
            val req = Request.Builder().url(u).post(form)
                .header("User-Agent", UA)
                .header("Accept-Language", "ru-RU,ru;q=0.9")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Origin", origin)
                .header("Referer", referer ?: "$origin/")
                .build()
            exec(req)
        }

    /** Удалить сессию (PHPSESSID) — FunPay выдаст новую по golden_key. */
    fun resetSession() = prefs.removeCookie("PHPSESSID")
}
