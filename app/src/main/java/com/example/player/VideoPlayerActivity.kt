package com.example.player

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
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
    private lateinit var switchShowSubtitle: SwitchCompat
    private lateinit var adapter: SubtitleAdapter
    private lateinit var meaningsAdapter: RareWordAdapter

    private var exoPlayer: ExoPlayer? = null
    private var videoFile: File? = null
    private var subtitles = listOf<Subtitle>()
    private var currentPlayingPos = -1

    private lateinit var db: SQLiteDatabase
    private lateinit var prefs: android.content.SharedPreferences
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
        switchShowSubtitle = findViewById(R.id.switchShowSubtitle)

        // 初始化 SharedPreferences
        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isChecked = prefs.getBoolean("show_subtitle", true)
        switchShowSubtitle.isChecked = isChecked

        // 确保 tvCurrentSubtitle 始终可见
        tvCurrentSubtitle.visibility = View.VISIBLE

        // 开关监听
        switchShowSubtitle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_subtitle", isChecked).apply()
            updateCurrentSubtitleDisplay()
        }

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

        // 先设置默认提示
        tvCurrentSubtitle.text = "加载字幕中..."
        updateCurrentSubtitleDisplay()

        meaningsAdapter = RareWordAdapter()
        rvMeanings.layoutManager = LinearLayoutManager(this)
        rvMeanings.adapter = meaningsAdapter

        // 🔥 核心：点击 tvCurrentSubtitle 只播放，绝不改变开关状态
        tvCurrentSubtitle.setOnClickListener {
            if (currentPlayingPos >= 0 && currentPlayingPos < subtitles.size) {
                val currentSub = subtitles[currentPlayingPos]
                playSubtitle(currentSub, currentPlayingPos)
            } else if (subtitles.isNotEmpty()) {
                playSubtitle(subtitles[0], 0)
            } else {
                Toast.makeText(this, "没有可播放的字幕", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 根据开关状态更新 tvCurrentSubtitle 的显示内容
     * 开关关闭时：固定显示提示文字（粗体、浅灰色）
     * 开关打开时：显示当前字幕（白色、常规字体）
     */
    private fun updateCurrentSubtitleDisplay() {
        // 确保 tvCurrentSubtitle 可见
        tvCurrentSubtitle.visibility = View.VISIBLE

        if (switchShowSubtitle.isChecked) {
            // ✅ 字幕打开模式：显示当前播放的字幕文本
            when {
                currentPlayingPos >= 0 && currentPlayingPos < subtitles.size -> {
                    tvCurrentSubtitle.text = subtitles[currentPlayingPos].text
                }
                subtitles.isNotEmpty() -> {
                    tvCurrentSubtitle.text = "点击下方字幕开始播放"
                }
                else -> {
                    tvCurrentSubtitle.text = "暂无字幕"
                }
            }
            tvCurrentSubtitle.setTextColor(0xFFFFFFFF.toInt()) // 白色
            tvCurrentSubtitle.textSize = 16f
            tvCurrentSubtitle.setTypeface(null, android.graphics.Typeface.NORMAL) // 常规字体
            tvCurrentSubtitle.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        } else {
            // ❌ 字幕关闭模式：固定显示提示文字（粗体、浅灰色）
            tvCurrentSubtitle.text = "subtitle was closed\nclick here to replay"
            tvCurrentSubtitle.setTextColor(0xFFAAAAAA.toInt()) // 浅灰色
            tvCurrentSubtitle.textSize = 16f
            tvCurrentSubtitle.setTypeface(null, android.graphics.Typeface.BOLD) // 粗体
            tvCurrentSubtitle.gravity = Gravity.CENTER
        }
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
                    // 加载完成后更新显示
                    updateCurrentSubtitleDisplay()
                } else {
                    Toast.makeText(this, "无字幕", Toast.LENGTH_SHORT).show()
                    tvCurrentSubtitle.text = "未找到字幕文件"
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

        currentPlayingPos = pos

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

        adapter.setCurrentPlayingPosition(pos)

        // 🔥 更新显示（根据当前开关状态）
        updateCurrentSubtitleDisplay()

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
            // 复数：-ies 变 y
            w.endsWith("ies") && w.length > 3 ->
                candidates.add(w.dropLast(3) + "y")

            // 复数：-ves 变 f/fe（仅处理常见情况）
            w.endsWith("ves") && w.length > 3 -> {
                candidates.add(w.dropLast(3) + "f")
                candidates.add(w.dropLast(3) + "fe")
            }

            // 复数：-es 且不是 ss, us, is 结尾
            w.endsWith("es") && w.length > 2 && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") ->
                candidates.add(w.dropLast(2))

            // 复数：-s 结尾（排除特殊情况）
            w.endsWith("s") && w.length > 1 && !w.endsWith("ss") && !w.endsWith("us") &&
                    !w.endsWith("is") && !w.endsWith("es") ->
                candidates.add(w.dropLast(1))

            // 现在分词：-ing
            w.endsWith("ing") && w.length > 3 -> {
                val stem = w.dropLast(3)
                candidates.add(stem)
                if (stem.length >= 3 && stem.last() == stem[stem.length - 2]) {
                    candidates.add(stem.dropLast(1))
                }
                if (stem.endsWith("ie")) {
                    candidates.add(stem.dropLast(2) + "y")
                }
            }

            // 过去式：-ed
            w.endsWith("ed") && w.length > 2 -> {
                val stem = w.dropLast(2)
                candidates.add(stem)
                if (stem.endsWith("e")) {
                    candidates.add(stem.dropLast(1))
                }
                if (stem.length >= 2 && stem.last() == stem[stem.length - 2]) {
                    candidates.add(stem.dropLast(1))
                }
                if (stem.endsWith("i")) {
                    candidates.add(stem.dropLast(1) + "y")
                }
            }

            // 比较级：-er
            w.endsWith("er") && w.length > 2 -> {
                val stem = w.dropLast(2)
                candidates.add(stem)
                if (stem.length >= 2 && stem.last() == stem[stem.length - 2]) {
                    candidates.add(stem.dropLast(1))
                }
                if (stem.endsWith("i")) {
                    candidates.add(stem.dropLast(1) + "y")
                }
            }

            // 最高级：-est
            w.endsWith("est") && w.length > 3 -> {
                val stem = w.dropLast(3)
                candidates.add(stem)
                if (stem.length >= 2 && stem.last() == stem[stem.length - 2]) {
                    candidates.add(stem.dropLast(1))
                }
                if (stem.endsWith("i")) {
                    candidates.add(stem.dropLast(1) + "y")
                }
            }
        }

        return candidates.distinct()
    }

    private fun lookupWordMeaningSync(originalWord: String): MeaningItem {
        val candidates = getWordCandidates(originalWord)
        for (candidate in candidates) {
            var cursor: Cursor? = null
            try {
                cursor = db.query(
                    "englishwords",
                    arrayOf("word", "pronunciation", "meaning"),
                    "LOWER(word) = LOWER(?)",
                    arrayOf(candidate),
                    null, null, null
                )
                if (cursor != null && cursor.moveToFirst()) {
                    val pronounce = cursor.getString(1)
                    val meaning = cursor.getString(2)
                    return MeaningItem(originalWord, pronounce, meaning)
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