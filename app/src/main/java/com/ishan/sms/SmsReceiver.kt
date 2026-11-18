package com.ishan.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val address = msgs.firstOrNull()?.originatingAddress ?: return
        val body = msgs.joinToString("") { it.messageBody ?: "" }

        val pay = SmsParser.parse(address, body) ?: return   // পেমেন্ট SMS না হলে সঙ্গে সঙ্গে বাদ
        val slot = intent.getIntExtra("slot", intent.getIntExtra("simSlot", -1))
        val sim = if (slot >= 0) slot + 1 else 0

        val app = context.applicationContext
        val s = Sms(pay.trx, pay.method, pay.amount, pay.from.ifEmpty { address }, body,
            System.currentTimeMillis(), sim)
        if (!Store.add(app, s)) return
        Bus.changed()

        val pr = goAsync()
        Thread {
            try { Sync.run(app) } catch (_: Exception) { } finally { pr.finish() }
        }.start()
    }
}
