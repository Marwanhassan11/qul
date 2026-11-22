package com.example.qul

import androidx.compose.ui.graphics.Color

data class Cat(val id: String, val name: String, val icon: String, val color: Color)

object Cats {
    val all = listOf(
        Cat("food", "طعام", "🍽️", Color(0xFFE8590C)),
        Cat("transport", "مواصلات", "🚗", Color(0xFF1C7ED6)),
        Cat("bills", "فواتير", "🧾", Color(0xFF7048E8)),
        Cat("shop", "تسوق", "🛍️", Color(0xFFD6336C)),
        Cat("health", "صحة", "💊", Color(0xFF2F9E44)),
        Cat("fun", "ترفيه", "🎬", Color(0xFFF08C00)),
        Cat("transfer", "تحويلات", "🔁", Color(0xFF0C8599)),
        Cat("income", "دخل", "💼", Color(0xFF0B6E6E)),
        Cat("other", "أخرى", "📦", Color(0xFF868E96))
    )

    fun get(id: String): Cat = all.firstOrNull { it.id == id } ?: all.last()

    private val I = RegexOption.IGNORE_CASE
    private val rules: List<Pair<String, Regex>> = listOf(
        "transfer" to Regex("حواله|تحويل|ايبان|iban|transfer|remit|western union|stc pay|urpay|sarie", I),
        "food" to Regex("اكل|غدا|عشا|فطور|مطعم|قهو|كافيه|بقال|سوبر|ماركت|شاورما|برجر|بيتزا|عصير|مخبز|حلا|starbucks|coffee|cafe|restaurant|kfc|mcdonald|burger|pizza|bakery|panda|danube|othaim|tamimi|lulu|carrefour|hungerstation|jahez|mrsool|talabat|dunkin|tim hortons|albaik|baik", I),
        "transport" to Regex("بنزين|وقود|اوبر|كريم|تاكسي|مواصلات|مترو|موقف|ديزل|uber|careem|taxi|petrol|fuel|aramco|sasco|adnoc|enoc|shell|parking|bolt|metro", I),
        "bills" to Regex("فاتور|كهرباء|مويه|انترنت|اتصالات|ايجار|قسط|اقساط|تامين|سداد|stc|mobily|zain|electric|water|internet|rent|insurance|sadad|bill|utility|telecom", I),
        "shop" to Regex("ملابس|تسوق|امازون|نون|هديه|جرير|اكسترا|amazon|noon|shein|ikea|jarir|extra|zara|h&m|mall|namshi|temu", I),
        "health" to Regex("دواء|صيدل|دكتور|مستشفي|مستشفى|عياده|مختبر|تحليل|pharmacy|nahdi|dawaa|hospital|clinic|medical", I),
        "fun" to Regex("سينما|نتفلكس|العاب|ترفيه|رحله|سفر|فندق|طيران|netflix|spotify|cinema|vox|steam|playstation|xbox|youtube|apple\\.com|google play|shahid|booking|airline|flynas|saudia", I)
    )

    private fun norm(s: String) = s.replace(Regex("[أإآ]"), "ا").replace("ة", "ه").replace("ى", "ي")

    fun guess(merchant: String, income: Boolean): String {
        if (income) return "income"
        val t = norm(merchant)
        return rules.firstOrNull { it.second.containsMatchIn(t) }?.first ?: "other"
    }
}
