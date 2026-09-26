package app.xtream.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class LocalNetworkTest {
    private fun at(ip: String): (String) -> Array<InetAddress> = { arrayOf(InetAddress.getByName(ip)) }

    @Test
    fun publicStreamsAreProxied() {
        assertTrue(LocalNetwork.isPublic("https://cdn.example.com/master.m3u8", at("93.184.216.34")))
        assertTrue(LocalNetwork.isPublic("http://playit11.xyz/seg.ts", at("2606:4700::1111")))
    }

    @Test
    fun homeNetworkIsNeverProxied() {
        assertFalse(LocalNetwork.isPublic("http://192.168.1.1/m3/status", at("192.168.1.1")))
        assertFalse(LocalNetwork.isPublic("http://10.0.0.5/master.m3u8", at("10.0.0.5")))
        assertFalse(LocalNetwork.isPublic("http://172.16.4.2/x.m3u8", at("172.16.4.2")))
        assertFalse(LocalNetwork.isPublic("http://127.0.0.1:8080/x.m3u8", at("127.0.0.1")))
        assertFalse(LocalNetwork.isPublic("http://[::1]/x.m3u8", at("::1")))
        assertFalse(LocalNetwork.isPublic("http://[fd00::2]/x.m3u8", at("fd00::2")))
        assertFalse(LocalNetwork.isPublic("http://169.254.169.254/m3/", at("169.254.169.254")))
        assertFalse(LocalNetwork.isPublic("http://100.64.0.9/x.m3u8", at("100.64.0.9")))
        assertFalse(LocalNetwork.isPublic("http://0.0.0.0/x.m3u8", at("0.0.0.0")))
    }

    @Test
    fun namesThatPointInsideAreRefused() {
        assertFalse(LocalNetwork.isPublic("http://playit.192.168.1.1.nip.io/seg", at("192.168.1.1")))
        assertFalse(LocalNetwork.isPublic("http://localhost/x.m3u8", at("93.184.216.34")))
        assertFalse(LocalNetwork.isPublic("http://tv.local/x.m3u8", at("93.184.216.34")))
        assertFalse(LocalNetwork.isPublic("http://router/x.m3u8", at("93.184.216.34")))
        assertFalse(LocalNetwork.isPublic("file:///sdcard/x.m3u8", at("93.184.216.34")))
    }
}
