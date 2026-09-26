package app.xtream.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsMasterTest {
    private val master = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="en",URI="audio.m3u8"
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,AUDIO="aud"
        360/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,AUDIO="aud"
        720/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,AUDIO="aud"
        1080/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=15000000,RESOLUTION=3840x2160,AUDIO="aud"
        2160/index.m3u8
    """.trimIndent()

    private fun uris(text: String) = text.lines().filter { it.endsWith("index.m3u8") }

    @Test
    fun readsTheRealQualities() {
        assertEquals(listOf(2160, 1080, 720, 360), HlsMaster.heights(master))
        assertTrue(HlsMaster.isMaster(master))
        assertFalse(HlsMaster.isMaster("#EXTM3U\n#EXTINF:4,\nseg1.ts"))
    }

    @Test
    fun aFixedChoiceLeavesOnlyThatStream() {
        val out = HlsMaster.filter(master, "720")
        assertEquals(listOf("720/index.m3u8"), uris(out))
        assertTrue(out.contains("#EXT-X-MEDIA:TYPE=AUDIO"))
        assertTrue(out.startsWith("#EXTM3U"))
        // 540 is not offered: the tallest under it (360) plays
        assertEquals(listOf("360/index.m3u8"), uris(HlsMaster.filter(master, "540")))
        // under every offer: the smallest plays
        assertEquals(listOf("360/index.m3u8"), uris(HlsMaster.filter(master, "240")))
    }

    @Test
    fun bestKeepsFullHdAndAbove() {
        assertEquals(listOf("1080/index.m3u8", "2160/index.m3u8"), uris(HlsMaster.filter(master, "best")))
        val sdOnly = master.lines().filterNot { it.contains("1920x1080") || it.contains("3840x2160") || it.startsWith("1080") || it.startsWith("2160") }.joinToString("\n")
        assertEquals(listOf("720/index.m3u8"), uris(HlsMaster.filter(sdOnly, "best")))
    }

    @Test
    fun autoAndUnknownPlaylistsAreUntouched() {
        assertEquals(master, HlsMaster.filter(master, "auto"))
        val noRes = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\na.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=2\nb.m3u8"
        assertEquals(noRes, HlsMaster.filter(noRes, "720"))
        val media = "#EXTM3U\n#EXTINF:4,\nseg1.ts"
        assertEquals(media, HlsMaster.filter(media, "720"))
    }
}
