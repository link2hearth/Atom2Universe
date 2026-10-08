package com.Atom2Universe.app.news

data class NewsArticle(
    val id: String,
    val title: String,
    val link: String?,
    val pubDateMs: Long?,
    val sourceId: String,
    val sourceName: String
)
