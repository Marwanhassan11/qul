package com.example.qul

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs

object Dups {
    private fun same(a: Txn, b: Txn): Boolean {
        if (a.income != b.income) return false
        val dt = abs(a.time - b.time)
        if (a.raw.isNotEmpty() && a.raw == b.raw && dt < 10 * 60_000L) return true
        return a.raw.isNotEmpty() && b.raw.isNotEmpty() && a.amount == b.amount &&
            dt < 3 * 60_000L && a.merchant.equals(b.merchant, ignoreCase = true)
    }

    fun isDup(list: List<Txn>, t: Txn) = list.any { it.id == t.id || same(it, t) }

    private fun sameDay(x: Long, y: Long): Boolean {
        val a = Calendar.getInstance(); a.timeInMillis = x
        val b = Calendar.getInstance(); b.timeInMillis = y
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    fun similarManual(list: List<Txn>, t: Txn) = list.any {
        it.id != t.id && it.income == t.income && it.amount == t.amount && sameDay(it.time, t.time)
    }
}

object Transfers {
    private val kw = Regex("حوال|تحويل|ايبان|iban|transfer|sarie|urpay", RegexOption.IGNORE_CASE)

    /** يربط التحويل الصادر بالوارد (نفس المبلغ خلال 6 ساعات) ويعتبرهما تحويلاً بين حساباتك. */
    fun link(l: List<Txn>): List<Txn> {
        val out = l.toMutableList()
        for (i in out.indices) {
            val a = out[i]
            if (!a.income) continue
            val j = out.indexOfFirst { b ->
                !b.income && abs(b.amount - a.amount) < 0.005 &&
                    abs(b.time - a.time) <= 6 * 3_600_000L &&
                    !(a.cat == "transfer" && b.cat == "transfer") &&
                    (a.cat == "transfer" || b.cat == "transfer" ||
                        kw.containsMatchIn(a.raw) || kw.containsMatchIn(b.raw))
            }
            if (j >= 0) { out[i] = a.copy(cat = "transfer"); out[j] = out[j].copy(cat = "transfer") }
        }
        return out
    }
}

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("qul", Context.MODE_PRIVATE)
    private fun rules(c: Context) = JSONObject(sp(c).getString("rules", "{}") ?: "{}")

    @Synchronized fun all(c: Context): List<Txn> {
        val a = JSONArray(sp(c).getString("tx", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val inc = o.getBoolean("i")
            val m = o.getString("m")
            Txn(o.getString("id"), o.getDouble("a"), inc, m, o.getLong("t"), o.getString("r"),
                o.optString("c", "").ifEmpty { Cats.guess(m, inc) })
        }.sortedByDescending { it.time }
    }

    @Synchronized fun addAll(c: Context, ts: List<Txn>): Int {
        val cur = all(c).toMutableList()
        val r = rules(c)
        var n = 0
        var changed = false
        for (t0 in ts) {
            val learned = r.optString(t0.merchant.lowercase(), "")
            val t = if (learned.isNotEmpty() && !t0.income) t0.copy(cat = learned) else t0
            if (Dups.isDup(cur, t)) continue
            // عملية أُدخلت يدوياً بنفس المبلغ ثم وصلت رسالتها: ندمجهما بدل التكرار
            val mi = cur.indexOfFirst {
                it.raw.isEmpty() && it.income == t.income && it.amount == t.amount &&
                    abs(it.time - t.time) < 86_400_000L
            }
            if (mi >= 0) { cur[mi] = cur[mi].copy(raw = t.raw); changed = true; continue }
            cur.add(t); n++; changed = true
        }
        if (changed) save(c, Transfers.link(cur))
        return n
    }

    @Synchronized fun put(c: Context, t: Txn) {
        val l = all(c)
        save(c, if (l.any { it.id == t.id }) l.map { if (it.id == t.id) t else it } else l + t)
    }

    @Synchronized fun remove(c: Context, id: String) = save(c, all(c).filter { it.id != id })

    @Synchronized fun learn(c: Context, merchant: String, cat: String) {
        val r = rules(c)
        r.put(merchant.lowercase(), cat)
        sp(c).edit().putString("rules", r.toString()).apply()
        save(c, all(c).map {
            if (!it.income && it.merchant.equals(merchant, ignoreCase = true)) it.copy(cat = cat) else it
        })
    }

    @Synchronized fun dedupe(c: Context): Int {
        val sorted = all(c).sortedBy { it.time }
        val keep = mutableListOf<Txn>()
        for (t in sorted) if (!Dups.isDup(keep, t)) keep.add(t)
        val removed = sorted.size - keep.size
        save(c, Transfers.link(keep))
        return removed
    }

    /** يحذف العمليات المسجّلة من الرسائل (وتبقى اليدوية) ليعاد استيرادها بالقواعد الجديدة. */
    @Synchronized fun resetAuto(c: Context) {
        save(c, all(c).filter { it.raw.isEmpty() || it.id.startsWith("m-") })
    }

    private fun save(c: Context, l: List<Txn>) {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("id", it.id).put("a", it.amount).put("i", it.income)
                .put("m", it.merchant).put("t", it.time).put("r", it.raw).put("c", it.cat))
        }
        sp(c).edit().putString("tx", a.toString()).apply()
    }
}
