package com.example.player

import java.io.File

sealed class FileItem {
    data class Folder(val file: File) : FileItem()
    data class Video(val file: File) : FileItem()

    val name: String get() = when(this) {
        is Folder -> file.name
        is Video -> file.name
    }

    val path: String get() = when(this) {
        is Folder -> file.absolutePath
        is Video -> file.absolutePath
    }
}