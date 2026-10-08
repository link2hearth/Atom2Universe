package com.Atom2Universe.app.news

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.google.android.material.card.MaterialCardView

class NewsAdapter(
    private val onOpen: (NewsArticle) -> Unit,
    private val onHide: (NewsArticle) -> Unit
) : RecyclerView.Adapter<NewsAdapter.ViewHolder>() {

    var items: List<NewsArticle> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var highlightedId: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card   : MaterialCardView = view.findViewById(R.id.news_card_root)
        val title  : TextView          = view.findViewById(R.id.news_card_title)
        val meta   : TextView          = view.findViewById(R.id.news_card_meta)
        val btnOpen: TextView          = view.findViewById(R.id.news_card_btn_open)
        val btnHide: TextView          = view.findViewById(R.id.news_card_btn_hide)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_news_article, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val article = items[position]
        holder.title.text = article.title
        holder.meta.text  = buildMeta(article)

        val isHighlighted = article.id == highlightedId
        holder.card.setCardBackgroundColor(
            Color.parseColor(if (isHighlighted) "#1E3A5F" else "#0F172A")
        )
        if (isHighlighted) {
            holder.card.strokeWidth = 2
            holder.card.strokeColor = Color.parseColor("#3B82F6")
        } else {
            holder.card.strokeWidth = 0
        }

        holder.btnOpen.setOnClickListener { onOpen(article) }
        holder.btnHide.setOnClickListener { onHide(article) }
    }

    override fun getItemCount(): Int = items.size

    private fun buildMeta(article: NewsArticle): String {
        val parts = mutableListOf<String>()
        if (article.sourceName.isNotBlank()) parts += article.sourceName
        article.pubDateMs?.let { parts += relativeTime(it) }
        return parts.joinToString(" · ")
    }

    private fun relativeTime(epochMs: Long): String {
        val diff = System.currentTimeMillis() - epochMs
        return when {
            diff < 60_000L         -> "à l’instant"
            diff < 3_600_000L      -> "il y a ${diff / 60_000} min"
            diff < 86_400_000L     -> "il y a ${diff / 3_600_000} h"
            else                   -> "il y a ${diff / 86_400_000} j"
        }
    }
}
