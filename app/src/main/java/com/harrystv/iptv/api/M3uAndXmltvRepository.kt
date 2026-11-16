package com.harrystv.iptv.api

import android.util.Xml
import com.harrystv.iptv.model.EpgProgram
import com.harrystv.iptv.model.M3uChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object M3uAndXmltvRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    var cachedM3uChannels: List<M3uChannel> = emptyList()
    var cachedXmltvPrograms: Map<String, List<EpgProgram>> = emptyMap()

    suspend fun loadM3uPlaylist(m3uUrl: String): List<M3uChannel> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(m3uUrl).build()
        val body = client.newCall(request).execute().body?.string() ?: ""
        val channels = parseM3uText(body)
        cachedM3uChannels = channels
        channels
    }

    fun parseM3uText(content: String): List<M3uChannel> {
        val result = mutableListOf<M3uChannel>()
        val lines = content.lines()
        var currentName = "Unknown Channel"
        var currentGroup = "Uncategorized"
        var currentLogo: String? = null
        var currentTvgId: String? = null

        val groupRegex = Regex("""group-title="([^"]*)"""")
        val logoRegex = Regex("""tvg-logo="([^"]*)"""")
        val idRegex = Regex("""tvg-id="([^"]*)"""")

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("#EXTINF:")) {
                currentGroup = groupRegex.find(line)?.groupValues?.get(1)?.ifBlank { "General" } ?: "General"
                currentLogo = logoRegex.find(line)?.groupValues?.get(1)?.ifBlank { null }
                currentTvgId = idRegex.find(line)?.groupValues?.get(1)?.ifBlank { null }
                val commaIdx = line.lastIndexOf(',')
                currentName = if (commaIdx != -1 && commaIdx < line.length - 1) {
                    line.substring(commaIdx + 1).trim()
                } else {
                    "Channel ${result.size + 1}"
                }
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                result.add(
                    M3uChannel(
                        name = currentName,
                        groupTitle = currentGroup,
                        logoUrl = currentLogo,
                        tvgId = currentTvgId,
                        streamUrl = line
                    )
                )
                currentName = "Unknown Channel"
                currentGroup = "Uncategorized"
                currentLogo = null
                currentTvgId = null
            }
        }
        return result
    }

    suspend fun loadXmltvEpg(xmltvUrl: String): Map<String, List<EpgProgram>> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(xmltvUrl).build()
        val xmlBody = client.newCall(request).execute().body?.string() ?: ""
        val map = parseXmltv(xmlBody)
        cachedXmltvPrograms = map
        map
    }

    private fun parseXmltv(xmlContent: String): Map<String, List<EpgProgram>> {
        val programsByChannel = mutableMapOf<String, MutableList<EpgProgram>>()
        if (xmlContent.isBlank()) return emptyMap()

        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xmlContent))
        var eventType = parser.eventType

        var channelId = ""
        var startMillis = 0L
        var endMillis = 0L
        var title = ""
        var desc = ""
        var inProgramme = false

        val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val fmtNoTz = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

        fun parseDate(raw: String?): Long {
            if (raw.isNullOrBlank()) return 0L
            return try {
                fmt.parse(raw)?.time ?: 0L
            } catch (_: Exception) {
                try {
                    fmtNoTz.parse(raw.take(14))?.time ?: 0L
                } catch (_: Exception) {
                    0L
                }
            }
        }

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "programme" -> {
                            inProgramme = true
                            channelId = parser.getAttributeValue(null, "channel") ?: ""
                            startMillis = parseDate(parser.getAttributeValue(null, "start"))
                            endMillis = parseDate(parser.getAttributeValue(null, "stop"))
                            title = ""
                            desc = ""
                        }
                        "title" -> if (inProgramme) title = parser.nextText()
                        "desc" -> if (inProgramme) desc = parser.nextText()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "programme" && inProgramme) {
                        if (channelId.isNotEmpty() && title.isNotEmpty()) {
                            programsByChannel.getOrPut(channelId) { mutableListOf() }
                                .add(EpgProgram(channelId, startMillis, endMillis, title, desc))
                        }
                        inProgramme = false
                    }
                }
            }
            eventType = parser.next()
        }
        return programsByChannel
    }
}
