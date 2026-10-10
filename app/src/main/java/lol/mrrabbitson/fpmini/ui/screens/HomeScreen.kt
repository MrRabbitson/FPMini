package lol.mrrabbitson.fpmini.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lol.mrrabbitson.fpmini.core.CategoryRef
import lol.mrrabbitson.fpmini.core.Game
import lol.mrrabbitson.fpmini.data.Prefs
import lol.mrrabbitson.fpmini.data.Repository
import lol.mrrabbitson.fpmini.ui.CapsLabel
import lol.mrrabbitson.fpmini.ui.LoadBox
import lol.mrrabbitson.fpmini.ui.rememberLoad
import lol.mrrabbitson.fpmini.ui.theme.Ym
import kotlinx.coroutines.launch

private sealed interface HomeItem {
    data class Header(val text: String) : HomeItem
    data class Letter(val letter: String) : HomeItem
    data class Pair2(val a: Game, val b: Game?) : HomeItem
    data object Favorites : HomeItem
    data object Empty : HomeItem
}

private fun letterOf(name: String): String {
    val ch = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: return "#"
    return if (ch.isDigit()) "#" else ch.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: Repository,
    prefs: Prefs,
    query: String,
    onQuery: (String) -> Unit,
    searchFocus: Boolean,
    onSearchFocused: () -> Unit,
    onCategory: (CategoryRef) -> Unit,
) {
    val state = rememberLoad("home") { force -> repo.home(force) }
    val favorites by prefs.favorites.collectAsState()
    val c = Ym.colors

    Column(Modifier.fillMaxSize().background(c.bg)) {
        SearchField(query, onQuery, searchFocus, onSearchFocused)
        LoadBox(state, "https://funpay.com/") { home ->
            val all = home.games.sortedWith(compareBy<Game>({ letterOf(it.name) == "#" }, { it.name.lowercase() }))
            val q = query.trim().lowercase()
            val filtered = if (q.isEmpty()) all else all.filter { g ->
                g.name.lowercase().contains(q) || g.categories.any { it.name.lowercase().contains(q) }
            }
            val rows = remember(filtered, q) {
                buildList {
                    if (q.isEmpty()) add(HomeItem.Favorites)
                    add(HomeItem.Header(if (q.isEmpty()) "Все игры по алфавиту" else "Найдено: ${filtered.size}"))
                    if (filtered.isEmpty()) add(HomeItem.Empty)
                    filtered.groupBy { letterOf(it.name) }.forEach { (letter, games) ->
                        add(HomeItem.Letter(letter))
                        games.chunked(2).forEach { add(HomeItem.Pair2(it[0], it.getOrNull(1))) }
                    }
                }
            }
            val letterIndex = remember(rows) {
                rows.withIndex().filter { it.value is HomeItem.Letter }
                    .associate { (i, v) -> (v as HomeItem.Letter).letter to i }
            }
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()

            PullToRefreshBox(isRefreshing = state.loading, onRefresh = { state.refresh() }) {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(rows) { item ->
                            when (item) {
                                HomeItem.Favorites -> FavoritesBlock(all.filter { it.id in favorites }, onCategory) { prefs.toggleFavorite(it) }
                                is HomeItem.Header -> CapsLabel(item.text, Modifier.padding(start = 16.dp, top = 28.dp, bottom = 4.dp))
                                is HomeItem.Letter -> Text(
                                    item.letter, color = c.textMuted, fontSize = 15.sp,
                                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
                                )
                                is HomeItem.Pair2 -> Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 40.dp)) {
                                    GameCell(item.a, item.a.id in favorites, onCategory, { prefs.toggleFavorite(it) }, Modifier.weight(1f))
                                    Spacer(Modifier.width(16.dp))
                                    if (item.b != null) GameCell(item.b, item.b.id in favorites, onCategory, { prefs.toggleFavorite(it) }, Modifier.weight(1f))
                                    else Spacer(Modifier.weight(1f))
                                }
                                HomeItem.Empty -> Text(
                                    "Ничего не нашлось", color = c.textMuted,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                        item { Spacer(Modifier.height(48.dp)) }
                    }
                    if (q.isEmpty() && letterIndex.size > 3) {
                        // Алфавитный указатель справа, как на сайте
                        Column(
                            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 12.dp, horizontal = 6.dp),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            letterIndex.keys.forEach { l ->
                                Text(
                                    l, color = c.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                    modifier = Modifier.clickable { scope.launch { listState.scrollToItem(letterIndex.getValue(l)) } }
                                        .padding(horizontal = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, focus: Boolean, onFocused: () -> Unit) {
    val c = Ym.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(focus) { if (focus) { fr.requestFocus(); onFocused() } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(Ym.shape).background(c.field).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Игра или раздел", color = c.textMuted, fontSize = 16.sp)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(fr),
            )
        }
        if (query.isNotEmpty()) Icon(
            Icons.Filled.Close, "Очистить", tint = c.textMuted,
            modifier = Modifier.size(20.dp).clickable { onQuery("") },
        )
    }
}

@Composable
private fun FavoritesBlock(games: List<Game>, onCategory: (CategoryRef) -> Unit, onToggle: (Int) -> Unit) {
    val c = Ym.colors
    Column(Modifier.fillMaxWidth().background(c.section).padding(16.dp)) {
        CapsLabel("Ваши игры")
        Spacer(Modifier.height(12.dp))
        if (games.isEmpty()) {
            Text("Нажмите ★ у игры ниже, чтобы закрепить её здесь.", color = c.textMuted, fontSize = 15.sp)
        } else {
            games.forEachIndexed { i, g ->
                if (i > 0) Spacer(Modifier.height(18.dp))
                GameCell(g, true, onCategory, onToggle, Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameCell(
    game: Game,
    favorite: Boolean,
    onCategory: (CategoryRef) -> Unit,
    onToggle: (Int) -> Unit,
    modifier: Modifier,
) {
    val c = Ym.colors
    Column(modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                game.name,
                color = c.text,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                modifier = Modifier.weight(1f, fill = false)
                    .clickable(enabled = game.categories.isNotEmpty()) { onCategory(game.categories.first()) },
            )
            Icon(
                Icons.Filled.Star, if (favorite) "Убрать из моих" else "В мои игры",
                tint = if (favorite) c.star else c.divider,
                modifier = Modifier.padding(start = 4.dp).size(18.dp).clickable { onToggle(game.id) },
            )
        }
        Spacer(Modifier.height(4.dp))
        FlowRow {
            game.categories.forEachIndexed { i, cat ->
                Text(
                    cat.name,
                    color = c.accent,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.clickable { onCategory(cat) },
                )
                if (i < game.categories.lastIndex) Text(" · ", color = c.accent, fontSize = 16.sp, lineHeight = 24.sp)
            }
        }
    }
}
