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

