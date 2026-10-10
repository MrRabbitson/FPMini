package lol.mrrabbitson.fpmini.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import lol.mrrabbitson.fpmini.core.MyLotGroup
import lol.mrrabbitson.fpmini.core.Order
import lol.mrrabbitson.fpmini.core.OrderStatus
import lol.mrrabbitson.fpmini.core.ProfilePage
import lol.mrrabbitson.fpmini.data.Me
import lol.mrrabbitson.fpmini.data.Repository
import lol.mrrabbitson.fpmini.ui.Avatar
import lol.mrrabbitson.fpmini.ui.Card
import lol.mrrabbitson.fpmini.ui.Chip
import lol.mrrabbitson.fpmini.ui.EmptyState
import lol.mrrabbitson.fpmini.ui.LoadBox
import lol.mrrabbitson.fpmini.ui.PrimaryButton
import lol.mrrabbitson.fpmini.ui.SecondaryButton
import lol.mrrabbitson.fpmini.ui.SectionLabel
import lol.mrrabbitson.fpmini.ui.TopBar
import lol.mrrabbitson.fpmini.ui.rememberLoad
import lol.mrrabbitson.fpmini.ui.theme.Ym
import java.util.Locale

private data class MyData(val me: Me, val profile: ProfilePage, val groups: List<MyLotGroup>)

// ===================== Мой профиль =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileScreen(repo: Repository, snackbar: SnackbarHostState, onSettings: () -> Unit, onLot: (type: String, id: String) -> Unit) {
    val c = Ym.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var raising by remember { mutableStateOf<Int?>(null) } // id игры или -1 для «все»
    val state = rememberLoad("me") {
        coroutineScope {
            val me = async { repo.me(force = true) }
            val p = async { repo.myProfile() }
            MyData(me.await(), p.await().first, p.await().second)
        }
    }

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
        TopBar("Мой профиль", actions = {
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Настройки", tint = c.text) }
        })
        LoadBox(state) { d ->
            PullToRefreshBox(isRefreshing = state.loading, onRefresh = { state.refresh() }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Card(Modifier.padding(12.dp)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(d.profile.user.avatarUrl, 52.dp, d.profile.user.name)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(d.me.name ?: d.profile.user.name, color = c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        d.profile.rating?.value?.let {
                                            Icon(Icons.Filled.Star, null, tint = c.star, modifier = Modifier.size(14.dp))
                                            Text(" $it", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text("  ·  ", color = c.textMuted, fontSize = 13.sp)
                                        }
                                        Text(d.profile.reviewsTotal ?: d.profile.rating?.countText ?: "Нет отзывов", color = c.textMuted, fontSize = 13.sp)
                                    }
                                }
                            }
                            HorizontalDivider(color = c.divider)
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Stat("Баланс", d.me.balance ?: "—", Modifier.weight(1f))
                                Stat("Активные продажи", d.me.activeSales?.toString() ?: "0", Modifier.weight(1f))
                                Stat("Лотов", d.groups.sumOf { it.offers.size }.toString(), Modifier.weight(1f))
                            }
                            HorizontalDivider(color = c.divider)
                            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton(if (raising == -1) "Поднимаю…" else "Поднять все лоты", Modifier.weight(1f), enabled = raising == null) {
                                    raise(null, d.groups)
                                }
                            }
                        }
                    }
                    item {
                        Card(Modifier.padding(horizontal = 12.dp)) {
                            Row(
                                Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Settings, null, tint = c.accent, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Настройки и темы", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text("Цвета, тёмная тема, скругление, размер текста, выход", color = c.textMuted, fontSize = 12.sp)
                                }
                                Text("›", color = c.textMuted, fontSize = 20.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    if (d.groups.isEmpty()) item { EmptyState("У вас пока нет лотов") }
                    d.groups.forEach { g ->
                        item(key = "g${g.type}${g.nodeId}") {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(g.title, color = c.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Text("${g.offers.size}", color = c.textMuted, fontSize = 13.sp)
                                if (g.type == "lots" && g.gameId != null) {
                                    Spacer(Modifier.width(8.dp))
                                    val gid = g.gameId!!
                                    Text(
                                        if (raising == gid) "…" else "Поднять",
                                        color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clip(Ym.shape).clickable(enabled = raising == null) {
                                            raise(gid, d.groups.filter { it.gameId == gid && it.type == "lots" })
                                        }.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        items(g.offers, key = { "o${g.type}${it.id}" }) { o ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { onLot(o.type, o.id) }
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(o.description.ifBlank { "Без описания" }, color = c.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    val extra = listOfNotNull(o.server, o.amount).joinToString(" · ")
                                    if (extra.isNotBlank()) Text(extra, color = c.textMuted, fontSize = 12.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(o.priceText, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = Ym.colors
    Column(modifier) {
        Text(value, color = c.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        Text(label, color = c.textMuted, fontSize = 11.sp, maxLines = 1)
    }
}

// ===================== Мои продажи =====================

private fun statusColor(s: OrderStatus, c: lol.mrrabbitson.fpmini.ui.theme.YmColors): Color = when (s) {
    OrderStatus.PAID -> c.accent
    OrderStatus.CLOSED -> c.online
    OrderStatus.REFUND -> Color(0xFFE5534B)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(repo: Repository, onOrder: (id: String) -> Unit) {
    val c = Ym.colors
    val ctx = LocalContext.current
    var filter by rememberSaveable { mutableStateOf<String?>(null) } // null | PAID | CLOSED | REFUND
    val state = rememberLoad("orders") { repo.orders() }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Мои продажи")
        LoadBox(state) { all ->
            val shown = remember(all, filter) { if (filter == null) all else all.filter { it.status.name == filter } }
            PullToRefreshBox(isRefreshing = state.loading, onRefresh = { state.refresh() }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        val paid = all.filter { it.status == OrderStatus.PAID }
                        val sum = paid.sumOf { it.priceValue ?: 0.0 }
                        Card(Modifier.padding(12.dp)) {
                            Row(Modifier.padding(14.dp)) {
                                Stat("Ждут выполнения", paid.size.toString(), Modifier.weight(1f))
                                Stat("На сумму", String.format(Locale.US, "%.2f ₽", sum).replace(".00 ", " "), Modifier.weight(1f))
                                Stat("Всего на странице", all.size.toString(), Modifier.weight(1f))
                            }
                        }
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(null to "Все", "PAID" to "Оплачены", "CLOSED" to "Закрыты", "REFUND" to "Возвраты").forEach { (k, label) ->
                                val sel = filter == k
                                Text(
                                    label, color = if (sel) c.onAccent else c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clip(Ym.shape).background(if (sel) c.accent else c.field)
                                        .clickable { filter = k }.padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    if (shown.isEmpty()) item { EmptyState("Заказов нет") }
                    items(shown, key = { it.id }) { o ->
                        OrderRow(o) { onOrder(o.id) }
                        HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 16.dp))
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun OrderRow(o: Order, onClick: () -> Unit) {
    val c = Ym.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${o.id}", color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                Chip(o.statusText.replaceFirstChar { it.uppercase() }, statusColor(o.status, c))
                Spacer(Modifier.weight(1f))
                Text(o.date, color = c.textMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(o.description.replaceFirstChar { it.uppercase() }, color = c.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(o.category.ifBlank { null }, o.buyer?.name?.let { "Покупатель: $it" }).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Text(o.priceText, color = c.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Suppress("unused")
@Composable
private fun SmallSpinner() = CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
