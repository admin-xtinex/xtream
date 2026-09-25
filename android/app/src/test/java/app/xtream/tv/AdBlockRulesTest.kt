package app.xtream.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockRulesTest {
    @Test
    fun videoStreamsAreNotBlocked() {
        assertFalse(AdBlock.wouldBlock(true, "https://cdn.example.com/live/index.m3u8", false))
        assertFalse(AdBlock.wouldBlock(true, "https://cdn.example.com/video/clip.mp4", false))
        assertFalse(AdBlock.wouldBlock(true, "https://player.example.com/hls/playlist", false, "application/vnd.apple.mpegurl"))
        assertFalse(AdBlock.wouldBlock(true, "https://videosite.com/watch", true))
        assertFalse(AdBlock.wouldBlock(true, "https://player.example.com/embed.js", false))
        assertTrue(AdBlock.wouldBlock(true, "https://pubads.g.doubleclick.net/gampad/ads?output=vast", false))
    }

    @Test
    fun downloadsAndAdHostsAreBlocked() {
        assertTrue(AdBlock.wouldBlock(true, "https://example.com/app.apk", true))
        assertTrue(AdBlock.wouldBlock(false, "https://example.com/movie.zip", false))
        assertTrue(AdBlock.wouldBlock(true, "https://ads.doubleclick.net/pagead/js", false))
        assertTrue(AdBlock.wouldBlock(true, "file:///sdcard/video.mp4", false))
    }
}
