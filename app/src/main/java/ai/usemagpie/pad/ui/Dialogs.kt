package ai.usemagpie.pad.ui

import ai.usemagpie.pad.ApiException
import ai.usemagpie.pad.AppViewModel
import ai.usemagpie.pad.MagpieApi
import ai.usemagpie.pad.Server
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@Composable
fun EditServerDialog(vm: AppViewModel, initial: Server?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var key by remember { mutableStateOf(initial?.apiKey ?: "") }
    var web by remember { mutableStateOf(initial?.webUrl ?: "") }
    var webKey by remember { mutableStateOf(initial?.webKey ?: "") }
    var test by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun build(): Server {
        val base = Server.normalizeUrl(url)
        return (initial ?: Server(name = "", baseUrl = "")).copy(
            name = name.trim().ifEmpty { runCatching { java.net.URI(base).host }.getOrNull() ?: "magpie" },
            baseUrl = base, apiKey = key.trim(),
            webUrl = Server.normalizeUrl(web, 3426), webKey = webKey.trim(),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 640.dp).padding(24.dp),
        title = { Text(if (initial == null) "添加 magpie" else "编辑 ${initial.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, placeholder = { Text("办公室 Mac") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("网关", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(url, { url = it }, label = { Text("网关地址") }, placeholder = { Text("192.168.1.20 或 https://magpie.example.com") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    supportingText = { Text("只写 IP 时默认端口 3425") })
                SecretField(key, { key = it }, "网关 key（sk-magpie-…）",
                    "远程 / 局域网需要：在那台 magpie 打开 设置 → 局域网共享，再到 Gateway → Gateway keys 添加；或运行 magpie gateway-key add \"Pad\"。本机可留空。")
                Text("控制台（可选）", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(web, {
                    // 直接粘贴 magpie web 打印的整条链接时，自动拆出 key
                    val sp = Server.splitWebLink(it)
                    if (sp != null) { web = sp.first; webKey = sp.second } else web = it
                }, label = { Text("控制台链接") }, placeholder = { Text("http://192.168.1.20:3426/?k=…") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    supportingText = { Text("magpie web --lan 打印的链接，粘贴整条即可") })
                SecretField(webKey, { webKey = it }, "控制台 key（k= / MAGPIE_WEB_KEY）", null)
                test?.let {
                    Text(it, color = if (it.startsWith("✓")) Green else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                TextButton(enabled = url.isNotBlank() && !testing, onClick = {
                    testing = true; test = null
                    scope.launch {
                        val s = build()
                        val api = MagpieApi(s)
                        test = try {
                            val h = api.hello()
                            val m = runCatching { api.models().size }
                            "✓ magpie ${h.version}，${h.latencyMs}ms" + m.fold({ "，$it 个模型" }, { "\n但读取模型失败：${it.message}" })
                        } catch (e: ApiException) { "✗ ${e.message}" } catch (e: Exception) { "✗ 连不上：${e.message ?: e.javaClass.simpleName}" }
                        testing = false
                    }
                }) { Text("测试连接") }
                TextButton(enabled = url.isNotBlank(), onClick = { vm.upsert(build()); onDismiss() }) { Text("保存") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SecretField(v: String, on: (String) -> Unit, label: String, help: String?) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(v, on, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, "显示") } },
        supportingText = help?.let { { Text(it) } })
}

@Composable
fun ScanDialog(vm: AppViewModel, onDismiss: () -> Unit, onPick: (Server) -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.scanLan() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("扫描局域网") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val ip = remember { ai.usemagpie.pad.LanScanner.localIPv4(ctx) }
                Text("在 ${ip?.substringBeforeLast('.')?.plus(".0/24") ?: "当前网络"} 中查找端口 3425 上的 magpie 网关。",
                    style = MaterialTheme.typography.bodyMedium)
                if (vm.scanning) LinearProgressIndicator(progress = { vm.scanProgress / 100f }, Modifier.fillMaxWidth())
                val found = vm.scanResults
                if (found != null && found.isEmpty()) Text("没找到。确认那台 magpie 开了 设置 → 局域网共享，且在同一个 Wi‑Fi。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                found?.forEach { (host, h) ->
                    val known = vm.servers.any { it.host == host }
                    ListItem(
                        headlineContent = { Text(if (host == "127.0.0.1") "本机 (127.0.0.1)" else host) },
                        supportingContent = { Text(if (h.version.first().isDigit()) "magpie ${h.version}" else h.version) },
                        trailingContent = {
                            if (known) Text("已添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else TextButton(onClick = { onPick(Server(name = if (host == "127.0.0.1") "本机" else host, baseUrl = "http://$host:3425")) }) { Text("添加") }
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = { TextButton(enabled = !vm.scanning, onClick = { vm.scanLan() }) { Text("重新扫描") } },
    )
}

@Composable
fun TermuxDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val installed = remember { vm.termuxInstalled() }
    var msg by remember { mutableStateOf<String?>(null) }
    val existing = vm.servers.firstOrNull { it.kind == Server.Kind.LOCAL && it.baseUrl.endsWith(":3425") }

    fun start() {
        val key = existing?.webKey?.ifEmpty { null } ?: java.util.UUID.randomUUID().toString().replace("-", "")
        vm.startInTermux(key).onSuccess {
            vm.upsert((existing ?: Server(name = "本机 (Termux)", baseUrl = "http://127.0.0.1:3425"))
                .copy(webUrl = "http://127.0.0.1:3426", webKey = key))
            msg = "已请求 Termux 启动 magpie，几秒后状态会变绿。日志在 Termux 的 ~/magpie-pad.log。"
        }.onFailure { msg = "启动失败：${it.message}\n请确认已按下面的步骤设置 Termux。" }
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) start() else msg = "没有授予「在 Termux 中运行命令」权限，可在 系统设置 → 应用 → Magpie Pad → 权限 中打开。"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 640.dp).padding(24.dp),
        title = { Text("在这台平板上运行 magpie") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!installed) Text("没有检测到 Termux。请先从 F-Droid 或 GitHub 安装 Termux。", color = MaterialTheme.colorScheme.error)
                Text("magpie 支持 Termux。首次使用在 Termux 里执行一次：", style = MaterialTheme.typography.bodyMedium)
                Code("curl -fsSL https://usemagpie.ai/install.sh | sh")
                Code("mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings")
                Text("之后点「启动」，本应用会让 Termux 在后台运行 magpie serve：网关在 127.0.0.1:3425，控制台在 127.0.0.1:3426，并自动登记到列表。",
                    style = MaterialTheme.typography.bodyMedium)
                msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = installed, onClick = { vm.stopInTermux(); msg = "已请求停止。" }) { Text("停止") }
                TextButton(enabled = installed, onClick = {
                    val p = "com.termux.permission.RUN_COMMAND"
                    if (ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED) start() else perm.launch(p)
                }) { Text("启动") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (existing == null) vm.upsert(Server(name = "本机", baseUrl = "http://127.0.0.1:3425"))
                onDismiss()
            }) { Text(if (existing == null) "只添加本机条目" else "关闭") }
        },
    )
}
