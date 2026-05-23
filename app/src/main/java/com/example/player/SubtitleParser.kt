package com.example.player

import java.io.File

object SubtitleParser {

    fun findSubtitleFile(videoFile: File): File? {
        val baseName = videoFile.nameWithoutExtension
        val parent = videoFile.parentFile ?: return null
        val srtFile = File(parent, "$baseName.srt")
        return if (srtFile.exists()) srtFile else null
    }

    fun parseSrt(file: File): List<Subtitle> {
        val subtitles = mutableListOf<Subtitle>()
        val lines = file.readLines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) { i++; continue }

            val index = line.toIntOrNull()
            if (index != null && i + 1 < lines.size) {
                val timeLine = lines[i + 1].trim()
                val textLines = mutableListOf<String>()
                var j = i + 2
                while (j < lines.size) {
                    val textLine = lines[j].trim()
                    if (textLine.isEmpty()) break
                    textLines.add(textLine)
                    j++
                }
                val text = textLines.joinToString(" ")
                val times = parseTimeLine(timeLine)
                if (times != null && text.isNotEmpty()) {
                    // 去重（避免连续相同文本）
                    if (subtitles.isEmpty() || subtitles.last().text != text) {
                        subtitles.add(Subtitle(index, times.first, times.second, text))
                    }
                }
                i = j
            } else {
                i++
            }
        }
        return subtitles
    }

    private fun parseTimeLine(line: String): Pair<Long, Long>? {
        val regex = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")
        val match = regex.find(line) ?: return null
        val start = timeToMillis(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt(), match.groupValues[4].toInt())
        val end = timeToMillis(match.groupValues[5].toInt(), match.groupValues[6].toInt(), match.groupValues[7].toInt(), match.groupValues[8].toInt())
        return Pair(start, end)
    }

    private fun timeToMillis(h: Int, m: Int, s: Int, ms: Int) = (h * 3600L + m * 60L + s) * 1000L + ms
}