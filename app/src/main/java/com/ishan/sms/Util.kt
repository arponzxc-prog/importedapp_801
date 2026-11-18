package com.ishan.sms

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.*

object U {
    val DAYS = arrayOf("রবি", "সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র", "শনি")

    fun dp(c: Context, v: Float) = v * c.resources.displayMetrics.density
    fun sp(c: Context, v: Float) = v * c.resources.displayMetrics.scaledDensity

    fun money(d: Double): String =
        if (d % 1.0 == 0.0) String.format(Locale.US, "%,.0f", d) else String.format(Locale.US, "%,.2f", d)

    fun startOfDay(t: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance(); c.timeInMillis = t
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun shortTime(t: Long): String = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(t))
    fun dbTime(t: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(t))
}

/** ব্যাকগ্রাউন্ড থেকে UI-কে জানানোর ছোট বাস */
object Bus {
    @Volatile var listener: (() -> Unit)? = null
    private val h = Handler(Looper.getMainLooper())
    fun changed() { h.post { listener?.invoke() } }
}
