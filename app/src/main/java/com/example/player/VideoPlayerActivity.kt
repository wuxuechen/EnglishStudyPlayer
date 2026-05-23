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
    private lateinit var tvName: TextView
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
        tvName = findViewById(R.id.tvVideoName)
        rvSubtitles = findViewById(R.id.rvSubtitles)

        val path = intent.getStringExtra("video_path") ?: return finish()
        val name = intent.getStringExtra("video_name") ?: "视频"
        tvName.text = name

        videoFile = File(path)
        if (!videoFile!!.exists()) {
            Toast.makeText(this, "视频不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initPlayer()
        loadSubtitles()
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

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(videoFile))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(sub.startTime)
                    .setEndPositionMs(sub.endTime)
                    .build()
            )
            .build()

        // 停止之前的播放监听
        player.removeListener(playbackListener)
        // 添加新的完成监听
        player.addListener(playbackListener)

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        // 更新高亮
        currentPlayingPos = pos
        adapter.setCurrentPlayingPosition(pos)
        rvSubtitles.smoothScrollToPosition(pos)

        Toast.makeText(this, "▶ ${sub.text.take(30)}", Toast.LENGTH_SHORT).show()
    }

    // 播放完成监听器
    private val playbackListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                // 播放结束，清除当前播放高亮（但永久标记仍然存在，由 adapter 内部维护）
                runOnUiThread {
                    currentPlayingPos = -1
                    adapter.setCurrentPlayingPosition(-1)
                }
            }
        }
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