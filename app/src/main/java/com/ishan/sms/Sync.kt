package com.ishan.sms

import android.content.Context

class SyncResult(val uploaded: Int, val errors: List<String>)

object Sync {
    private val lock = Any()

    /** ব্লকিং — সবসময় ব্যাকগ্রাউন্ড থ্রেড থেকে ডাকুন */
    fun run(c: Context): SyncResult = synchronized(lock) {
        val app = c.applicationContext
        val projects = Projects.all(app).filter { it.active }
        Store.rematch(app, projects)
        var uploaded = 0
        val errors = mutableListOf<String>()

        for (p in projects) {
            try {
                Api.handshake(p)
                for (s in Store.all(app).filter { p.id in it.matched && p.id !in it.uploaded && !it.used }) {
                    Api.putIfAbsent(p, s)
                    Store.mutate(app, s.trx) { it.uploaded.add(p.id) }
                    uploaded++
                }
                // ওয়েবসাইটে ব্যবহৃত হয়েছে কিনা দেখে নেওয়া
                for (s in Store.all(app).filter { p.id in it.uploaded && !it.used }.take(40)) {
                    if (Api.isUsed(p, s.trx) == true) Store.mutate(app, s.trx) { it.used = true }
                }
                Projects.states[p.id] = "ok"
            } catch (e: KeyDisabled) {
                Projects.states[p.id] = "off"; errors.add("${p.name}: ${e.message}")
            } catch (e: Exception) {
                Projects.states[p.id] = "err"; errors.add("${p.name}: ${e.message ?: "নেটওয়ার্ক সমস্যা"}")
            }
        }
        Prefs.setLastSync(app, System.currentTimeMillis())
        Bus.changed()
        SyncResult(uploaded, errors)
    }
}
