package app.xtream.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineAgeTest {
    // 2026-09-26 as days since 1970-01-01
    private val today = 20_722L

    @Test
    fun recentEnginesPass() {
        assertFalse(EngineAge.isOutdated(155, today))
        assertFalse(EngineAge.isOutdated(145, today))
    }

    @Test
    fun enginesAYearBehindAreFlagged() {
        assertTrue(EngineAge.isOutdated(130, today))
        assertTrue(EngineAge.isOutdated(96, today))
    }
}
