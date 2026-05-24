package com.example.player

import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
        items.add(MeaningItem("", "", "无网络连接，无法查词"))
        notifyDataSetChanged()
    }

    fun setEmpty(message: String = "当前字幕无英文单词") {
        items.clear()
        items.add(MeaningItem("", "", message))
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val container = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val topRow = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val wordTv = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textSize = 16f
            setTextColor(0xFFFFD700.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val pronounceTv = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 12 }
            textSize = 13f
            setTextColor(0xFFAAAAAA.toInt())
        }
        topRow.addView(wordTv)
        topRow.addView(pronounceTv)

        val meaningTv = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
            textSize = 14f
            setTextColor(0xFFEEEEEE.toInt())
        }

        container.addView(topRow)
        container.addView(meaningTv)

        val holder = ViewHolder(container, wordTv, pronounceTv, meaningTv)
        holder.itemView.setOnLongClickListener {
            val position = holder.adapterPosition
            if (position != RecyclerView.NO_POSITION && position < items.size) {
                val item = items[position]
                val word = item.word
                if (word.isNotEmpty()) {
                    // 标记为常用词（以后不再显示）
                    CommonWords.addCommonWord(word)
                    // 弹出 Toast 提示
                    Toast.makeText(
                        holder.itemView.context,
                        "已标记「$word」为已掌握单词",
                        Toast.LENGTH_SHORT
                    ).show()
                    // 从当前列表中移除
                    items.removeAt(position)
                    notifyItemRemoved(position)
                    if (items.isEmpty()) {
                        setEmpty("暂无非常用词（长按单词可标记为已会）")
                    }
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        if (item.word.isEmpty()) {
            holder.wordTv.text = ""
            holder.pronounceTv.text = ""
            holder.meaningTv.text = item.meaning
        } else {
            holder.wordTv.text = item.word
            holder.pronounceTv.text = item.pronounce
            holder.meaningTv.text = item.meaning
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        container: LinearLayout,
        val wordTv: TextView,
        val pronounceTv: TextView,
        val meaningTv: TextView
    ) : RecyclerView.ViewHolder(container)
}