package com.Atom2Universe.app.games.caves

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.Atom2Universe.app.R

/** One reusable tile for the bag, recipe ingredients and both sides of a chest. */
internal class CaveItemTile(context: Context): FrameLayout(context) {
    companion object {
        fun edge(context: Context)=minOf(CaveUiStyle.dp(context,60),
            (context.resources.displayMetrics.widthPixels-CaveUiStyle.dp(context,100))/CaveActivity.ACTIVE_SIZE-CaveUiStyle.dp(context,4))
            .coerceAtLeast(CaveUiStyle.dp(context,32))
        fun pitch(context: Context)=edge(context)+CaveUiStyle.dp(context,4)
    }
    private fun dp(n: Int)=CaveUiStyle.dp(context,n)
    private val icon=View(context)
    private val label=TextView(context).apply {
        textSize=10f;setTextColor(CaveUiStyle.TEXT);gravity=Gravity.CENTER;maxLines=1;ellipsize=TextUtils.TruncateAt.END
    }
    private val count=TextView(context).apply {
        textSize=10f;setTextColor(CaveUiStyle.TEXT);setPadding(dp(3),0,dp(3),0)
        background=CaveUiStyle.panel(context,0xE51A2E29.toInt(),0)
    }
    private val badge=View(context)
    init {
        isFocusable=true
        addView(icon,LayoutParams(-1,-1).apply { setMargins(dp(10),dp(5),dp(10),dp(21)) })
        addView(label,LayoutParams(-1,dp(19),Gravity.BOTTOM).apply { marginStart=dp(3);marginEnd=dp(3) })
        addView(count,LayoutParams(-2,-2,Gravity.TOP or Gravity.END).apply { topMargin=dp(3);marginEnd=dp(3) })
        addView(badge,LayoutParams(dp(17),dp(17),Gravity.TOP or Gravity.START).apply { topMargin=dp(3);marginStart=dp(3) })
    }
    fun bind(drawable: Drawable?,name: String,amount: Int,selected: Boolean=false,favorite: Boolean=false,pinned: Boolean=false,available: Boolean=true,accent: Int?=null) {
        icon.background=drawable;label.text=name
        count.text=if(amount>0) CaveUiStyle.count(context,amount) else ""
        count.visibility=if(amount>0) VISIBLE else GONE
        badge.background=CaveActionDrawable(if(favorite) "star" else "pin")
        badge.visibility=if(favorite || pinned) VISIBLE else GONE
        background=CaveUiStyle.panel(context,if(selected) CaveUiStyle.SELECTED else 0xFF2A4038.toInt(),
            if(!available) CaveUiStyle.WARNING else if(selected) CaveUiStyle.ACCENT else accent ?: CaveUiStyle.BORDER,selected)
        alpha=if(available) 1f else .65f
        contentDescription=context.getString(R.string.cave_storage_row,name,amount)
        tooltipText=name
    }
}
