package com.Atom2Universe.app.games.memory

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R

class MemoryAdapter(
    private val cards: List<MemoryCard>,
    private val bitmapCache: Map<Int, Bitmap>,
    private val onCardClick: (Int) -> Unit
) : RecyclerView.Adapter<MemoryAdapter.CardViewHolder>() {

    var cardSize: Int = 0

    inner class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: FrameLayout = view.findViewById(R.id.memory_card_container)
        val front: ImageView = view.findViewById(R.id.memory_card_front)
        val back: View = view.findViewById(R.id.memory_card_back)

        init {
            view.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onCardClick(pos)
            }
        }

        fun bind(card: MemoryCard) = applyState(card)

        fun animateFlip(card: MemoryCard) {
            ObjectAnimator.ofFloat(container, "scaleX", 1f, 0f).apply {
                duration = 130
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        applyState(card)
                        ObjectAnimator.ofFloat(container, "scaleX", 0f, 1f).apply {
                            duration = 130
                            start()
                        }
                    }
                })
                start()
            }
        }

        private fun applyState(card: MemoryCard) {
            when (card.state) {
                CardState.FACE_DOWN -> {
                    front.visibility = View.INVISIBLE
                    back.visibility = View.VISIBLE
                    itemView.alpha = 1f
                    itemView.isClickable = true
                }
                CardState.FACE_UP -> {
                    front.setImageBitmap(bitmapCache[card.imageIndex])
                    front.visibility = View.VISIBLE
                    back.visibility = View.INVISIBLE
                    itemView.alpha = 1f
                    itemView.isClickable = true
                }
                CardState.MATCHED -> {
                    front.setImageBitmap(bitmapCache[card.imageIndex])
                    front.visibility = View.VISIBLE
                    back.visibility = View.INVISIBLE
                    itemView.alpha = 0.45f
                    itemView.isClickable = false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_memory_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_FLIP)) {
            holder.animateFlip(cards[position])
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        if (cardSize > 0) {
            val lp = holder.itemView.layoutParams
            if (lp != null) {
                lp.height = cardSize
                holder.itemView.layoutParams = lp
            }
        }
        holder.bind(cards[position])
    }

    override fun getItemCount() = cards.size

    fun flipCard(position: Int) = notifyItemChanged(position, PAYLOAD_FLIP)

    companion object {
        const val PAYLOAD_FLIP = "FLIP"
    }
}
