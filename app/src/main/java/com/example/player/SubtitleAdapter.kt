package com.example.player

import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SubtitleAdapter(
    private val subtitles: List<Subtitle>,
    private val onItemClick: (Subtitle, Int) -> Unit
) : RecyclerView.Adapter<SubtitleAdapter.ViewHolder>() {

    private var currentPlayingPos = -1               // 当前正在播放的位置
    private val playedSet = mutableSetOf<Int>()      // 永久标记已播放过的位置

    class ViewHolder(val timeTv: TextView, val textTv: TextView) : RecyclerView.ViewHolder(timeTv.parent as android.view.View) {
        init {
            timeTv.setTextColor(0xFFFFD700.toInt())
            timeTv.textSize = 12f
            textTv.setTextColor(0xFFFFFFFF.toInt())
            textTv.textSize = 14f
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(0)
        }
        val timeTv = TextView(context)
        timeTv.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val textTv = TextView(context)
        textTv.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        textTv.setPadding(0, 16, 0, 0)
        container.addView(timeTv)
        container.addView(textTv)

        val divider = android.view.View(context)
        divider.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1
        )
        divider.setBackgroundColor(0xFF333333.toInt())
        container.addView(divider)

        val holder = ViewHolder(timeTv, textTv)
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                // 点击时，立即标记为已播放（永久）
                playedSet.add(pos)
                // 刷新该 item（若当前正在播放其它，也会刷新）
                notifyItemChanged(pos)
                // 执行播放回调
                onItemClick(subtitles[pos], pos)
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sub = subtitles[position]
        holder.timeTv.text = formatTime(sub.startTime) + " → " + formatTime(sub.endTime)
        holder.textTv.text = sub.text

        // 判断背景色
        when {
            position == currentPlayingPos -> {
                // 当前正在播放：亮绿色高亮
                holder.itemView.setBackgroundColor(0x3366FF00.toInt()) // 亮绿色半透明
                holder.textTv.setTextColor(0xFFFFFFFF.toInt())
                holder.timeTv.setTextColor(0xFFFFFFFF.toInt())
            }
            playedSet.contains(position) -> {
                // 播放过的永久标记：浅绿色
                holder.itemView.setBackgroundColor(0x3390EE90.toInt())
                holder.textTv.setTextColor(0xFFFFFFFF.toInt())
                holder.timeTv.setTextColor(0xFFFFD700.toInt())
            }
            else -> {
                holder.itemView.setBackgroundColor(0)
                holder.textTv.setTextColor(0xFFFFFFFF.toInt())
                holder.timeTv.setTextColor(0xFFFFD700.toInt())
            }
        }
    }

    override fun getItemCount() = subtitles.size

    /** 由外部调用，更新当前正在播放的位置（高亮变化时刷新所有） */
    fun setCurrentPlayingPosition(pos: Int) {
        val oldPos = currentPlayingPos
        currentPlayingPos = pos
        // 刷新旧位置和新位置
        if (oldPos != -1) notifyItemChanged(oldPos)
        if (pos != -1) notifyItemChanged(pos)
        // 如果新旧位置相同且不为-1，也刷新（确保显示）
        if (oldPos == pos && pos != -1) notifyItemChanged(pos)
    }

    private fun formatTime(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val millis = ms % 1000
        return if (hours > 0) {
            String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
        } else {
            String.format("%02d:%02d.%03d", minutes, seconds, millis)
        }
    }
}