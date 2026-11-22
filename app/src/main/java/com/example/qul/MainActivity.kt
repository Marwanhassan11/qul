package com.example.qul

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.provider.Telephony
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

private const val GREEN = 0xFF0B6E6E.toInt()
private const val RED = 0xFFB42318.toInt()

class MainActivity : Activity() {
    private val df = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    private var from = monthStart()
    private var to = dayEnd(System.currentTimeMillis())
    private var shown: List<Txn> = emptyList()
    private var pendingPdf: ByteArray? = null

    private lateinit var list: ListView
    private lateinit var adp: Adp
    private lateinit var btnFrom: Button
    private lateinit var btnTo: Button
    private lateinit var tvIn: TextView
    private lateinit var tvOut: TextView
    private lateinit var tvNet: TextView
    private lateinit var bars: BarsView
    private lateinit var top: TopView

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun dayStart(t: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = t
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
    private fun dayEnd(t: Long) = dayStart(t) + 24L * 3600 * 1000 - 1
    private fun monthStart(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        return dayStart(c.timeInMillis)
    }

    private fun btn(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 13f
        b.setOnClickListener { onClick() }
        return b
    }
    private fun row(vararg v: View): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        v.forEach { r.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        return r
    }
    private fun card(color: Int): TextView {
        val t = TextView(this)
        t.setPadding(dp(12), dp(10), dp(12), dp(10))
        t.textSize = 16f
        t.setTextColor(color)
        t.setBackgroundColor(0x1A808080)
        return t
    }
    private fun label(s: String): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = 15f
        t.setTypeface(null, Typeface.BOLD)
        t.setPadding(0, dp(12), 0, dp(4))
        return t
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        header.layoutDirection = View.LAYOUT_DIRECTION_RTL
        header.setPadding(dp(12), dp(28), dp(12), dp(8))

        val title = TextView(this)
        title.text = "قُل"
        title.textSize = 26f
        title.setTypeface(null, Typeface.BOLD)
        header.addView(title)

        btnFrom = btn("") { pickDate(from) { from = dayStart(it); refresh() } }
        btnTo = btn("") { pickDate(to) { to = dayEnd(it); refresh() } }
        header.addView(row(btnFrom, btnTo))
        header.addView(row(
            btn("هذا الشهر") { from = monthStart(); to = dayEnd(System.currentTimeMillis()); refresh() },
            btn("آخر 30 يوم") {
                val now = System.currentTimeMillis()
                from = dayStart(now - 29L * 24 * 3600 * 1000); to = dayEnd(now); refresh()
            },
            btn("الكل") {
                val all = Store.all(this)
                val now = System.currentTimeMillis()
                from = dayStart(all.minOfOrNull { it.time } ?: now)
                to = dayEnd(maxOf(now, all.maxOfOrNull { it.time } ?: now))
                refresh()
            }
        ))

        tvIn = card(GREEN); tvOut = card(RED); tvNet = card(Color.DKGRAY)
        header.addView(label("ملخص الفترة"))
        header.addView(row(tvIn, tvOut))
        header.addView(tvNet)

        header.addView(label("الدخل مقابل المصروف"))
        bars = BarsView(this)
        header.addView(bars, LinearLayout.LayoutParams(-1, 420))
        header.addView(label("أعلى المصروفات"))
        top = TopView(this)
        header.addView(top, LinearLayout.LayoutParams(-1, 400))

        header.addView(row(
            btn("➕ إضافة عملية") { txnDialog(null) },
            btn("📄 تصدير PDF") { exportPdf() },
            btn("📥 استيراد الرسائل") { ensurePerm { importOld() } }
        ))
        header.addView(label("العمليات (اضغط على عملية للتعديل أو الحذف)"))

        list = ListView(this)
        list.layoutDirection = View.LAYOUT_DIRECTION_RTL
        list.addHeaderView(header, null, false)
        adp = Adp()
        list.adapter = adp
        list.setOnItemClickListener { _, _, pos, _ ->
            val i = pos - list.headerViewsCount
            if (i in shown.indices) txnDialog(shown[i])
        }
        setContentView(list)
        ensurePerm { }
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val all = Store.all(this)
        shown = all.filter { it.time in from..to }
        val inc = shown.filter { it.income }.sumOf { it.amount }
        val out = shown.filter { !it.income }.sumOf { it.amount }
        tvIn.text = "الدخل\n" + "%,.2f".format(inc)
        tvOut.text = "المصروف\n" + "%,.2f".format(out)
        tvNet.text = "الصافي: " + "%,.2f".format(inc - out)
        bars.inc = inc; bars.out = out; bars.invalidate()
        top.update(shown.filter { !it.income }.groupBy { it.merchant }
            .map { Pair(it.key, it.value.sumOf { x -> x.amount }) }
            .sortedByDescending { it.second }.take(5))
        btnFrom.text = "من: " + df.format(Date(from))
        btnTo.text = "إلى: " + df.format(Date(to))
        adp.notifyDataSetChanged()
    }

    private inner class Adp : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(i: Int) = shown[i]
        override fun getItemId(i: Int) = i.toLong()
        override fun getView(i: Int, v: View?, p: ViewGroup?): View {
            val t = shown[i]
            val tv = (v as? TextView) ?: TextView(this@MainActivity).also {
                it.setPadding(dp(16), dp(10), dp(16), dp(10)); it.textSize = 16f
            }
            val amt = "%,.2f".format(t.amount)
            tv.text = (if (t.income) "+" else "-") + amt + "   " + t.merchant + "\n" + df.format(Date(t.time))
            tv.setTextColor(if (t.income) GREEN else RED)
            return tv
        }
    }

    private fun pickDate(initial: Long, cb: (Long) -> Unit) {
        val c = Calendar.getInstance()
        c.timeInMillis = initial
        DatePickerDialog(this, { _, y, m, d ->
            val r = Calendar.getInstance()
            r.set(y, m, d, 12, 0, 0); r.set(Calendar.MILLISECOND, 0)
            cb(r.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun txnDialog(ex: Txn?) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.layoutDirection = View.LAYOUT_DIRECTION_RTL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val name = EditText(this)
        name.hint = "اسم العملية"
        name.setText(ex?.merchant ?: "")
        val amt = EditText(this)
        amt.hint = "المبلغ"
        amt.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        if (ex != null) amt.setText(ex.amount.toString())
        val rg = RadioGroup(this)
        rg.orientation = RadioGroup.HORIZONTAL
        val rOut = RadioButton(this); rOut.text = "مصروف"; rOut.id = 1001
        val rIn = RadioButton(this); rIn.text = "دخل"; rIn.id = 1002
        rg.addView(rOut); rg.addView(rIn)
        rg.check(if (ex?.income == true) 1002 else 1001)
        var time = ex?.time ?: System.currentTimeMillis()
        val dateBtn = Button(this)
        dateBtn.text = "التاريخ: " + df.format(Date(time))
        dateBtn.setOnClickListener {
            pickDate(time) { time = it; dateBtn.text = "التاريخ: " + df.format(Date(time)) }
        }
        box.addView(name); box.addView(amt); box.addView(rg); box.addView(dateBtn)

        val dlg = AlertDialog.Builder(this)
            .setTitle(if (ex == null) "إضافة عملية" else "تعديل العملية")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val a = amt.text.toString().toDoubleOrNull()
                if (a == null || a <= 0) {
                    toast("المبلغ غير صحيح")
                } else {
                    val n = name.text.toString().trim().ifEmpty { "بدون اسم" }
                    val id = ex?.id ?: ("m-" + UUID.randomUUID())
                    Store.put(this, Txn(id, a, rg.checkedRadioButtonId == 1002, n, time, ex?.raw ?: ""))
                    refresh()
                }
            }
            .setNegativeButton("إلغاء", null)
        if (ex != null) dlg.setNeutralButton("حذف") { _, _ -> Store.remove(this, ex.id); refresh() }
        dlg.show()
    }

    private fun exportPdf() {
        if (shown.isEmpty()) { toast("لا توجد عمليات في هذه الفترة"); return }
        pendingPdf = buildPdf()
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_TITLE, "qul-report.pdf")
        startActivityForResult(i, 2)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data
        val bytes = pendingPdf
        if (req == 2 && res == RESULT_OK && uri != null && bytes != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                toast("تم حفظ ملف PDF")
            } catch (e: Exception) { toast("تعذر حفظ الملف") }
        }
    }

    private fun buildPdf(): ByteArray {
        val W = 595; val H = 842; val R = W - 40f; val L = 40f
        val doc = PdfDocument()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var cv = page.canvas
        var y = 60f
        fun t(s: String, x: Float, yy: Float, size: Float, al: Paint.Align, col: Int = Color.BLACK, bold: Boolean = false) {
            p.textSize = size; p.textAlign = al; p.color = col; p.isFakeBoldText = bold
            cv.drawText(s, x, yy, p)
        }
        fun header() {
            t("التاريخ", R, y, 12f, Paint.Align.RIGHT, Color.DKGRAY, true)
            t("الاسم", R - 90f, y, 12f, Paint.Align.RIGHT, Color.DKGRAY, true)
            t("النوع", 170f, y, 12f, Paint.Align.RIGHT, Color.DKGRAY, true)
            t("المبلغ", L, y, 12f, Paint.Align.LEFT, Color.DKGRAY, true)
            p.color = Color.GRAY
            cv.drawLine(L, y + 6f, R, y + 6f, p)
            y += 26f
        }
        val inc = shown.filter { it.income }.sumOf { it.amount }
        val out = shown.filter { !it.income }.sumOf { it.amount }
        t("تقرير الإيرادات والمصروفات", R, y, 20f, Paint.Align.RIGHT, Color.BLACK, true); y += 28f
        t("الفترة", R, y, 12f, Paint.Align.RIGHT)
        t(df.format(Date(from)) + " - " + df.format(Date(to)), R - 50f, y, 12f, Paint.Align.RIGHT); y += 22f
        t("إجمالي الدخل", R, y, 13f, Paint.Align.RIGHT)
        t("%,.2f".format(inc), R - 110f, y, 13f, Paint.Align.RIGHT, GREEN, true); y += 20f
        t("إجمالي المصروف", R, y, 13f, Paint.Align.RIGHT)
        t("%,.2f".format(out), R - 110f, y, 13f, Paint.Align.RIGHT, RED, true); y += 20f
        t("الصافي", R, y, 13f, Paint.Align.RIGHT)
        t("%,.2f".format(inc - out), R - 110f, y, 13f, Paint.Align.RIGHT, Color.BLACK, true); y += 34f
        header()
        for (x in shown.sortedBy { it.time }) {
            if (y > H - 50f) {
                doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                cv = page.canvas; y = 60f; header()
            }
            t(df.format(Date(x.time)), R, y, 11f, Paint.Align.RIGHT)
            t(x.merchant.take(34), R - 90f, y, 11f, Paint.Align.RIGHT)
            t(if (x.income) "دخل" else "مصروف", 170f, y, 11f, Paint.Align.RIGHT)
            t((if (x.income) "+" else "-") + "%,.2f".format(x.amount), L, y, 11f, Paint.Align.LEFT, if (x.income) GREEN else RED)
            y += 20f
        }
        doc.finishPage(page)
        val os = ByteArrayOutputStream()
        doc.writeTo(os)
        doc.close()
        return os.toByteArray()
    }

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
            Telephony.Sms.DATE + " > ?", arrayOf(since.toString()), null)?.use {
            while (it.moveToNext()) {
                SmsParser.parse(it.getString(0) ?: "", it.getLong(1))?.let { x -> found.add(x) }
            }
        }
        val n = Store.addAll(this, found)
        toast("تمت إضافة $n عملية")
        refresh()
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        if (g.isNotEmpty() && g.all { it == PackageManager.PERMISSION_GRANTED }) importOld()
    }
}

class BarsView(c: Context) : View(c) {
    var inc = 0.0
    var out = 0.0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val max = maxOf(inc, out, 1.0)
        val bw = w / 4f
        val base = h - 44f
        val items = listOf(Triple("الدخل", inc, GREEN), Triple("المصروف", out, RED))
        items.forEachIndexed { i, (name, v, col) ->
            val left = w * (0.15f + i * 0.45f)
            val bh = ((v / max) * (base - 40f)).toFloat()
            p.color = col
            cv.drawRect(left, base - bh, left + bw, base, p)
            p.color = Color.GRAY; p.textSize = 30f; p.textAlign = Paint.Align.CENTER
            cv.drawText(name, left + bw / 2, h - 8f, p)
            cv.drawText("%,.0f".format(v), left + bw / 2, base - bh - 10f, p)
        }
    }
}

class TopView(c: Context) : View(c) {
    private var rows: List<Pair<String, Double>> = emptyList()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    fun update(r: List<Pair<String, Double>>) { rows = r; invalidate() }
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val max = rows.maxOfOrNull { it.second } ?: 1.0
        rows.forEachIndexed { i, (n, v) ->
            val y = i * 76f
            p.color = Color.GRAY; p.textSize = 28f
            p.textAlign = Paint.Align.RIGHT
            cv.drawText(n.take(24), w - 4f, y + 28f, p)
            p.textAlign = Paint.Align.LEFT
            cv.drawText("%,.0f".format(v), 4f, y + 28f, p)
            p.color = RED
            val bw = ((v / max) * (w - 8f)).toFloat()
            cv.drawRect(w - 4f - bw, y + 38f, w - 4f, y + 56f, p)
        }
    }
}
