package com.hungryears.music.domain.queue

import kotlin.random.Random

enum class RepeatMode { OFF, ALL, ONE }

/**
 * Pure queue navigation math shared by the player and its unit tests.
 *
 * Queue order is owned by the player layer (shuffle is a fixed permutation, never re-rolled),
 * so these functions only answer "where does navigation go" for a given shape and repeat mode.
 */
object QueueLogic {

    /**
     * Builds a playback order as a permutation of `0 until size`.
     *
     * The track at [keepFirstAt] stays at the front so shuffling never interrupts the current
     * track; the remaining positions are Fisher-Yates shuffled with the supplied [random].
     */
    fun shuffledOrder(size: Int, keepFirstAt: Int, random: Random): List<Int> {
        if (size <= 0) return emptyList()
        val first = keepFirstAt.coerceIn(0, size - 1)
        val rest = MutableList(size - 1) { index -> if (index < first) index else index + 1 }
        for (i in rest.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val swap = rest[i]
            rest[i] = rest[j]
            rest[j] = swap
        }
        return buildList(size) {
            add(first)
            addAll(rest)
        }
    }

    /** Target for the Next button; `null` means the queue is exhausted and playback should stop. */
    fun nextIndex(currentIndex: Int, size: Int, repeatMode: RepeatMode): Int? {
        if (size <= 0) return null
        val current = currentIndex.coerceIn(0, size - 1)
        return when {
            repeatMode == RepeatMode.ONE -> current
            current < size - 1 -> current + 1
            repeatMode == RepeatMode.ALL -> 0
            else -> null
        }
    }

    /** Target for the Previous button; `null` means there is nothing before the current track. */
    fun previousIndex(currentIndex: Int, size: Int, repeatMode: RepeatMode): Int? {
        if (size <= 0) return null
        val current = currentIndex.coerceIn(0, size - 1)
        return when {
            repeatMode == RepeatMode.ONE -> current
            current > 0 -> current - 1
            repeatMode == RepeatMode.ALL -> size - 1
            else -> null
        }
    }

    /**
     * Target that auto-advance takes when the current track *finishes* playing. With
     * [RepeatMode.ONE] the same track plays again; otherwise it matches [nextIndex].
     */
    fun upNextIndex(currentIndex: Int, size: Int, repeatMode: RepeatMode): Int? {
        if (size <= 0) return null
        val current = currentIndex.coerceIn(0, size - 1)
        return when {
            repeatMode == RepeatMode.ONE -> current
            current < size - 1 -> current + 1
            repeatMode == RepeatMode.ALL -> 0
            else -> null
        }
    }

    fun hasNext(currentIndex: Int, size: Int, repeatMode: RepeatMode): Boolean =
        nextIndex(currentIndex, size, repeatMode) != null

    fun hasPrevious(currentIndex: Int, size: Int, repeatMode: RepeatMode): Boolean =
        previousIndex(currentIndex, size, repeatMode) != null
}
