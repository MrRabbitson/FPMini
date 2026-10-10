package lol.mrrabbitson.fpmini.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import lol.mrrabbitson.fpmini.auto.AutomationService
import lol.mrrabbitson.fpmini.auto.EventKind
import lol.mrrabbitson.fpmini.auto.EventLog
import lol.mrrabbitson.fpmini.data.Prefs
import lol.mrrabbitson.fpmini.data.ReplyRule
import lol.mrrabbitson.fpmini.data.Repository
import lol.mrrabbitson.fpmini.ui.Card
import lol.mrrabbitson.fpmini.ui.Dot
import lol.mrrabbitson.fpmini.ui.PrimaryButton
import lol.mrrabbitson.fpmini.ui.SecondaryButton
import lol.mrrabbitson.fpmini.ui.SectionLabel
import lol.mrrabbitson.fpmini.ui.TopBar
import lol.mrrabbitson.fpmini.ui.theme.Ym

@SuppressLint("BatteryLife")
@Composable
fun AutomationScreen(prefs: Prefs, repo: Repository, log: EventLog, snackbar: SnackbarHostState) {
    val c = Ym.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val a by prefs.auto.collectAsState()
    val status by log.status.collectAsState()
    val events by log.events.collectAsState()
    var editRule by remember { mutableStateOf<ReplyRule?>(null) }
    var raisingNow by remember { mutableStateOf(false) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun setBackground(on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        prefs.updateAuto { it.copy(background = on) }
        AutomationService.sync(ctx)
    }

    val pm = ctx.getSystemService(PowerManager::class.java)
    val batteryRestricted = Build.VERSION.SDK_INT >= 23 && pm?.isIgnoringBatteryOptimizations(ctx.packageName) == false

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Автоматизация")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            item {
                Card {
                    SwitchRow(
                        "Работа в фоне",
                        if (a.background) status else "Выключено — автоответы и поднятие не работают",
                        a.background, ::setBackground,
                    )
                    if (a.background && batteryRestricted) {
                        HorizontalDivider(color = c.divider)
                        Text(
                            "Android может останавливать фон для экономии батареи. Нажмите, чтобы разрешить работу без ограничений.",
                            color = Color(0xFFE59A1D), fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                runCatching {
                                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                                }
                            }.padding(14.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---------- поднятие ----------
            item {
                SectionLabel("Поднятие лотов", Modifier.padding(start = 4.dp, bottom = 6.dp))
                Card {
                    SwitchRow("Автоподнятие", "Поднимает все ваши лоты, когда FunPay разрешает", a.raise) { v ->
                        prefs.updateAuto { it.copy(raise = v) }
                    }
                    HorizontalDivider(color = c.divider)
                    Column(Modifier.padding(14.dp)) {
                        Text("Не чаще, чем раз в", color = c.text, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(30 to "30 мин", 60 to "1 ч", 120 to "2 ч", 240 to "4 ч").forEach { (m, label) ->
                                val sel = a.raiseIntervalMin == m
                                Text(
                                    label, color = if (sel) c.onAccent else c.text, fontSize = 13.sp,
                                    modifier = Modifier.clip(Ym.shape).background(if (sel) c.accent else c.field)
                                        .clickable { prefs.updateAuto { it.copy(raiseIntervalMin = m) } }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton(if (raisingNow) "Поднимаю…" else "Поднять сейчас", enabled = !raisingNow) {
                            raisingNow = true
                            scope.launch {
                                try {
                                    val res = repo.raiseAll()
                                    res.forEach { (_, r) -> log.add(if (r.ok) EventKind.OK else EventKind.INFO, "${r.game}: ${r.message}") }
                                    snackbar.showSnackbar(if (res.isEmpty()) "Нечего поднимать" else res.joinToString("\n") { (_, r) -> "${r.game}: ${r.message}" })
                                } catch (e: Exception) {
                                    snackbar.showSnackbar(e.message ?: "Ошибка")
                                } finally { raisingNow = false }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---------- автоответы ----------
            item {
                SectionLabel("Автоответы", Modifier.padding(start = 4.dp, bottom = 6.dp))
                Card {
                    SwitchRow("Приветствие", "Новому покупателю, если вы ещё не писали в этот чат", a.greet) { v ->
                        prefs.updateAuto { it.copy(greet = v) }
                    }
                    if (a.greet) {
                        var greet by remember(a.greetText) { mutableStateOf(a.greetText) }
                        OutlinedTextField(
                            value = greet,
                            onValueChange = { greet = it; prefs.updateAuto { s -> s.copy(greetText = it) } },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                            label = { Text("Текст приветствия") },
                        )
                    }
                    HorizontalDivider(color = c.divider)
                    SwitchRow("Ответы по ключевым словам", "Если сообщение содержит слово — отправить ответ", a.rulesEnabled) { v ->
                        prefs.updateAuto { it.copy(rulesEnabled = v) }
                    }
                    a.rules.forEach { r ->
                        HorizontalDivider(color = c.divider)
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.keywords.joinToString(", "), color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("→ ${r.reply}", color = c.textMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Switch(
                                checked = r.enabled,
                                onCheckedChange = { v -> prefs.updateAuto { s -> s.copy(rules = s.rules.map { if (it.id == r.id) it.copy(enabled = v) else it }) } },
                                colors = SwitchDefaults.colors(checkedTrackColor = c.accent),
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            IconButton(onClick = { editRule = r }) { Icon(Icons.Filled.Edit, "Изменить", tint = c.textMuted, modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = { prefs.updateAuto { s -> s.copy(rules = s.rules.filter { it.id != r.id }) } }) {
                                Icon(Icons.Filled.Delete, "Удалить", tint = c.textMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = c.divider)
                    Text(
                        "+ Добавить правило", color = c.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clickable { editRule = ReplyRule(System.currentTimeMillis(), emptyList(), "") }.padding(14.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---------- уведомления ----------
            item {
                SectionLabel("Уведомления", Modifier.padding(start = 4.dp, bottom = 6.dp))
                Card {
                    SwitchRow("Новые сообщения", null, a.notifyMessages) { v -> prefs.updateAuto { it.copy(notifyMessages = v) } }
                    HorizontalDivider(color = c.divider)
                    SwitchRow("Новые заказы", null, a.notifyOrders) { v -> prefs.updateAuto { it.copy(notifyOrders = v) } }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ---------- журнал ----------
            item {
                Row(Modifier.padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Журнал", Modifier.weight(1f))
                    if (events.isNotEmpty()) Text("Очистить", color = c.accent, fontSize = 12.sp, modifier = Modifier.clickable { log.clear() })
                }
            }
            if (events.isEmpty()) item {
                Text("Пока пусто", color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(4.dp))
            }
            items(events.take(60)) { e ->
                Row(Modifier.padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    Text(e.time, color = c.textMuted, fontSize = 11.sp, modifier = Modifier.width(56.dp))
                    Dot(
                        when (e.kind) {
                            EventKind.OK -> c.online
                            EventKind.WARN -> Color(0xFFE59A1D)
                            EventKind.ERROR -> Color(0xFFE5534B)
                            EventKind.INFO -> c.textMuted
                        }, 6.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(e.text, color = c.text, fontSize = 12.sp)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editRule?.let { r ->
        var kw by remember(r.id) { mutableStateOf(r.keywords.joinToString(", ")) }
        var reply by remember(r.id) { mutableStateOf(r.reply) }
        AlertDialog(
            onDismissRequest = { editRule = null },
            title = { Text("Правило автоответа") },
            text = {
                Column {
                    OutlinedTextField(kw, { kw = it }, label = { Text("Ключевые слова через запятую") }, placeholder = { Text("цена, сколько, есть ли") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(reply, { reply = it }, label = { Text("Ответ") }, minLines = 3)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = kw.isNotBlank() && reply.isNotBlank(),
                    onClick = {
                        val rule = r.copy(keywords = kw.split(',').map { it.trim() }.filter { it.isNotEmpty() }, reply = reply.trim())
                        prefs.updateAuto { s ->
                            s.copy(rules = if (s.rules.any { it.id == rule.id }) s.rules.map { if (it.id == rule.id) rule else it } else s.rules + rule)
                        }
                        editRule = null
                    },
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editRule = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Ym.colors
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = c.textMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
    }
}

@Suppress("unused")
@Composable
private fun Unused() = PrimaryButton("") {}
