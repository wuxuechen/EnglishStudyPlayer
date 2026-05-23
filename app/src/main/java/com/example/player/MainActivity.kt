package com.example.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var rvVideoList: RecyclerView
    private lateinit var tvPath: TextView
    private lateinit var adapter: VideoListAdapter
    private val items = mutableListOf<FileItem>()

    private var currentDirectory: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    private val navigationStack = mutableListOf<File>()

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvVideoList = findViewById(R.id.rvVideoList)
        tvPath = findViewById(R.id.tvPath)

        setupRecyclerView()
        checkPermissions()
    }

    private fun setupRecyclerView() {
        rvVideoList.layoutManager = LinearLayoutManager(this)
        adapter = VideoListAdapter(items) { item ->
            when (item) {
                is FileItem.Folder -> {
                    navigationStack.add(currentDirectory)
                    currentDirectory = item.file
                    loadCurrentDirectory()
                }
                is FileItem.Video -> {
                    val intent = Intent(this, VideoPlayerActivity::class.java).apply {
                        putExtra("video_path", item.file.absolutePath)
                        putExtra("video_name", item.file.name)
                    }
                    startActivity(intent)
                }
            }
        }
        rvVideoList.adapter = adapter
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (needPermissions.isEmpty()) {
            loadCurrentDirectory()
        } else {
            ActivityCompat.requestPermissions(this, needPermissions, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadCurrentDirectory()
            } else {
                tvPath.text = "需要权限才能读取文件"
                Toast.makeText(this, "请授予存储权限", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadCurrentDirectory() {
        tvPath.text = currentDirectory.absolutePath

        Thread {
            val fileList = scanDirectory(currentDirectory)
            runOnUiThread {
                items.clear()
                items.addAll(fileList)
                adapter.notifyDataSetChanged()

                if (items.isEmpty()) {
                    tvPath.text = "${currentDirectory.absolutePath}\n(空文件夹)"
                }
            }
        }.start()
    }

    private fun scanDirectory(directory: File): List<FileItem> {
        val result = mutableListOf<FileItem>()
        val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "3gp", "m4v", "webm", "flv", "wmv")

        if (!directory.exists() || !directory.isDirectory) {
            return result
        }

        directory.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { file ->
            when {
                file.isDirectory -> {
                    result.add(FileItem.Folder(file))
                }
                videoExtensions.contains(file.extension.lowercase()) && file.length() > 0 -> {
                    result.add(FileItem.Video(file))
                }
            }
        }

        return result
    }

    override fun onBackPressed() {
        if (navigationStack.isNotEmpty()) {
            currentDirectory = navigationStack.removeAt(navigationStack.size - 1)
            loadCurrentDirectory()
        } else {
            super.onBackPressed()
        }
    }
}