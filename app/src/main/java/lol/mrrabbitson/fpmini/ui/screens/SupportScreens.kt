package lol.mrrabbitson.fpmini.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import lol.mrrabbitson.fpmini.data.Repository
import lol.mrrabbitson.fpmini.ui.Card
import lol.mrrabbitson.fpmini.ui.Chip
import lol.mrrabbitson.fpmini.ui.EmptyState
import lol.mrrabbitson.fpmini.ui.LoadBox
import lol.mrrabbitson.fpmini.ui.TopBar
import lol.mrrabbitson.fpmini.ui.openUrl
import lol.mrrabbitson.fpmini.ui.rememberLoad
import lol.mrrabbitson.fpmini.ui.theme.Ym

private fun ticketColor(kind: String, accent: Color): Color = when (kind) {
    "open" -> Color(0xFFE5534B)
    "waiting" -> Color(0xFFE59A1D)
    "solved" -> Color(0xFF3FA34D)
    "closed" -> Color(0xFF8A8F98)
    else -> accent
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(repo: Repository, onOpen: (id: String, title: String) -> Unit) {
    val c = Ym.colors
    val ctx = LocalContext.current
    val state = rememberLoad("tickets") { repo.tickets() }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Заявки в поддержку", actions = {
            IconButton(onClick = { openUrl(ctx, "https://support.funpay.com/tickets/new") }) {
                Icon(Icons.Filled.Add, "Новая заявка", tint = c.accent)
            }
        })
        LoadBox(state) { list ->
            PullToRefreshBox(isRefreshing = state.loading, onRefresh = { state.refresh() }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (list.isEmpty()) item { EmptyState("Заявок нет", "Новую заявку можно создать кнопкой + вверху") }
                    items(list, key = { it.id }) { t ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onOpen(t.id, t.title) }.padding(horizontal = 16.dp, vertical = 11.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("#${t.id}", color = c.textMuted, fontSize = 12.sp)
                                Spacer(Modifier.width(6.dp))
                                if (t.status.isNotBlank()) Chip(t.status, ticketColor(t.statusKind, c.accent))
                                Spacer(Modifier.weight(1f))
                                Text(t.date, color = c.textMuted, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(t.title, color = c.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TicketScreen(repo: Repository, id: String, title: String, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val c = Ym.colors
    val scope = rememberCoroutineScope()
    val state = rememberLoad("ticket", id) { repo.ticket(id) }
    var text by rememberSaveable(id) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        TopBar(state.data?.title ?: title, subtitle = "Заявка #$id" + (state.data?.status?.let { " · $it" } ?: ""), onBack = onBack)
        Box(Modifier.weight(1f)) {
            LoadBox(state) { t ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (t.comments.isEmpty()) item { EmptyState("Сообщений нет") }
                    items(t.comments) { cm ->
                        Card {
                            Column(Modifier.padding(12.dp)) {
                                Row {
                                    Text(cm.author, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Text(cm.time, color = c.textMuted, fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(cm.text, color = c.text, fontSize = 14.sp, lineHeight = 19.sp)
                            }
                        }
                    }
                }
            }
        }
        if (state.data?.replyToken != null) {
            HorizontalDivider(color = c.divider)
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(c.field).padding(horizontal = 14.dp, vertical = 9.dp)) {
                    if (text.isEmpty()) Text("Ответить в заявку", color = c.textMuted, fontSize = 15.sp)
                    BasicTextField(
                        value = text, onValueChange = { text = it }, maxLines = 6,
                        textStyle = TextStyle(color = c.text, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(if (text.isBlank()) c.field else c.accent)
                        .clickable(enabled = text.isNotBlank() && !sending) {
                            sending = true
                            val msg = text.trim()
                            scope.launch {
                                try { repo.replyTicket(id, msg); text = ""; state.reloadSilently() }
                                catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Не отправилось") }
                                finally { sending = false }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(18.dp), color = c.onAccent, strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = if (text.isBlank()) c.textMuted else c.onAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
