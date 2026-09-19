package com.hungryears.music.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokensTest {

    @Test
    fun `spacing scale sits on the 4dp grid`() {
        val tokens = listOf(
            HungryEarsSpacing.xs.value,
            HungryEarsSpacing.s.value,
            HungryEarsSpacing.m.value,
            HungryEarsSpacing.l.value,
            HungryEarsSpacing.xl.value,
            HungryEarsSpacing.xxl.value,
        )
        tokens.forEach { dp ->
            assertTrue("expected $dp to align to ${HungryEarsSpacing.Grid}dp", dp % HungryEarsSpacing.Grid == 0f)
        }
        assertEquals(6, tokens.size)
    }
}