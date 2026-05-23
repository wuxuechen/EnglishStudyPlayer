package com.example.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoListAdapter(
    private val items: List<FileItem>,
    private val onItemClick: (FileItem) -> Unit
) : RecyclerView.Adapter<VideoListAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        val tvFileInfo: TextView = itemView.findViewById(R.id.tvFileInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvFileName.text = item.name

        when (item) {
            is FileItem.Folder -> {
                holder.ivIcon.setImageResource(android.R.drawable.ic_menu_edit)
                val fileCount = item.file.listFiles()?.size ?: 0
                holder.tvFileInfo.text = "📁 文件夹 | $fileCount 个文件"
            }
            is FileItem.Video -> {
                holder.ivIcon.setImageResource(android.R.drawable.ic_menu_camera)
                holder.tvFileInfo.text = formatFileInfo(item.file)
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun formatFileInfo(file: File): String {
        val size = formatFileSize(file.length())
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
        return "$size  |  $date"
    }

    private fun formatFileSize(size: Long): String {
        val kb = size / 1024.0
        return when {
            kb < 1024 -> String.format("%.1f KB", kb)
            else -> String.format("%.1f MB", kb / 1024)
        }
    }
}