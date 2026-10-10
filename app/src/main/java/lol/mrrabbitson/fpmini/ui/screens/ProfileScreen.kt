package lol.mrrabbitson.fpmini.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import lol.mrrabbitson.fpmini.core.MyLotGroup
import lol.mrrabbitson.fpmini.core.Order
import lol.mrrabbitson.fpmini.core.ProfilePage
import lol.mrrabbitson.fpmini.data.Me
import lol.mrrabbitson.fpmini.data.Repository
import lol.mrrabbitson.fpmini.data.Stats
import lol.mrrabbitson.fpmini.ui.Avatar
import lol.mrrabbitson.fpmini.ui.Card
import lol.mrrabbitson.fpmini.ui.EmptyState
import lol.mrrabbitson.fpmini.ui.LoadBox
import lol.mrrabbitson.fpmini.ui.PrimaryButton
import lol.mrrabbitson.fpmini.ui.SectionLabel
import lol.mrrabbitson.fpmini.ui.TopBar
import lol.mrrabbitson.fpmini.ui.rememberLoad
import lol.mrrabbitson.fpmini.ui.theme.Ym
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class MyData(val me: Me, val profile: ProfilePage, val groups: List<MyLotGroup>)

private fun untilText(at: Long, now: Long = System.currentTimeMillis()): String {
    val min = ((at - now) / 60_000).coerceAtLeast(0)
    val h = min / 60; val m = min % 60
    return when {
        h >= 1 -> "через $h ч ${m} мин"
        else -> "через $m мин"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileScreen(repo: Repository, snackbar: SnackbarHostState, onLot: (offerId: String) -> Unit) {
    val c = Ym.colors
    val scope = rememberCoroutineScope()
    var raising by remember { mutableStateOf<Int?>(null) } // id игры или -1 для «все»
    var showAllUnlocks by remember { mutableStateOf(false) }
    val state = rememberLoad("me") {
        coroutineScope {
            val me = async { repo.me(force = true) }
            val p = async { repo.myProfile() }
            MyData(me.await(), p.await().first, p.await().second)
        }
    }
    // продажи — отдельно, их загрузка дольше
    val orders = rememberLoad("me_orders") { repo.allOrders() }

    fun raise(gameId: Int?, groups: List<MyLotGroup>) {
        raising = gameId ?: -1
        scope.launch {
            try {
                val text = if (gameId == null) {
                    val res = repo.raiseAll()
                    if (res.isEmpty()) "Нечего поднимать" else res.joinToString("\n") { (_, r) -> "${r.game}: ${r.message}" }
                } else {
                    val r = repo.raiseGame(gameId, groups.map { it.nodeId }, groups.joinToString(", ") { it.title })
                    "${r.game}: ${r.message}"
                }
                snackbar.showSnackbar(text)
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "Ошибка")
            } finally { raising = null }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Мой профиль")
        LoadBox(state) { d ->
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { state.refresh(); orders.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    // ---------- шапка ----------
                    item {
                        Card(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(d.profile.user.avatarUrl, 52.dp, d.profile.user.name)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(d.me.name ?: d.profile.user.name, color = c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        d.profile.rating?.value?.let {
                                            Icon(Icons.Filled.Star, null, tint = c.star, modifier = Modifier.size(14.dp))
                                            Text(" $it  ·  ", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Text(d.profile.reviewsTotal ?: d.profile.rating?.countText ?: "Нет отзывов", color = c.textMuted, fontSize = 13.sp)
                                    }
                                    Text("Активных продаж: ${d.me.activeSales ?: 0}", color = c.textMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // ---------- баланс и разблокировка ----------
                    item {
                        SectionLabel("Баланс", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
                        Card(Modifier.padding(horizontal = 12.dp)) {
                            val list = orders.data
                            val funds = remember(d.me.balance, list) { Stats.funds(d.me.balance, list.orEmpty()) }
                            Column(Modifier.padding(14.dp)) {
                                Text(d.me.balance ?: "—", color = c.text, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                Text("Всего на балансе", color = c.textMuted, fontSize = 12.sp)
                                Spacer(Modifier.height(8.dp))
                                if (list == null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(14.dp), color = c.accent, strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (orders.error != null) "Не удалось загрузить продажи" else "Считаю замороженные средства…", color = c.textMuted, fontSize = 13.sp)
                                    }
                                } else Row {
                                    StatCell("Доступно", funds.available?.let { Stats.money(it) } ?: "—", null, Modifier.weight(1f), c.online)
                                    StatCell("Заморожено", Stats.money(funds.frozen), "${funds.unlocks.size} заказ(ов)", Modifier.weight(1f), c.accent)
                                }
                            }
                            val unlocks = orders.data?.let { Stats.funds(d.me.balance, it).unlocks }.orEmpty()
                            if (unlocks.isNotEmpty()) {
                                HorizontalDivider(color = c.divider)
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                    Text("Разблокировка", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    val fmt = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))
                                    (if (showAllUnlocks) unlocks else unlocks.take(4)).forEach { u ->
                                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("+${Stats.money(u.amount)}", color = c.online, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.width(96.dp))
                                            Text("#${u.orderId}", color = c.textMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(untilText(u.at), color = c.text, fontSize = 12.sp)
                                                Text(fmt.format(Date(u.at)), color = c.textMuted, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    if (unlocks.size > 4) Text(
                                        if (showAllUnlocks) "Свернуть" else "Показать все (${unlocks.size})",
                                        color = c.accent, fontSize = 13.sp,
                                        modifier = Modifier.clickable { showAllUnlocks = !showAllUnlocks }.padding(vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }

                    // ---------- заработок ----------
                    orders.data?.let { list ->
                        item { Earnings(list) }
                    }

                    // ---------- лоты ----------
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionLabel("Мои лоты · ${d.groups.sumOf { it.offers.size }}", Modifier.weight(1f))
                            PrimaryButton(if (raising == -1) "Поднимаю…" else "Поднять все", enabled = raising == null) { raise(null, d.groups) }
                        }
                    }
                    if (d.groups.isEmpty()) item { EmptyState("У вас пока нет лотов") }
                    d.groups.forEach { g ->
                        item(key = "g${g.type}${g.nodeId}") {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(g.title, color = c.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Text("${g.offers.size}", color = c.textMuted, fontSize = 13.sp)
                                if (g.type == "lots" && g.gameId != null) {
                                    val gid = g.gameId!!
                                    Text(
                                        if (raising == gid) "…" else "Поднять",
                                        color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = 8.dp).clip(Ym.shape).clickable(enabled = raising == null) {
                                            raise(gid, d.groups.filter { it.gameId == gid && it.type == "lots" })
                                        }.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        items(g.offers, key = { "o${g.type}${it.id}" }) { o ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = o.type == "lots") { onLot(o.id) }
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(o.description.ifBlank { "Без описания" }, color = c.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    val extra = listOfNotNull(o.server, o.amount).joinToString(" · ")
                                    if (extra.isNotBlank()) Text(extra, color = c.textMuted, fontSize = 12.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(o.priceText, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                if (o.type == "lots") Text("  ✎", color = c.textMuted, fontSize = 14.sp)
                            }
                            HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/** Статистика заработка: суммы за периоды и график по дням. */
@Composable
private fun Earnings(orders: List<Order>) {
    val c = Ym.colors
    val closed = remember(orders) { orders.filter { it.status == lol.mrrabbitson.fpmini.core.OrderStatus.CLOSED } }
    fun sum(from: Long?) = closed.filter { Stats.inRange(it, from, null) }.sumOf { it.priceValue ?: 0.0 }
    val days = remember(orders) { Stats.byDay(orders, 14) }
    val max = days.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val dayFmt = SimpleDateFormat("d.MM", Locale("ru"))

    SectionLabel("Заработок", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
    Card(Modifier.padding(horizontal = 12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row {
                StatCell("Сегодня", Stats.money(sum(Stats.startOfDay(0))), modifier = Modifier.weight(1f))
                StatCell("7 дней", Stats.money(sum(Stats.startOfDay(6))), modifier = Modifier.weight(1f))
            }
            Row {
                StatCell("30 дней", Stats.money(sum(Stats.startOfDay(29))), modifier = Modifier.weight(1f))
                StatCell("Всего", Stats.money(sum(null)), "${closed.size} закрытых заказов", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text("По дням, последние 2 недели", color = c.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            val bar = c.accent; val empty = c.field
            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                val n = days.size
                val gap = 4.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                days.forEachIndexed { i, (_, v) ->
                    val h = if (v <= 0) 3.dp.toPx() else (size.height * (v / max).toFloat()).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        color = if (v <= 0) empty else bar,
                        topLeft = Offset(i * (w + gap), size.height - h),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dayFmt.format(Date(days.first().first)), color = c.textMuted, fontSize = 10.sp)
                Text("макс. ${Stats.money(max)}".replaceFirstChar { it.uppercase() }, color = c.textMuted, fontSize = 10.sp)
                Text("Сегодня", color = c.textMuted, fontSize = 10.sp)
            }
        }
    }
}
