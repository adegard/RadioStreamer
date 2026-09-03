package com.adegard.radiostreamer.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URL

data class Episode(
    val title: String,
    val audioUrl: String,
    val pubDate: String,
    val duration: String?
)

data class PodcastFeed(
    val title: String,
    val imageUrl: String?,
    val episodes: List<Episode>
)

data class ItunesResult(
    val name: String,
    val feedUrl: String?,
    val imageUrl: String?,
    val artistName: String
)

object PodcastFetcher {

    private const val TAG = "PodcastFetcher"

    suspend fun fetchFeed(feedUrl: String): PodcastFeed? = withContext(Dispatchers.IO) {
        try {
            val xml = URL(feedUrl).readText()
            parseFeed(xml)
        } catch (e: Exception) {
            Log.e(TAG, "fetchFeed failed: ${e.message}")
            null
        }
    }

    suspend fun searchITunes(query: String): List<ItunesResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val json = URL("https://itunes.apple.com/search?term=$encoded&entity=podcast&limit=10").readText()
            val obj = JSONObject(json)
            val arr = obj.getJSONArray("results")
            (0 until arr.length()).mapNotNull { i ->
                val r = arr.getJSONObject(i)
                ItunesResult(
                    name = r.optString("trackName", r.optString("collectionName", "")),
                    feedUrl = r.optString("feedUrl", null),
                    imageUrl = r.optString("artworkUrl100", null),
                    artistName = r.optString("artistName", "")
                )
            }.filter { it.feedUrl != null }
        } catch (e: Exception) {
            Log.e(TAG, "searchITunes failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun discoverRssUrl(pageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val html = URL(pageUrl).readText()
            val patterns = listOf(
                Regex("""<link[^>]*type="application/rss\+xml"[^>]*href="([^"]*)""", RegexOption.IGNORE_CASE),
                Regex("""<link[^>]*href="([^"]*)"[^>]*type="application/rss\+xml""", RegexOption.IGNORE_CASE),
                Regex("""<link[^>]*type="application/atom\+xml"[^>]*href="([^"]*)""", RegexOption.IGNORE_CASE),
                Regex("""<link[^>]*href="([^"]*)"[^>]*type="application/atom\+xml""", RegexOption.IGNORE_CASE)
            )
            for (regex in patterns) {
                regex.find(html)?.groupValues?.get(1)?.let { return@withContext it }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "discoverRssUrl failed: ${e.message}")
            null
        }
    }

    private fun parseFeed(xml: String): PodcastFeed? {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var channelTitle: String? = null
        var channelImage: String? = null
        val episodes = mutableListOf<Episode>()

        var inItem = false
        var inImage = false
        var title: String? = null
        var audioUrl: String? = null
        var pubDate: String? = null
        var duration: String? = null
        var gotChannelTitle = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    when {
                        name.equals("item", ignoreCase = true) -> {
                            inItem = true
                            title = null; audioUrl = null; pubDate = null; duration = null
                        }
                        name.equals("image", ignoreCase = true) && !inItem -> inImage = true
                        name.equals("title", ignoreCase = true) && !inItem && !gotChannelTitle -> {
                            channelTitle = parser.nextText(); gotChannelTitle = true
                        }
                        name.equals("title", ignoreCase = true) && inItem -> {
                            title = parser.nextText()
                        }
                        name.equals("enclosure", ignoreCase = true) && inItem -> {
                            audioUrl = parser.getAttributeValue(null, "url")
                        }
                        name.equals("pubDate", ignoreCase = true) && inItem -> {
                            pubDate = parser.nextText()
                        }
                        name.equals("duration", ignoreCase = true) && inItem -> {
                            duration = parser.nextText()
                        }
                        name.equals("url", ignoreCase = true) && inImage -> {
                            channelImage = parser.nextText()
                        }
                        name.equals("image", ignoreCase = true) -> {
                            val href = parser.getAttributeValue(null, "href")
                            if (href != null && channelImage == null) channelImage = href
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    if (name.equals("image", ignoreCase = true) && !inItem) inImage = false
                    if (name.equals("item", ignoreCase = true) && inItem) {
                        inItem = false
                        if (audioUrl != null) {
                            episodes.add(
                                Episode(
                                    title = title ?: "Unknown",
                                    audioUrl = audioUrl,
                                    pubDate = pubDate ?: "",
                                    duration = duration
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return PodcastFeed(
            title = channelTitle ?: "Unknown Podcast",
            imageUrl = channelImage,
            episodes = episodes
        )
    }
}
