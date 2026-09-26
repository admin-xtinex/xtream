package app.xtream.tv

/**
 * Reads the quality list out of an HLS master playlist and trims it to the saved
 * quality, so every player on the page can only load the chosen stream.
 */
object HlsMaster {
    private val resolution = Regex("RESOLUTION=\\d+x(\\d+)", RegexOption.IGNORE_CASE)

    fun isMaster(text: String): Boolean = text.contains("#EXT-X-STREAM-INF", ignoreCase = true)

    /** Heights of every variant, tallest first, without repeats. */
    fun heights(text: String): List<Int> =
        variants(text).map { it.height }.filter { it > 0 }.distinct().sortedDescending()

    /**
     * Keeps only the variants the quality allows: "auto" keeps all, "best" keeps
     * 1080p and up (or the top one when the stream stops lower), a height keeps the
     * tallest variant at or under it. A playlist without resolutions is left alone.
     */
    fun filter(text: String, quality: String): String {
        if (quality == "auto" || !isMaster(text)) return text
        val all = variants(text)
        val sized = all.filter { it.height > 0 }
        if (sized.isEmpty() || sized.size != all.size) return text
        val keep = keptHeights(sized.map { it.height }, quality)
        if (keep.isEmpty()) return text
        val drop = all.filter { it.height !in keep }.flatMap { listOf(it.infoLine, it.uriLine) }.toSet()
        val lines = text.lines()
        return lines.filterIndexed { index, _ -> index !in drop }.joinToString("\n")
    }

    fun keptHeights(heights: List<Int>, quality: String): Set<Int> {
        if (heights.isEmpty()) return emptySet()
        val top = heights.max()
        if (quality == "best") {
            val floor = minOf(top, 1080)
            return heights.filter { it >= floor }.toSet()
        }
        val want = quality.toIntOrNull() ?: return heights.toSet()
        val pick = heights.filter { it <= want }.maxOrNull() ?: heights.min()
        return setOf(pick)
    }

    private class Variant(val infoLine: Int, val uriLine: Int, val height: Int)

    private fun variants(text: String): List<Variant> {
        val lines = text.lines()
        val found = ArrayList<Variant>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || lines[j].trim().startsWith("#"))) j++
                if (j < lines.size) {
                    val h = resolution.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    found.add(Variant(i, j, h))
                    i = j
                }
            }
            i++
        }
        return found
    }
}
