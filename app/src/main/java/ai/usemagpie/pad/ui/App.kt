package ai.usemagpie.pad.ui

import ai.usemagpie.pad.AppViewModel
import ai.usemagpie.pad.Server
import ai.usemagpie.pad.Status
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 对话框状态 */
sealed interface Sheet {
    data class Edit(val server: Server?) : Sheet
    data object Scan : Sheet
    data object Termux : Sheet
}

@Composable
fun App(vm: AppViewModel) {
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            // 首次打开自动选中第一台
            LaunchedEffect(vm.servers.size) {
                if (vm.selected == null && vm.servers.isNotEmpty()) vm.select(vm.servers.first().id)
            }
            Row(Modifier.fillMaxSize()) {
                // 收起（或正在看控制台）时只留一条窄栏
                val rail = vm.sidebarCollapsed || (vm.consoleFull && vm.selected != null)
                AnimatedContent(rail, transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = true) }, label = "sidebar") { r ->
                    if (r) ServerRail(vm, Modifier.width(76.dp).fillMaxHeight(), onSheet = { sheet = it }, onExpand = {
                        vm.setSidebar(collapsed = false); vm.consoleFull = false
                    })
                    else ServerList(
                        vm, Modifier.width(if (this@BoxWithConstraints.maxWidth >= 1000.dp) 360.dp else 260.dp).fillMaxHeight(),
                        onSheet = { sheet = it }, onCollapse = { vm.setSidebar(collapsed = true) },
                    )
                }
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val s = vm.selected
                    if (s == null) EmptyDetail(onAdd = { sheet = Sheet.Edit(null) }, onScan = { sheet = Sheet.Scan })
                    else DetailPane(vm, s, onEdit = { sheet = Sheet.Edit(s) }, onBack = null)
                }
            }
        } else {
            val s = vm.selected
            if (s == null) ServerList(vm, Modifier.fillMaxSize(), onSheet = { sheet = it })
            else {
                BackHandler { vm.select(null) }
                DetailPane(vm, s, onEdit = { sheet = Sheet.Edit(s) }, onBack = { vm.select(null) })
            }
        }
    }

    when (val sh = sheet) {
        is Sheet.Edit -> EditServerDialog(vm, sh.server, onDismiss = { sheet = null })
        Sheet.Scan -> ScanDialog(vm, onDismiss = { sheet = null }, onPick = { sheet = Sheet.Edit(it) })
        Sheet.Termux -> TermuxDialog(vm, onDismiss = { sheet = null })
        null -> {}
    }
}

@Composable
private fun ServerList(vm: AppViewModel, modifier: Modifier, onSheet: (Sheet) -> Unit, onCollapse: (() -> Unit)? = null) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Magpie Pad", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                val on = vm.servers.count { vm.status[it.id] is Status.Online }
                Text("${vm.servers.size} 台 magpie · $on 台在线", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.checkAll() }) { Icon(Icons.Default.Refresh, "刷新状态") }
            if (onCollapse != null) IconButton(onClick = onCollapse) { Icon(Icons.Default.KeyboardDoubleArrowLeft, "收起侧边栏") }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onSheet(Sheet.Edit(null)) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("添加", maxLines = 1, softWrap = false)
            }
            OutlinedButton(onClick = { onSheet(Sheet.Scan) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Default.WifiFind, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("扫描", maxLines = 1, softWrap = false)
            }
            OutlinedButton(onClick = { onSheet(Sheet.Termux) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Default.TabletAndroid, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("本机", maxLines = 1, softWrap = false)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (vm.servers.isEmpty()) {
            Text(
                "还没有 magpie。\n· 添加：填写远程或局域网的网关地址\n· 扫描：找同一 Wi‑Fi 下开了共享的 magpie\n· 本机：在这台平板的 Termux 里运行 magpie",
                Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val groups = vm.servers.groupBy { it.kind }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp)) {
            Server.Kind.entries.forEach { k ->
                val list = groups[k] ?: return@forEach
                item(key = "h-$k") {
                    Text(k.label, Modifier.padding(start = 8.dp, top = 12.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                items(list, key = { it.id }) { s ->
                    ServerRow(s, vm.status[s.id] ?: Status.Unknown, s.id == vm.selectedId) { vm.select(s.id) }
                }
            }
        }
    }
}

@Composable
private fun ServerRow(s: Server, st: Status, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (s.kind) { Server.Kind.LOCAL -> Icons.Default.TabletAndroid; Server.Kind.LAN -> Icons.Default.Router; Server.Kind.REMOTE -> Icons.Default.Public }
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(if (s.webUrl.isNotEmpty() && s.kind == Server.Kind.LAN) Icons.Default.Computer else icon, null, Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.baseUrl.removePrefix("http://").removePrefix("https://"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            StatusDot(st)
            val sub = when (st) {
                is Status.Online -> "v${st.hello.version} · ${st.hello.latencyMs}ms"
                is Status.Offline -> if (st.reason.startsWith("HTTP 401")) "需要 key" else if (st.reason.startsWith("HTTP 403")) "未共享" else "离线"
                Status.Checking -> "检查中"
                Status.Unknown -> ""
            }
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusDot(st: Status, size: Int = 10) {
    val c = when (st) {
        is Status.Online -> Green
        is Status.Offline -> if (st.reason.startsWith("HTTP 40")) Amber else Red // 连得上但被拒
        else -> Amber
    }
    Box(Modifier.padding(4.dp).size(size.dp).clip(CircleShape).background(c))
}

@Composable
private fun EmptyDetail(onAdd: () -> Unit, onScan: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("连接一台 magpie", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("查看用量、额度和模型，打开完整的 magpie 控制台管理 Agents 与 Providers。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onAdd) { Text("手动添加") }
            OutlinedButton(onClick = onScan) { Text("扫描局域网") }
        }
    }
}

/** 收起后的窄栏：展开按钮、添加/扫描，以及每台 magpie 的头像 + 状态点 */
@Composable
private fun ServerRail(vm: AppViewModel, modifier: Modifier, onSheet: (Sheet) -> Unit, onExpand: () -> Unit) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onExpand) { Icon(Icons.Default.KeyboardDoubleArrowRight, "展开侧边栏") }
        Spacer(Modifier.height(4.dp))
        FilledTonalIconButton(onClick = { onSheet(Sheet.Edit(null)) }) { Icon(Icons.Default.Add, "添加") }
        IconButton(onClick = { onSheet(Sheet.Scan) }) { Icon(Icons.Default.WifiFind, "扫描局域网") }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        LazyColumn(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.servers, key = { it.id }) { s ->
                val sel = s.id == vm.selectedId
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { vm.select(s.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Avatar(s.name, 40.dp, key = s.id)
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(14.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(2.dp),
                    ) {
                        val c = when (val st = vm.status[s.id]) {
                            is Status.Online -> Green
                            is Status.Offline -> if (st.reason.startsWith("HTTP 40")) Amber else Red
                            else -> Amber
                        }
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(c))
                    }
                }
            }
        }
    }
}
