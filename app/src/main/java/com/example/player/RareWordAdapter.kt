package com.example.player

import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RareWordAdapter : RecyclerView.Adapter<RareWordAdapter.ViewHolder>() {

    private val items = mutableListOf<MeaningItem>()

    fun submitList(newItems: List<MeaningItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setOffline() {
        items.clear()
        items.add(MeaningItem("", "无网络连接，无法查词"))
        notifyDataSetChanged()
    }

    fun setEmpty(message: String = "当前字幕无英文单词") {
        items.clear()
        items.add(MeaningItem("", message))
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val container = android.widget.LinearLayout(parent.context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(32, 16, 32, 16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val wordTv = TextView(parent.context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            textSize = 16f
            setTextColor(0xFFFFD700.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val meaningTv = TextView(parent.context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                2f
            )
            textSize = 14f
            setTextColor(0xFFEEEEEE.toInt())
        }

        container.addView(wordTv)
        container.addView(meaningTv)

        return ViewHolder(container, wordTv, meaningTv)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.wordTv.text = if (item.word.isEmpty()) "" else item.word
        holder.meaningTv.text = item.meaning
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        container: android.widget.LinearLayout,
        val wordTv: TextView,
        val meaningTv: TextView
    ) : RecyclerView.ViewHolder(container)
}