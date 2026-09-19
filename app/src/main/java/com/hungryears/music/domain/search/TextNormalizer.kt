package com.hungryears.music.domain.search

import java.text.Normalizer

/**
 * Query/text normalization shared by indexing and ranking (SEARCH_SYSTEM.md §3).
 *
 * lower-case → strip diacritics (NFD + remove marks) → collapse whitespace → trim. Leading
 * articles are **not** stripped here so *The Beatles* stays findable by "the"; article handling
 * belongs to ranking boosts only.
 */
object TextNormalizer {

    private val combiningMarks = Regex("\\p{M}+")
    private val whitespace = Regex("\\s+")

    fun normalize(text: String): String = text
        .lowercase()
        .let { Normalizer.normalize(it, Normalizer.Form.NFD) }
        .replace(combiningMarks, "")
        .replace(whitespace, " ")
        .trim()

    fun tokenize(text: String): List<String> =
        normalize(text).split(' ').filter { it.isNotEmpty() }

    /** Splits raw (un-normalized) text into whitespace-separated tokens. */
    fun tokenizePreservingCase(text: String): List<String> =
        text.trim().split(' ').filter { it.isNotEmpty() }
}
