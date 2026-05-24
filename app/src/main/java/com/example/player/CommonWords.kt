package com.example.player

import android.content.Context
import java.io.File

object CommonWords {
    private var wordsSet: MutableSet<String>? = null
    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
        if (wordsSet != null) return
        wordsSet = mutableSetOf()
        // 加载 assets 中的基础常用词表（只读）
        try {
            context.assets.open("common_words.txt").bufferedReader().useLines { lines ->
                wordsSet?.addAll(lines.map { it.trim().lowercase() })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // 加载用户自定义的常用词文件（可写）
        val userFile = File(context.filesDir, "common_words_user.txt")
        if (userFile.exists()) {
            userFile.bufferedReader().useLines { lines ->
                wordsSet?.addAll(lines.map { it.trim().lowercase() })
            }
        }
    }

    fun isCommonWord(word: String): Boolean {
        return wordsSet?.contains(word.lowercase()) == true
    }

    fun addCommonWord(word: String) {
        val lowerWord = word.lowercase()
        if (wordsSet?.add(lowerWord) == true) {
            // 追加到用户文件
            val userFile = File(context.filesDir, "common_words_user.txt")
            userFile.appendText("$lowerWord\n")
        }
    }
}