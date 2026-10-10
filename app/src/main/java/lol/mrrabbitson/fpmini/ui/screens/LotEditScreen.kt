package lol.mrrabbitson.fpmini.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import lol.mrrabbitson.fpmini.core.LotEditForm
import lol.mrrabbitson.fpmini.core.LotField
import lol.mrrabbitson.fpmini.data.Repository
import lol.mrrabbitson.fpmini.ui.Card
import lol.mrrabbitson.fpmini.ui.LoadBox
import lol.mrrabbitson.fpmini.ui.PrimaryButton
import lol.mrrabbitson.fpmini.ui.SectionLabel
import lol.mrrabbitson.fpmini.ui.TopBar
import lol.mrrabbitson.fpmini.ui.YmSwitch
import lol.mrrabbitson.fpmini.ui.rememberLoad
import lol.mrrabbitson.fpmini.ui.theme.Ym

private fun prettyLabel(f: LotField): String {
    val base = f.label.replace("*", "").trim().ifBlank { f.name }
    return when (f.locale) {
        "ru" -> "$base (рус.)"
        "en" -> "$base (англ.)"
        else -> base
    }
}

@Composable
fun LotEditScreen(repo: Repository, offerId: String, snackbar: SnackbarHostState, onBack: () -> Unit, onSaved: () -> Unit) {
    val c = Ym.colors
    val scope = rememberCoroutineScope()
    val state = rememberLoad("lot_edit", offerId) { repo.lotForm(offerId) }
    val values = remember(offerId) { mutableStateMapOf<String, String>() }
    var saving by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
        TopBar("Редактирование лота", subtitle = "Лот #$offerId", onBack = onBack)
        Box(Modifier.weight(1f)) {
            LoadBox(state) { form ->
                LaunchedEffect(form) {
                    if (values.isEmpty()) form.fields.forEach { values[it.name] = it.value }
                }
                FormList(form, values)
            }
        }
        val form = state.data
        if (form != null) {
            HorizontalDivider(color = c.divider)
            PrimaryButton(if (saving) "Сохраняю…" else "Сохранить", Modifier.fillMaxWidth().padding(10.dp), enabled = !saving) {
                saving = true
                scope.launch {
                    try {
                        repo.saveLot(form, values.toMap())
                        snackbar.showSnackbar("Лот сохранён")
                        onSaved()
                    } catch (e: Exception) {
                        snackbar.showSnackbar(e.message ?: "Не удалось сохранить")
                    } finally { saving = false }
                }
            }
        }
    }
}

@Composable
private fun FormList(form: LotEditForm, values: MutableMap<String, String>) {
    val c = Ym.colors
    val snapshot = values.toMap()
    val visible = form.fields.filter { it.kind != "hidden" && form.isActive(it, snapshot) }
    // главное — сверху: цена, наличие, активность
    val main = visible.filter { it.name == "price" || it.name == "amount" || it.kind == "checkbox" }
    val rest = visible - main.toSet()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (form.title.isNotBlank()) item {
            Text(form.title, color = c.textMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
        if (main.isNotEmpty()) item {
            Card {
                main.forEachIndexed { i, f ->
                    if (i > 0) HorizontalDivider(color = c.divider)
                    if (f.kind == "checkbox") {
                        Row(
                            Modifier.fillMaxWidth().clickable { values[f.name] = if (values[f.name] == "on") "" else "on" }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(prettyLabel(f), color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            YmSwitch(values[f.name] == "on", { on -> values[f.name] = if (on) "on" else "" })
                        }
                    } else {
                        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) { FieldEditor(f, values) }
                    }
                }
            }
        }
        if (rest.isNotEmpty()) item { SectionLabel("Параметры и описание", Modifier.padding(start = 4.dp, top = 6.dp)) }
        items(rest, key = { it.name }) { f -> FieldEditor(f, values) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun FieldEditor(f: LotField, values: MutableMap<String, String>) {
    val c = Ym.colors
    val value = values[f.name] ?: f.value
    when (f.kind) {
        "select" -> {
            var open by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth()) {
                Text(prettyLabel(f), color = c.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Box {
                    Text(
                        f.options.firstOrNull { it.value == value }?.label?.ifBlank { null } ?: "Не выбрано",
                        color = c.text, fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().clip(Ym.shape).background(c.field)
                            .clickable { open = true }.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        f.options.forEach { o ->
                            DropdownMenuItem(text = { Text(o.label.ifBlank { "—" }) }, onClick = { values[f.name] = o.value; open = false })
                        }
                    }
                }
            }
        }
        "checkbox" -> Row(
            Modifier.fillMaxWidth().clickable { values[f.name] = if (value == "on") "" else "on" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(prettyLabel(f), color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            YmSwitch(value == "on", { on -> values[f.name] = if (on) "on" else "" })
        }
        else -> Column {
            OutlinedTextField(
                value = value,
                onValueChange = { values[f.name] = it },
                label = { Text(prettyLabel(f)) },
                singleLine = f.kind != "textarea",
                minLines = if (f.kind == "textarea") 4 else 1,
                textStyle = TextStyle(fontSize = 14.sp, fontWeight = if (f.name == "price") FontWeight.SemiBold else FontWeight.Normal),
                keyboardOptions = if (f.kind == "number") KeyboardOptions(keyboardType = KeyboardType.Decimal)
                else KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            if (f.hint.isNotBlank()) Text(f.hint, color = c.textMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }
    }
}
