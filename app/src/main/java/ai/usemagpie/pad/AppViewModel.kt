package ai.usemagpie.pad

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

sealed interface Status {
    data object Unknown : Status
    data object Checking : Status
    data class Online(val hello: Hello) : Status
    data class Offline(val reason: String) : Status
}

/** 详情页的数据，按服务器缓存 */
class Detail {
    var usage by mutableStateOf<Result<Usage>?>(null)
    var quotas by mutableStateOf<Result<List<Quota>>?>(null)
    var models by mutableStateOf<Result<List<ModelInfo>>?>(null)
    var loading by mutableStateOf(false)
    var period by mutableStateOf("today")
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ServerStore(app)

    var servers by mutableStateOf(store.load())
        private set
    var selectedId by mutableStateOf(store.selectedId)
        private set
    val status = mutableStateMapOf<String, Status>()
    private val details = mutableMapOf<String, Detail>()

    var scanning by mutableStateOf(false); private set
    var scanProgress by mutableStateOf(0); private set
    var scanResults by mutableStateOf<List<Pair<String, Hello>>?>(null)

    private var poller: Job? = null

    /** 当前在看控制台：宽屏时收起左侧列表 */
    var consoleFull by mutableStateOf(false)

    /** 用户手动收起了侧边栏（记住） */
    var sidebarCollapsed by mutableStateOf(store.sidebarCollapsed)
        private set

    fun setSidebar(collapsed: Boolean) { sidebarCollapsed = collapsed; store.sidebarCollapsed = collapsed }

    val selected: Server? get() = servers.firstOrNull { it.id == selectedId }

    init { startPolling() }

    fun detail(id: String) = details.getOrPut(id) { Detail() }

    fun select(id: String?) {
        selectedId = id
        store.selectedId = id
        id?.let { refreshDetail(it) }
    }

    fun upsert(s: Server) {
        servers = if (servers.any { it.id == s.id }) servers.map { if (it.id == s.id) s else it } else servers + s
        store.save(servers)
        details.remove(s.id)
        check(s)
        select(s.id)
    }

    fun remove(id: String) {
        servers = servers.filterNot { it.id == id }
        store.save(servers)
        status.remove(id); details.remove(id)
        if (selectedId == id) select(servers.firstOrNull()?.id)
    }

    fun move(id: String, delta: Int) {
        val i = servers.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in servers.indices) return
        servers = servers.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
        store.save(servers)
    }

    private fun startPolling() {
        poller?.cancel()
        poller = viewModelScope.launch {
            while (isActive) {
                checkAll()
                delay(15_000)
            }
        }
    }

    fun checkAll() {
        viewModelScope.launch { supervisorScope { servers.forEach { check(it) } } }
    }

    fun check(s: Server) {
        if (status[s.id] == null) status[s.id] = Status.Checking
        viewModelScope.launch {
            status[s.id] = try {
                Status.Online(MagpieApi(s).hello())
            } catch (e: ApiException) {
                Status.Offline(e.message ?: "HTTP ${e.code}")
            } catch (e: Exception) {
                Status.Offline(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
            }
        }
    }

    fun setPeriod(id: String, p: String) {
        detail(id).period = p
        refreshDetail(id, quotas = false, models = false)
    }

    fun refreshDetail(id: String, quotas: Boolean = true, models: Boolean = true, liveQuotas: Boolean = false) {
        val s = servers.firstOrNull { it.id == id } ?: return
        val d = detail(id)
        val api = MagpieApi(s)
        d.loading = true
        viewModelScope.launch {
            supervisorScope {
                val jobs = buildList {
                    add(launch { d.usage = runCatching { api.usage(d.period) } })
                    if (quotas) add(launch { d.quotas = runCatching { api.quotas(kept = !liveQuotas) } })
                    if (models) add(launch { d.models = runCatching { api.models() } })
                }
                jobs.forEach { it.join() }
            }
            d.loading = false
            check(s)
        }
    }

    fun scanLan() {
        if (scanning) return
        scanning = true; scanProgress = 0; scanResults = null
        viewModelScope.launch {
            val r = runCatching { LanScanner.scan(getApplication()) { scanProgress = it } }.getOrDefault(emptyList())
            scanResults = r
            scanning = false
        }
    }

    // ---------- 本机 Termux ----------

    fun termuxInstalled(): Boolean = runCatching {
        getApplication<Application>().packageManager.getPackageInfo("com.termux", 0); true
    }.getOrDefault(false)

    /**
     * 请 Termux 在后台跑 `magpie serve --web`：网关在 127.0.0.1:3425，控制台在 127.0.0.1:3426。
     * 需要 Termux 的 ~/.termux/termux.properties 里有 allow-external-apps=true，并授予本应用 RUN_COMMAND 权限。
     */
    fun startInTermux(webKey: String): Result<Unit> = runCatching {
        val cmd = "export MAGPIE_WEB_KEY=$webKey; " +
            "pgrep -f 'magpie serve' >/dev/null || nohup magpie serve --web=127.0.0.1:3426 > \$HOME/magpie-pad.log 2>&1 &"
        val i = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", cmd))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        getApplication<Application>().startService(i)
        Unit
    }

    fun stopInTermux(): Result<Unit> = runCatching {
        val i = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", "pkill -f 'magpie serve'"))
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        getApplication<Application>().startService(i)
        Unit
    }
}
