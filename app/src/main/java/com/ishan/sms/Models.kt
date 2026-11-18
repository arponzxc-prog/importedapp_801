package com.ishan.sms

import org.json.JSONArray
import org.json.JSONObject

/** একটি Firebase প্রজেক্ট (Database URL + Reader Key) ও তার ফিল্টার */
class Project(
    val id: String,
    var name: String,
    var url: String,
    var key: String,
    var sim: Int = 0,                       // 0 = যেকোনো, 1 = SIM 1, 2 = SIM 2
    var methods: Set<String> = emptySet(),  // খালি = সব মেথড (bkash / nagad / rocket)
    var active: Boolean = true
) {
    fun toJson() = JSONObject().put("id", id).put("name", name).put("url", url).put("key", key)
        .put("sim", sim).put("methods", JSONArray(methods.toList())).put("active", active)

    fun matches(s: Sms): Boolean {
        if (!active) return false
        if (!s.manual && sim != 0 && s.sim != 0 && sim != s.sim) return false
        if (methods.isNotEmpty() && s.method.lowercase() !in methods) return false
        return true
    }

    companion object {
        fun from(o: JSONObject): Project {
            val m = mutableSetOf<String>()
            o.optJSONArray("methods")?.let { for (i in 0 until it.length()) m.add(it.getString(i)) }
            return Project(o.getString("id"), o.optString("name"), o.optString("url"), o.optString("key"),
                o.optInt("sim", 0), m, o.optBoolean("active", true))
        }
    }
}

/** একটি পেমেন্ট SMS (বা ম্যানুয়ালি যোগ করা TrxID) */
class Sms(
    val trx: String,
    val method: String,      // bKash / Nagad / Rocket
    val amount: Double,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val sim: Int,            // 0 = অজানা
    val manual: Boolean = false
) {
    val matched = mutableSetOf<String>()    // যে প্রজেক্টগুলোর ফিল্টারে মেলে
    val uploaded = mutableSetOf<String>()   // যেগুলোতে আপলোড হয়ে গেছে
    var used = false

    val ignored get() = !used && matched.isEmpty()
    val pending get() = !used && matched.isNotEmpty() && matched.any { it !in uploaded }

    fun toJson() = JSONObject().put("trx", trx).put("method", method).put("amount", amount)
        .put("sender", sender).put("body", body).put("receivedAt", receivedAt).put("sim", sim)
        .put("manual", manual).put("used", used)
        .put("matched", JSONArray(matched.toList())).put("uploaded", JSONArray(uploaded.toList()))

    fun copy(): Sms = from(toJson())

    companion object {
        fun from(o: JSONObject): Sms {
            val s = Sms(o.getString("trx"), o.optString("method"), o.optDouble("amount", 0.0),
                o.optString("sender"), o.optString("body"), o.optLong("receivedAt"),
                o.optInt("sim", 0), o.optBoolean("manual", false))
            s.used = o.optBoolean("used", false)
            o.optJSONArray("matched")?.let { for (i in 0 until it.length()) s.matched.add(it.getString(i)) }
            o.optJSONArray("uploaded")?.let { for (i in 0 until it.length()) s.uploaded.add(it.getString(i)) }
            return s
        }
    }
}
