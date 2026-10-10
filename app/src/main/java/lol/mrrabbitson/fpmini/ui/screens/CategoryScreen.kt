package lol.mrrabbitson.fpmini.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lol.mrrabbitson.fpmini.core.OfferRow
import lol.mrrabbitson.fpmini.data.Repository
import lol.mrrabbitson.fpmini.ui.CapsLabel
import lol.mrrabbitson.fpmini.ui.CornerCover
import lol.mrrabbitson.fpmini.ui.LoadBox
import lol.mrrabbitson.fpmini.ui.OfferTable
import lol.mrrabbitson.fpmini.ui.rememberLoad
import lol.mrrabbitson.fpmini.ui.theme.Ym

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    repo: Repository,
    type: String,
    id: Int,
    onBack: () -> Unit,
    onCategory: (String, Int) -> Unit,
    onOffer: (OfferRow) -> Unit,
    onUser: (Int) -> Unit,
) {
    val state = rememberLoad(type, id) { force -> repo.category(type, id, force) }
    val c = Ym.colors
    var onlyOnline by rememberSaveable { mutableStateOf(false) }

    LoadBox(state, "https://funpay.com/$type/$id/") { page ->
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = { state.refresh() }, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())) {
                CornerCover(page.coverUrl, page.title, onBack)

                if (page.siblings.size > 1) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        page.siblings.forEach { s ->
                            val active = s.active || (s.id == id && s.type == type)
                            Row(
                                Modifier.clip(Ym.shape)
                                    .background(if (active) c.accent else c.field)
                                    .clickable(enabled = !active) { onCategory(s.type, s.id) }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(s.name, color = if (active) c.onAccent else c.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                s.count?.let {
                                    Spacer(Modifier.width(6.dp))
                                    Text(it, color = if (active) c.onAccent.copy(alpha = 0.8f) else c.textMuted, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CapsLabel("Предложения · ${page.offers.size}", Modifier.weight(1f))
                    Text("Только онлайн", color = c.text, fontSize = 15.sp)
                    Checkbox(
                        checked = onlyOnline, onCheckedChange = { onlyOnline = it },
                        colors = CheckboxDefaults.colors(checkedColor = c.accent),
                    )
                }

                val rows = remember(page.offers, onlyOnline) {
                    if (onlyOnline) page.offers.filter { it.seller?.isOnline == true } else page.offers
                }
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(Ym.shape).background(c.card),
                ) {
                    if (rows.isEmpty()) {
                        Text("Здесь пока пусто", color = c.textMuted, modifier = Modifier.padding(20.dp))
                    } else {
                        OfferTable(rows, showSeller = true, onRow = onOffer, onSeller = onUser)
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
