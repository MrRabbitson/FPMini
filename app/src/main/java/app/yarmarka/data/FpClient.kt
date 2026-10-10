package app.yarmarka.data

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

class FpException(message: String, val openInBrowser: Boolean = false) : IOException(message)

/** HTTP-клиент для funpay.com: свои куки + golden_key пользователя. */
class FpClient(private val prefs: Prefs) {

    companion object {
        const val BASE = "https://funpay.com"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"
    }

    private val jar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (!url.host.endsWith("funpay.com")) return
            cookies.forEach { prefs.saveCookie(it.name, if (it.expiresAt < System.currentTimeMillis()) null else it.value) }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            if (!url.host.endsWith("funpay.com")) return emptyList()
            val all = prefs.cookies().toMutableMap()
            prefs.account.value.goldenKey?.let { all["golden_key"] = it }
            return all.map { (k, v) -> Cookie.Builder().domain("funpay.com").path("/").name(k).value(v).build() }
        }
    }

    private val http = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun Request.Builder.common() = this
        .header("User-Agent", UA)
        .header("Accept-Language", "ru-RU,ru;q=0.9")

    suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val url = if (path.startsWith("http")) path else BASE + path
        val req = Request.Builder().url(url).common()
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        val resp = try {
            http.newCall(req).execute()
        } catch (e: IOException) {
            throw FpException("Нет соединения с FunPay. Проверьте интернет.")
        }
        resp.use {
            val body = it.body?.string().orEmpty()
            when {
                body.contains("cf-browser-verification") || body.contains("challenge-platform") ->
                    throw FpException("FunPay попросил проверку браузера. Откройте страницу в браузере, затем повторите.", openInBrowser = true)
                it.code == 404 -> throw FpException("Страница не найдена (404).")
                !it.isSuccessful -> throw FpException("FunPay ответил ошибкой ${it.code}.", openInBrowser = true)
            }
            body
        }
    }

    /** POST /runner/ — так сайт отправляет сообщения в чат. */
    suspend fun runner(objects: String, request: String, csrf: String): String = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("objects", objects)
            .add("request", request)
            .add("csrf_token", csrf)
            .build()
        val req = Request.Builder().url("$BASE/runner/").post(form).common()
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Origin", BASE)
            .header("Referer", "$BASE/")
            .build()
        val resp = try {
            http.newCall(req).execute()
        } catch (e: IOException) {
            throw FpException("Нет соединения с FunPay.")
        }
        resp.use {
            if (!it.isSuccessful) throw FpException("FunPay не принял запрос (${it.code}).")
            it.body?.string().orEmpty()
        }
    }
}
