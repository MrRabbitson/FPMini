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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lol.mrrabbitson.fpmini.core.Order
import lol.mrrabbitson.fpmini.core.OrderStatus
import lol.mrrabbitson.fpmini.data.SalesStore
import androidx.compose.runtime.collectAsState
import lol.mrrabbitson.fpmini.data.Stats
import lol.mrrabbitson.fpmini.ui.Card
import lol.mrrabbitson.fpmini.ui.Chip
import lol.mrrabbitson.fpmini.ui.EmptyState
import lol.mrrabbitson.fpmini.ui.ErrorBox
import lol.mrrabbitson.fpmini.ui.SectionLabel
import lol.mrrabbitson.fpmini.ui.TopBar
import lol.mrrabbitson.fpmini.ui.rememberLoad
import lol.mrrabbitson.fpmini.ui.theme.Ym
import lol.mrrabbitson.fpmini.ui.theme.YmColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun statusColor(s: OrderStatus, c: YmColors): Color = when (s) {
    OrderStatus.PAID -> c.accent
    OrderStatus.CLOSED -> c.online
    OrderStatus.REFUND -> Color(0xFFE5534B)
}

private enum class Period(val label: String) { TODAY("Сегодня"), WEEK("7 дней"), MONTH("30 дней"), ALL("Всё время"), CUSTOM("Свой период") }

@Composable
internal fun Chips(options: List<Pair<String, Boolean>>, onClick: (Int) -> Unit) {
    val c = Ym.colors
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { i, (label, sel) ->
            Text(
                label, color = if (sel) c.onAccent else c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(Ym.shape).background(if (sel) c.accent else c.field)
                    .clickable { onClick(i) }.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
internal fun StatCell(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier, color: Color? = null) {
    val c = Ym.colors
    Column(modifier.padding(vertical = 6.dp)) {
        Text(label, color = c.textMuted, fontSize = 12.sp, maxLines = 1)
        Text(value, color = color ?: c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
        if (sub != null) Text(sub, color = c.textMuted, fontSize = 11.sp, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(store: SalesStore, onOrder: (id: String) -> Unit) {
    val c = Ym.colors
    val st by store.state.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf(Period.ALL) }
    var customFrom by rememberSaveable { mutableStateOf<Long?>(null) }
    var customTo by rememberSaveable { mutableStateOf<Long?>(null) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var pickRange by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Мои продажи", actions = {
            IconButton(onClick = { store.refresh() }) { Icon(Icons.Filled.Refresh, "Обновить", tint = c.text) }
        })

        val all = st.orders
        when {
            all == null && st.error == null -> Column(
                Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = c.accent, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.height(10.dp))
                Text(if (st.progress > 0) "Загружено заказов: ${st.progress}" else "Загружаю продажи…", color = c.textMuted, fontSize = 13.sp)
            }
            all == null -> ErrorBox(st.error) { store.refresh() }
            else -> {
                val (from, to) = when (period) {
                    Period.TODAY -> Stats.startOfDay(0) to null
                    Period.WEEK -> Stats.startOfDay(6) to null
                    Period.MONTH -> Stats.startOfDay(29) to null
                    Period.ALL -> null to null
                    Period.CUSTOM -> customFrom to customTo
                }
                val q = query.trim().lowercase()
                val inPeriod = remember(all, from, to) { all.filter { Stats.inRange(it, from, to) } }
                val found = remember(inPeriod, q) {
                    if (q.isEmpty()) inPeriod else inPeriod.filter { o ->
                        o.description.lowercase().contains(q) || o.game.lowercase().contains(q) ||
                            o.subcategory.lowercase().contains(q) || o.buyer?.name?.lowercase()?.contains(q) == true ||
                            o.id.lowercase().contains(q)
                    }
                }
                val stats = remember(found) { Stats.sales(found) }
                val shown = remember(found, status) { if (status == null) found else found.filter { it.status.name == status } }

                PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = { store.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            SearchBox(query, "Название, игра, раздел или ник покупателя") { query = it }
                            Chips(Period.entries.map { p ->
                                val label = if (p == Period.CUSTOM && customFrom != null) rangeLabel(customFrom, customTo) else p.label
                                label to (period == p)
                            }) { i ->
                                val p = Period.entries[i]
                                if (p == Period.CUSTOM) pickRange = true else period = p
                            }
                            Spacer(Modifier.height(10.dp))
                            Card(Modifier.padding(horizontal = 12.dp)) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                    Row {
                                        StatCell("Заработано", Stats.money(stats.earned), "${stats.earnedCount} закрыто", Modifier.weight(1f), c.online)
                                        StatCell("В работе", Stats.money(stats.inWork), "${stats.inWorkCount} оплачено", Modifier.weight(1f), c.accent)
                                    }
                                    Row {
                                        StatCell("Возвраты", Stats.money(stats.refunds), "${stats.refundCount} шт.", Modifier.weight(1f))
                                        StatCell("Средний чек", Stats.money(stats.average), "${stats.count} заказов всего", Modifier.weight(1f))
                                    }
                                    if (stats.top.isNotEmpty()) {
                                        HorizontalDivider(color = c.divider, modifier = Modifier.padding(vertical = 6.dp))
                                        Text("Больше всего заработано", color = c.textMuted, fontSize = 12.sp)
                                        stats.top.forEach { (name, sum) ->
                                            Row(Modifier.padding(vertical = 2.dp)) {
                                                Text(name, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                Text(Stats.money(sum), color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Chips(listOf(null to "Все", "PAID" to "Оплачены", "CLOSED" to "Закрыты", "REFUND" to "Возвраты").map { (k, l) -> l to (status == k) }) { i ->
                                status = listOf(null, "PAID", "CLOSED", "REFUND")[i]
                            }
                            Text(
                                "Показано ${shown.size} из ${all.size}" + if (st.loading) " · обновляю… ${st.progress}" else "", color = c.textMuted, fontSize = 12.sp,
                                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
                            )
                        }
                        if (shown.isEmpty()) item { EmptyState("Ничего не найдено") }
                        items(shown, key = { it.id }) { o ->
                            OrderRow(o) { onOrder(o.id) }
                            HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 16.dp))
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    if (pickRange) {
        val st = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(enabled = st.selectedStartDateMillis != null, onClick = {
                    // выбранные даты приходят в UTC — переводим в начало местных суток
                    customFrom = st.selectedStartDateMillis?.let { localDay(it) }
                    customTo = (st.selectedEndDateMillis ?: st.selectedStartDateMillis)?.let { localDay(it) + 24 * 3600_000L }
                    period = Period.CUSTOM
                    pickRange = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text("Отмена") } },
        ) {
            DateRangePicker(state = st, modifier = Modifier.height(480.dp), title = { Text("Период", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}

private fun localDay(utcMidnight: Long): Long {
    val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
    return java.util.Calendar.getInstance().apply {
        clear()
        set(utc.get(java.util.Calendar.YEAR), utc.get(java.util.Calendar.MONTH), utc.get(java.util.Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

private fun rangeLabel(from: Long?, to: Long?): String {
    val f = SimpleDateFormat("d MMM", Locale("ru"))
    if (from == null) return "Свой период"
    val end = (to ?: from) - 1
    val a = f.format(Date(from)); val b = f.format(Date(end))
    return if (a == b) a else "$a – $b"
}

@Composable
internal fun SearchBox(value: String, hint: String, onChange: (String) -> Unit) {
    val c = Ym.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(Ym.shape).background(c.field)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = c.textMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 14.sp), cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) Icon(Icons.Filled.Close, "Очистить", tint = c.textMuted, modifier = Modifier.size(18.dp).clickable { onChange("") })
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
                Chip(o.statusText, statusColor(o.status, c))
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
