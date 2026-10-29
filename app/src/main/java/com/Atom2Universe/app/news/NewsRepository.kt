package com.Atom2Universe.app.news

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import java.io.IOException
import java.io.StringReader
import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.concurrent.TimeUnit

object NewsRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Rend la liste lue, sans la garder : l'écran Actus et le widget ont chacun la leur
     * (une recherche dans l'écran ne doit pas changer le widget).
     * Lève [IOException] si aucune source n'a répondu, pour que l'appelant garde ses articles.
     */
    suspend fun refresh(
        sources: List<NewsSource>,
        query: String = "",
        bannedWords: List<String> = emptyList()
    ): List<NewsArticle> = withContext(Dispatchers.IO) {
        val all = mutableListOf<NewsArticle>()
        var failures = 0

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
                failures++ // continue with next source
            }
        }
        if (sources.isNotEmpty() && failures == sources.size) throw IOException("all sources failed")

        val banned = normalizeWords(bannedWords)
        all
            .sortedByDescending { it.pubDateMs ?: 0L }
            .take(40)
            .filter { article -> !isBanned(article, banned) }
    }

    fun filterVisible(
        articles: List<NewsArticle>,
        hiddenIds: Set<String>,
        bannedWords: List<String>
    ): List<NewsArticle> {
        val banned = normalizeWords(bannedWords)
        return articles.filter { it.id !in hiddenIds && !isBanned(it, banned) }
    }

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
                                        link       = curLink?.takeIf { isWebLink(it) },
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

    /** Seuls les liens web s'ouvrent (ni `javascript:`, ni `file:`, ni `intent:`). */
    fun isWebLink(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val scheme = url.trim().substringBefore(':', "").lowercase()
        return scheme == "http" || scheme == "https"
    }

    private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}")

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()

    private fun normalizeWords(words: List<String>): List<String> =
        words.map { normalize(it) }.filter { it.isNotEmpty() }

    /** [bannedWords] déjà passés par [normalizeWords]. */
    private fun isBanned(article: NewsArticle, bannedWords: List<String>): Boolean {
        if (bannedWords.isEmpty()) return false
        val title  = normalize(article.title)
        val source = normalize(article.sourceName)
        return bannedWords.any { w -> title.contains(w) || source.contains(w) }
    }

    private fun buildId(title: String, link: String?): String =
        "${title.take(60)}_${link.orEmpty().take(80)}".hashCode().toString()

    // RFC 822 avec un fuseau nommé (« EST », « CET »…), que java.time ne lit pas.
    // SimpleDateFormat n'est pas sûr entre fils : l'écran et le widget lisent en même temps.
    private val RFC822_NAMED_ZONE = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
        .apply { isLenient = true }

    // ISO avec un décalage sans deux-points (« +0100 »)
    private val ISO_COMPACT_OFFSET = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]xx", Locale.ENGLISH)

    private fun parseDate(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        // ISO 8601 : « Z », « +01:00 », fractions de seconde
        try { return OffsetDateTime.parse(s).toInstant().toEpochMilli() } catch (_: DateTimeParseException) {}
        // RFC 822 avec décalage numérique ou GMT
        try {
            return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {}
        try { return OffsetDateTime.parse(s, ISO_COMPACT_OFFSET).toInstant().toEpochMilli() } catch (_: DateTimeParseException) {}
        try {
            return LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {}
        try {
            return LocalDate.parse(s).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {}
        return try {
            synchronized(RFC822_NAMED_ZONE) { RFC822_NAMED_ZONE.parse(s)?.time }
        } catch (_: Exception) { null }
    }
}
