package com.ishan.sms

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HttpError(val code: Int, msg: String) : Exception(msg)
class KeyDisabled(msg: String) : Exception(msg)

/** Firebase Realtime Database REST: readers/{key}, sms_inbox/{TrxID} */
object Api {
    private fun base(p: Project) = p.url.trim().trimEnd('/')

    private fun req(method: String, url: String, body: String? = null, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15000; c.readTimeout = 20000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally { c.disconnect() }
    }

    /** Key আছে ও active কিনা যাচাই; reader-এর নাম ফেরত দেয় */
    fun checkKey(p: Project): String {
        val (code, text) = req("GET", "${base(p)}/readers/${p.key.trim()}.json")
        if (code == 401 || code == 403) throw HttpError(code, "Database rules অনুমতি দিচ্ছে না")
        if (code != 200) throw HttpError(code, "সার্ভার সমস্যা ($code)")
        if (text.trim() == "null") throw KeyDisabled("এই Key পাওয়া যায়নি")
        val o = JSONObject(text)
        if (!o.optBoolean("active", false)) throw KeyDisabled("এই Key বন্ধ করা আছে")
        return o.optString("name", "Reader")
    }

    /** Key যাচাই + lastSeen আপডেট */
    fun handshake(p: Project) {
        checkKey(p)
        val (code, _) = req("PUT", "${base(p)}/readers/${p.key.trim()}/lastSeen.json",
            JSONObject.quote(U.dbTime(System.currentTimeMillis())))
        if (code >= 400) throw HttpError(code, "lastSeen আপডেট হয়নি ($code)")
    }

    /** sms_inbox/{TrxID} শুধু তখনই লেখে যখন আগে নেই (if-match: null_etag) */
    fun putIfAbsent(p: Project, s: Sms) {
        val o = JSONObject().put("method", s.method).put("amount", s.amount).put("sender", s.sender)
            .put("time", U.dbTime(s.receivedAt)).put("receivedAt", s.receivedAt).put("used", false)
            .put("sim", s.sim).put("reader", p.key.trim())
        if (s.manual) o.put("manual", true)
        val (code, _) = req("PUT", "${base(p)}/sms_inbox/${s.trx}.json", o.toString(), mapOf("if-match" to "null_etag"))
        if (code == 412) return                       // আগেই আছে → ঠিক আছে
        if (code == 401 || code == 403) throw HttpError(code, "Database rules অনুমতি দিচ্ছে না")
        if (code >= 400) throw HttpError(code, "আপলোড ব্যর্থ ($code)")
    }

    /** null = রেকর্ড নেই বা পড়া যায়নি */
    fun isUsed(p: Project, trx: String): Boolean? {
        val (code, text) = req("GET", "${base(p)}/sms_inbox/$trx/used.json")
        if (code != 200) return null
        return when (text.trim()) { "true" -> true; "false" -> false; else -> null }
    }
}
