package com.hungryears.music.domain.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueLogicTest {

    @Test
    fun `shuffled order is a permutation and keeps the requested track first`() {
        val order = QueueLogic.shuffledOrder(size = 8, keepFirstAt = 3, random = Random(42))

        assertEquals(8, order.size)
        assertEquals(3, order.first())
        assertEquals((0 until 8).toList(), order.sorted())
    }

    @Test
    fun `shuffled order is deterministic for the same seed`() {
        val first = QueueLogic.shuffledOrder(size = 10, keepFirstAt = 0, random = Random(7))
        val second = QueueLogic.shuffledOrder(size = 10, keepFirstAt = 0, random = Random(7))

        assertEquals(first, second)
    }

    @Test
    fun `shuffled order actually reorders the tail`() {
        val order = QueueLogic.shuffledOrder(size = 12, keepFirstAt = 0, random = Random(1))
        val tail = order.drop(1)

        assertTrue(tail != (1 until 12).toList())
    }

    @Test
    fun `shuffled order handles empty and single-item queues`() {
        assertEquals(emptyList<Int>(), QueueLogic.shuffledOrder(0, 0, Random(1)))
        assertEquals(listOf(0), QueueLogic.shuffledOrder(1, 0, Random(1)))
    }

    @Test
    fun `next follows the queue and stops at the end when repeat is off`() {
        assertEquals(1, QueueLogic.nextIndex(0, size = 3, repeatMode = RepeatMode.OFF))
        assertEquals(2, QueueLogic.nextIndex(1, size = 3, repeatMode = RepeatMode.OFF))
        assertNull(QueueLogic.nextIndex(2, size = 3, repeatMode = RepeatMode.OFF))
    }

    @Test
    fun `next wraps when repeat all is enabled`() {
        assertEquals(0, QueueLogic.nextIndex(2, size = 3, repeatMode = RepeatMode.ALL))
        assertEquals(1, QueueLogic.nextIndex(0, size = 3, repeatMode = RepeatMode.ALL))
    }

    @Test
    fun `next stays on the same track when repeat one is enabled`() {
        assertEquals(0, QueueLogic.nextIndex(0, size = 3, repeatMode = RepeatMode.ONE))
        assertEquals(2, QueueLogic.nextIndex(2, size = 3, repeatMode = RepeatMode.ONE))
    }

    @Test
    fun `previous steps back and stops at the start when repeat is off`() {
        assertEquals(1, QueueLogic.previousIndex(2, size = 3, repeatMode = RepeatMode.OFF))
        assertEquals(0, QueueLogic.previousIndex(1, size = 3, repeatMode = RepeatMode.OFF))
        assertNull(QueueLogic.previousIndex(0, size = 3, repeatMode = RepeatMode.OFF))
    }

    @Test
    fun `previous wraps when repeat all is enabled`() {
        assertEquals(2, QueueLogic.previousIndex(0, size = 3, repeatMode = RepeatMode.ALL))
    }

    @Test
    fun `up next repeats the same track with repeat one and wraps with repeat all`() {
        assertEquals(0, QueueLogic.upNextIndex(0, size = 3, repeatMode = RepeatMode.ONE))
        assertEquals(2, QueueLogic.upNextIndex(2, size = 3, repeatMode = RepeatMode.ONE))
        assertEquals(0, QueueLogic.upNextIndex(2, size = 3, repeatMode = RepeatMode.ALL))
        assertNull(QueueLogic.upNextIndex(2, size = 3, repeatMode = RepeatMode.OFF))
    }

    @Test
    fun `has next and previous expose the same semantics as the index math`() {
        assertTrue(QueueLogic.hasNext(0, size = 2, repeatMode = RepeatMode.OFF))
        assertFalse(QueueLogic.hasNext(1, size = 2, repeatMode = RepeatMode.OFF))
        assertFalse(QueueLogic.hasPrevious(0, size = 2, repeatMode = RepeatMode.OFF))
        assertTrue(QueueLogic.hasPrevious(0, size = 2, repeatMode = RepeatMode.ALL))
    }

    @Test
    fun `empty queue has no destinations`() {
        assertNull(QueueLogic.nextIndex(0, size = 0, repeatMode = RepeatMode.ALL))
        assertNull(QueueLogic.previousIndex(0, size = 0, repeatMode = RepeatMode.ALL))
        assertNull(QueueLogic.upNextIndex(0, size = 0, repeatMode = RepeatMode.ALL))
    }
}
