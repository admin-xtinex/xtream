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
        assertTrue(AdBlock.wouldBlock(true, "https://ads.fwmrm.net/ad/g/1?resp=vast", false))
        assertFalse(AdBlock.wouldBlock(true, "https://cdn.example.com/movie/stream.mp4", false))
    }

    @Test
    fun downloadsAndAdHostsAreBlocked() {
        assertTrue(AdBlock.wouldBlock(true, "https://example.com/app.apk", true))
        assertTrue(AdBlock.wouldBlock(false, "https://example.com/movie.zip", false))
        assertTrue(AdBlock.wouldBlock(true, "https://ads.doubleclick.net/pagead/js", false))
        assertTrue(AdBlock.wouldBlock(true, "file:///sdcard/video.mp4", false))
    }

    @Test
    fun onlyVideoFilesCanBeSaved() {
        org.junit.Assert.assertEquals("clip.mp4", AdBlock.videoName("https://cdn.example.com/video/clip.mp4"))
        org.junit.Assert.assertNull(AdBlock.videoName("https://cdn.example.com/app.apk"))
        org.junit.Assert.assertNull(AdBlock.videoName("https://cdn.example.com/live/index.m3u8"))
        org.junit.Assert.assertNull(AdBlock.videoName("blob:https://example.com/1234"))
    }
}
