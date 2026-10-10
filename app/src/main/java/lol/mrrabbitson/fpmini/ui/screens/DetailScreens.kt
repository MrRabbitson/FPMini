package lol.mrrabbitson.fpmini.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import lol.mrrabbitson.fpmini.core.TicketCategory
import lol.mrrabbitson.fpmini.core.TicketField
import lol.mrrabbitson.fpmini.data.Repository
import lol.mrrabbitson.fpmini.ui.Avatar
import lol.mrrabbitson.fpmini.ui.Card
import lol.mrrabbitson.fpmini.ui.EmptyState
import lol.mrrabbitson.fpmini.ui.LoadBox
import lol.mrrabbitson.fpmini.ui.ParamRow
import lol.mrrabbitson.fpmini.ui.PrimaryButton
import lol.mrrabbitson.fpmini.ui.SectionLabel
import lol.mrrabbitson.fpmini.ui.TopBar
import lol.mrrabbitson.fpmini.ui.rememberLoad
import lol.mrrabbitson.fpmini.ui.theme.Ym

// ===================== Заказ =====================

@Composable
fun OrderScreen(repo: Repository, id: String, onBack: () -> Unit, onChat: (node: String, name: String) -> Unit) {
    val c = Ym.colors
    val scope = rememberCoroutineScope()
    val state = rememberLoad("order", id) { repo.order(id) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Заказ #$id", subtitle = state.data?.status, onBack = onBack)
        LoadBox(state) { o ->
            LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars), contentPadding = PaddingValues(12.dp)) {
                o.buyer?.let { b ->
                    item {
                        Card {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(b.avatarUrl, 40.dp, b.name)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Покупатель", color = c.textMuted, fontSize = 12.sp)
                                    Text(b.name, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                                PrimaryButton("Написать") {
                                    val node = o.chatNode
                                    if (node != null) onChat(node, b.name)
                                    else b.id?.let { uid -> scope.launch { onChat(repo.nodeWith(uid), b.name) } }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                item {
                    SectionLabel("Детали заказа", Modifier.padding(start = 4.dp, bottom = 6.dp))
                    Card {
                        val rows = listOfNotNull(o.amount?.let { "Сумма" to it }, o.paidGoods?.let { "Оплаченный товар" to it }) +
                            o.params.map { it.label to it.value }
                        if (rows.isEmpty()) EmptyState("Нет данных о заказе")
                        rows.forEachIndexed { i, (label, value) ->
                            if (i > 0) HorizontalDivider(color = c.divider)
                            ParamRow(label, value)
                        }
                    }
                }
            }
        }
    }
}

// ===================== Лот (просмотр) =====================

@Composable
fun LotScreen(repo: Repository, type: String, id: String, onBack: () -> Unit) {
    val c = Ym.colors
    val state = rememberLoad("lot", type, id) { repo.offer(type, id) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar(state.data?.categoryTitle ?: "Лот", subtitle = "Лот #$id", onBack = onBack)
        LoadBox(state) { lot ->
            LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars), contentPadding = PaddingValues(12.dp)) {
                item {
                    Card {
                        if (lot.params.isEmpty()) EmptyState("Нет данных о лоте")
                        lot.params.forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(color = c.divider)
                            ParamRow(p.label, p.value)
                        }
                    }
                }
                if (lot.methods.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        SectionLabel("Цена для покупателя", Modifier.padding(start = 4.dp, bottom = 6.dp))
                    }
                    item {
                        Card {
                            lot.methods.forEachIndexed { i, m ->
                                if (i > 0) HorizontalDivider(color = c.divider)
                                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(m.title, color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    m.priceText?.let { Text(it, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================== Новая заявка =====================

@Composable
fun NewTicketScreen(repo: Repository, snackbar: SnackbarHostState, onBack: () -> Unit, onCreated: (id: String?) -> Unit) {
    val c = Ym.colors
    var category by remember { mutableStateOf<TicketCategory?>(null) }
    val cats = rememberLoad("ticket_cats") { repo.ticketCategories() }

    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
        TopBar(
            "Новая заявка",
            subtitle = category?.name ?: "Выберите тему",
            onBack = { if (category != null) category = null else onBack() },
        )
        val cat = category
        if (cat == null) {
            LoadBox(cats) { list ->
                LazyColumn(Modifier.fillMaxSize()) {
                    if (list.isEmpty()) item { EmptyState("Темы не загрузились", "Попробуйте позже.") }
                    items(list, key = { it.id }) { t ->
                        Text(
                            t.name, color = c.text, fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().clickable { category = t }.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                        HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        } else {
            TicketFormView(repo, cat, snackbar, onCreated)
        }
    }
}

@Composable
private fun TicketFormView(repo: Repository, cat: TicketCategory, snackbar: SnackbarHostState, onCreated: (String?) -> Unit) {
    val c = Ym.colors
    val scope = rememberCoroutineScope()
    val state = rememberLoad("ticket_form", cat.id) { repo.ticketForm(cat.id) }
    val values = remember(cat.id) { mutableStateMapOf<String, String>() }
    var sending by remember { mutableStateOf(false) }

    LoadBox(state) { form ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(form.fields, key = { it.name }) { f -> FieldInput(f, values[f.name].orEmpty()) { values[f.name] = it } }
            item {
                PrimaryButton(if (sending) "Отправляю…" else "Отправить заявку", Modifier.fillMaxWidth(), enabled = !sending) {
                    sending = true
                    scope.launch {
                        try {
                            val id = repo.createTicket(form, values.toMap())
                            snackbar.showSnackbar("Заявка создана")
                            onCreated(id)
                        } catch (e: Exception) {
                            snackbar.showSnackbar(e.message ?: "Не удалось создать заявку")
                        } finally { sending = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldInput(f: TicketField, value: String, onChange: (String) -> Unit) {
    val c = Ym.colors
    val label = f.label.ifBlank { "Поле" } + if (f.required) " *" else ""
    when (f.kind) {
        "select" -> {
            var open by remember { mutableStateOf(false) }
            Column {
                Text(label, color = c.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    f.options.firstOrNull { it.value == value }?.label ?: "Выберите",
                    color = if (value.isEmpty()) c.textMuted else c.text, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().clip(Ym.shape).background(c.field).clickable { open = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    f.options.forEach { o -> DropdownMenuItem(text = { Text(o.label) }, onClick = { onChange(o.value); open = false }) }
                }
            }
        }
        "radio" -> Column {
            Text(label, color = c.textMuted, fontSize = 12.sp)
            f.options.forEach { o ->
                Row(
                    Modifier.fillMaxWidth().clickable { onChange(o.value) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = value == o.value, onClick = { onChange(o.value) }, colors = RadioButtonDefaults.colors(selectedColor = c.accent))
                    Text(o.label, color = c.text, fontSize = 14.sp)
                }
            }
        }
        else -> OutlinedTextField(
            value = value, onValueChange = onChange,
            label = { Text(label) },
            minLines = if (f.kind == "textarea") 4 else 1,
            singleLine = f.kind != "textarea",
            textStyle = TextStyle(fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
