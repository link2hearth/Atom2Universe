package com.Atom2Universe.app.games.caves

import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.Atom2Universe.app.R

internal class FrontierLifePanel(private val a: CaveActivity) {
    private var dialog: AlertDialog?=null
    private var busy=false
    private fun available(): Boolean {
        if(a.isFinishing || a.isDestroyed) { a.renderer.gamePaused=false;return false }
        return dialog?.isShowing!=true
    }
    private fun content()=LinearLayout(a).apply {
        orientation=LinearLayout.VERTICAL
        val p=CaveUiStyle.dp(a,12);setPadding(p,p,p,p)
    }
    private fun show(title: Int,body: LinearLayout) {
        a.renderer.gamePaused=true
        dialog=AlertDialog.Builder(a).setTitle(title)
            .setView(ScrollView(a).apply { addView(body) })
            .setPositiveButton(R.string.cave_storage_close,null).create().also {
                it.setOnDismissListener { dialog=null;a.renderer.gamePaused=false;a.saveWorldAsync() }
                it.show()
            }
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
                busy=true;isEnabled=false
                a.glView.queueEvent {
                    val message=a.renderer.travel(home)
                    a.runOnUiThread {
                        busy=false;isEnabled=true
                        Toast.makeText(a,message,Toast.LENGTH_LONG).show()
                        if(message==R.string.cave_travel_done) dialog?.dismiss()
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
                    isAllCaps=false
                    text=a.getString(R.string.cave_trade_offer,a.blockName(offer.cost),offer.costCount,
                        a.blockName(offer.result),offer.count,view.remaining[index])
                    isEnabled=!busy && view.remaining[index]>0 && (view.items[offer.cost] ?: 0)>=offer.costCount
                    setOnClickListener {
                        if(busy) return@setOnClickListener
                        busy=true;render(view)
                        a.glView.queueEvent {
                            val ok=a.renderer.trade(view.key,index)
                            val next=a.renderer.tradeView(view.key)
                            a.runOnUiThread {
                                busy=false
                                if(!ok) Toast.makeText(a,R.string.cave_trade_failed,Toast.LENGTH_SHORT).show()
                                if(next==null) dialog?.dismiss() else if(dialog!=null) render(next)
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
