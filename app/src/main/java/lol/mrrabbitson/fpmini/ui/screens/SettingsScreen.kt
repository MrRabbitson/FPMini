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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lol.mrrabbitson.fpmini.App
import lol.mrrabbitson.fpmini.auto.AutomationService
import lol.mrrabbitson.fpmini.data.ThemeMode
import lol.mrrabbitson.fpmini.data.ThemeSettings
import lol.mrrabbitson.fpmini.ui.Card
import lol.mrrabbitson.fpmini.ui.Chip
import lol.mrrabbitson.fpmini.ui.PrimaryButton
import lol.mrrabbitson.fpmini.ui.SectionLabel
import lol.mrrabbitson.fpmini.ui.TopBar
import lol.mrrabbitson.fpmini.ui.YmSwitch
import lol.mrrabbitson.fpmini.ui.theme.Ym

@Composable
fun SettingsScreen(app: App, onBack: () -> Unit) {
    val c = Ym.colors
    val ctx = LocalContext.current
    val prefs = app.prefs
    val theme by prefs.theme.collectAsState()
    val account by prefs.account.collectAsState()

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Настройки", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {

            SectionLabel("Аккаунт", Modifier.padding(start = 4.dp, bottom = 6.dp))
            Card {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(account.name ?: "Аккаунт FunPay", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        account.userId?.let { Text("ID $it", color = c.textMuted, fontSize = 12.sp) }
                    }
                    Text(
                        "Выйти", color = Color(0xFFE5534B), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            prefs.updateAuto { it.copy(background = false) }
                            AutomationService.stop(ctx)
                            prefs.setGoldenKey(null)
                            app.repo.clear()
                        }.padding(6.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            SectionLabel("Тема", Modifier.padding(start = 4.dp, bottom = 6.dp))
            Card {
                Column(Modifier.padding(14.dp)) {
                    Segmented(
                        listOf("Системная" to ThemeMode.SYSTEM, "Светлая" to ThemeMode.LIGHT, "Тёмная" to ThemeMode.DARK),
                        theme.mode,
                    ) { m -> prefs.updateTheme { it.copy(mode = m) } }
                }
                HorizontalDivider(color = c.divider)
                Row(
                    Modifier.fillMaxWidth().clickable { prefs.updateTheme { it.copy(amoled = !it.amoled) } }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Чёрный фон (AMOLED)", color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    YmSwitch(theme.amoled, { v -> prefs.updateTheme { it.copy(amoled = v) } })
                }
                HorizontalDivider(color = c.divider)
                Column(Modifier.padding(14.dp)) {
                    Text("Акцентный цвет", color = c.text, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemeSettings.PRESETS.forEach { col ->
                            val sel = col == theme.accent
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(Color(col))
                                    .border(if (sel) 2.dp else 0.dp, c.text, CircleShape)
                                    .clickable { prefs.updateTheme { it.copy(accent = col) } },
                                contentAlignment = Alignment.Center,
                            ) { if (sel) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HexField(theme.accent) { col -> prefs.updateTheme { it.copy(accent = col) } }
                }
                HorizontalDivider(color = c.divider)
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("Скругление углов · ${theme.radius} dp", color = c.text, fontSize = 14.sp)
                    Slider(
                        value = theme.radius.toFloat(), onValueChange = { v -> prefs.updateTheme { it.copy(radius = v.toInt()) } },
                        valueRange = 0f..20f, steps = 19,
                        colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
                    )
                    Text("Размер текста · ${(theme.fontScale * 100).toInt()}%", color = c.text, fontSize = 14.sp)
                    Slider(
                        value = theme.fontScale, onValueChange = { v -> prefs.updateTheme { it.copy(fontScale = (v * 20).toInt() / 20f) } },
                        valueRange = 0.85f..1.2f,
                        colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
                    )
                }
                HorizontalDivider(color = c.divider)
                // предпросмотр
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip("Оплачен", c.accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Пример заказа", color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    PrimaryButton("Кнопка") {}
                }
                HorizontalDivider(color = c.divider)
                Text(
                    "Сбросить тему", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().clickable { prefs.updateTheme { ThemeSettings() } }.padding(14.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "FPMini — неофициальный бесплатный клиент для продавцов FunPay. Не связан с FunPay. " +
                    "Ключ входа хранится только на этом телефоне и отправляется только на funpay.com.",
                color = c.textMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    val c = Ym.colors
    Row(Modifier.fillMaxWidth().clip(Ym.shape).background(c.field).padding(3.dp)) {
        options.forEach { (label, v) ->
            val sel = v == selected
            Box(
                Modifier.weight(1f).clip(Ym.shape).background(if (sel) c.card else Color.Transparent)
                    .clickable { onSelect(v) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (sel) c.text else c.textMuted, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun HexField(current: Long, onColor: (Long) -> Unit) {
    val c = Ym.colors
    var text by remember(current) { mutableStateOf(String.format("%06X", Color(current).toArgb() and 0xFFFFFF)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Свой цвет  #", color = c.text, fontSize = 14.sp)
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                if (text.length == 6) onColor(0xFF000000 or text.toLong(16))
            },
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp),
            modifier = Modifier.width(130.dp),
        )
    }
}
