package com.example.qul

data class Txn(val id: String, val amount: Double, val income: Boolean,
               val merchant: String, val time: Long, val raw: String,
               val cat: String = "other")

object SmsParser {
    private val I = RegexOption.IGNORE_CASE
    private val skip = Regex("رمز|OTP|verification|كلمة المرور|password|one.?time", I)
    private val incomeRe = Regex("ايداع|إيداع|حوالة واردة|حوالة داخلة|راتب|Credit|Deposit|received|Salary", I)
    private val expenseRe = Regex("شراء|سحب|خصم|مدين|سداد|دفع|حوالة صادرة|Purchase|POS|Debit|Withdraw|Paid|Payment", I)
    private val balanceRe = Regex("(?:الرصيد|Balance|Avail\\w*)[^\\n]*", I)
    private val amt1 = Regex("(?:SAR|SR|ر\\.?\\s?س|ريال|مبلغ|Amount)\\s*[:：]?\\s*(\\d[\\d,]*(?:\\.\\d+)?)", I)
    private val amt2 = Regex("(\\d[\\d,]*(?:\\.\\d+)?)\\s*(?:SAR|SR|ر\\.?\\s?س|ريال)", I)
    private val shopRe = Regex("(?:لدى|عند|at|Merchant|المتجر)\\s*[:：]?\\s*([^\\n\\r]{2,60})", I)
    private val stop = setOf("مبلغ", "بتاريخ", "في", "on", "date", "card", "بطاقة", "amount",
        "sar", "sr", "balance", "الرصيد", "من", "رقم")
    private const val AR = "٠١٢٣٤٥٦٧٨٩"

    private fun norm(s: String) = String(s.map { c ->
        val i = AR.indexOf(c)
        when { i >= 0 -> '0' + i; c == '٫' -> '.'; c == '٬' -> ','; else -> c }
    }.toCharArray())

    fun parse(body: String, time: Long): Txn? {
        val s = norm(body)
        if (skip.containsMatchIn(s)) return null
        val inc = incomeRe.containsMatchIn(s)
        val exp = expenseRe.containsMatchIn(s)
        if (!inc && !exp) return null
        val s2 = s.replace(balanceRe, "")
        val m = amt1.find(s2) ?: amt2.find(s2) ?: return null
        val amount = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null
        val raw = shopRe.find(s2)?.groupValues?.get(1) ?: ""
        val merchant = raw.trim().split(Regex("\\s+"))
            .takeWhile { it.lowercase().trimEnd(':') !in stop }
            .joinToString(" ").trim(' ', ':', '-', '.')
            .ifEmpty { "بنك" }
        val isIncome = inc && !exp
        return Txn("$time-${body.hashCode()}", amount, isIncome, merchant, time, body, Cats.guess(merchant, isIncome))
    }
}
