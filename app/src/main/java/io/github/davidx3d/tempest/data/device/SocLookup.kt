package io.github.davidx3d.tempest.data.device

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional name lookup for chips missing from [SocCatalog]. It downloads Wikipedia's public chip
 * list for the maker and matches the code on the device, so nothing about the phone is sent.
 * Answers are kept, so every chip is looked up once.
 */
class SocLookup(context: Context) {

    private val prefs = context.getSharedPreferences("soc_lookup", Context.MODE_PRIVATE)

    suspend fun find(code: String): SocName? = withContext(Dispatchers.IO) {
        val key = code.trim().lowercase()
        prefs.getString(key, null)?.let { return@withContext SocName(it) }
        val name = runCatching { fetch(key) }.getOrNull() ?: return@withContext null
        prefs.edit().putString(key, name.name).apply()
        name
    }

    private fun fetch(code: String): SocName? {
        val name = when (SocCatalog.vendor(code)) {
            SocVendor.MediaTek -> WikiSocParser.mediatek(wikitext("List_of_MediaTek_systems_on_chips"), code)
            SocVendor.Qualcomm -> WikiSocParser.qualcomm(wikitext("List_of_Qualcomm_Snapdragon_systems_on_chips"), code)
            else -> null
        }
        return name?.let { SocName(it) }
    }

    private fun wikitext(page: String): String {
        val url = URL("https://en.wikipedia.org/w/api.php?action=parse&page=$page&prop=wikitext&format=json&formatversion=2")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Tempest/1.4 (https://github.com/david-x3d/tempest)")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
            val body = connection.inputStream.use { stream ->
                // readNBytes needs Android 13; read in chunks so Android 12 works too.
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    require(out.size() <= MAX_BYTES) { "response too large" }
                }
                out.toString(Charsets.UTF_8.name())
            }
            return JSONObject(body).getJSONObject("parse").getString("wikitext")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val MAX_BYTES = 4_000_000
    }
}

/** Pulls a marketing name out of Wikipedia's wikitext tables. Pure, so it is easy to test. */
object WikiSocParser {

    /** "MT6995", "MT6989T" or "mt6991z/cza" to the four-digit part, or null for other codes. */
    fun mediatekCode(hint: String): String? = Regex("^mt\\d{4}", RegexOption.IGNORE_CASE).find(hint.trim())?.value?.uppercase()

    fun qualcommCode(hint: String): String? =
        Regex("^(?:sm|sdm|msm|sd|qcm)\\d{3,4}", RegexOption.IGNORE_CASE).find(hint.trim())?.value?.uppercase()

    /** Rows look like `<b>Dimensity{{nbs}}9300</b> ... (MT6989 <br>MT6989W/CZA)`. */
    fun mediatek(wikitext: String, hint: String): String? {
        val code = mediatekCode(hint) ?: return null
        val match = Regex("""\b${Regex.escape(code)}(?![0-9])""", RegexOption.IGNORE_CASE)
        val names = Regex("""<b>((?:Dimensity|Helio)[^<]*)</b>""").findAll(wikitext).toList()
        names.forEachIndexed { i, name ->
            val end = names.getOrNull(i + 1)?.range?.first ?: wikitext.length
            if (match.containsMatchIn(wikitext.substring(name.range.last + 1, end))) return clean(name.groupValues[1])
        }
        return null
    }

    /** Rows start with the code (`|SM8650-AB`); the name sits in the first row of a rowspan group. */
    fun qualcomm(wikitext: String, hint: String): String? {
        val code = qualcommCode(hint) ?: return null
        val name = Regex("""^\|\s*(?:rowspan="\d+"\s*\|\s*)?Snapdragon(?:\s|<br\s*/?>|&nbsp;)*([^<{|\n">\[\]]+)""", RegexOption.MULTILINE)
        val match = Regex("""^\|\s*${Regex.escape(code)}(?![0-9])""", RegexOption.IGNORE_CASE)
        var last: String? = null
        for (row in wikitext.split("\n|-")) {
            name.find(row)?.let { last = "Snapdragon " + clean(it.groupValues[1]) }
            if (match.containsMatchIn(row.trimStart())) return last
        }
        return null
    }

    private fun clean(raw: String): String =
        raw.replace("{{nbs}}", " ").replace("&nbsp;", " ").replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim()
}
