package lol.mrrabbitson.fpmini.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lol.mrrabbitson.fpmini.data.Prefs
import lol.mrrabbitson.fpmini.data.Repository
import lol.mrrabbitson.fpmini.data.ThemeMode
import lol.mrrabbitson.fpmini.data.ThemeSettings
import lol.mrrabbitson.fpmini.ui.CapsLabel
import lol.mrrabbitson.fpmini.ui.Stars
import lol.mrrabbitson.fpmini.ui.YmButton
import androidx.compose.foundation.layout.statusBarsPadding
import lol.mrrabbitson.fpmini.ui.theme.Ym
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    prefs: Prefs,
    repo: Repository,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onLoginWeb: () -> Unit,
) {
    val c = Ym.colors
    val theme by prefs.theme.collectAsState()
    val account by prefs.account.collectAsState()
    val scope = rememberCoroutineScope()
    var keyDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = c.text) }
            Text("Настройки", color = c.text, fontWeight = FontWeight.Black, fontSize = 24.sp)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {

            // ---------- аккаунт ----------
            Section("Аккаунт") {
                if (account.loggedIn) {
                    Text(account.name ?: "Вход выполнен", color = c.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    account.userId?.let { Text("ID $it", color = c.textMuted, fontSize = 14.sp) }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Выйти", color = Color(0xFFD9473F), fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { prefs.setGoldenKey(null); repo.clearCache() }.padding(vertical = 6.dp),
                    )
                } else {
                    Text(
                        "Каталог и лоты доступны без входа. Вход нужен, чтобы писать продавцам.",
                        color = c.textMuted, fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    YmButton("Войти через сайт FunPay", Modifier.fillMaxWidth(), onClick = onLoginWeb)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ввести golden_key вручную", color = c.accent, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { keyDialog = true }.padding(vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ключ хранится только на этом телефоне и отправляется только на funpay.com.",
                    color = c.textMuted, fontSize = 13.sp,
                )
            }

            // ---------- тема ----------
            Section("Тема") {
                Segmented(
                    listOf("Как в системе" to ThemeMode.SYSTEM, "Светлая" to ThemeMode.LIGHT, "Тёмная" to ThemeMode.DARK),
                    theme.mode,
                ) { m -> prefs.updateTheme { it.copy(mode = m) } }
                Spacer(Modifier.height(10.dp))
                SwitchRow("Чёрный фон (AMOLED)", theme.amoled) { v -> prefs.updateTheme { it.copy(amoled = v) } }
            }

            Section("Акцентный цвет") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ThemeSettings.PRESETS.forEach { col ->
                        val sel = col == theme.accent
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Color(col))
                                .border(if (sel) 3.dp else 0.dp, c.text, CircleShape)
                                .clickable { prefs.updateTheme { it.copy(accent = col) } },
                            contentAlignment = Alignment.Center,
                        ) { if (sel) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                HexField(theme.accent) { col -> prefs.updateTheme { it.copy(accent = col) } }
            }

            Section("Скругление углов · ${theme.radius} dp") {
                Slider(
                    value = theme.radius.toFloat(), onValueChange = { v -> prefs.updateTheme { it.copy(radius = v.toInt()) } },
                    valueRange = 0f..24f, steps = 23,
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
                )
            }

            Section("Размер текста · ${(theme.fontScale * 100).toInt()}%") {
                Slider(
                    value = theme.fontScale, onValueChange = { v -> prefs.updateTheme { it.copy(fontScale = (v * 20).toInt() / 20f) } },
                    valueRange = 0.85f..1.3f,
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
                )
            }

            Section("Предпросмотр") { Preview() }

            Text(
                "Сбросить тему", color = c.accent, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { prefs.updateTheme { ThemeSettings() } }.padding(vertical = 12.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "FPMini — неофициальный клиент. Не связан с FunPay. Покупка и оплата проходят на сайте funpay.com.",
                color = c.textMuted, fontSize = 13.sp,
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    if (keyDialog) {
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { keyDialog = false },
            title = { Text("golden_key") },
            text = {
                Column {
                    Text("Скопируйте куку golden_key из браузера, где вы вошли на funpay.com.", fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(key, { key = it.trim() }, singleLine = true, placeholder = { Text("32 символа") })
                }
            },
            confirmButton = {
                TextButton(enabled = key.length >= 16, onClick = {
                    keyDialog = false
                    prefs.setGoldenKey(key)
                    repo.clearCache()
                    scope.launch {
                        val ok = runCatching { repo.verifyLogin() }.getOrDefault(false)
                        if (!ok) prefs.setGoldenKey(null)
                        snackbar.showSnackbar(if (ok) "Вход выполнен" else "FunPay не принял ключ")
                    }
                }) { Text("Войти") }
            },
            dismissButton = { TextButton(onClick = { keyDialog = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 14.dp)) {
        CapsLabel(title)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    val c = Ym.colors
    Row(Modifier.fillMaxWidth().clip(Ym.shape).background(c.field).padding(4.dp)) {
        options.forEach { (label, v) ->
            val sel = v == selected
            Box(
                Modifier.weight(1f).clip(Ym.shape).background(if (sel) c.card else Color.Transparent)
                    .clickable { onSelect(v) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (sel) c.text else c.textMuted, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Ym.colors
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
    }
}

@Composable
private fun HexField(current: Long, onColor: (Long) -> Unit) {
    val c = Ym.colors
    var text by remember(current) { mutableStateOf(String.format("%06X", Color(current).toArgb() and 0xFFFFFF)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Свой цвет  #", color = c.text, fontSize = 16.sp)
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                if (text.length == 6) onColor(0xFF000000 or text.toLong(16))
            },
            singleLine = true,
            modifier = Modifier.width(140.dp),
        )
    }
}

@Composable
private fun Preview() {
    val c = Ym.colors
    Column(Modifier.fillMaxWidth().clip(Ym.shape).border(1.dp, c.border, Ym.shape).background(c.card).padding(16.dp)) {
        Text("Услуги Discord", color = c.accent, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Пример лота", color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("99 ₽", color = c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Онлайн", color = c.online, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(10.dp))
            Stars(5, size = 14.dp)
        }
        Spacer(Modifier.height(12.dp))
        YmButton("Купить", Modifier.fillMaxWidth()) {}
    }
}
