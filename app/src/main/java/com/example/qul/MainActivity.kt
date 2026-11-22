@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.qul

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private val GREEN = Color(0xFF0B6E6E)
private val RED = Color(0xFFB42318)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            QulTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { App() }
            }
        }
    }
}

@Composable
fun QulTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF3FC1B8), onPrimary = Color(0xFF00201E),
        primaryContainer = Color(0xFF14313A), onPrimaryContainer = Color(0xFFD5EEEC),
        background = Color(0xFF0E1720), surface = Color(0xFF16222E),
        surfaceVariant = Color(0xFF263544)
    ) else lightColorScheme(
        primary = GREEN, onPrimary = Color.White,
        primaryContainer = Color(0xFFD5EEEC), onPrimaryContainer = Color(0xFF00201E),
        background = Color(0xFFF3F6F6), surface = Color.White,
        surfaceVariant = Color(0xFFE6EDED)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

fun dayStart(t: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = t
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}
fun dayEnd(t: Long) = dayStart(t) + 24L * 3600 * 1000 - 1
fun monthStart(t: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = t
    c.set(Calendar.DAY_OF_MONTH, 1)
    return dayStart(c.timeInMillis)
}
fun shiftMonth(t: Long, by: Int): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = monthStart(t)
    c.add(Calendar.MONTH, by)
    return c.timeInMillis
}
fun monthEnd(t: Long): Long = shiftMonth(t, 1) - 1

fun money(d: Double): String = "%,.2f".format(Locale.US, d)
fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_SHORT).show()

fun pick(ctx: Context, initial: Long, cb: (Long) -> Unit) {
    val c = Calendar.getInstance()
    c.timeInMillis = initial
    DatePickerDialog(ctx, { _, y, m, d ->
        val r = Calendar.getInstance()
        r.set(y, m, d, 12, 0, 0); r.set(Calendar.MILLISECOND, 0)
        cb(r.timeInMillis)
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
}

fun importSms(ctx: Context): Int {
    val since = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
    val found = mutableListOf<Txn>()
    ctx.contentResolver.query(
        Telephony.Sms.Inbox.CONTENT_URI,
        arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
        Telephony.Sms.DATE + " > ?", arrayOf(since.toString()), null
    )?.use { cur ->
        while (cur.moveToNext()) {
            SmsParser.parse(cur.getString(0) ?: "", cur.getLong(1))?.let { found.add(it) }
        }
    }
    return Store.addAll(ctx, found)
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var ver by remember { mutableIntStateOf(0) }
    val now = System.currentTimeMillis()
    var from by remember { mutableLongStateOf(monthStart(now)) }
    var to by remember { mutableLongStateOf(monthEnd(now)) }
    var edit by remember { mutableStateOf<Txn?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var pdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    val reload: () -> Unit = { ver++ }

    val all = remember(ver) { Store.all(ctx) }
    val shown = remember(all, from, to) { all.filter { it.time in from..to } }

    val perms = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) {
            toast(ctx, "تمت إضافة " + importSms(ctx) + " عملية")
            reload()
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val bytes = pdfBytes
        if (uri != null && bytes != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                toast(ctx, "تم حفظ ملف PDF")
            } catch (e: Exception) { toast(ctx, "تعذر حفظ الملف") }
        }
    }
    fun granted() = perms.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    fun startImport() {
        if (granted()) { toast(ctx, "تمت إضافة " + importSms(ctx) + " عملية"); reload() }
        else permLauncher.launch(perms)
    }
    LaunchedEffect(Unit) { if (!granted()) permLauncher.launch(perms) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                listOf("🏠" to "الرئيسية", "🧾" to "العمليات", "⚙️" to "المزيد").forEachIndexed { i, (ic, lb) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Text(ic, fontSize = 20.sp) }, label = { Text(lb) }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { edit = null; showForm = true }) { Text("＋", fontSize = 26.sp) }
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (tab != 2) {
                FilterBar(from, to,
                    onShift = { d -> from = shiftMonth(from, d); to = monthEnd(from) },
                    onFrom = { pick(ctx, from) { from = dayStart(it) } },
                    onTo = { pick(ctx, to) { to = dayEnd(it) } })
            }
            val open: (Txn) -> Unit = { edit = it; showForm = true }
            when (tab) {
                0 -> Home(shown, open)
                1 -> TxnList(shown, open) {
                    if (shown.isEmpty()) toast(ctx, "لا توجد عمليات في هذه الفترة")
                    else { pdfBytes = PdfExport.build(shown, from, to); pdfLauncher.launch("qul-report.pdf") }
                }
                else -> MoreTab(all.size, { startImport() }) {
                    toast(ctx, "تمت إزالة " + Store.dedupe(ctx) + " عملية مكررة"); reload()
                }
            }
        }
    }

    if (showForm) {
        TxnForm(edit, all, onDismiss = { showForm = false }, onSaved = { showForm = false; reload() })
    }
}

@Composable
fun FilterBar(from: Long, to: Long, onShift: (Int) -> Unit, onFrom: () -> Unit, onTo: () -> Unit) {
    val df = remember { SimpleDateFormat("yyyy/MM/dd", Locale.US) }
    val mf = remember { SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("ar-u-nu-latn-ca-gregory")) }
    val isMonth = monthStart(from) == from && monthEnd(from) == to
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onShift(-1) }) { Text("السابق") }
            Text(if (isMonth) mf.format(Date(from)) else "فترة مخصصة",
                fontWeight = FontWeight.Bold, fontSize = 17.sp)
            TextButton(onClick = { onShift(1) }) { Text("التالي") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onFrom, modifier = Modifier.weight(1f)) { Text("من: " + df.format(Date(from))) }
            OutlinedButton(onClick = onTo, modifier = Modifier.weight(1f)) { Text("إلى: " + df.format(Date(to))) }
        }
    }
}

@Composable
fun Home(list: List<Txn>, onOpen: (Txn) -> Unit) {
    val inc = list.filter { it.income }.sumOf { it.amount }
    val out = list.filter { !it.income }.sumOf { it.amount }
    val byCat = list.filter { !it.income }.groupBy { it.cat }
        .map { Pair(Cats.get(it.key), it.value.sumOf { x -> x.amount }) }
        .sortedByDescending { it.second }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("الصافي")
                    Text(money(inc - out), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الدخل  " + money(inc), color = GREEN, fontWeight = FontWeight.SemiBold)
                        Text("المصروف  " + money(out), color = RED, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item { DonutCard(byCat, out) }
        item { Text("آخر العمليات", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        if (list.isEmpty()) item { EmptyState() }
        items(list.take(5), key = { it.id }) { TxnRow(it, onOpen) }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
fun DonutCard(byCat: List<Pair<Cat, Double>>, total: Double) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("المصروف حسب التصنيف", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (total <= 0.0) {
                Text("لا مصروفات في هذه الفترة", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val sw = 28.dp.toPx()
                            var start = -90f
                            byCat.forEach { (c, v) ->
                                val sweep = (v / total * 360.0).toFloat()
                                drawArc(c.color, start, sweep, false,
                                    topLeft = Offset(sw / 2, sw / 2),
                                    size = Size(size.width - sw, size.height - sw),
                                    style = Stroke(sw))
                                start += sweep
                            }
                        }
                        Text("%,.0f".format(Locale.US, total), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        byCat.take(6).forEach { (c, v) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(c.color))
                                Spacer(Modifier.width(6.dp))
                                Text(c.icon + " " + c.name, Modifier.weight(1f), fontSize = 13.sp)
                                Text((v / total * 100).toInt().toString() + "%", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TxnRow(t: Txn, onOpen: (Txn) -> Unit) {
    val cat = Cats.get(t.cat)
    val df = remember { SimpleDateFormat("yyyy/MM/dd", Locale.US) }
    Card(onClick = { onOpen(t) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(cat.color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center) { Text(cat.icon, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.merchant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(cat.name + " • " + df.format(Date(t.time)), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Text("\u200E" + (if (t.income) "+" else "-") + money(t.amount),
                color = if (t.income) GREEN else RED, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EmptyState() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("لا توجد عمليات في هذه الفترة.\nاضغط ＋ لإضافة عملية، أو استورد رسائل البنك من «المزيد».",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
fun TxnList(list: List<Txn>, onOpen: (Txn) -> Unit, onPdf: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var catF by remember { mutableStateOf("") }
    val f = list.filter {
        (catF.isEmpty() || it.cat == catF) &&
            (q.isBlank() || it.merchant.contains(q, true) || Cats.get(it.cat).name.contains(q))
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(q, { q = it }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("بحث بالاسم أو التصنيف") })
            TextButton(onClick = onPdf) { Text("📄 PDF") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(catF.isEmpty(), { catF = "" }, label = { Text("الكل") })
            Cats.all.forEach { c ->
                FilterChip(catF == c.id, { catF = if (catF == c.id) "" else c.id },
                    label = { Text(c.icon + " " + c.name) })
            }
        }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (f.isEmpty()) item { EmptyState() }
            items(f, key = { it.id }) { TxnRow(it, onOpen) }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
fun MoreTab(count: Int, onImport: () -> Unit, onDedupe: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("المزيد", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("رسائل البنك", fontWeight = FontWeight.Bold)
                Text("التطبيق يلتقط رسائل البنك الجديدة تلقائياً. ويمكنك استيراد رسائل آخر 90 يوماً.")
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("📥 استيراد رسائل آخر 90 يوم") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("العمليات المكررة", fontWeight = FontWeight.Bold)
                Text("التطبيق لا يضيف نفس الرسالة مرتين. اضغط هنا لتنظيف أي تكرار قديم.")
                OutlinedButton(onClick = onDedupe, modifier = Modifier.fillMaxWidth()) { Text("🧹 إزالة المكرر") }
            }
        }
        Text("عدد العمليات المخزنة: $count", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text("كل بياناتك محفوظة على جوالك فقط ولا تُرسل لأي جهة.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
fun TxnForm(ex: Txn?, all: List<Txn>, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val df = remember { SimpleDateFormat("yyyy/MM/dd", Locale.US) }
    var name by remember { mutableStateOf(ex?.merchant ?: "") }
    var amt by remember {
        mutableStateOf(ex?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }
    var income by remember { mutableStateOf(ex?.income ?: false) }
    var cat by remember { mutableStateOf(ex?.cat ?: "other") }
    var time by remember { mutableLongStateOf(ex?.time ?: System.currentTimeMillis()) }
    var picked by remember { mutableStateOf(false) }
    var dupAsk by remember { mutableStateOf(false) }

    fun save(force: Boolean) {
        val a = amt.replace(',', '.').toDoubleOrNull()
        if (a == null || a <= 0) { toast(ctx, "المبلغ غير صحيح"); return }
        val n = name.trim().ifEmpty { "بدون اسم" }
        val c = if (income) "income" else cat
        val t = Txn(ex?.id ?: ("m-" + UUID.randomUUID()), a, income, n, time, ex?.raw ?: "", c)
        if (ex == null && !force && Dups.similarManual(all, t)) { dupAsk = true; return }
        Store.put(ctx, t)
        if (picked && !income && n != "بدون اسم") Store.learn(ctx, n, c)
        onSaved()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ex == null) "إضافة عملية" else "تعديل العملية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, {
                    name = it
                    if (!picked && ex == null) cat = Cats.guess(it, false)
                }, label = { Text("اسم العملية") }, singleLine = true)
                OutlinedTextField(amt, { amt = it }, label = { Text("المبلغ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!income, { income = false }, label = { Text("مصروف") })
                    FilterChip(income, { income = true }, label = { Text("دخل") })
                }
                if (!income) {
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Cats.all.filter { it.id != "income" }.forEach { c ->
                            FilterChip(cat == c.id, { cat = c.id; picked = true },
                                label = { Text(c.icon + " " + c.name) })
                        }
                    }
                }
                OutlinedButton(onClick = { pick(ctx, time) { time = it } }) {
                    Text("التاريخ: " + df.format(Date(time)))
                }
            }
        },
        confirmButton = { TextButton(onClick = { save(false) }) { Text("حفظ") } },
        dismissButton = {
            Row {
                if (ex != null) TextButton(onClick = { Store.remove(ctx, ex.id); onSaved() }) {
                    Text("حذف", color = RED)
                }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )

    if (dupAsk) {
        AlertDialog(
            onDismissRequest = { dupAsk = false },
            title = { Text("عملية مشابهة") },
            text = { Text("توجد عملية بنفس المبلغ والنوع في نفس اليوم. هل تضيفها مرة ثانية؟") },
            confirmButton = { TextButton(onClick = { dupAsk = false; save(true) }) { Text("أضف") } },
            dismissButton = { TextButton(onClick = { dupAsk = false }) { Text("إلغاء") } }
        )
    }
}
