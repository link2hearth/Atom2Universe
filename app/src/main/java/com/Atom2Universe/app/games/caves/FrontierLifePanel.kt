package com.Atom2Universe.app.games.caves

import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.view.Gravity
import android.view.View
import com.Atom2Universe.app.R

internal class FrontierLifePanel(private val a: CaveActivity) {
    private var open=false
    private var generation=0
    private var busy=false
    private fun available(): Boolean {
        if(a.isFinishing || a.isDestroyed) { a.renderer.gamePaused=false;return false }
        return !open && a.invOverlay.visibility != View.VISIBLE
    }
    private fun content()=LinearLayout(a).apply {
        orientation=LinearLayout.VERTICAL
        val p=CaveUiStyle.dp(a,12);setPadding(p,p,p,p)
    }
    private fun show(title: Int,body: LinearLayout) {
        val dp = { value: Int -> CaveUiStyle.dp(a,value) }
        val root=object : LinearLayout(a) {
            override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
                super.onMeasure(
                    View.MeasureSpec.makeMeasureSpec(minOf(View.MeasureSpec.getSize(widthMeasureSpec),dp(560)),View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(minOf(View.MeasureSpec.getSize(heightMeasureSpec),dp(320)),View.MeasureSpec.EXACTLY))
            }
        }.apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(12),dp(8),dp(12),dp(8))
            background=CaveUiStyle.bubble(a)
            elevation=dp(8).toFloat()
        }
        root.addView(LinearLayout(a).apply {
            gravity=Gravity.CENTER_VERTICAL
            addView(TextView(a).apply {
                setText(title);textSize=18f;setTextColor(CaveUiStyle.TEXT)
            },LinearLayout.LayoutParams(0,-2,1f))
            addView(Button(a).apply {
                CaveUiStyle.icon(this,"close",a.getString(R.string.cave_storage_close))
                setOnClickListener { a.invManager.closeInventory() }
            },LinearLayout.LayoutParams(dp(44),dp(44)))
        })
        fun style(column: LinearLayout) {
            for(index in 0 until column.childCount) {
                when(val child=column.getChildAt(index)) {
                    is Button -> CaveUiStyle.button(child)
                    is TextView -> child.setTextColor(CaveUiStyle.TEXT)
                    is LinearLayout -> style(child)
                }
            }
        }
        style(body)
        root.addView(ScrollView(a).apply { addView(body) },LinearLayout.LayoutParams(-1,0,1f))
        open=true;generation++
        a.invManager.showStoragePage(root) { open=false;generation++ }
    }
    fun travel() {
        if(!available()) return
        busy=false
        val body=content()
        body.addView(TextView(a).apply { setText(R.string.cave_travel_hint) })
        for(home in listOf(true,false)) body.addView(Button(a).apply {
            setText(if(home) R.string.cave_travel_home else R.string.cave_travel_explore)
            setOnClickListener {
                if(busy) return@setOnClickListener
                val session=generation
                busy=true;isEnabled=false
                a.glView.queueEvent {
                    val message=a.renderer.travel(home)
                    a.runOnUiThread {
                        if(!open || session!=generation) return@runOnUiThread
                        busy=false;isEnabled=true
                        Toast.makeText(a,message,Toast.LENGTH_LONG).show()
                        if(open && message==R.string.cave_travel_done) a.invManager.closeInventory()
                    }
                }
            }
        })
        show(R.string.cave_travel_title,body)
    }
    fun trade(initial: CaveRenderer.TradeView) {
        if(!available()) return
        busy=false
        val body=content()
        body.addView(TextView(a).apply { setText(R.string.cave_trade_hint) })
        val rows=content();body.addView(rows)
        fun render(view: CaveRenderer.TradeView) {
            rows.removeAllViews()
            view.offers.forEachIndexed { index,offer ->
                rows.addView(Button(a).apply {
                    CaveUiStyle.button(this)
                    isAllCaps=false
                    text=a.getString(R.string.cave_trade_offer,a.blockName(offer.cost),offer.costCount,
                        a.blockName(offer.result),offer.count,view.remaining[index])
                    isEnabled=!busy && view.remaining[index]>0 && (view.items[offer.cost] ?: 0)>=offer.costCount
                    setOnClickListener {
                        if(busy) return@setOnClickListener
                        val session=generation
                        busy=true;render(view)
                        a.glView.queueEvent {
                            val ok=a.renderer.trade(view.key,index)
                            val next=a.renderer.tradeView(view.key)
                            a.runOnUiThread {
                                if(!open || session!=generation) return@runOnUiThread
                                busy=false
                                if(!ok) Toast.makeText(a,R.string.cave_trade_failed,Toast.LENGTH_SHORT).show()
                                if(open) {
                                    if(next==null) a.invManager.closeInventory() else render(next)
                                }
                            }
                        }
                    }
                })
            }
        }
        render(initial)
        show(when(initial.role) { 0->R.string.cave_trade_farmer;1->R.string.cave_trade_artisan;else->R.string.cave_trade_keeper },body)
    }
}
