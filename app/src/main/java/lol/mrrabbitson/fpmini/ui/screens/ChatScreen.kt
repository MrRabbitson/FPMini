package lol.mrrabbitson.fpmini.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import lol.mrrabbitson.fpmini.core.ChatMessage
import lol.mrrabbitson.fpmini.data.Prefs
import lol.mrrabbitson.fpmini.data.Repository
import lol.mrrabbitson.fpmini.ui.Avatar
import lol.mrrabbitson.fpmini.ui.EmptyState
import lol.mrrabbitson.fpmini.ui.ImageViewer
import lol.mrrabbitson.fpmini.ui.LoadBox
import lol.mrrabbitson.fpmini.ui.rememberLoad
import lol.mrrabbitson.fpmini.ui.theme.Ym

/** Элемент ленты: сообщение или разделитель даты. */
private sealed interface Row2 {
    data class Date(val text: String) : Row2
    data class Msg(val m: ChatMessage, val mine: Boolean, val firstInGroup: Boolean, val lastInGroup: Boolean) : Row2
}

private fun datePart(time: String): String =
    time.substringBeforeLast(", ", "").trim().replaceFirstChar { it.uppercase() }

private fun clock(time: String): String =
    time.substringAfterLast(", ").trim().take(5)

private fun buildRows(msgs: List<ChatMessage>, me: Int?): List<Row2> {
    val out = mutableListOf<Row2>()
    var lastDate = ""
    msgs.forEachIndexed { i, m ->
        val d = datePart(m.time)
        if (d.isNotBlank() && d != lastDate) { out += Row2.Date(d); lastDate = d }
        val prev = msgs.getOrNull(i - 1)
        val next = msgs.getOrNull(i + 1)
        val first = prev == null || prev.authorId != m.authorId || datePart(prev.time).let { it.isNotBlank() && it != d }
        val last = next == null || next.authorId != m.authorId || datePart(next.time).let { it.isNotBlank() && it != d }
        out += Row2.Msg(m, m.authorId != null && m.authorId == me, first, last)
    }
    return out
}

/** Текст сообщения со ссылками: ник, номер заказа, адреса. */
private fun linkify(m: ChatMessage, color: Color, onLink: (String) -> Unit): AnnotatedString {
    val text = m.text
    val style = TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))
    val taken = mutableListOf<IntRange>()
    return buildAnnotatedString {
        append(text)
        fun link(start: Int, end: Int, url: String) {
            if (start < 0 || end > text.length || start >= end) return
            if (taken.any { it.first < end && start < it.last + 1 }) return
            taken += start until end
            addLink(LinkAnnotation.Clickable(url, style) { onLink(url) }, start, end)
        }
        // ссылки из разметки FunPay (ник покупателя, заказ)
        var from = 0
        m.links.forEach { l ->
            val idx = text.indexOf(l.text, from).let { if (it < 0) text.indexOf(l.text) else it }
            if (idx >= 0) { link(idx, idx + l.text.length, l.url); from = idx + l.text.length }
        }
        // адреса в тексте
        Regex("https?://[^\\s<>\"]+").findAll(text).forEach { r ->
            val url = r.value.trimEnd('.', ',', ')', '!', '?')
            link(r.range.first, r.range.first + url.length, url)
        }
        // номера заказов вида #ABC12345
        Regex("#([A-Z0-9]{6,12})\\b").findAll(text).forEach { r ->
            link(r.range.first, r.range.last + 1, "https://funpay.com/orders/${r.groupValues[1]}/")
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
    onLink: (String) -> Unit,
    onUser: (Int) -> Unit,
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

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // ---------- шапка как в мессенджере ----------
        val who = state.data?.interlocutor
        Column(Modifier.background(c.bg).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = c.text) }
                Row(
                    Modifier.weight(1f).clip(Ym.shape)
                        .clickable(enabled = who?.id != null) { who?.id?.let(onUser) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(who?.avatarUrl, 38.dp, who?.name ?: title)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(who?.name ?: title, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val st = who?.status
                        if (st != null) Text(
                            st.replaceFirstChar { it.uppercase() },
                            color = if (who?.isOnline == true) c.online else c.textMuted, fontSize = 12.sp, maxLines = 1,
                        )
                    }
                }
            }
            HorizontalDivider(color = c.divider)
        }

        // ---------- лента ----------
        Box(Modifier.weight(1f).fillMaxWidth().background(c.section)) {
            LoadBox(state) { page ->
                val rows = remember(page.messages, account.userId) { buildRows(page.messages, account.userId) }
                if (rows.isEmpty()) EmptyState("Сообщений пока нет", "Напишите первым.")
                else {
                    val list = rememberLazyListState()
                    val lastId = page.messages.lastOrNull()?.id
                    LaunchedEffect(lastId) { list.animateScrollToItem(0) }
                    LazyColumn(
                        state = list,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        items(rows.asReversed(), key = { r -> if (r is Row2.Msg) "m${r.m.id}" else "d${(r as Row2.Date).text}" }) { r ->
                            when (r) {
                                is Row2.Date -> DateChip(r.text)
                                is Row2.Msg -> MessageBubble(r, onImage = { image = it }, onLink = onLink)
                            }
                        }
                    }
                }
            }
        }
        image?.let { ImageViewer(it) { image = null } }

        // ---------- поле ввода: над клавиатурой и системной панелью ----------
        Column(Modifier.background(c.bg).windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
            HorizontalDivider(color = c.divider)
            if (showTemplates && templates.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
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
            Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = { showTemplates = !showTemplates }, enabled = templates.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.List, "Шаблоны", tint = if (showTemplates) c.accent else c.textMuted)
                }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(c.field).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (text.isEmpty()) Text("Сообщение", color = c.textMuted, fontSize = 15.sp)
                    BasicTextField(
                        value = text, onValueChange = { text = it },
                        textStyle = TextStyle(color = c.text, fontSize = 15.sp, lineHeight = 20.sp),
                        cursorBrush = SolidColor(c.accent),
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.padding(bottom = 2.dp).size(42.dp).clip(CircleShape).background(if (text.isBlank()) c.field else c.accent)
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
                    else Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = if (text.isBlank()) c.textMuted else c.onAccent, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun DateChip(text: String) {
    val c = Ym.colors
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            text, color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.bg).padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun MessageBubble(r: Row2.Msg, onImage: (String) -> Unit, onLink: (String) -> Unit) {
    val c = Ym.colors
    val m = r.m
    val top = if (r.firstInGroup) 6.dp else 1.5.dp

    if (m.authorId == 0) {
        // оповещение FunPay — по центру, со ссылками на покупателя и заказ
        Box(Modifier.fillMaxWidth().padding(top = top + 2.dp, bottom = 2.dp, start = 24.dp, end = 24.dp), contentAlignment = Alignment.Center) {
            Text(
                linkify(m, c.accent, onLink), color = c.text, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.bg)
                    .border(1.dp, c.divider, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        return
    }

    val big = 18.dp; val small = 5.dp
    val shape = if (r.mine) RoundedCornerShape(big, if (r.firstInGroup) big else small, small, big)
    else RoundedCornerShape(if (r.firstInGroup) big else small, big, big, small)
    val bg = if (r.mine) c.accent else c.card
    val fg = if (r.mine) c.onAccent else c.text
    val linkColor = if (r.mine) c.onAccent else c.accent

    Box(Modifier.fillMaxWidth().padding(top = top), contentAlignment = if (r.mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(min = 64.dp, max = 300.dp).clip(shape).background(bg)
                .then(if (r.mine) Modifier else Modifier.border(1.dp, c.divider, shape))
                .padding(start = 11.dp, end = 11.dp, top = 7.dp, bottom = 5.dp),
        ) {
            m.imageUrl?.let { url ->
                AsyncImage(
                    url, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(bottom = 4.dp).size(220.dp).clip(RoundedCornerShape(12.dp)).clickable { onImage(url) },
                )
            }
            if (m.text.isNotBlank()) {
                Text(linkify(m, linkColor, onLink), color = fg, fontSize = 15.sp, lineHeight = 20.sp)
            }
            val t = clock(m.time)
            if (t.isNotBlank()) Text(
                t, color = fg.copy(alpha = 0.6f), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End).padding(top = 1.dp),
            )
        }
    }
}
