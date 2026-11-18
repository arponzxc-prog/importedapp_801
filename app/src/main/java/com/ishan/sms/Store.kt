package com.ishan.sms

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("sms_prefs", Context.MODE_PRIVATE)
    fun keepAlive(c: Context) = sp(c).getBoolean("keep", true)
    fun setKeepAlive(c: Context, v: Boolean) = sp(c).edit().putBoolean("keep", v).apply()
    fun lastSync(c: Context) = sp(c).getLong("last_sync", 0L)
    fun setLastSync(c: Context, t: Long) = sp(c).edit().putLong("last_sync", t).apply()
    fun asked(c: Context) = sp(c).getBoolean("asked", false)
    fun setAsked(c: Context) = sp(c).edit().putBoolean("asked", true).apply()
    fun projectsJson(c: Context) = sp(c).getString("projects", "[]") ?: "[]"
    fun setProjectsJson(c: Context, s: String) = sp(c).edit().putString("projects", s).apply()
}

object Projects {
    /** ok / off (Key বন্ধ বা নেই) / err (নেটওয়ার্ক/অন্য সমস্যা) */
    val states = ConcurrentHashMap<String, String>()

    @Synchronized fun all(c: Context): MutableList<Project> {
        val a = JSONArray(Prefs.projectsJson(c))
        return MutableList(a.length()) { Project.from(a.getJSONObject(it)) }
    }

    @Synchronized private fun save(c: Context, l: List<Project>) {
        val a = JSONArray(); l.forEach { a.put(it.toJson()) }
        Prefs.setProjectsJson(c, a.toString())
    }

    @Synchronized fun upsert(c: Context, p: Project) {
        val l = all(c); val i = l.indexOfFirst { it.id == p.id }
        if (i >= 0) l[i] = p else l.add(p)
        save(c, l)
    }

    @Synchronized fun remove(c: Context, id: String) { save(c, all(c).filter { it.id != id }); states.remove(id) }
}

object Store {
    private const val MAX = 2000
    private var cache: MutableList<Sms>? = null

    private fun file(c: Context) = File(c.filesDir, "sms_store.json")

    private fun load(c: Context): MutableList<Sms> {
        cache?.let { return it }
        val l = mutableListOf<Sms>()
        try {
            val f = file(c)
            if (f.exists()) {
                val a = JSONArray(f.readText())
                for (i in 0 until a.length()) l.add(Sms.from(a.getJSONObject(i)))
            }
        } catch (_: Exception) { }
        cache = l
        return l
    }

    private fun persist(c: Context) {
        val a = JSONArray(); load(c).forEach { a.put(it.toJson()) }
        val tmp = File(c.filesDir, "sms_store.tmp")
        tmp.writeText(a.toString()); tmp.renameTo(file(c))
    }

    @Synchronized fun all(c: Context): List<Sms> = load(c).map { it.copy() }
    @Synchronized fun has(c: Context, trx: String) = load(c).any { it.trx == trx }

    /** নতুন হলে true; একই TrxID আগে থাকলে false */
    @Synchronized fun add(c: Context, s: Sms): Boolean {
        val l = load(c)
        if (l.any { it.trx == s.trx }) return false
        l.add(s)
        while (l.size > MAX) l.removeAt(0)
        persist(c); return true
    }

    @Synchronized fun mutate(c: Context, trx: String, fn: (Sms) -> Unit) {
        load(c).firstOrNull { it.trx == trx }?.let { fn(it); persist(c) }
    }

    @Synchronized fun remove(c: Context, trx: String) { load(c).removeAll { it.trx == trx }; persist(c) }
    @Synchronized fun clear(c: Context) { load(c).clear(); persist(c) }

    /** প্রজেক্ট বদলালে/যোগ হলে ফিল্টার নতুন করে মেলানো (ব্যবহৃত SMS বাদে) */
    @Synchronized fun rematch(c: Context, projects: List<Project>) {
        var changed = false
        load(c).forEach { s ->
            if (s.used) return@forEach
            val m = projects.filter { it.matches(s) }.map { it.id }.toMutableSet()
            m.addAll(s.uploaded.filter { id -> projects.any { it.id == id } })
            if (m != s.matched) { s.matched.clear(); s.matched.addAll(m); changed = true }
        }
        if (changed) persist(c)
    }

    @Synchronized fun hasPending(c: Context) = load(c).any { it.pending }
}
