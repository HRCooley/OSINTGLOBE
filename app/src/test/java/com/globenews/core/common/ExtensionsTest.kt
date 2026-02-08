package com.globenews.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionsTest {

    @Test
    fun `normalizeUrl strips protocol and www`() {
        assertEquals("example.com/path", "https://www.example.com/path".normalizeUrl())
        assertEquals("example.com/path", "http://example.com/path".normalizeUrl())
        assertEquals("example.com", "https://example.com/".normalizeUrl())
    }

    @Test
    fun `jaccardSimilarity returns 1 for identical strings`() {
        val similarity = jaccardSimilarity("hello world", "hello world")
        assertEquals(1.0, similarity, 0.001)
    }

    @Test
    fun `jaccardSimilarity returns 0 for completely different strings`() {
        val similarity = jaccardSimilarity("aaa", "zzz")
        assertEquals(0.0, similarity, 0.001)
    }

    @Test
    fun `jaccardSimilarity detects similar titles`() {
        val similarity = jaccardSimilarity(
            "Breaking: Major earthquake strikes California today",
            "Breaking: Major earthquake strikes California today"
        )
        assertTrue(similarity > 0.85)
    }

    @Test
    fun `jaccardSimilarity handles empty strings`() {
        assertEquals(1.0, jaccardSimilarity("", ""), 0.001)
        assertEquals(0.0, jaccardSimilarity("", "abc"), 0.001)
    }
}
