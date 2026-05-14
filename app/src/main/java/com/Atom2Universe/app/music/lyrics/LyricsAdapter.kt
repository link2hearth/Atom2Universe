package com.Atom2Universe.app.music.lyrics

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R

/**
 * Adapter pour afficher les paroles synchronisées ou non.
 * Utilise un système d'échelle progressive pour mettre en valeur la ligne actuelle (mode synced).
 * En mode non-synced, toutes les lignes sont affichées en blanc avec opacité complète.
 */
class LyricsAdapter(
    private val lines: List<LrcParser.LyricLine>,
    private val isSynced: Boolean = true
) : RecyclerView.Adapter<LyricsAdapter.LyricViewHolder>() {

    private var currentLineIndex: Int = -1
    private var isFirstUpdate: Boolean = true
    private var autoScrollEnabled: Boolean = true

    companion object {
        // Lignes vides ajoutées en bas pour que la dernière parole puisse
        // défiler jusqu'au milieu de l'écran au lieu de rester collée en bas
        private const val BOTTOM_PADDING_LINES = 4
    }

    class LyricViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: FrameLayout = view.findViewById(R.id.lyric_container)
        val textView: TextView = view.findViewById(R.id.lyric_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lyric_line, parent, false)
        return LyricViewHolder(view)
    }

    override fun onBindViewHolder(holder: LyricViewHolder, position: Int) {
        // Lignes de padding en bas : transparentes, sans texte
        if (position >= lines.size) {
            holder.textView.text = ""
            holder.textView.alpha = 0f
            return
        }

        val line = lines[position]
        holder.textView.text = line.text

        // Pour les paroles non synchronisées (plain text), afficher en blanc avec opacité complète
        // et permettre l'affichage complet du texte sans limite de lignes
        if (!isSynced) {
            holder.textView.textSize = 18f
            holder.textView.alpha = 1.0f
            holder.textView.scaleX = 1f
            holder.textView.scaleY = 1f
            holder.textView.setTextColor(0xFFFFFFFF.toInt())
            holder.textView.setShadowLayer(0f, 0f, 0f, 0)
            // Pas de limite de lignes pour le plain text - afficher tout le texte
            holder.textView.maxLines = Int.MAX_VALUE
            holder.textView.ellipsize = null
            return
        }

        // Pour les paroles synchronisées, limiter à 3 lignes pour un défilement fluide
        holder.textView.maxLines = 3
        holder.textView.ellipsize = TextUtils.TruncateAt.END

        // Mode synchronisé : calculer la distance par rapport à la ligne actuelle
        val distance = if (currentLineIndex >= 0) {
            kotlin.math.abs(position - currentLineIndex)
        } else {
            Int.MAX_VALUE
        }

        // Déterminer la taille du texte en fonction de la distance
        // Base 16sp, avec progression pour les lignes proches
        val textSize = when (distance) {
            0 -> 22f     // Ligne actuelle (16 * 1.4 ≈ 22)
            1 -> 20f     // ±1 ligne (16 * 1.3 ≈ 21)
            2 -> 18f     // ±2 lignes (16 * 1.2 ≈ 19)
            3 -> 17f     // ±3 lignes (16 * 1.1 ≈ 18)
            else -> 16f  // Toutes les autres
        }

        // Déterminer l'opacité en fonction de la distance
        val alpha = when (distance) {
            0 -> 1.0f
            1 -> 0.9f
            2 -> 0.8f
            3 -> 0.7f
            else -> 0.5f
        }

        // Appliquer la taille et l'opacité
        holder.textView.textSize = textSize
        holder.textView.alpha = alpha

        // Reset scale (au cas où il y avait des valeurs précédentes)
        holder.textView.scaleX = 1f
        holder.textView.scaleY = 1f

        // Couleur blanche uniforme, pas de glow
        holder.textView.setTextColor(0xFFFFFFFF.toInt())
        holder.textView.setShadowLayer(0f, 0f, 0f, 0)
    }

    override fun getItemCount(): Int = lines.size + BOTTOM_PADDING_LINES

    /**
     * Active ou désactive le défilement automatique.
     */
    fun setAutoScrollEnabled(enabled: Boolean) {
        autoScrollEnabled = enabled
    }

    /**
     * Retourne l'état du défilement automatique.
     */
    fun isAutoScrollEnabled(): Boolean = autoScrollEnabled

    /**
     * Met à jour la ligne actuelle et rafraîchit l'affichage.
     */
    fun setCurrentLine(index: Int, recyclerView: RecyclerView) {
        if (index == currentLineIndex) return

        val previousIndex = currentLineIndex
        currentLineIndex = index

        // Lors de la première mise à jour, rafraîchir TOUTES les lignes
        if (isFirstUpdate) {
            isFirstUpdate = false
            notifyDataSetChanged()
            // Scroller directement à l'index actuel (et non à 0) pour éviter
            // le flash au début lors d'un changement de visuel en cours de lecture
            if (autoScrollEnabled && index >= 0) {
                recyclerView.post {
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                    if (layoutManager != null) {
                        val recyclerHeight = recyclerView.height
                        var itemHeight = 0
                        for (i in 0 until recyclerView.childCount) {
                            val child = recyclerView.getChildAt(i)
                            if (child != null && child.height > 0) {
                                itemHeight = child.height
                                break
                            }
                        }
                        if (itemHeight == 0) itemHeight = 100
                        val offset = (recyclerHeight / 3) - (itemHeight / 2)
                        layoutManager.scrollToPositionWithOffset(index, offset)
                    }
                }
            }
        } else {
            // Rafraîchir les lignes dans la zone d'effet (±4 lignes autour de l'ancienne et nouvelle position)
            val minPrev = maxOf(0, previousIndex - 4)
            val maxPrev = minOf(lines.size - 1, previousIndex + 4)
            val minCurr = maxOf(0, currentLineIndex - 4)
            val maxCurr = minOf(lines.size - 1, currentLineIndex + 4)

            // Notifier les changements pour toutes les lignes affectées
            for (i in minPrev..maxPrev) {
                notifyItemChanged(i)
            }
            for (i in minCurr..maxCurr) {
                if (i < minPrev || i > maxPrev) { // Éviter les doublons
                    notifyItemChanged(i)
                }
            }

            // Auto-scroll vers la ligne actuelle si activé
            if (autoScrollEnabled && currentLineIndex >= 0) {
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                if (layoutManager != null) {
                    recyclerView.post {
                        // Calculer l'offset pour positionner la ligne
                        val recyclerHeight = recyclerView.height

                        // Trouver la hauteur d'un item
                        var itemHeight = 0
                        for (i in 0 until recyclerView.childCount) {
                            val child = recyclerView.getChildAt(i)
                            if (child != null && child.height > 0) {
                                itemHeight = child.height
                                break
                            }
                        }

                        if (itemHeight == 0) {
                            itemHeight = 100
                        }

                        // Positionner la ligne actuelle au tiers supérieur
                        val offset = (recyclerHeight / 3) - (itemHeight / 2)

                        layoutManager.scrollToPositionWithOffset(currentLineIndex, offset)
                    }
                }
            }
        }
    }
}
