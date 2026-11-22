package com.example.qul

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExport {
    fun build(list: List<Txn>, from: Long, to: Long): ByteArray {
        val df = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        val green = 0xFF0B6E6E.toInt()
        val red = 0xFFB42318.toInt()
        val W = 595; val H = 842; val R = W - 40f; val L = 40f
        val doc = PdfDocument()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var cv = page.canvas
        var y = 60f
        fun money(d: Double) = "%,.2f".format(Locale.US, d)
        fun t(s: String, x: Float, yy: Float, size: Float, al: Paint.Align, col: Int = Color.BLACK, bold: Boolean = false) {
            p.textSize = size; p.textAlign = al; p.color = col; p.isFakeBoldText = bold
            cv.drawText(s, x, yy, p)
        }
        fun header() {
            t("التاريخ", R, y, 12f, Paint.Align.RIGHT, Color.DKGRAY, true)
            t("الاسم", R - 90f, y, 12f, Paint.Align.RIGHT, Color.DKGRAY, true)
            t("التصنيف", 210f, y, 12f, Paint.Align.RIGHT, Color.DKGRAY, true)
            t("المبلغ", L, y, 12f, Paint.Align.LEFT, Color.DKGRAY, true)
            p.color = Color.GRAY
            cv.drawLine(L, y + 6f, R, y + 6f, p)
            y += 26f
        }
        val inc = list.filter { it.income }.sumOf { it.amount }
        val out = list.filter { !it.income }.sumOf { it.amount }
        t("تقرير الإيرادات والمصروفات", R, y, 20f, Paint.Align.RIGHT, Color.BLACK, true); y += 28f
        t("الفترة", R, y, 12f, Paint.Align.RIGHT)
        t(df.format(Date(from)) + " - " + df.format(Date(to)), R - 50f, y, 12f, Paint.Align.RIGHT); y += 22f
        t("إجمالي الدخل", R, y, 13f, Paint.Align.RIGHT)
        t(money(inc), R - 110f, y, 13f, Paint.Align.RIGHT, green, true); y += 20f
        t("إجمالي المصروف", R, y, 13f, Paint.Align.RIGHT)
        t(money(out), R - 110f, y, 13f, Paint.Align.RIGHT, red, true); y += 20f
        t("الصافي", R, y, 13f, Paint.Align.RIGHT)
        t(money(inc - out), R - 110f, y, 13f, Paint.Align.RIGHT, Color.BLACK, true); y += 34f
        header()
        for (x in list.sortedBy { it.time }) {
            if (y > H - 50f) {
                doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                cv = page.canvas; y = 60f; header()
            }
            t(df.format(Date(x.time)), R, y, 11f, Paint.Align.RIGHT)
            t(x.merchant.take(34), R - 90f, y, 11f, Paint.Align.RIGHT)
            t(Cats.get(x.cat).name, 210f, y, 11f, Paint.Align.RIGHT)
            t((if (x.income) "+" else "-") + money(x.amount), L, y, 11f, Paint.Align.LEFT, if (x.income) green else red)
            y += 20f
        }
        doc.finishPage(page)
        val os = ByteArrayOutputStream()
        doc.writeTo(os)
        doc.close()
        return os.toByteArray()
    }
}
