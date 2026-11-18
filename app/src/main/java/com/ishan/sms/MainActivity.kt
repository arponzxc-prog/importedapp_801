package com.ishan.sms

import android.Manifest
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.ChipGroup
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val ui = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()
    private lateinit var adapter: SmsAdapter
    private var filter = "all"
    private var query = ""
    private var lastError: String? = null

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ensureService(); refresh()
    }

    private fun <T : View> v(id: Int): T = findViewById(id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        v<View>(R.id.btnAddFirst).setOnClickListener { showProjectDialog(null) }
        v<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { showPage(it.itemId); true }
        v<View>(R.id.btnSync).setOnClickListener { syncNow(true) }

        // SMS পেজ
        adapter = SmsAdapter { showSmsDetail(it) }
        v<androidx.recyclerview.widget.RecyclerView>(R.id.rvSms).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity); adapter = this@MainActivity.adapter
        }
        v<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString()?.trim() ?: ""; renderSms() }
        })
        v<ChipGroup>(R.id.chipGroup).setOnCheckedStateChangeListener { _, ids ->
            filter = when (ids.firstOrNull()) {
                R.id.chipUnused -> "unused"; R.id.chipUsed -> "used"; R.id.chipPending -> "pending"
                R.id.chipIgnored -> "ignored"; else -> "all"
            }
            renderSms()
        }
        v<View>(R.id.fabAddTrx).setOnClickListener { showManualDialog() }   // ★ নতুন ফিচার

        // সেটিংস পেজ
        v<View>(R.id.btnAddProject).setOnClickListener { showProjectDialog(null) }
        v<View>(R.id.rowSms).setOnClickListener { permLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS)) }
        v<View>(R.id.rowBattery).setOnClickListener { askBattery() }
        v<View>(R.id.rowNotif).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
        v<View>(R.id.rowAuto).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        v<SwitchCompat>(R.id.swKeepAlive).apply {
            isChecked = Prefs.keepAlive(this@MainActivity)
            setOnCheckedChangeListener { _, on ->
                Prefs.setKeepAlive(this@MainActivity, on)
                if (on) KeepAliveService.start(this@MainActivity) else KeepAliveService.stop(this@MainActivity)
            }
        }
        v<View>(R.id.rowKeep).setOnClickListener { v<SwitchCompat>(R.id.swKeepAlive).toggle() }
        v<View>(R.id.btnAllPerm).setOnClickListener {
            val l = mutableListOf(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
            permLauncher.launch(l.toTypedArray()); askBattery()
        }
        v<View>(R.id.btnSyncNow).setOnClickListener { syncNow(true) }
        v<View>(R.id.btnClear).setOnClickListener {
            AlertDialog.Builder(this).setTitle("লোকাল হিস্টোরি মুছবেন?")
                .setMessage("শুধু এই ফোনের তালিকা মুছবে। Firebase-এর ডাটা মুছবে না।")
                .setPositiveButton("মুছুন") { _, _ -> Store.clear(this); refresh() }
                .setNegativeButton("বাতিল", null).show()
        }
        v<TextView>(R.id.tvAbout).text = "SMS Reader v1.0\nশুধু bKash / Nagad / Rocket-এর \"টাকা পেয়েছেন\" SMS পড়া হয়; OTP বা অন্য SMS কখনো আপলোড হয় না।"

        showPage(R.id.nav_home)
        if (!Prefs.asked(this)) { Prefs.setAsked(this); permLauncher.launch(permissionsNeeded()) }
    }

    override fun onResume() {
        super.onResume()
        Bus.listener = { refresh() }
        refresh(); ensureService(); syncNow(false)
    }

    override fun onPause() { Bus.listener = null; super.onPause() }

    // ---------- নেভিগেশন ----------
    private fun showPage(id: Int) {
        v<View>(R.id.pageHome).visibility = if (id == R.id.nav_home) View.VISIBLE else View.GONE
        v<View>(R.id.pageSms).visibility = if (id == R.id.nav_sms) View.VISIBLE else View.GONE
        v<View>(R.id.pageSettings).visibility = if (id == R.id.nav_settings) View.VISIBLE else View.GONE
    }

    // ---------- পারমিশন ----------
    private fun permissionsNeeded(): Array<String> {
        val l = mutableListOf(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        return l.toTypedArray()
    }
    private fun smsGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    private fun notifGranted() = if (Build.VERSION.SDK_INT >= 33)
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    else NotificationManagerCompat.from(this).areNotificationsEnabled()
    private fun batteryOk() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun askBattery() {
        if (batteryOk()) return
        try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
        catch (_: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    private fun ensureService() {
        if (Prefs.keepAlive(this) && Projects.all(this).isNotEmpty()) KeepAliveService.start(this)
    }

    // ---------- সিঙ্ক ----------
    private fun syncNow(toast: Boolean) {
        if (Projects.all(this).isEmpty()) return
        bg.execute {
            val r = try { Sync.run(this) } catch (e: Exception) { SyncResult(0, listOf(e.message ?: "ত্রুটি")) }
            ui.post {
                lastError = r.errors.firstOrNull()
                if (toast) Toast.makeText(this, if (r.errors.isEmpty()) "সিঙ্ক সম্পন্ন (${r.uploaded}টি আপলোড)" else "সিঙ্কে সমস্যা", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    // ---------- UI রিফ্রেশ ----------
    private fun refresh() {
        val projects = Projects.all(this)
        val has = projects.isNotEmpty()
        v<View>(R.id.loginRoot).visibility = if (has) View.GONE else View.VISIBLE
        v<View>(R.id.mainRoot).visibility = if (has) View.VISIBLE else View.GONE
        adapter.projectNames = projects.associate { it.id to it.name }
        renderHome(projects); renderSms(); renderSettings(projects)
    }

    private fun renderHome(projects: List<Project>) {
        val all = Store.all(this)
        val start = U.startOfDay()
        val today = all.filter { it.receivedAt >= start }
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        v<TextView>(R.id.tvGreeting).text = when (h) { in 5..11 -> "শুভ সকাল"; in 12..15 -> "শুভ দুপুর"; in 16..17 -> "শুভ বিকাল"; in 18..19 -> "শুভ সন্ধ্যা"; else -> "শুভ রাত্রি" }
        val online = smsGranted() && projects.any { Projects.states[it.id] == "ok" } && lastError == null
        v<View>(R.id.dotStatus).background.mutate().setTint(if (online) Color.parseColor("#22A06B") else Color.parseColor("#E53E3E"))
        v<TextView>(R.id.tvStatus).text = "SMS Reader • " + if (online) "Online" else "Offline"
        v<TextView>(R.id.tvToday).text = today.size.toString()
        v<RingView>(R.id.ring).progress = if (today.isEmpty()) 0f else today.count { it.used } / today.size.toFloat()
        v<TextView>(R.id.tvUnused).text = all.count { !it.used && !it.ignored }.toString()
        v<TextView>(R.id.tvUsed).text = all.count { it.used }.toString()
        v<TextView>(R.id.tvTaka).text = "৳ " + U.money(today.sumOf { it.amount })
        v<TextView>(R.id.tvPending).text = all.count { it.pending }.toString()

        val vals = mutableListOf<Int>(); val labels = mutableListOf<String>()
        for (d in 6 downTo 0) {
            val s = U.startOfDay() - d * 86_400_000L
            vals.add(all.count { it.receivedAt >= s && it.receivedAt < s + 86_400_000L })
            val c = Calendar.getInstance().apply { timeInMillis = s + 3_600_000L }
            labels.add(U.DAYS[c.get(Calendar.DAY_OF_WEEK) - 1])
        }
        v<BarChartView>(R.id.chart).set(vals, labels)

        val err = v<TextView>(R.id.tvError)
        val msg = when {
            !smsGranted() -> "SMS পারমিশন দেওয়া নেই — সেটিংসে গিয়ে চালু করুন"
            else -> lastError
        }
        err.text = msg; err.visibility = if (msg == null) View.GONE else View.VISIBLE
        val ls = Prefs.lastSync(this)
        v<TextView>(R.id.tvLastSync).text = if (ls == 0L) "এখনো সিঙ্ক হয়নি" else "সর্বশেষ সিঙ্ক: " + U.shortTime(ls)
    }

    private fun renderSms() {
        var l = Store.all(this).sortedByDescending { it.receivedAt }
        l = when (filter) {
            "unused" -> l.filter { !it.used && !it.ignored }
            "used" -> l.filter { it.used }
            "pending" -> l.filter { it.pending }
            "ignored" -> l.filter { it.ignored }
            else -> l
        }
        if (query.isNotEmpty()) {
            val q = query.lowercase()
            l = l.filter { it.trx.lowercase().contains(q) || it.sender.contains(q) || U.money(it.amount).replace(",", "").contains(q) || it.amount.toString().contains(q) }
        }
        adapter.submit(l)
        v<TextView>(R.id.tvCount).text = "${l.size}টি SMS"
        v<View>(R.id.tvEmpty).visibility = if (l.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderSettings(projects: List<Project>) {
        val box = v<LinearLayout>(R.id.projectList); box.removeAllViews()
        projects.forEach { p ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_project, box, false)
            row.findViewById<TextView>(R.id.tvPrjName).text = p.name
            row.findViewById<TextView>(R.id.tvPrjHost).text = Uri.parse(p.url).host ?: p.url
            row.findViewById<TextView>(R.id.tvPrjFilter).text =
                "SIM: " + (if (p.sim == 0) "যেকোনো" else p.sim.toString()) + " • " +
                    (if (p.methods.isEmpty()) "সব মেথড" else p.methods.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } })
            val st = row.findViewById<TextView>(R.id.tvPrjState)
            when (Projects.states[p.id]) {
                "ok" -> { st.text = "সংযুক্ত"; st.setTextColor(Color.parseColor("#1B7F55")) }
                "off" -> { st.text = "বন্ধ"; st.setTextColor(Color.parseColor("#C2255C")) }
                "err" -> { st.text = "সমস্যা"; st.setTextColor(Color.parseColor("#D97706")) }
                else -> { st.text = "—"; st.setTextColor(Color.parseColor("#6B6785")) }
            }
            row.setOnClickListener {
                AlertDialog.Builder(this).setTitle(p.name).setItems(arrayOf("এডিট করুন", "মুছে ফেলুন")) { _, w ->
                    if (w == 0) showProjectDialog(p) else AlertDialog.Builder(this).setTitle("প্রজেক্ট মুছবেন?")
                        .setPositiveButton("মুছুন") { _, _ -> Projects.remove(this, p.id); refresh() }
                        .setNegativeButton("বাতিল", null).show()
                }.show()
            }
            box.addView(row)
        }
        fun st(id: Int, ok: Boolean) { val t = v<TextView>(id); t.text = if (ok) "চালু ✓" else "বন্ধ"; t.setTextColor(Color.parseColor(if (ok) "#1B7F55" else "#C2255C")) }
        st(R.id.stSms, smsGranted()); st(R.id.stBattery, batteryOk()); st(R.id.stNotif, notifGranted())
    }

    private fun showSmsDetail(s: Sms) {
        val b = AlertDialog.Builder(this).setTitle("${s.method} • ৳ ${U.money(s.amount)}")
            .setMessage("TrxID: ${s.trx}\nপ্রেরক: ${s.sender}\nসময়: ${U.shortTime(s.receivedAt)}\n\n${s.body}")
            .setPositiveButton("TrxID কপি") { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("trx", s.trx))
                Toast.makeText(this, "কপি হয়েছে", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("বন্ধ", null)
        if (!s.used) b.setNeutralButton("লোকাল থেকে মুছুন") { _, _ -> Store.remove(this, s.trx); refresh() }
        b.show()
    }

    // ---------- প্রজেক্ট যোগ/এডিট ----------
    private fun showProjectDialog(edit: Project?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_project, null)
        val etName = view.findViewById<EditText>(R.id.etPName)
        val etUrl = view.findViewById<EditText>(R.id.etPUrl)
        val etKey = view.findViewById<EditText>(R.id.etPKey)
        val rg = view.findViewById<RadioGroup>(R.id.rgSim)
        val cbs = mapOf("bkash" to view.findViewById<CheckBox>(R.id.cbBkash), "nagad" to view.findViewById<CheckBox>(R.id.cbNagad), "rocket" to view.findViewById<CheckBox>(R.id.cbRocket))
        val msg = view.findViewById<TextView>(R.id.tvPMsg)
        edit?.let {
            etName.setText(it.name); etUrl.setText(it.url); etKey.setText(it.key)
            rg.check(when (it.sim) { 1 -> R.id.rbSim1; 2 -> R.id.rbSim2; else -> R.id.rbAny })
            cbs.forEach { (k, cb) -> cb.isChecked = k in it.methods }
        }
        val d = AlertDialog.Builder(this).setTitle(if (edit == null) "প্রজেক্ট যোগ করুন" else "প্রজেক্ট এডিট")
            .setView(view).setPositiveButton("সংরক্ষণ", null).setNegativeButton("বাতিল", null).create()
        d.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { btn ->
            val name = etName.text.toString().trim()
            val url = etUrl.text.toString().trim().trimEnd('/')
            val key = etKey.text.toString().trim()
            when {
                name.isEmpty() -> { msg.text = "প্রজেক্টের নাম দিন"; return@setOnClickListener }
                !Regex("^https://[A-Za-z0-9.-]+\\.(firebaseio\\.com|firebasedatabase\\.app)$").matches(url) ->
                { msg.text = "সঠিক Firebase Database URL দিন (https://...firebaseio.com)"; return@setOnClickListener }
                key.isEmpty() -> { msg.text = "Reader Key দিন"; return@setOnClickListener }
            }
            val p = Project(edit?.id ?: UUID.randomUUID().toString(), name, url, key,
                when (rg.checkedRadioButtonId) { R.id.rbSim1 -> 1; R.id.rbSim2 -> 2; else -> 0 },
                cbs.filter { it.value.isChecked }.keys)
            btn.isEnabled = false; msg.setTextColor(Color.parseColor("#6B6785")); msg.text = "Key যাচাই করা হচ্ছে..."
            bg.execute {
                try {
                    val reader = Api.checkKey(p)
                    ui.post {
                        Projects.upsert(this, p); Projects.states[p.id] = "ok"
                        Toast.makeText(this, "সংযুক্ত: $reader", Toast.LENGTH_SHORT).show()
                        d.dismiss(); refresh(); ensureService(); syncNow(false)
                    }
                } catch (e: Exception) {
                    ui.post { btn.isEnabled = true; msg.setTextColor(Color.parseColor("#C2255C")); msg.text = e.message ?: "সংযোগ ব্যর্থ" }
                }
            }
        }
    }

    // ---------- ★ নতুন ফিচার: অ্যাপ থেকে ম্যানুয়ালি TrxID যোগ ----------
    private fun showManualDialog() {
        if (Projects.all(this).isEmpty()) { Toast.makeText(this, "আগে একটি প্রজেক্ট যোগ করুন", Toast.LENGTH_SHORT).show(); return }
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_manual, null)
        val rg = view.findViewById<RadioGroup>(R.id.rgMethod)
        val etTrx = view.findViewById<EditText>(R.id.etMTrx)
        val etAmt = view.findViewById<EditText>(R.id.etMAmount)
        val etFrom = view.findViewById<EditText>(R.id.etMSender)
        val msg = view.findViewById<TextView>(R.id.tvMMsg)

        view.findViewById<View>(R.id.btnPaste).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
            val p = SmsParser.parseBody(text)
            if (p == null) { msg.text = "ক্লিপবোর্ডে সঠিক পেমেন্ট SMS পাওয়া যায়নি"; return@setOnClickListener }
            msg.text = ""
            etTrx.setText(p.trx); etAmt.setText(if (p.amount % 1.0 == 0.0) p.amount.toLong().toString() else p.amount.toString())
            etFrom.setText(p.from)
            rg.check(when (p.method) { "Nagad" -> R.id.rbMNagad; "Rocket" -> R.id.rbMRocket; else -> R.id.rbMBkash })
        }

        val d = AlertDialog.Builder(this).setTitle("TrxID যোগ করুন").setView(view)
            .setPositiveButton("যোগ করুন", null).setNegativeButton("বাতিল", null).create()
        d.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val trx = etTrx.text.toString().trim().uppercase()
            val amount = etAmt.text.toString().trim().toDoubleOrNull() ?: 0.0
            val method = when (rg.checkedRadioButtonId) { R.id.rbMNagad -> "Nagad"; R.id.rbMRocket -> "Rocket"; else -> "bKash" }
            when {
                !Regex("^[A-Z0-9]{6,20}$").matches(trx) -> { msg.text = "TrxID ৬–২০ অক্ষরের (ইংরেজি অক্ষর/সংখ্যা) হতে হবে"; return@setOnClickListener }
                amount <= 0 -> { msg.text = "সঠিক পরিমাণ দিন"; return@setOnClickListener }
                Store.has(this, trx) -> { msg.text = "এই TrxID আগে থেকেই তালিকায় আছে"; return@setOnClickListener }
            }
            val s = Sms(trx, method, amount, etFrom.text.toString().trim().ifEmpty { "manual" },
                "ম্যানুয়ালি যোগ করা হয়েছে", System.currentTimeMillis(), 0, manual = true)
            Store.add(this, s)
            d.dismiss(); Toast.makeText(this, "যোগ হয়েছে, আপলোড হচ্ছে...", Toast.LENGTH_SHORT).show()
            refresh(); syncNow(false)
        }
    }
}
