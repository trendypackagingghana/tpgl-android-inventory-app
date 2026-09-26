package com.example.tpglstock.ui.dashboard

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.tpglstock.data.Insight
import com.example.tpglstock.data.InsightTone
import com.example.tpglstock.data.StockRepository
import com.example.tpglstock.data.WeekTotals
import com.example.tpglstock.data.WeeklySummary
import com.example.tpglstock.data.grouped
import com.example.tpglstock.data.weeklySummary
import com.example.tpglstock.ui.appViewModel
import com.example.tpglstock.ui.components.BackBar
import com.example.tpglstock.ui.components.IconTile
import com.example.tpglstock.ui.components.ListPanel
import com.example.tpglstock.ui.components.Panel
import com.example.tpglstock.ui.components.SectionTitle
import com.example.tpglstock.ui.theme.StockTheme
import com.example.tpglstock.ui.theme.mono
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class InsightsViewModel(repo: StockRepository) : ViewModel() {
    val summary: StateFlow<WeeklySummary?> = combine(repo.products, repo.movements, repo.status) { products, movements, status ->
        if (status.loaded) weeklySummary(products, movements) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Last week's figures against a usual week, then concerns, tips and good signs. */
@Composable
fun InsightsScreen(onBack: () -> Unit, onOpenProduct: (Long) -> Unit) {
    val vm = appViewModel { c, _ -> InsightsViewModel(c.repository) }
    val summary by vm.summary.collectAsStateWithLifecycle()
    val c = StockTheme.colors

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).navigationBarsPadding()) {
        BackBar(onBack, Modifier.padding(horizontal = 10.dp).padding(top = 4.dp), center = "Weekly summary")
        val s = summary ?: return@Column
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Last week", style = MaterialTheme.typography.displaySmall)
                    Text("Mon–Sun · ${s.rangeText}", style = MaterialTheme.typography.bodyLarge, color = c.muted)
                }
            }
            item { Figures(s.totals, s.usual, s.baselineWeeks) }
            val groups = listOf(
                InsightTone.CONCERN to "Concerns",
                InsightTone.IMPROVE to "Places to improve",
                InsightTone.GOOD to "Going well",
            )
            groups.forEach { (tone, title) ->
                val list = s.insights.filter { it.tone == tone }
                if (list.isNotEmpty()) item(key = tone.name) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle(title)
                        ListPanel(list) { InsightRow(it, onOpenProduct) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Figures(t: WeekTotals, usual: WeekTotals?, baselineWeeks: Int) {
    val c = StockTheme.colors
    Panel(shape = RoundedCornerShape(24.dp), padding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Figure("Added to stock", "${t.added.grouped()} pcs", usual?.added, t.added, Modifier.weight(1f))
                Figure("Went out", "${t.removed.grouped()} pcs", usual?.removed, t.removed, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Figure("Raw material used", "${t.bagsUsed.grouped()} bags", usual?.bagsUsed, t.bagsUsed, Modifier.weight(1f), higherIsGood = false)
                Figure("Updates", "${t.updates} on ${t.activeDays} days", usual?.updates?.toLong(), t.updates.toLong(), Modifier.weight(1f))
            }
            Text(
                if (usual == null) "No earlier weeks to compare with yet."
                else "Compared with the average of the $baselineWeeks week${if (baselineWeeks == 1) "" else "s"} before.",
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
            )
        }
    }
}

@Composable
private fun Figure(label: String, value: String, usual: Long?, now: Long, modifier: Modifier, higherIsGood: Boolean = true) {
    val c = StockTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Text(value, style = mono(18.sp), maxLines = 1)
        if (usual != null && usual > 0) {
            val pct = ((now - usual) * 100 / usual).toInt()
            val color = when {
                pct == 0 -> c.muted
                (pct > 0) == higherIsGood -> c.inFg
                else -> c.outFg
            }
            Text("${if (pct > 0) "+" else ""}$pct% vs usual", style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
fun insightLook(tone: InsightTone): Triple<androidx.compose.ui.graphics.vector.ImageVector, Color, Color> {
    val c = StockTheme.colors
    return when (tone) {
        InsightTone.CONCERN -> Triple(Icons.Rounded.WarningAmber, c.out.bg, c.out.fg)
        InsightTone.IMPROVE -> Triple(Icons.Rounded.Lightbulb, c.low.bg, c.low.fg)
        InsightTone.GOOD -> Triple(Icons.Rounded.TrendingUp, c.ok.bg, c.ok.fg)
    }
}

@Composable
private fun InsightRow(insight: Insight, onOpenProduct: (Long) -> Unit) {
    val c = StockTheme.colors
    val (icon, bg, fg) = insightLook(insight.tone)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (insight.productId != null) Modifier.clickable { onOpenProduct(insight.productId) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconTile(icon, bg, fg)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(insight.title, style = MaterialTheme.typography.labelLarge)
            Text(insight.detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        if (insight.productId != null) {
            Box(Modifier.padding(start = 6.dp, top = 4.dp)) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = c.faint, modifier = Modifier.size(20.dp))
            }
        }
    }
}
