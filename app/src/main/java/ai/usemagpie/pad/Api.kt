package ai.usemagpie.pad

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/** magpie 网关的只读接口：/api/hello、/v1/models、/v1/magpie/quotas、/v1/magpie/usage */
class MagpieApi(private val s: Server) {

    private fun get(path: String, timeoutMs: Int = 8000): JSONObject {
        val c = URL(s.baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs.coerceAtMost(5000)
        c.readTimeout = timeoutMs
        c.setRequestProperty("Accept", "application/json")
        // 本机任何 token 都行；远程需要启用的网关 key
        c.setRequestProperty("Authorization", "Bearer " + s.apiKey.ifEmpty { "magpie" })
        try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiException(code, errorText(code, body))
            return JSONObject(body)
        } finally {
            c.disconnect()
        }
    }

    private fun errorText(code: Int, body: String): String {
        val msg = runCatching {
            val o = JSONObject(body)
            o.optJSONObject("error")?.optString("message")?.ifEmpty { null }
                ?: o.optString("error").ifEmpty { null } ?: o.optString("message")
        }.getOrNull()?.ifEmpty { null } ?: body.take(300)
        val hint = when (code) {
            401 -> "需要有效的网关 key（Gateway → Gateway keys）。"
            403 -> "这台 magpie 未开启局域网共享，或该 key 无权访问。"
            else -> ""
        }
        return listOf("HTTP $code", hint, msg).filter { it.isNotBlank() }.joinToString("\n")
    }

    suspend fun hello(): Hello = withContext(Dispatchers.IO) {
        val t = System.nanoTime()
        val o = get("/api/hello", 4000)
        Hello(o.optString("name"), o.optString("version"), (System.nanoTime() - t) / 1_000_000)
    }

    suspend fun models(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val a = get("/v1/models").optJSONArray("data") ?: return@withContext emptyList()
        (0 until a.length()).map {
            val m = a.getJSONObject(it)
            ModelInfo(m.optString("id"), m.optString("owned_by"))
        }.sortedBy { it.id }
    }

    /** kept=true 只读缓存、不去问供应商，快很多 */
    suspend fun quotas(kept: Boolean): List<Quota> = withContext(Dispatchers.IO) {
        parseQuotas(get("/v1/magpie/quotas" + if (kept) "?kept=1" else "", 15000))
    }

    suspend fun usage(period: String): Usage = withContext(Dispatchers.IO) {
        parseUsage(get("/v1/magpie/usage?period=$period", 15000))
    }
}

/** 扫描本机所在 /24 网段里开着 magpie 网关（默认 3425）的机器 */
object LanScanner {
    fun localIPv4(ctx: Context): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val lp: LinkProperties = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }?.hostAddress
    }

    suspend fun scan(ctx: Context, port: Int = 3425, onProgress: (Int) -> Unit): List<Pair<String, Hello>> =
        withContext(Dispatchers.IO) {
            val me = localIPv4(ctx) ?: return@withContext emptyList()
            val prefix = me.substringBeforeLast('.')
            val hosts = (1..254).map { "$prefix.$it" } + "127.0.0.1"
            val gate = Semaphore(48)
            var done = 0
            coroutineScope {
                hosts.map { h ->
                    async {
                        gate.withPermit {
                            val open = runCatching {
                                Socket().use { it.connect(InetSocketAddress(h, port), 350); true }
                            }.getOrDefault(false)
                            val r = if (!open) null else try {
                                (h to MagpieApi(Server(name = h, baseUrl = "http://$h:$port")).hello())
                                    .takeIf { it.second.name == "magpie" }
                            } catch (e: ApiException) {
                                // 端口上是 magpie，但未共享或需要 key
                                if (e.code == 401 || e.code == 403) h to Hello("magpie", if (e.code == 401) "需要网关 key" else "未开启共享", 0) else null
                            } catch (e: Exception) { null }
                            synchronized(this@LanScanner) { done++; onProgress(done * 100 / hosts.size) }
                            r
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }
}
