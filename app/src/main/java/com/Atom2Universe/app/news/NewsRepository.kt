package com.Atom2Universe.app.news

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import java.io.StringReader
import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object NewsRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    @Volatile var articles: List<NewsArticle> = emptyList()
        private set

    @Volatile var lastError: String? = null
        private set

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun refresh(
        sources: List<NewsSource>,
        query: String = "",
        bannedWords: List<String> = emptyList()
    ): List<NewsArticle> = withContext(Dispatchers.IO) {
        lastError = null
        val all = mutableListOf<NewsArticle>()

        for (source in sources) {
            val url = if (query.isNotBlank() && source.id == "google_fr") {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                NewsSource.SEARCH_URL.replace("{query}", encoded)
            } else {
                source.feedUrl
            }
            try {
                all.addAll(fetchSource(url, source.id, source.name))
            } catch (_: Exception) {
                // continue with next source
            }
        }

        val sorted = all
            .sortedByDescending { it.pubDateMs ?: 0L }
            .take(40)
            .filter { article -> !isBanned(article, bannedWords) }

        articles = sorted
        sorted
    }

    fun filterVisible(hiddenIds: Set<String>, bannedWords: List<String>): List<NewsArticle> =
        articles.filter { it.id !in hiddenIds }
            .filter { article -> !isBanned(article, bannedWords) }

    // ── Network ───────────────────────────────────────────────────────────────

    private fun fetchSource(url: String, sourceId: String, sourceName: String): List<NewsArticle> {
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; RSS Reader)")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw Exception("HTTP ${response.code}")
        }
        val body = response.body?.string().orEmpty()
        response.close()
        return parseRss(body, sourceId, sourceName)
    }

    // ── RSS/Atom parser ───────────────────────────────────────────────────────

    private fun parseRss(xml: String, sourceId: String, sourceName: String): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        try {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var inItem    = false
            var curTitle  : String? = null
            var curLink   : String? = null
            var curDate   : Long?   = null
            var lastTag   : String? = null

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        lastTag = parser.name.lowercase()
                        when (lastTag) {
                            "item", "entry" -> {
                                inItem = true
                                curTitle = null; curLink = null; curDate = null
                            }
                            "link" -> if (inItem) {
                                val href = parser.getAttributeValue(null, "href")
                                if (!href.isNullOrBlank()) curLink = href
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inItem) {
                            val t = parser.text?.trim() ?: ""
                            when (lastTag) {
                                "title"                      -> if (t.isNotBlank()) curTitle = t
                                "link"                       -> if (t.isNotBlank() && curLink == null) curLink = t
                                "pubdate", "published",
                                "updated", "dc:date"         -> if (curDate == null) curDate = parseDate(t)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name.lowercase()) {
                            "item", "entry" -> {
                                if (inItem && !curTitle.isNullOrBlank()) {
                                    articles += NewsArticle(
                                        id         = buildId(curTitle!!, curLink),
                                        title      = curTitle!!.trim(),
                                        link       = curLink,
                                        pubDateMs  = curDate,
                                        sourceId   = sourceId,
                                        sourceName = sourceName
                                    )
                                }
                                inItem  = false
                                lastTag = null
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) { /* return what we have */ }
        return articles
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}"), "")
            .lowercase()

    private fun isBanned(article: NewsArticle, bannedWords: List<String>): Boolean {
        if (bannedWords.isEmpty()) return false
        val title  = normalize(article.title)
        val source = normalize(article.sourceName)
        return bannedWords.any { word ->
            val w = normalize(word)
            w.isNotEmpty() && (title.contains(w) || source.contains(w))
        }
    }

    private fun buildId(title: String, link: String?): String =
        "${title.take(60)}_${link.orEmpty().take(80)}".hashCode().toString()

    private val DATE_FORMATS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd"
    )

    private fun parseDate(raw: String): Long? {
        val s = raw.trim()
        for (pattern in DATE_FORMATS) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
                sdf.isLenient = true
                return sdf.parse(s)?.time
            } catch (_: Exception) {}
        }
        return null
    }
}
