package com.example.qul

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("qul", Context.MODE_PRIVATE)

    @Synchronized fun all(c: Context): List<Txn> {
        val a = JSONArray(sp(c).getString("tx", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Txn(o.getString("id"), o.getDouble("a"), o.getBoolean("i"),
                o.getString("m"), o.getLong("t"), o.getString("r"))
        }.sortedByDescending { it.time }
    }

    @Synchronized fun addAll(c: Context, ts: List<Txn>): Int {
        val old = all(c)
        val ids = old.map { it.id }.toSet()
        val fresh = ts.filter { it.id !in ids }.distinctBy { it.id }
        if (fresh.isNotEmpty()) save(c, old + fresh)
        return fresh.size
    }

    @Synchronized fun put(c: Context, t: Txn) {
        val l = all(c)
        save(c, if (l.any { it.id == t.id }) l.map { if (it.id == t.id) t else it } else l + t)
    }

    @Synchronized fun remove(c: Context, id: String) = save(c, all(c).filter { it.id != id })

    private fun save(c: Context, l: List<Txn>) {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("id", it.id).put("a", it.amount).put("i", it.income)
                .put("m", it.merchant).put("t", it.time).put("r", it.raw))
        }
        sp(c).edit().putString("tx", a.toString()).apply()
    }
}
