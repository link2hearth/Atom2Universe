package com.Atom2Universe.app.news

data class NewsSource(
    val id: String,
    val name: String,
    val feedUrl: String,
    val enabledByDefault: Boolean = true
) {
    companion object {
        val DEFAULT_SOURCES = listOf(
            NewsSource("google_fr",  "Google News",  "https://news.google.com/rss?hl=fr&gl=FR&ceid=FR:fr"),
            NewsSource("nasa",       "NASA",          "https://www.nasa.gov/rss/dyn/breaking_news.rss")
        )

        const val SEARCH_URL = "https://news.google.com/rss/search?q={query}&hl=fr&gl=FR&ceid=FR:fr"
    }
}
