package com.hungryears.music.data.format

object SupportedFormats {

    val EXTENSIONS: Set<String> = setOf(
        "mp3", "mp4", "m4a", "m4b", "aac", "oga", "ogg", "opus", "flac", "wav", "aiff", "aif", "amr", "mp2",
    )

    fun isSupported(container: String): Boolean = EXTENSIONS.contains(container.lowercase())

    fun containerOf(fileName: String): String = fileName.substringAfterLast('.', "").lowercase()

    fun isSupportedFile(fileName: String): Boolean = isSupported(containerOf(fileName))
}