package com.ishan.sms

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** ফোনে অ্যাপ বন্ধ থাকলেও SMS ধরা ও পেন্ডিং আপলোড চালু রাখে */
class KeepAliveService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var n = 0
    private val tick = object : Runnable {
        override fun run() {
            n++
            val app = applicationContext
            // পেন্ডিং থাকলে প্রতি মিনিটে, না থাকলেও প্রতি ৫ মিনিটে (lastSeen আপডেটের জন্য)
            if (Store.hasPending(app) || n % 5 == 0) Thread { try { Sync.run(app) } catch (_: Exception) { } }.start()
            h.postDelayed(this, 60_000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("keep", "SMS Reader", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, "keep")
            .setSmallIcon(R.drawable.ic_sms)
            .setContentTitle("SMS Reader চালু আছে")
            .setContentText("bKash / Nagad / Rocket SMS পর্যবেক্ষণ করা হচ্ছে")
            .setContentIntent(pi).setOngoing(true).build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 1, notif, type)
        h.removeCallbacks(tick); h.post(tick)
        return START_STICKY
    }

    override fun onDestroy() { h.removeCallbacks(tick); super.onDestroy() }

    companion object {
        fun start(c: Context) = try { ContextCompat.startForegroundService(c, Intent(c, KeepAliveService::class.java)) } catch (_: Exception) { }
        fun stop(c: Context) { c.stopService(Intent(c, KeepAliveService::class.java)) }
    }
}
