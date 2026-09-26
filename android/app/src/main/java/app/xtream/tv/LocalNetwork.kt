package app.xtream.tv

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Keeps web pages from using the app as a bridge into the TV's own network.
 * Only addresses on the public internet count as public; the router, other
 * devices on the Wi-Fi and the TV itself do not.
 */
object LocalNetwork {
    private val localNames = listOf(".local", ".localhost", ".lan", ".home", ".internal", ".home.arpa")

    /** Resolves the host, so call it off the main thread. */
    fun isPublic(raw: String, resolve: (String) -> Array<InetAddress> = InetAddress::getAllByName): Boolean {
        val uri = try {
            java.net.URI(raw)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase()?.trim('[', ']')?.trimEnd('.') ?: return false
        if (host.isEmpty() || host == "localhost" || !host.contains('.') && !host.contains(':')) return false
        if (localNames.any { host.endsWith(it) }) return false
        val addresses = try {
            resolve(host)
        } catch (_: Exception) {
            return false
        }
        return addresses.isNotEmpty() && addresses.none { isPrivate(it) }
    }

    fun isPrivate(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) {
            return true
        }
        val b = address.address
        return when (address) {
            // 100.64.0.0/10 carrier NAT, 0.0.0.0/8
            is Inet4Address -> b[0].toInt() == 0 || (b[0].toInt() == 100 && (b[1].toInt() and 0xC0) == 64)
            // fc00::/7 unique local, and IPv4-mapped addresses pointing inside
            is Inet6Address -> (b[0].toInt() and 0xFE) == 0xFC || isMappedPrivate(b)
            else -> true
        }
    }

    private fun isMappedPrivate(b: ByteArray): Boolean {
        for (i in 0 until 10) if (b[i].toInt() != 0) return false
        if (b[10].toInt() != -1 || b[11].toInt() != -1) return false
        return isPrivate(InetAddress.getByAddress(b.copyOfRange(12, 16)))
    }
}
