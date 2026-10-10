package lol.mrrabbitson.fpmini.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.DARK,
    val amoled: Boolean = false,
    val accent: Long = DEFAULT_ACCENT,
    val radius: Int = 10,
    val fontScale: Float = 1f,
) {
    companion object {
        const val DEFAULT_ACCENT = 0xFF3F85D6
        val PRESETS = listOf(
            0xFF3F85D6, 0xFF7C4DFF, 0xFFE0457B, 0xFFEF6C00,
            0xFF2E9E5B, 0xFF00A3B4, 0xFFD32F2F, 0xFF455A64,
        )
    }
}

data class Account(val goldenKey: String?, val userId: Int?, val name: String?) {
    val loggedIn: Boolean get() = !goldenKey.isNullOrBlank()
}

/** Правило автоответа: если сообщение содержит любое из слов — отправить ответ. */
data class ReplyRule(val id: Long, val keywords: List<String>, val reply: String, val enabled: Boolean = true)

data class AutoSettings(
    val background: Boolean = false,
    val raise: Boolean = false,
    val raiseIntervalMin: Int = 60,
    val greet: Boolean = false,
    val greetText: String = "Здравствуйте! Получил ваше сообщение, скоро отвечу.",
    val rulesEnabled: Boolean = false,
    val rules: List<ReplyRule> = emptyList(),
    val notifyMessages: Boolean = true,
    val notifyOrders: Boolean = true,
) {
    val needsChats: Boolean get() = greet || rulesEnabled || notifyMessages
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("fpmini", Context.MODE_PRIVATE)
    private val cookiesSp = context.getSharedPreferences("fpmini_cookies", Context.MODE_PRIVATE)
    private val stateSp = context.getSharedPreferences("fpmini_state", Context.MODE_PRIVATE)

    // ---------- тема ----------

    private val _theme = MutableStateFlow(readTheme())
    val theme: StateFlow<ThemeSettings> = _theme.asStateFlow()

    private fun readTheme() = ThemeSettings(
        mode = runCatching { ThemeMode.valueOf(sp.getString("t_mode", null) ?: "DARK") }.getOrDefault(ThemeMode.DARK),
        amoled = sp.getBoolean("t_amoled", false),
        accent = sp.getLong("t_accent", ThemeSettings.DEFAULT_ACCENT),
        radius = sp.getInt("t_radius", 10),
        fontScale = sp.getFloat("t_font", 1f),
    )

    fun updateTheme(block: (ThemeSettings) -> ThemeSettings) {
        val t = block(_theme.value)
        sp.edit().putString("t_mode", t.mode.name).putBoolean("t_amoled", t.amoled)
            .putLong("t_accent", t.accent).putInt("t_radius", t.radius).putFloat("t_font", t.fontScale).apply()
        _theme.value = t
    }

    // ---------- аккаунт ----------

    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<Account> = _account.asStateFlow()

    private fun readAccount() = Account(
        goldenKey = sp.getString("golden_key", null),
        userId = sp.getInt("user_id", 0).takeIf { it > 0 },
        name = sp.getString("user_name", null),
    )

    fun setGoldenKey(key: String?) {
        sp.edit().putString("golden_key", key?.trim()?.ifBlank { null }).remove("user_id").remove("user_name").apply()
        cookiesSp.edit().clear().apply()
        stateSp.edit().clear().apply()
        _account.value = readAccount()
    }

    fun setMe(userId: Int?, name: String?) {
        val cur = _account.value
        if (cur.userId == userId && (name == null || cur.name == name)) return
        sp.edit().putInt("user_id", userId ?: 0).apply {
            if (name != null) putString("user_name", name)
        }.apply()
        _account.value = readAccount()
    }

    // ---------- автоматизация ----------

    private val _auto = MutableStateFlow(readAuto())
    val auto: StateFlow<AutoSettings> = _auto.asStateFlow()

    private fun readAuto(): AutoSettings {
        val rules = runCatching {
            val arr = JSONArray(sp.getString("a_rules", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val kw = o.getJSONArray("k")
                ReplyRule(
                    id = o.getLong("id"),
                    keywords = (0 until kw.length()).map { kw.getString(it) },
                    reply = o.getString("r"),
                    enabled = o.optBoolean("e", true),
                )
            }
        }.getOrDefault(emptyList())
        val d = AutoSettings()
        return AutoSettings(
            background = sp.getBoolean("a_bg", d.background),
            raise = sp.getBoolean("a_raise", d.raise),
            raiseIntervalMin = sp.getInt("a_raise_int", d.raiseIntervalMin),
            greet = sp.getBoolean("a_greet", d.greet),
            greetText = sp.getString("a_greet_text", d.greetText) ?: d.greetText,
            rulesEnabled = sp.getBoolean("a_rules_on", d.rulesEnabled),
            rules = rules,
            notifyMessages = sp.getBoolean("a_n_msg", d.notifyMessages),
            notifyOrders = sp.getBoolean("a_n_ord", d.notifyOrders),
        )
    }

    fun updateAuto(block: (AutoSettings) -> AutoSettings) {
        val a = block(_auto.value)
        val arr = JSONArray()
        a.rules.forEach { r ->
            arr.put(JSONObject().put("id", r.id).put("k", JSONArray(r.keywords)).put("r", r.reply).put("e", r.enabled))
        }
        sp.edit()
            .putBoolean("a_bg", a.background).putBoolean("a_raise", a.raise).putInt("a_raise_int", a.raiseIntervalMin)
            .putBoolean("a_greet", a.greet).putString("a_greet_text", a.greetText)
            .putBoolean("a_rules_on", a.rulesEnabled).putString("a_rules", arr.toString())
            .putBoolean("a_n_msg", a.notifyMessages).putBoolean("a_n_ord", a.notifyOrders)
            .apply()
        _auto.value = a
    }

    // ---------- состояние фоновой работы ----------

    fun getLong(key: String, def: Long = 0L) = stateSp.getLong(key, def)
    fun putLong(key: String, v: Long) = stateSp.edit().putLong(key, v).apply()
    fun getStringSet(key: String): Set<String> = stateSp.getStringSet(key, emptySet()) ?: emptySet()
    fun putStringSet(key: String, v: Set<String>) = stateSp.edit().putStringSet(key, v).apply()
    fun getString(key: String): String? = stateSp.getString(key, null)
    fun putString(key: String, v: String?) = stateSp.edit().putString(key, v).apply()

    // ---------- куки ----------
    // Ключ: "scope|name". scope — точный хост ("support.funpay.com") или "*домен" для кук на весь домен.

    /** Куки для запроса к хосту [host]. */
    fun cookies(host: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        cookiesSp.all.forEach { (k, v) ->
            if (v !is String) return@forEach
            val scope = k.substringBefore('|')
            val name = k.substringAfter('|')
            val match = if (scope.startsWith("*")) {
                val dom = scope.substring(1)
                host == dom || host.endsWith(".$dom")
            } else scope == host
            if (match) out[name] = v
        }
        return out
    }

    fun saveCookie(scope: String, name: String, value: String?) {
        if (name == "golden_key") return
        val key = "$scope|$name"
        cookiesSp.edit().apply { if (value.isNullOrEmpty() || value == "deleted") remove(key) else putString(key, value) }.apply()
    }

    fun removeCookie(name: String) {
        val e = cookiesSp.edit()
        cookiesSp.all.keys.filter { it.substringAfter('|') == name }.forEach { e.remove(it) }
        e.apply()
    }
}
