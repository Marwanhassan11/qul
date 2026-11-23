package com.example.qul

import java.util.Calendar
import kotlin.math.abs

data class Txn(val id: String, val amount: Double, val income: Boolean,
               val merchant: String, val time: Long, val raw: String,
               val cat: String = "other", val src: String = "")

object SmsParser {
    private val I = RegexOption.IGNORE_CASE
    private val skip = Regex("رمز|OTP|verification|كلمة المرور|password|one.?time", I)
    private val incomeRe = Regex("ايداع|إيداع|حوالة واردة|حوالة داخلة|راتب|Credit|Deposit|received|Salary", I)
    private val expenseRe = Regex("شراء|سحب|خصم|مدين|سداد|دفع|حوالة صادرة|Purchase|POS|Debit|Withdraw|Paid|Payment", I)
    private val purchaseRe = Regex("شراء|POS|Purchase", I)
    private val transferRe = Regex("حوال|تحويل|ايبان|iban|transfer|sarie", I)
    private val balanceRe = Regex("(?:الرصيد|Balance|Avail\\w*)[^\\n]*", I)
    private val amt1 = Regex("(?:SAR|SR|ر\\.?\\s?س|ريال|مبلغ|Amount)\\s*[:：]?\\s*(\\d[\\d,]*(?:\\.\\d+)?)", I)
    private val amt2 = Regex("(\\d[\\d,]*(?:\\.\\d+)?)\\s*(?:SAR|SR|ر\\.?\\s?س|ريال)", I)
    private val shopRe = Regex("(?:لدى|عند|at|Merchant|المتجر)\\s*[:：]?\\s*([^\\n\\r]{2,60})", I)
    private val fromLine = Regex("^\\s*من(?:\\s*[:：]\\s*|\\s+)(.+?)\\s*$")
    private val toLine = Regex("^\\s*(?:الى|إلى|المستفيد|اسم المستفيد|المرسل|To|Beneficiary|Sender)\\s*[:：]\\s*(.+?)\\s*$", I)
    private val onlyDigits = Regex("[*\\d\\s]+")
    private val ymd = Regex("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)")
    private val dmy = Regex("(?<!\\d)(\\d{1,2})-(\\d{1,2})-(\\d{2,4})\\s+(\\d{1,2}):(\\d{2})")
    private val hm = Regex("(\\d{1,2}):(\\d{2})")
    private val stop = setOf("مبلغ", "بتاريخ", "في", "on", "date", "card", "بطاقة", "amount",
        "sar", "sr", "balance", "الرصيد", "من", "رقم")
    private const val AR = "٠١٢٣٤٥٦٧٨٩"

    private fun norm(s: String) = String(s.map { c ->
        val i = AR.indexOf(c)
        when { i >= 0 -> '0' + i; c == '٫' -> '.'; c == '٬' -> ','; else -> c }
    }.toCharArray())

    /** اسم المتجر من سطر «من: ...» أو «من ...» (مع تجاهل «من حساب *1234»). */
    private fun merchantFromLines(s: String): String? {
        for (line in s.lines()) {
            val m = fromLine.find(line) ?: continue
            val v = m.groupValues[1].trim()
            if (v.startsWith("حساب") || v.startsWith("بطاق") || onlyDigits.matches(v)) continue
            val clean = v.trimEnd('.', ' ', ':')
            if (clean.isNotEmpty()) return clean
        }
        for (line in s.lines()) {
            val m = toLine.find(line) ?: continue
            val clean = m.groupValues[1].trim().trimEnd('.', ' ', ':')
            if (clean.isNotEmpty() && !onlyDigits.matches(clean)) return clean
        }
        return null
    }

    private fun merchantLegacy(s: String): String {
        val raw = shopRe.find(s)?.groupValues?.get(1) ?: ""
        return raw.trim().split(Regex("\\s+"))
            .takeWhile { it.lowercase().trimEnd(':') !in stop }
            .joinToString(" ").trim(' ', ':', '-', '.')
    }

    /** تاريخ ووقت العملية كما هو مكتوب داخل الرسالة (وإلا null). */
    private fun whenOf(s: String): Long? {
        val c = Calendar.getInstance()
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        val d = ymd.find(s)
        if (d != null) {
            val t = hm.find(s)
            c.set(d.groupValues[1].toInt(), d.groupValues[2].toInt() - 1, d.groupValues[3].toInt(),
                t?.groupValues?.get(1)?.toInt() ?: 12, t?.groupValues?.get(2)?.toInt() ?: 0)
            return c.timeInMillis
        }
        val m = dmy.find(s) ?: return null
        var y = m.groupValues[3].toInt()
        if (y < 100) y += 2000
        c.set(y, m.groupValues[2].toInt() - 1, m.groupValues[1].toInt(),
            m.groupValues[4].toInt(), m.groupValues[5].toInt())
        return c.timeInMillis
    }

    fun isTransferMsg(raw: String) = transferRe.containsMatchIn(raw) && !purchaseRe.containsMatchIn(raw)

    fun parse(body: String, time: Long, src: String = ""): Txn? {
        val s = norm(body)
        if (skip.containsMatchIn(s)) return null
        val inc = incomeRe.containsMatchIn(s)
        val exp = expenseRe.containsMatchIn(s)
        if (!inc && !exp) return null
        val s2 = s.replace(balanceRe, "")
        val m = amt1.find(s2) ?: amt2.find(s2) ?: return null
        val amount = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null
        val merchant = (merchantFromLines(s2) ?: merchantLegacy(s2)).ifEmpty { src.ifEmpty { "بنك" } }
        val isIncome = inc && !exp
        val parsed = try { whenOf(s2) } catch (e: Exception) { null }
        val t = parsed?.takeIf { abs(it - time) < 3 * 86_400_000L } ?: time
        return Txn("$t-${body.hashCode()}", amount, isIncome, merchant, t, body, Cats.guess(merchant, isIncome), src)
    }
}
