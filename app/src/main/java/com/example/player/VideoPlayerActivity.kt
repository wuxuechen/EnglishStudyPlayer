package com.example.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var tvTitle: TextView
    private lateinit var tvCurrentSubtitle: TextView
    private lateinit var rvSubtitles: RecyclerView
    private lateinit var rvMeanings: RecyclerView
    private lateinit var progressFill: View
    private lateinit var adapter: SubtitleAdapter
    private lateinit var meaningsAdapter: RareWordAdapter

    private var exoPlayer: ExoPlayer? = null
    private var videoFile: File? = null
    private var subtitles = listOf<Subtitle>()
    private var currentPlayingPos = -1

    private val backgroundThread = HandlerThread("WordLookup").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)
    private val uiHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        tvTitle = findViewById(R.id.tvTitle)
        tvCurrentSubtitle = findViewById(R.id.tvCurrentSubtitle)
        rvSubtitles = findViewById(R.id.rvSubtitles)
        rvMeanings = findViewById(R.id.rvMeanings)
        progressFill = findViewById(R.id.progressFill)

        val path = intent.getStringExtra("video_path") ?: return finish()
        val name = intent.getStringExtra("video_name") ?: "视频"
        tvTitle.text = name

        videoFile = File(path)
        if (!videoFile!!.exists()) {
            Toast.makeText(this, "视频不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 初始化常用词库
        CommonWords.init(this)

        initPlayer()
        loadSubtitles()

        tvCurrentSubtitle.setOnClickListener {
            if (currentPlayingPos >= 0 && currentPlayingPos < subtitles.size) {
                val currentSub = subtitles[currentPlayingPos]
                playSubtitle(currentSub, currentPlayingPos)
            } else {
                Toast.makeText(this, "没有正在播放的字幕", Toast.LENGTH_SHORT).show()
            }
        }

        meaningsAdapter = RareWordAdapter()
        rvMeanings.layoutManager = GridLayoutManager(this, 2)
        rvMeanings.adapter = meaningsAdapter
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

        updateProgressBar(pos)

        uiHandler.post {
            val nextPos = pos + 1
            if (nextPos < subtitles.size) {
                (rvSubtitles.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(nextPos, 0)
            }
        }

        fetchMeaningsForSubtitle(sub)
    }

    private fun updateProgressBar(currentIndex: Int) {
        if (subtitles.isEmpty()) return
        val percent = if (currentIndex < 0) 0f else currentIndex.toFloat() / (subtitles.size - 1).toFloat()
        uiHandler.post {
            val parentWidth = (progressFill.parent as View).width
            if (parentWidth > 0) {
                val newWidth = (parentWidth * percent).toInt()
                progressFill.layoutParams.width = newWidth
                progressFill.requestLayout()
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting
    }

    private fun extractWords(text: String): List<String> {
        val cleaned = text.replace(Regex("[^a-zA-Z\\s]"), "")
        return cleaned.split(Regex("\\s+")).filter { it.length > 1 && it.all(Char::isLetter) }
    }

    private fun fetchMeaningsForSubtitle(subtitle: Subtitle) {
        meaningsAdapter.submitList(emptyList())

        if (!isNetworkAvailable()) {
            meaningsAdapter.setOffline()
            return
        }

        val allWords = extractWords(subtitle.text)
        // 过滤掉常见词（top 1500）
        val rareWords = allWords.filter { it.length > 1 && !CommonWords.isCommonWord(it) }.distinct()

        if (rareWords.isEmpty()) {
            meaningsAdapter.setEmpty("当前字幕无非常用词")
            return
        }

        val resultList = mutableListOf<MeaningItem>()
        var pendingRequests = rareWords.size

        for (word in rareWords) {
            backgroundHandler.post {
                val meaningItem = lookupWordMeaningSync(word)
                uiHandler.post {
                    resultList.add(meaningItem)
                    pendingRequests--
                    if (pendingRequests == 0) {
                        meaningsAdapter.submitList(resultList)
                    }
                }
            }
        }
    }

    private fun lookupWordMeaningSync(word: String): MeaningItem {
        return try {
            val url = URL("http://dict.cn/ws.php?q=${URLEncoder.encode(word, "utf-8")}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(connection.inputStream, "UTF-8")
                var eventType = parser.eventType
                var currentMeaning = ""
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "def") {
                        currentMeaning = parser.nextText().trim()
                        break
                    }
                    eventType = parser.next()
                }
                if (currentMeaning.isNotEmpty()) {
                    MeaningItem(word, currentMeaning)
                } else {
                    MeaningItem(word, "暂无释义")
                }
            } else {
                Log.e("DictAPI", "HTTP error: ${connection.responseCode}")
                MeaningItem(word, "查询失败")
            }
        } catch (e: Exception) {
            Log.e("DictAPI", "Exception: ${e.message}")
            MeaningItem(word, "网络错误")
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundThread.quitSafely()
        exoPlayer?.release()
        exoPlayer = null
    }
}