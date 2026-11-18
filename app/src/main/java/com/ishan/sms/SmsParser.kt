package com.ishan.sms

object SmsParser {
    class Payment(val method: String, val trx: String, val amount: Double, val from: String)

    private val AMOUNT = Regex("(?:Tk|BDT|\\u09F3)\\s*:?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
    private val TRX = Regex("(?:TrxID|TxnID|Trx\\s*ID|Txn\\s*ID|TransactionID)\\s*[:#]?\\s*([A-Za-z0-9]{6,20})", RegexOption.IGNORE_CASE)
    private val FROM = Regex("(?:from|Sender:?)\\s*(01[0-9]{9})", RegexOption.IGNORE_CASE)

    /** শুধু পরিচিত পেমেন্ট প্রোভাইডারের সেন্ডার; অন্য কারো SMS কখনো পড়া/আপলোড হয় না */
    fun methodOfSender(address: String): String? {
        val a = address.lowercase()
        return when {
            a.contains("bkash") -> "bKash"
            a.contains("nagad") -> "Nagad"
            a.contains("rocket") || a == "16216" || a.contains("dbbl") -> "Rocket"
            else -> null
        }
    }

    fun methodOfBody(body: String): String? {
        val b = body.lowercase()
        return when {
            b.contains("bkash") -> "bKash"
            b.contains("nagad") -> "Nagad"
            b.contains("rocket") || b.contains("dbbl") -> "Rocket"
            else -> null
        }
    }

    private fun isReceive(body: String) =
        body.contains("received", true) || body.contains("পেয়েছেন") || body.contains("cash in", true)

    /** আসল SMS (সেন্ডার সহ) */
    fun parse(address: String, body: String): Payment? {
        val method = methodOfSender(address) ?: return null
        if (!isReceive(body)) return null
        return extract(method, body)
    }

    /** ক্লিপবোর্ডে কপি করা SMS টেক্সট (ম্যানুয়াল যোগের জন্য), সেন্ডার ছাড়া */
    fun parseBody(body: String): Payment? {
        val method = methodOfBody(body) ?: return null
        return extract(method, body)
    }

    private fun extract(method: String, body: String): Payment? {
        val trx = TRX.find(body)?.groupValues?.get(1)?.uppercase() ?: return null
        val amount = AMOUNT.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: return null
        if (amount <= 0) return null
        val from = FROM.find(body)?.groupValues?.get(1) ?: ""
        return Payment(method, trx, amount, from)
    }
}
