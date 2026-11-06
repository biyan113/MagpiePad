package ai.usemagpie.pad.ui

import ai.usemagpie.pad.AppViewModel
import ai.usemagpie.pad.Quota
import ai.usemagpie.pad.Server
import ai.usemagpie.pad.Status
import ai.usemagpie.pad.Usage
import ai.usemagpie.pad.UsageGroup
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val tabs = listOf("概览", "额度", "模型", "控制台")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailPane(vm: AppViewModel, s: Server, onEdit: () -> Unit, onBack: (() -> Unit)?) {
    val d = vm.detail(s.id)
    var tab by rememberSaveable(s.id) { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    LaunchedEffect(s.id) { if (d.usage == null) vm.refreshDetail(s.id) }
    // 控制台需要整屏宽度，切过去时收起左侧列表
    LaunchedEffect(tab) { vm.consoleFull = tab == 3 }
    DisposableEffect(Unit) { onDispose { vm.consoleFull = false } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(vm.status[s.id] ?: Status.Unknown, 12)
                    Column {
                        Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val st = vm.status[s.id]
                        Text(
                            "${s.kind.label} · ${s.baseUrl}" + if (st is Status.Online) " · magpie ${st.hello.version}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                    }
                }
            },
            actions = {
                if (d.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                IconButton(onClick = { vm.refreshDetail(s.id) }) { Icon(Icons.Default.Refresh, "刷新") }
                if (s.webUrl.isNotEmpty()) IconButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webLink(s))))
                }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "在浏览器打开控制台") }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "删除") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        )
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        val st = vm.status[s.id]
        if (st is Status.Offline && tab != 3) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text("连不上这台 magpie：${st.reason}", Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> Overview(vm, s)
                1 -> Quotas(vm, s) { tab = 3 }
                2 -> Models(vm, s) { tab = 3 }
                3 -> Console(s, onEdit)
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("移除「${s.name}」？") },
        text = { Text("只从本应用的列表里移除，不会影响那台 magpie。") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.remove(s.id) }) { Text("移除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )
}

fun webLink(s: Server): String {
    val base = s.webUrl.trimEnd('/')
    return if (s.webKey.isEmpty()) "$base/" else "$base/?k=${Uri.encode(s.webKey)}"
}

// ---------------- 控制台（magpie web） ----------------

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Console(s: Server, onEdit: () -> Unit) {
    if (s.webUrl.isEmpty()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("还没有设置控制台地址", style = MaterialTheme.typography.titleLarge)
            Text("控制台就是 magpie 自己的完整界面（Agents、Providers、路由组、网关 key、设置……），由 magpie web 提供。在那台机器上运行其一：")
            Code("magpie web --lan --no-open")
            Code("MAGPIE_WEB_KEY=$(openssl rand -hex 16) magpie serve --web=0.0.0.0:3426")
            Text("然后把它打印的链接（形如 http://192.168.1.20:3426/?k=…）粘贴到「编辑 → 控制台链接」。远程服务器建议放在 Tailscale 或 HTTPS 反向代理后面。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onEdit) { Text("设置控制台链接") }
        }
        return
    }
    var web by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    BackHandler(enabled = canBack) { web?.goBack() }
    Box(Modifier.fillMaxSize()) {
        key(s.id, s.webUrl, s.webKey) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        // 必须撑满：magpie 的页面是 html, body { height: 100% }，WRAP_CONTENT 时视口高度为 0，内容会被压扁看不见
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                                val host = Uri.parse(s.webUrl).host
                                if (req.url.host == host) return false
                                // 外链交给系统浏览器
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, req.url)) }
                                return true
                            }
                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                canBack = view.canGoBack()
                            }
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView, p: Int) { progress = p }
                        }
                        loadUrl(webLink(s))
                        web = this
                    }
                },
            )
        }
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, Modifier.fillMaxWidth().align(Alignment.TopCenter))
    }
}

@Composable
fun Code(text: String) {
    SelectionContainer {
        Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    }
}

