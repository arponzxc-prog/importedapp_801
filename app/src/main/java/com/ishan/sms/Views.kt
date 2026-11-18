package com.ishan.sms

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class RingView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeCap = Paint.Cap.ROUND }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF7C4DDB.toInt(); strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E1B3A.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val r = RectF()

    override fun onDraw(cv: Canvas) {
        val w = U.dp(context, 10f)
        track.strokeWidth = w; arc.strokeWidth = w
        val half = w / 2 + 1
        r.set(half, half, width - half, height - half)
        cv.drawArc(r, 0f, 360f, false, track)
        cv.drawArc(r, -90f, 360f * progress, false, arc)
        txt.textSize = U.sp(context, 16f)
        cv.drawText("${(progress * 100).toInt()}%", width / 2f, height / 2f + txt.textSize / 3f, txt)
    }
}

class BarChartView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    var values: List<Int> = List(7) { 0 }
    var labels: List<String> = List(7) { "" }
        set(v) { field = v; invalidate() }
    fun set(v: List<Int>, l: List<String>) { values = v; labels = l; invalidate() }

    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lbl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6B6785.toInt(); textAlign = Paint.Align.CENTER }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E1B3A.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val r = RectF()

    override fun onDraw(cv: Canvas) {
        val n = values.size
        if (n == 0) return
        lbl.textSize = U.sp(context, 11f); num.textSize = U.sp(context, 11f)
        val bottom = height - U.dp(context, 20f)
        val top = U.dp(context, 18f)
        val slot = width.toFloat() / n
        val bw = slot * 0.5f
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        for (i in 0 until n) {
            val cx = slot * i + slot / 2
            val h = (bottom - top) * values[i] / max
            val min = U.dp(context, 6f)
            r.set(cx - bw / 2, bottom - h.coerceAtLeast(min), cx + bw / 2, bottom)
            bar.color = if (i == n - 1) 0xFF7C4DDB.toInt() else 0xFFDDD6FA.toInt()
            cv.drawRoundRect(r, bw / 2.5f, bw / 2.5f, bar)
            if (values[i] > 0) cv.drawText(values[i].toString(), cx, r.top - U.dp(context, 4f), num)
            cv.drawText(labels.getOrElse(i) { "" }, cx, height - U.dp(context, 4f), lbl)
        }
    }
}

class BrandFooterView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFA9A4C4.toInt(); textAlign = Paint.Align.CENTER }
    override fun onDraw(cv: Canvas) {
        p.textSize = U.sp(context, 11f)
        cv.drawText("SMS Reader • Tele Earn X", width / 2f, height / 2f + p.textSize / 3f, p)
    }
}
