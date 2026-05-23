package com.example.player

import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var tvTitle: TextView
    private lateinit var tvCurrentSubtitle: TextView
    private lateinit var rvSubtitles: RecyclerView
    private lateinit var adapter: SubtitleAdapter

    private var exoPlayer: ExoPlayer? = null
    private var videoFile: File? = null
    private var subtitles = listOf<Subtitle>()
    private var currentPlayingPos = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        tvTitle = findViewById(R.id.tvTitle)
        tvCurrentSubtitle = findViewById(R.id.tvCurrentSubtitle)
        rvSubtitles = findViewById(R.id.rvSubtitles)

        val path = intent.getStringExtra("video_path") ?: return finish()
        val name = intent.getStringExtra("video_name") ?: "视频"
        tvTitle.text = name

        videoFile = File(path)
        if (!videoFile!!.exists()) {
            Toast.makeText(this, "视频不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initPlayer()
        loadSubtitles()

        // 点击当前播放的字幕区域，重新播放当前字幕
        tvCurrentSubtitle.setOnClickListener {
            if (currentPlayingPos >= 0 && currentPlayingPos < subtitles.size) {
                val currentSub = subtitles[currentPlayingPos]
                playSubtitle(currentSub, currentPlayingPos)
            } else {
                Toast.makeText(this, "没有正在播放的字幕", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            playerView.player = this
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
        }
    }

    private fun loadSubtitles() {
        Thread {
            val srtFile = SubtitleParser.findSubtitleFile(videoFile!!)
            val list = if (srtFile != null) SubtitleParser.parseSrt(srtFile) else emptyList()
            runOnUiThread {
                subtitles = list
                if (subtitles.isNotEmpty()) {
                    setupRecyclerView()
                    Toast.makeText(this, "已加载 ${subtitles.size} 条字幕", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "无字幕", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun setupRecyclerView() {
        rvSubtitles.layoutManager = LinearLayoutManager(this)
        adapter = SubtitleAdapter(subtitles) { sub, pos ->
            playSubtitle(sub, pos)
        }
        rvSubtitles.adapter = adapter
    }

    private fun playSubtitle(sub: Subtitle, pos: Int) {
        val player = exoPlayer ?: return

        // 更新顶部当前播放字幕显示
        tvCurrentSubtitle.text = sub.text

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(videoFile))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(sub.startTime)
                    .setEndPositionMs(sub.endTime)
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        currentPlayingPos = pos
        adapter.setCurrentPlayingPosition(pos)

        // 自动滚动：让下一个 item 置顶
        rvSubtitles.post {
            val nextPos = pos + 1
            if (nextPos < subtitles.size) {
                val layoutManager = rvSubtitles.layoutManager as LinearLayoutManager
                layoutManager.scrollToPositionWithOffset(nextPos, 0)
            }
        }

        Toast.makeText(this, "▶ ${sub.text.take(30)}", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}