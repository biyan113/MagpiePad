package ai.usemagpie.pad.ui

import ai.usemagpie.pad.AppViewModel
import ai.usemagpie.pad.ModelInfo
import ai.usemagpie.pad.Quota
import ai.usemagpie.pad.QuotaSpan
import ai.usemagpie.pad.SeriesPoint
import ai.usemagpie.pad.Server
import ai.usemagpie.pad.Usage
import ai.usemagpie.pad.UsageGroup
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs

// ============ 共用 ============

private val palette = listOf(
    Color(0xFF5B8DEF), Color(0xFF2EB67D), Color(0xFFE8A33D), Color(0xFFA06CD5),
    Color(0xFFE5677D), Color(0xFF2BB3C0), Color(0xFF8A9A5B), Color(0xFFD9774B),
)

fun accentOf(key: String): Color = palette[abs(key.lowercase().hashCode()) % palette.size]

/** 圆形头像：名字首字母 + 按名字取色 */
@Composable
fun Avatar(name: String, size: Dp = 36.dp, key: String = name) {
    val c = accentOf(key)
    Box(Modifier.size(size).clip(CircleShape).background(c.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = c, fontWeight = FontWeight.Bold,
            style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
        )
    }
}

/** 统一的卡片：浅底 + 细描边 + 大圆角 */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
    ) { Column(Modifier.padding(padding), content = content) }
}

@Composable
private fun PanelTitle(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Pill(text: String, color: Color, icon: ImageVector? = null) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .12f)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = color); Spacer(Modifier.width(6.dp)) }
            Text(text, color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(20.dp)); action() }
    }
}

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(e: Throwable?) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(e?.message ?: e?.javaClass?.simpleName ?: "出错了", color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

/** "401 Unauthorized: {"error":{"message":"…"}}" → "401 Unauthorized · …" */
fun readableError(e: String): String {
    val head = Regex("^\\d{3}[^:{]*").find(e.trim())?.value?.trim()
    val msg = Regex("\"message\"\\s*:\\s*\"([^\"]+)").find(e)?.groupValues?.get(1)
        ?.replace(Regex("\\s*\\(request_id:[^)]*\\)"), "")
    if (head == null && msg == null) return e.take(200)
    return listOfNotNull(head, msg).joinToString(" · ")
}

private fun pct(x: Double) = String.format(Locale.US, if (x < 10 && x > 0) "%.1f%%" else "%.0f%%", x)

// ============ 概览 ============

private val periods = listOf("today" to "今天", "7d" to "7 天", "30d" to "30 天", "all" to "全部")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Overview(vm: AppViewModel, s: Server) {
    val d = vm.detail(s.id)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            periods.forEachIndexed { i, (k, label) ->
                SegmentedButton(
                    selected = d.period == k, onClick = { vm.setPeriod(s.id, k) },
                    shape = SegmentedButtonDefaults.itemShape(i, periods.size), icon = {},
                ) { Text(label) }
            }
        }
        val r = d.usage
        when {
            r == null -> LoadingBox()
            r.isFailure -> ErrorBox(r.exceptionOrNull())
            else -> UsageView(r.getOrThrow())
        }
    }
}

@Composable
private fun UsageView(u: Usage) {
    val okRate = if (u.calls > 0) (u.calls - u.errors) * 100.0 / u.calls else 100.0
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stats: List<@Composable (Modifier) -> Unit> = listOf(
            { m -> Stat(m, Icons.Default.Bolt, Color(0xFF5B8DEF), "请求", u.calls.toString(), if (u.calls > 0) "平均 ${fmtTokens(u.tokens / u.calls)} tokens / 次" else "还没有调用") },
            { m -> Stat(m, Icons.Default.DataUsage, Color(0xFFA06CD5), "Tokens", fmtTokens(u.tokens), "入 ${fmtTokens(u.input)} · 出 ${fmtTokens(u.output)} · 缓存 ${fmtTokens(u.cacheRead)}") },
            { m -> Stat(m, Icons.Default.Payments, Color(0xFFE8A33D), "估算费用", fmtCost(u.cost), if (u.calls > 0) "约 ${fmtCost(u.cost / u.calls)} / 次" else "按 magpie 的价格表估算") },
            { m ->
                val c = when { okRate >= 98 -> Green; okRate >= 90 -> Amber; else -> Red }
                Stat(m, Icons.Default.CheckCircle, c, "成功率", pct(okRate), "${u.errors} 次出错")
            },
        )
        val perRow = if (maxWidth < 720.dp) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            stats.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
    if (u.series.isNotEmpty()) TrendPanel(u)
    BoxWithConstraints {
        if (maxWidth >= 900.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Ranking("模型", u.models, Modifier.weight(1f))
            Ranking("Agents", u.agents, Modifier.weight(1f))
            Ranking("账号", u.accounts, Modifier.weight(1f))
        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Ranking("模型", u.models, Modifier.fillMaxWidth())
            Ranking("Agents", u.agents, Modifier.fillMaxWidth())
            if (u.accounts.isNotEmpty()) Ranking("账号", u.accounts, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, icon: ImageVector, color: Color, label: String, value: String, sub: String) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = color.copy(alpha = .08f), border = BorderStroke(1.dp, color.copy(alpha = .18f))) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(18.dp), tint = color)
                }
                Spacer(Modifier.width(10.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Spacer(Modifier.height(2.dp))
            Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

private val cIn = Color(0xFF5B8DEF)
private val cOut = Color(0xFFA06CD5)
private val cCache = Color(0xFF5B8DEF).copy(alpha = .28f)

@Composable
private fun TrendPanel(u: Usage) {
    var sel by remember(u) { mutableStateOf<Int?>(null) }
    val pts = u.series
    Panel(Modifier.fillMaxWidth()) {
        PanelTitle("Tokens 走势") {
            Legend(cIn, "输入"); Spacer(Modifier.width(12.dp)); Legend(cOut, "输出"); Spacer(Modifier.width(12.dp)); Legend(cCache, "缓存读取")
        }
        // 选中柱子时显示它的明细，否则显示峰值
        val p = sel?.let { pts.getOrNull(it) }
        val peak = pts.maxByOrNull { it.total }
        Row(verticalAlignment = Alignment.Bottom) {
            val show = p ?: peak
            Text(fmtTokens(show?.total ?: 0), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                if (p != null) "${labelOf(p, u.bucket)} · ${p.calls} 次 · ${fmtCost(p.cost)}" else "峰值 · ${peak?.let { labelOf(it, u.bucket) } ?: ""}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Text(
            if (p != null) "输入 ${fmtTokens(p.input)} · 输出 ${fmtTokens(p.output)} · 缓存读取 ${fmtTokens(p.cache)}" else "点柱子查看当时的明细",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        StackedBars(pts, sel, { sel = if (sel == it) null else it }, Modifier.fillMaxWidth().height(180.dp))
        Spacer(Modifier.height(8.dp))
        XLabels(pts.map { labelOf(it, u.bucket) })
    }
}

private fun labelOf(p: SeriesPoint, bucket: String) = if (bucket == "hour") "${p.label}:00" else p.label

@Composable
private fun Legend(c: Color, t: String) {
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
    Spacer(Modifier.width(6.dp))
    Text(t, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StackedBars(pts: List<SeriesPoint>, sel: Int?, onPick: (Int) -> Unit, modifier: Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (pts.maxOfOrNull { it.total } ?: 1L).coerceAtLeast(1L).toFloat()
    Canvas(modifier.pointerInput(pts) {
        detectTapGestures { o -> onPick((o.x / (size.width.toFloat() / pts.size)).toInt().coerceIn(0, pts.size - 1)) }
    }) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        for (i in 0..3) {
            val y = size.height * i / 3f
            drawLine(grid.copy(alpha = if (i == 3) .8f else .4f), Offset(0f, y), Offset(size.width, y), 1.5f, pathEffect = if (i == 3) null else dash)
        }
        val slot = size.width / pts.size
        val w = (slot * if (pts.size > 40) .7f else .58f).coerceAtMost(56.dp.toPx())
        pts.forEachIndexed { i, p ->
            val dim = sel != null && sel != i
            val x = i * slot + (slot - w) / 2
            val totalH = p.total / max * size.height
            if (totalH <= 0f) return@forEachIndexed
            // 整根柱子裁一次圆角，里面的分段用直角矩形，薄的分段才不会变成小圆点
            val clip = Path().apply {
                addRoundRect(RoundRect(x, size.height - totalH.coerceAtLeast(2f), x + w, size.height, CornerRadius(5.dp.toPx())))
            }
            clipPath(clip) {
                var top = size.height
                listOf(p.input to cIn, p.output to cOut, p.cache to cCache).forEach { (v, c) ->
                    if (v <= 0) return@forEach
                    val h = v / max * size.height
                    top -= h
                    drawRect(if (dim) c.copy(alpha = c.alpha * .35f) else c, Offset(x, top), Size(w, h + .5f))
                }
            }
        }
    }
}

/** 均匀挑几个横轴标签，居中放在对应柱子下面 */
@Composable
private fun XLabels(labels: List<String>) {
    if (labels.isEmpty()) return
    val step = ((labels.size + 6) / 7).coerceAtLeast(1)
    val shown = labels.indices.filter { it % step == 0 }
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Layout(content = { shown.forEach { Text(labels[it], style = style, color = color, maxLines = 1, softWrap = false) } }, modifier = Modifier.fillMaxWidth()) { ms, c ->
        val ps = ms.map { it.measure(c.copy(minWidth = 0)) }
        val slot = c.maxWidth.toFloat() / labels.size
        layout(c.maxWidth, ps.maxOfOrNull { it.height } ?: 0) {
            ps.forEachIndexed { k, p ->
                val cx = shown[k] * slot + slot / 2
                p.place((cx - p.width / 2).toInt().coerceIn(0, (c.maxWidth - p.width).coerceAtLeast(0)), 0)
            }
        }
    }
}

@Composable
private fun Ranking(title: String, rows: List<UsageGroup>, modifier: Modifier) {
    Panel(modifier) {
        val total = rows.sumOf { it.tokens }.coerceAtLeast(1)
        PanelTitle(title) { Text("${rows.size} 项", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (rows.isEmpty()) Text("这段时间没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        rows.take(6).forEachIndexed { i, g ->
            if (i > 0) Spacer(Modifier.height(14.dp))
            val share = g.tokens.toDouble() / total
            val c = accentOf(g.name)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(g.name, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (g.sub.isNotEmpty() && g.sub != g.name)
                                Text(g.sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(fmtTokens(g.tokens), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.copy(alpha = .12f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(share.toFloat().coerceIn(.01f, 1f)).clip(RoundedCornerShape(3.dp)).background(c))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("${pct(share * 100)} · ${g.calls} 次" + if (g.cost > 0) " · ${fmtCost(g.cost)}" else "",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ============ 额度 ============

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Quotas(vm: AppViewModel, s: Server, onOpenConsole: () -> Unit) {
    val d = vm.detail(s.id)
    val r = d.quotas
    when {
        r == null -> LoadingBox()
        r.isFailure -> Box(Modifier.padding(24.dp)) { ErrorBox(r.exceptionOrNull()) }
        else -> {
            val list = r.getOrThrow()
            LazyVerticalGrid(
                GridCells.Adaptive(340.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val bad = list.count { it.error.isNotEmpty() }
                            val low = list.count { q -> q.windows.any { !it.unlimited && it.used >= .9 } }
                            Pill("${list.size} 个账号与余额", MaterialTheme.colorScheme.primary, Icons.Default.AccountBalanceWallet)
                            if (list.size - bad > 0) Pill("${list.size - bad} 项正常", Green, Icons.Default.CheckCircle)
                            if (low > 0) Pill("$low 项快用完", Amber, Icons.Default.WarningAmber)
                            if (bad > 0) Pill("$bad 项读取失败", Red, Icons.Default.WarningAmber)
                        }
                        FilledTonalButton(onClick = { vm.refreshDetail(s.id, models = false, liveQuotas = true) }, enabled = !d.loading) {
                            Icon(Icons.Default.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("重新读取")
                        }
                    }
                }
                if (list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Default.AccountBalanceWallet, "还没有额度信息",
                        "登录订阅（Claude、ChatGPT、Copilot…）或添加带余额的 API key 后，这里会显示各自还剩多少。") {
                        OutlinedButton(onClick = onOpenConsole) { Text("去控制台添加供应商") }
                    }
                }
                items(list) { QuotaCard(it) }
            }
        }
    }
}

@Composable
private fun QuotaCard(q: Quota) {
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(q.name.ifEmpty { q.provider }, 44.dp, key = q.provider)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(q.name.ifEmpty { q.provider }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOf(q.plan, q.user).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { q.provider }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val (kind, kc) = when (q.kind) {
                "subscription" -> "订阅" to Color(0xFFA06CD5)
                "plan" -> "套餐" to Color(0xFF2BB3C0)
                "balance" -> "余额" to Color(0xFFE8A33D)
                else -> q.kind to MaterialTheme.colorScheme.primary
            }
            Pill(kind, kc)
        }
        if (q.balance.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("余额", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(q.balance, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        q.windows.forEach { w -> Spacer(Modifier.height(16.dp)); QuotaWindow(w) }
        if (q.error.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = Red.copy(alpha = .08f)) {
                Row(Modifier.fillMaxWidth().padding(12.dp)) {
                    Icon(Icons.Default.WarningAmber, null, Modifier.size(18.dp), tint = Red)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("读取失败", style = MaterialTheme.typography.labelLarge, color = Red)
                        Text(readableError(q.error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (q.windows.isEmpty() && q.balance.isEmpty() && q.error.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("暂未读取到额度", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuotaWindow(w: QuotaSpan) {
    val left = (1 - w.used) * 100
    val c = when { w.unlimited -> Green; w.used >= .9 -> Red; w.used >= .7 -> Amber; else -> Green }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(w.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (w.unlimited) Text("不限量", style = MaterialTheme.typography.titleSmall, color = c)
        else {
            Text("剩 ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(pct(left), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c)
        }
    }
    if (!w.unlimited) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.copy(alpha = .14f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(w.used.toFloat().coerceIn(.02f, 1f)).clip(RoundedCornerShape(5.dp)).background(c))
        }
    }
    val foot = listOfNotNull(w.display.ifEmpty { null }, fmtReset(w.resetsAt))
    if (foot.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(foot.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============ 模型 ============

@Composable
internal fun Models(vm: AppViewModel, s: Server, onOpenConsole: () -> Unit) {
    val d = vm.detail(s.id)
    val ctx = LocalContext.current
    var q by rememberSaveable(s.id) { mutableStateOf("") }
    var pick by rememberSaveable(s.id) { mutableStateOf<String?>(null) }
    val r = d.models
    when {
        r == null -> LoadingBox()
        r.isFailure -> Box(Modifier.padding(24.dp)) { ErrorBox(r.exceptionOrNull()) }
        else -> {
            val all = r.getOrThrow()
            fun prov(m: ModelInfo) = m.id.substringBefore('/', m.ownedBy.ifEmpty { "其他" })
            val byProv = all.groupBy(::prov).toSortedMap()
            val list = all.filter { (pick == null || prov(it) == pick) && (q.isBlank() || it.id.contains(q.trim(), true)) }
            LazyVerticalGrid(
                GridCells.Adaptive(250.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextField(
                            q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("搜索模型，例如 kimi、deepseek/…") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Default.Close, "清除") } },
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(pick == null, { pick = null }, label = { Text("全部 ${all.size}") })
                            byProv.forEach { (p, ms) ->
                                FilterChip(pick == p, { pick = if (pick == p) null else p }, label = { Text("$p ${ms.size}") },
                                    leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(accentOf(p))) })
                            }
                        }
                        Text("点卡片复制 provider/model，填到 Agent 的模型里即可经 magpie 调用",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (all.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Default.Inventory2, "还没有可用的模型", "在控制台添加一个供应商（API key、本地模型或订阅），它的模型会出现在这里。") {
                        OutlinedButton(onClick = onOpenConsole) { Text("去控制台添加供应商") }
                    }
                } else if (list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Default.Search, "没有匹配的模型", "换个关键词试试。")
                }
                list.groupBy(::prov).toSortedMap().forEach { (p, ms) ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h-$p") {
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p, 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(p, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(6.dp))
                            Text("${ms.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(ms, key = { it.id }) { m -> ModelCard(m, p) { copy(ctx, m.id) } }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(m: ModelInfo, prov: String, onCopy: () -> Unit) {
    val c = accentOf(prov)
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onCopy),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(c))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.id.substringAfter('/'), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(m.id, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ContentCopy, "复制", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun copy(ctx: android.content.Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("model", text))
    Toast.makeText(ctx, "已复制 $text", Toast.LENGTH_SHORT).show()
}
