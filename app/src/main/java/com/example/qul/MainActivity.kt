package com.example.qul

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var summary: TextView
    private lateinit var list: ListView
    private var items: List<Txn> = emptyList()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(40, 96, 40, 24)
        }
        summary = TextView(this).apply { textSize = 20f }
        val btn = Button(this).apply {
            text = "استيراد رسائل آخر 90 يوم"
            setOnClickListener { ensurePerm { importOld() } }
        }
        list = ListView(this)
        list.setOnItemLongClickListener { _, _, pos, _ ->
            Store.remove(this, items[pos].id); refresh(); true
        }
        root.addView(summary); root.addView(btn)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        ensurePerm { }
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun ensurePerm(then: () -> Unit) {
        val p = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (p.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) then()
        else requestPermissions(p, 1)
    }

    private fun importOld() {
        val since = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
        val found = mutableListOf<Txn>()
        contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?", arrayOf(since.toString()), null)?.use {
            while (it.moveToNext()) {
                SmsParser.parse(it.getString(0) ?: "", it.getLong(1))?.let(found::add)
            }
        }
        val n = Store.addAll(this, found)
        Toast.makeText(this, "تمت إضافة $n عملية", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun refresh() {
        items = Store.all(this)
        val now = Calendar.getInstance()
        val month = items.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.time }
            c[Calendar.MONTH] == now[Calendar.MONTH] && c[Calendar.YEAR] == now[Calendar.YEAR]
        }
        val sp = month.filter { !it.income }.sumOf { it.amount }
        val inc = month.filter { it.income }.sumOf { it.amount }
        summary.text = "المصروف هذا الشهر: %,.2f ر.س\nالدخل: %,.2f ر.س".format(sp, inc)
        val df = SimpleDateFormat("d/M", Locale.US)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items.map {
            "${if (it.income) "+" else "-"}%,.2f  ·  ${it.merchant}  ·  ${df.format(Date(it.time))}".format(it.amount)
        })
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        if (g.isNotEmpty() && g.all { it == PackageManager.PERMISSION_GRANTED }) importOld()
    }
}
