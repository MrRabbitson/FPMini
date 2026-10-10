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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import lol.mrrabbitson.fpmini.core.ChatMessage
import lol.mrrabbitson.fpmini.data.Prefs
import lol.mrrabbitson.fpmini.data.Repository
import lol.mrrabbitson.fpmini.ui.Avatar
import lol.mrrabbitson.fpmini.ui.Dot
import lol.mrrabbitson.fpmini.ui.EmptyState
import lol.mrrabbitson.fpmini.ui.ListRow
import lol.mrrabbitson.fpmini.ui.LoadBox
import lol.mrrabbitson.fpmini.ui.TopBar
import lol.mrrabbitson.fpmini.ui.ImageViewer
import lol.mrrabbitson.fpmini.ui.rememberLoad
import lol.mrrabbitson.fpmini.ui.theme.Ym

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(repo: Repository, onOpen: (node: String, name: String) -> Unit) {
    val c = Ym.colors
    val state = rememberLoad("chats", autoRefreshMs = 15_000) { repo.chats() }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Чаты")
        LoadBox(state) { chats ->
            PullToRefreshBox(isRefreshing = state.loading, onRefresh = { state.refresh() }, modifier = Modifier.fillMaxSize()) {
                if (chats.isEmpty()) {
                    LazyColumn(Modifier.fillMaxSize()) { item { EmptyState("Чатов пока нет") } }
                } else LazyColumn(Modifier.fillMaxSize()) {
                    items(chats, key = { it.node }) { ch ->
                        ListRow(
                            onClick = { onOpen(ch.node, ch.name) },
                            leading = { Avatar(ch.avatarUrl, 44.dp, ch.name) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    ch.name, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                )
                                Text(ch.time, color = c.textMuted, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    ch.lastMessage, color = if (ch.unread) c.text else c.textMuted, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                )
                                if (ch.unread) { Spacer(Modifier.width(8.dp)); Dot(c.accent) }
                            }
                        }
                        HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    repo: Repository,
    prefs: Prefs,
    node: String,
    title: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val c = Ym.colors
    val scope = rememberCoroutineScope()
    val account by prefs.account.collectAsState()
    val auto by prefs.auto.collectAsState()
    val state = rememberLoad("chat", node, autoRefreshMs = 5_000) { repo.chat(node) }
    var text by rememberSaveable(node) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var image by remember { mutableStateOf<String?>(null) }
    val templates = remember(auto) {
        (listOf(auto.greetText) + auto.rules.map { it.reply }).filter { it.isNotBlank() }.distinct()
    }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        val who = state.data?.interlocutor
        TopBar(
            title = who?.name ?: title,
            subtitle = who?.status,
            onBack = onBack,
        )
        Box(Modifier.weight(1f)) {
            LoadBox(state) { page ->
                val msgs = page.messages
                if (msgs.isEmpty()) EmptyState("Сообщений пока нет", "Напишите первым")
                else {
                    val list = rememberLazyListState()
                    LazyColumn(
                        state = list,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(msgs.asReversed(), key = { it.id }) { m ->
                            Bubble(m, mine = m.authorId == account.userId) { url -> image = url }
                        }
                    }
                }
            }
        }

        if (showTemplates && templates.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(templates) { t ->
                    Text(
                        t, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 220.dp).clip(Ym.shape).background(c.field)
                            .clickable { text = t; showTemplates = false }.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
        }
        image?.let { ImageViewer(it) { image = null } }
        HorizontalDivider(color = c.divider)
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showTemplates = !showTemplates }, enabled = templates.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.List, "Шаблоны", tint = if (showTemplates) c.accent else c.textMuted)
            }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(c.field).padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                if (text.isEmpty()) Text("Сообщение", color = c.textMuted, fontSize = 15.sp)
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(c.accent),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (text.isBlank()) c.field else c.accent)
                    .clickable(enabled = text.isNotBlank() && !sending) {
                        val msg = text.trim()
                        sending = true
                        scope.launch {
                            try {
                                repo.send(node, msg)
                                text = ""
                                state.reloadSilently()
                            } catch (e: Exception) {
                                snackbar.showSnackbar(e.message ?: "Не отправилось")
                            } finally { sending = false }
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

@Composable
private fun Bubble(m: ChatMessage, mine: Boolean, onImage: (String) -> Unit) {
    val c = Ym.colors
    if (m.authorId == 0) {
        // системное сообщение FunPay
        Text(
            m.text, color = c.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)
                .clip(Ym.shape).background(c.section).padding(8.dp),
        )
        return
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp, 14.dp, if (mine) 4.dp else 14.dp, if (mine) 14.dp else 4.dp))
                .background(if (mine) c.accent else c.field)
                .padding(horizontal = 11.dp, vertical = 7.dp),
        ) {
            val fg = if (mine) c.onAccent else c.text
            if (m.imageUrl != null) {
                AsyncImage(
                    m.imageUrl, null,
                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(8.dp)).clickable { m.imageUrl?.let(onImage) },
                )
            }
            if (m.text.isNotBlank()) Text(m.text, color = fg, fontSize = 14.sp, lineHeight = 19.sp)
            val time = m.time.substringAfterLast(", ").take(5)
            if (time.isNotBlank()) Text(
                time, color = fg.copy(alpha = 0.6f), fontSize = 10.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
