package ai.usemagpie.pad

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

/** 一个 magpie 实例：网关地址 + 可选的网关 key，以及可选的 magpie web 控制台地址和 key。 */
data class Server(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** 网关地址，例如 http://192.168.1.20:3425 */
    val baseUrl: String,
    /** 网关 key（sk-magpie-…）。本机 127.0.0.1 不需要。 */
    val apiKey: String = "",
    /** magpie web / serve --web 的地址，例如 http://192.168.1.20:3426 */
    val webUrl: String = "",
    /** magpie web 的 key（链接里的 k=，或 MAGPIE_WEB_KEY） */
    val webKey: String = "",
) {
    val host: String get() = runCatching { URI(baseUrl).host ?: "" }.getOrDefault("")

    val kind: Kind
        get() = when {
            host == "127.0.0.1" || host == "localhost" || host == "::1" -> Kind.LOCAL
            isPrivate(host) -> Kind.LAN
            else -> Kind.REMOTE
        }

    enum class Kind(val label: String) { LOCAL("本机"), LAN("局域网"), REMOTE("远程") }

    fun toJson() = JSONObject()
        .put("id", id).put("name", name).put("baseUrl", baseUrl).put("apiKey", apiKey)
        .put("webUrl", webUrl).put("webKey", webKey)

    companion object {
        fun fromJson(o: JSONObject) = Server(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            name = o.optString("name"),
            baseUrl = o.optString("baseUrl"),
            apiKey = o.optString("apiKey"),
            webUrl = o.optString("webUrl"),
            webKey = o.optString("webKey"),
        )

        fun isPrivate(h: String): Boolean {
            val p = h.split(".").mapNotNull { it.toIntOrNull() }
            if (p.size != 4) return h.endsWith(".local") || h.endsWith(".lan")
            return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168) ||
                (p[0] == 100 && p[1] in 64..127) // Tailscale CGNAT
        }

        /** 用户只写了 "192.168.1.20" 或 "host:3425" 时补全为 http://…:3425 */
        fun normalizeUrl(raw: String, defaultPort: Int = 3425): String {
            var s = raw.trim().trimEnd('/')
            if (s.isEmpty()) return s
            if (!s.contains("://")) s = "http://$s"
            val u = runCatching { URI(s) }.getOrNull() ?: return s
            if (u.port == -1 && u.scheme == "http" && (u.path.isNullOrEmpty())) {
                s = "${u.scheme}://${u.host}:$defaultPort"
            }
            return s
        }

        /** 粘贴 magpie web 打印的整条链接（http://host:port/?k=xxx）时拆出地址和 key */
        fun splitWebLink(raw: String): Pair<String, String>? {
            val u = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
            val k = u.rawQuery?.split("&")?.firstOrNull { it.startsWith("k=") }?.substring(2) ?: return null
            val base = "${u.scheme}://${u.authority}"
            return base to k
        }
    }
}

class ServerStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("servers", Context.MODE_PRIVATE)

    fun load(): List<Server> {
        val raw = prefs.getString("list", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { Server.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(list: List<Server>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        prefs.edit().putString("list", a.toString()).apply()
    }

    var sidebarCollapsed: Boolean
        get() = prefs.getBoolean("sidebarCollapsed", false)
        set(v) { prefs.edit().putBoolean("sidebarCollapsed", v).apply() }

    var selectedId: String?
        get() = prefs.getString("selected", null)
        set(v) { prefs.edit().putString("selected", v).apply() }
}

// ---------- 接口返回 ----------

data class Hello(val name: String, val version: String, val latencyMs: Long)

data class ModelInfo(val id: String, val ownedBy: String)

data class QuotaSpan(
    val name: String,
    val used: Double,        // 0..1 的占比（magpie 返回的是 0..100 的百分比）
    val display: String,
    val resetsAt: String?,
    val unlimited: Boolean,
)

data class Quota(
    val provider: String,
    val name: String,
    val kind: String,
    val plan: String,
    val user: String,
    val balance: String,
    val error: String,
    val windows: List<QuotaSpan>,
    val readAt: String? = null,
)

data class UsageGroup(val name: String, val sub: String, val calls: Int, val tokens: Long, val cost: Double)

data class SeriesPoint(val label: String, val time: String, val input: Long, val output: Long, val cache: Long, val calls: Int, val cost: Double) {
    val total get() = input + output + cache
}

data class Usage(
    val period: String,
    val calls: Int,
    val errors: Int,
    val input: Long,
    val output: Long,
    val cacheRead: Long,
    val cost: Double,
    val bucket: String,
    val series: List<SeriesPoint>,
    val models: List<UsageGroup>,
    val agents: List<UsageGroup>,
    val accounts: List<UsageGroup>,
) {
    val tokens get() = input + output + cacheRead
}

class ApiException(val code: Int, message: String) : Exception(message)

fun parseUsage(o: JSONObject): Usage {
    fun groups(key: String): List<UsageGroup> {
        val a = o.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map {
            val g = a.getJSONObject(it)
            UsageGroup(
                name = g.optString("name").ifEmpty { g.optString("model").ifEmpty { g.optString("id") } },
                sub = g.optString("sub").ifEmpty { g.optString("account").ifEmpty { g.optString("provider") } },
                calls = g.optInt("calls"),
                tokens = g.optLong("input") + g.optLong("output") + g.optLong("cache_read"),
                cost = g.optDouble("cost", 0.0),
            )
        }.sortedByDescending { it.tokens }
    }
    val series = o.optJSONArray("series")?.let { a ->
        (0 until a.length()).map {
            val p = a.getJSONObject(it)
            SeriesPoint(
                label = p.optString("label").ifEmpty { p.optString("time") }, time = p.optString("time"),
                input = p.optLong("input"), output = p.optLong("output"), cache = p.optLong("cache_read"),
                calls = p.optInt("calls"), cost = p.optDouble("cost", 0.0),
            )
        }
    } ?: emptyList()
    return Usage(
        period = o.optString("period"),
        calls = o.optInt("calls"),
        errors = o.optInt("errors"),
        input = o.optLong("input"),
        output = o.optLong("output"),
        cacheRead = o.optLong("cache_read"),
        cost = o.optDouble("cost", 0.0),
        bucket = o.optString("bucket"),
        series = series,
        models = groups("models"),
        agents = groups("agents"),
        accounts = groups("accounts"),
    )
}

fun parseQuotas(o: JSONObject): List<Quota> {
    val a = o.optJSONArray("data") ?: return emptyList()
    return (0 until a.length()).map {
        val q = a.getJSONObject(it)
        val ws = q.optJSONArray("windows")
        Quota(
            provider = q.optString("provider"),
            name = q.optString("name"),
            kind = q.optString("kind"),
            plan = q.optString("plan"),
            user = q.optString("user"),
            balance = q.optString("balance"),
            error = q.optString("error"),
            readAt = q.optString("readAt").ifEmpty { q.optString("asOf").ifEmpty { null } },
            windows = if (ws == null) emptyList() else (0 until ws.length()).map { i ->
                val w = ws.getJSONObject(i)
                val used = w.optDouble("used", 0.0)
                QuotaSpan(
                    name = w.optString("name"),
                    used = (used / 100.0).coerceIn(0.0, 1.0),
                    display = w.optString("display"),
                    resetsAt = w.optString("resetsAt").ifEmpty { null },
                    unlimited = w.optBoolean("unlimited"),
                )
            },
        )
    }
}
