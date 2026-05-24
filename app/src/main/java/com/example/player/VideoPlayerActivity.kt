package com.example.player

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream

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

    private lateinit var db: SQLiteDatabase
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

        // 初始化数据库
        initDatabase()
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
        rvMeanings.layoutManager = LinearLayoutManager(this)
        rvMeanings.adapter = meaningsAdapter
    }

    private fun initDatabase() {
        val dbFile = File(filesDir, "englishwords.db")
        if (!dbFile.exists()) {
            assets.open("englishwords.db").use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
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

    private fun extractWords(text: String): List<String> {
        val regex = Regex("""[a-zA-Z']{2,}""")
        return regex.findAll(text)
            .map { it.value.lowercase() }
            .toList()
            .filter { word ->
                word.isNotEmpty() && word.any { it.isLetter() } && !word.matches(Regex("^'+$"))
            }
            .distinct()
    }

    private fun fetchMeaningsForSubtitle(subtitle: Subtitle) {
        meaningsAdapter.submitList(emptyList())

        val allWords = extractWords(subtitle.text)
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

    /**
     * 返回候选词列表（原词 + 可能的原形），顺序为原词优先
     */
    private fun getWordCandidates(original: String): List<String> {
        val candidates = mutableListOf(original)
        val w = original.lowercase()
        when {
            w.endsWith("ies") && w.length > 3 -> candidates.add(w.dropLast(3) + "y")   // studies -> study
            w.endsWith("ves") && w.length > 3 -> candidates.add(w.dropLast(3) + "fe") // knives -> knife
            w.endsWith("es") && w.length > 2 -> candidates.add(w.dropLast(2))         // watches -> watch
            w.endsWith("s") && w.length > 1 && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") ->
                candidates.add(w.dropLast(1))                                          // cats -> cat
            w.endsWith("ing") && w.length > 3 -> {
                candidates.add(w.dropLast(3))                                          // playing -> play
                if (w.length > 4 && w[w.length-4] == w[w.length-5])                    // running -> run
                    candidates.add(w.dropLast(4))
            }
            w.endsWith("ed") && w.length > 2 -> {
                candidates.add(w.dropLast(2))                                          // played -> play
                if (w.length > 3 && w[w.length-3] == w[w.length-4])                    // stopped -> stop
                    candidates.add(w.dropLast(3))
            }
            w.endsWith("er") && w.length > 2 -> candidates.add(w.dropLast(2))          // smaller -> small
            w.endsWith("est") && w.length > 3 -> candidates.add(w.dropLast(3))         // smallest -> small
        }
        return candidates.distinct()
    }

    private fun lookupWordMeaningSync(originalWord: String): MeaningItem {
        val candidates = getWordCandidates(originalWord)
        for (candidate in candidates) {
            var cursor: Cursor? = null
            try {
                cursor = db.query(
                    "englishwords",                // 你的表名
                    arrayOf("word", "pronunciation", "meaning"),
                    "LOWER(word) = LOWER(?)",
                    arrayOf(candidate),
                    null, null, null
                )
                if (cursor != null && cursor.moveToFirst()) {
                    val pronounce = cursor.getString(1)
                    val meaning = cursor.getString(2)
                    return MeaningItem(originalWord, pronounce, meaning)   // 单词显示原始形式
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cursor?.close()
            }
        }
        return MeaningItem(originalWord, "", "暂无释义")
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
        db.close()
    }
}