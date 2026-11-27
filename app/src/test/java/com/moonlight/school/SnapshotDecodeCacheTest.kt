package com.moonlight.school

import org.junit.Assert.*
import org.junit.Test

class SnapshotDecodeCacheTest {
    @Test fun onlyDecodeChangedContent() {
        var calls = 0
        val cache = SnapshotDecodeCache<String> { calls++; it.uppercase() }
        assertNull(cache.get(null))
        assertEquals("A", cache.get("a"))
        assertEquals("A", cache.get("a"))
        assertEquals(1, calls)
        assertEquals("B", cache.get("b"))
        assertEquals(2, calls)
        assertNull(cache.get(null))
        assertEquals("B", cache.get("b"))
        assertEquals(3, calls)
    }

    @Test fun invalidContentIsNotRepeatedlyDecoded() {
        var calls = 0
        val cache = SnapshotDecodeCache<String> { calls++; null }
        assertNull(cache.get("bad"))
        assertNull(cache.get("bad"))
        assertEquals(1, calls)
    }
}
