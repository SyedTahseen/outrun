package app.xvpn.data

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Session(val start: Long, val end: Long, val server: String, val up: Long, val down: Long)

data class AppUsage(val label: String, val bytes: Long)

object Usage {
    private const val FILE = "xvpn_usage"
    private const val MAX_SESSIONS = 100
    private const val MAX_DAYS = 90

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun dayKey(ms: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

    @Synchronized
    fun addToday(ctx: Context, up: Long, down: Long) {
        if (up <= 0 && down <= 0) return
        val p = prefs(ctx)
        val j = runCatching { JSONObject(p.getString("days", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val k = dayKey()
        val cur = j.optJSONArray(k)
        j.put(k, JSONArray().put((cur?.optLong(0) ?: 0L) + up).put((cur?.optLong(1) ?: 0L) + down))

        if (j.length() > MAX_DAYS) {
            j.keys().asSequence().sorted().take(j.length() - MAX_DAYS).toList().forEach { j.remove(it) }
        }
        p.edit().putString("days", j.toString()).apply()
    }

    @Synchronized
    fun days(ctx: Context): Map<String, Pair<Long, Long>> = runCatching {
        val j = JSONObject(prefs(ctx).getString("days", "{}") ?: "{}")
        j.keys().asSequence().associateWith { k -> j.getJSONArray(k).let { it.optLong(0) to it.optLong(1) } }
    }.getOrDefault(emptyMap())

    fun lastDays(ctx: Context, n: Int): List<Triple<String, Long, Long>> {
        val d = days(ctx)
        val cal = Calendar.getInstance()
        val out = ArrayList<Triple<String, Long, Long>>()
        for (i in n - 1 downTo 0) {
            val c = cal.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, -i)
            val k = dayKey(c.timeInMillis)
            val v = d[k]
            out += Triple(k, v?.first ?: 0L, v?.second ?: 0L)
        }
        return out
    }

    fun total(ctx: Context, n: Int): Pair<Long, Long> =
        lastDays(ctx, n).let { l -> l.sumOf { it.second } to l.sumOf { it.third } }

    @Synchronized
    fun saveSession(ctx: Context, s: Session) {
        if (s.end <= s.start) return
        val p = prefs(ctx)
        val arr = runCatching { JSONArray(p.getString("sessions", "[]") ?: "[]") }.getOrDefault(JSONArray())
        arr.put(JSONObject().put("s", s.start).put("e", s.end).put("n", s.server).put("u", s.up).put("d", s.down))
        val keep = JSONArray()
        for (i in maxOf(0, arr.length() - MAX_SESSIONS) until arr.length()) keep.put(arr.get(i))
        p.edit().putString("sessions", keep.toString()).apply()
    }

    @Synchronized
    fun sessions(ctx: Context): List<Session> = runCatching {
        val arr = JSONArray(prefs(ctx).getString("sessions", "[]") ?: "[]")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Session(o.optLong("s"), o.optLong("e"), o.optString("n"), o.optLong("u"), o.optLong("d"))
        }.sortedByDescending { it.start }
    }.getOrDefault(emptyList())

    @Synchronized
    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    fun hasUsageAccess(ctx: Context): Boolean = runCatching {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else
            @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun perApp(ctx: Context, sessions: List<Session>, limit: Int = 30): List<AppUsage> {
        if (!hasUsageAccess(ctx) || sessions.isEmpty()) return emptyList()
        val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager ?: return emptyList()
        val perUid = HashMap<Int, Long>()
        val types = buildList {
            add(ConnectivityManager.TYPE_WIFI)

            if (Build.VERSION.SDK_INT >= 29) add(ConnectivityManager.TYPE_MOBILE)
        }
        for (s in sessions) for (t in types) runCatching {
            val stats = nsm.querySummary(t, null, s.start, s.end)
            val b = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(b)
                perUid[b.uid] = (perUid[b.uid] ?: 0L) + b.rxBytes + b.txBytes
            }
            stats.close()
        }
        val pm = ctx.packageManager
        return perUid.entries
            .filter { it.value > 0 && it.key != Process.myUid() }
            .mapNotNull { (uid, bytes) ->
                val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: return@mapNotNull null
                val label = runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
                AppUsage(label, bytes)
            }
            .sortedByDescending { it.bytes }
            .take(limit)
    }
}
