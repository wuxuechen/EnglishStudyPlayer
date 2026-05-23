package com.example.player

import android.content.Context

object CommonWords {
    private var wordsSet: Set<String>? = null

    fun init(context: Context) {
        if (wordsSet != null) return
        wordsSet = try {
            context.assets.open("common_words.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }.toSet()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptySet()
        }
    }

    fun isCommonWord(word: String): Boolean {
        return wordsSet?.contains(word.lowercase()) == true
    }
}