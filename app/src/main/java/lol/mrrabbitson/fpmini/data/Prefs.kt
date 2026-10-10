package lol.mrrabbitson.fpmini.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val accent: Long = DEFAULT_ACCENT,
    val radius: Int = 8,          // скругление карточек и кнопок, dp
    val fontScale: Float = 1f,    // масштаб текста внутри приложения
) {
    companion object {
        const val DEFAULT_ACCENT = 0xFF3F85D6
        val PRESETS = listOf(
            0xFF3F85D6, // синий
            0xFF7C4DFF, // фиолетовый
            0xFFE0457B, // малиновый
            0xFFEF6C00, // оранжевый
            0xFF2E9E5B, // зелёный
            0xFF00A3B4, // бирюзовый
            0xFFD32F2F, // красный
            0xFF455A64, // графит
        )
    }
}

data class Account(
    val goldenKey: String?,
    val userId: Int?,
    val name: String?,
) {
    val loggedIn: Boolean get() = !goldenKey.isNullOrBlank()
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("fpmini", Context.MODE_PRIVATE)
    private val cookiesSp = context.getSharedPreferences("fpmini_cookies", Context.MODE_PRIVATE)

    // ---------- тема ----------

    private val _theme = MutableStateFlow(readTheme())
    val theme: StateFlow<ThemeSettings> = _theme.asStateFlow()

    private fun readTheme() = ThemeSettings(
        mode = runCatching { ThemeMode.valueOf(sp.getString("t_mode", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
        amoled = sp.getBoolean("t_amoled", false),
        accent = sp.getLong("t_accent", ThemeSettings.DEFAULT_ACCENT),
        radius = sp.getInt("t_radius", 8),
        fontScale = sp.getFloat("t_font", 1f),
    )

    fun updateTheme(block: (ThemeSettings) -> ThemeSettings) {
        val t = block(_theme.value)
        sp.edit()
            .putString("t_mode", t.mode.name)
            .putBoolean("t_amoled", t.amoled)
            .putLong("t_accent", t.accent)
            .putInt("t_radius", t.radius)
            .putFloat("t_font", t.fontScale)
            .apply()
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
        sp.edit().putString("golden_key", key?.trim()?.ifBlank { null })
            .remove("user_id").remove("user_name").apply()
        cookiesSp.edit().clear().apply()
        _account.value = readAccount()
    }

    fun setMe(userId: Int?, name: String?) {
        val cur = _account.value
        if (cur.userId == userId && cur.name == name) return
        sp.edit().putInt("user_id", userId ?: 0).putString("user_name", name).apply()
        _account.value = readAccount()
    }

    // ---------- избранные игры ----------

    private val _favorites = MutableStateFlow(sp.getStringSet("fav_games", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet())
    val favorites: StateFlow<Set<Int>> = _favorites.asStateFlow()

    fun toggleFavorite(gameId: Int) {
        val next = _favorites.value.let { if (gameId in it) it - gameId else it + gameId }
        sp.edit().putStringSet("fav_games", next.map { it.toString() }.toSet()).apply()
        _favorites.value = next
    }

    // ---------- куки FunPay (PHPSESSID, golden_seal и т.п.) ----------

    fun cookies(): Map<String, String> =
        cookiesSp.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    fun saveCookie(name: String, value: String?) {
        if (name == "golden_key") return // ключ хранится отдельно
        cookiesSp.edit().apply {
            if (value.isNullOrEmpty() || value == "deleted") remove(name) else putString(name, value)
        }.apply()
    }
}
