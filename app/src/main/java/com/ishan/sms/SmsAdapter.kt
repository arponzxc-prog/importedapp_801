package com.ishan.sms

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SmsAdapter(private val onClick: (Sms) -> Unit) : RecyclerView.Adapter<SmsAdapter.VH>() {
    private var items: List<Sms> = emptyList()
    var projectNames: Map<String, String> = emptyMap()

    fun submit(l: List<Sms>) { items = l; notifyDataSetChanged() }

    class VH(v: android.view.View) : RecyclerView.ViewHolder(v) {
        val method: TextView = v.findViewById(R.id.tvMethod)
        val amount: TextView = v.findViewById(R.id.tvAmount)
        val trx: TextView = v.findViewById(R.id.tvTrx)
        val from: TextView = v.findViewById(R.id.tvFrom)
        val projects: TextView = v.findViewById(R.id.tvProjects)
        val time: TextView = v.findViewById(R.id.tvTime)
        val state: TextView = v.findViewById(R.id.tvState)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_sms, p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val s = items[i]
        h.method.text = s.method.uppercase() + if (s.manual) "  •  ম্যানুয়াল" else ""
        h.method.setTextColor(when (s.method.lowercase()) {
            "bkash" -> Color.parseColor("#E2136E"); "nagad" -> Color.parseColor("#F6921E"); else -> Color.parseColor("#8C3494")
        })
        h.amount.text = "৳ " + U.money(s.amount)
        h.trx.text = "TrxID: ${s.trx}"
        h.from.text = "প্রেরক: ${s.sender}" + if (s.sim > 0) "  •  SIM ${s.sim}" else ""
        val names = s.matched.mapNotNull { projectNames[it] }
        h.projects.text = if (names.isEmpty()) "" else "→ " + names.joinToString(", ")
        h.projects.visibility = if (names.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        h.time.text = U.shortTime(s.receivedAt)
        val (txt, col) = when {
            s.used -> "ব্যবহৃত" to "#1B7F55"
            s.ignored -> "অন্যান্য" to "#6B6785"
            s.pending -> "আপলোড বাকি" to "#D97706"
            else -> "অব্যবহৃত" to "#C2255C"
        }
        h.state.text = txt; h.state.setTextColor(Color.parseColor(col))
        h.itemView.setOnClickListener { onClick(s) }
    }
}
