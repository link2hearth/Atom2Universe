package com.Atom2Universe.app.games.caves

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import com.Atom2Universe.app.R

/** Storage actions stay pending until explicitly confirmed; dismissing never splits. */
internal class CaveStackPopup(private val a: CaveActivity) {
    private var popup: PopupWindow?=null
    private fun dp(n: Int)=CaveUiStyle.dp(a,n)
    fun cancel() { popup?.dismiss();popup=null }

    private fun titled(title: String)=LinearLayout(a).apply {
        orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8))
        addView(LinearLayout(a).apply {
            gravity=Gravity.CENTER_VERTICAL
            addView(TextView(a).apply {
                text=title;textSize=16f;setTextColor(CaveUiStyle.TEXT)
            },LinearLayout.LayoutParams(0,-2,1f))
            addView(Button(a).apply {
                CaveUiStyle.icon(this,"close",a.getString(R.string.cave_ui_close))
                setOnClickListener { cancel() }
            },LinearLayout.LayoutParams(dp(44),dp(44)))
        })
    }

    fun message(anchor: View,title: String,message: String) {
        cancel()
        val body=titled(title).apply {
            addView(TextView(a).apply {
                text=message;textSize=13f;setTextColor(CaveUiStyle.TEXT);setPadding(0,dp(8),0,dp(8))
            })
        }
        show(anchor,ScrollView(a).apply { addView(body) },dp(320))
    }

    fun choices(anchor: View,title: String,labels: List<String>,selected: Int=-1,choose: (Int)->Unit) {
        cancel()
        val body=titled(title).apply {
            labels.forEachIndexed { index,label ->
                addView(Button(a).apply {
                    CaveUiStyle.button(this,index==selected);text=label;gravity=Gravity.START or Gravity.CENTER_VERTICAL
                    isSelected=index==selected
                    minHeight=dp(48);setPadding(dp(10),dp(8),dp(10),dp(8))
                    setOnClickListener { cancel();choose(index) }
                },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(4) })
            }
        }
        show(anchor,ScrollView(a).apply { addView(body) },dp(380))
    }

    fun info(anchor: View,id: Short,count: Int) {
        cancel()
        val content=LinearLayout(a).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10))
            addView(TextView(a).apply { text=a.blockName(id);textSize=16f;setTextColor(CaveUiStyle.TEXT);setTypeface(typeface,1) })
            addView(TextView(a).apply { text=a.getString(R.string.cave_ui_owned,count);textSize=12f;setTextColor(CaveUiStyle.MUTED) })
            addView(TextView(a).apply {
                text=a.invManager.itemDescription(id);textSize=13f;setTextColor(CaveUiStyle.TEXT);setPadding(0,dp(8),0,0)
            })
        }
        show(anchor,ScrollView(a).apply { addView(content) },dp(280))
    }

    fun split(anchor: View,id: Short,count: Int,favorite: () -> Unit,apply: (Int) -> Unit) {
        cancel()
        val canSplit=count>1
        val maximum=(count-1).coerceAtLeast(1)
        val initial=(count/2).coerceAtLeast(1)
        val number=EditText(a).apply {
            inputType=InputType.TYPE_CLASS_NUMBER;setSingleLine();gravity=Gravity.CENTER
            textSize=19f;setTextColor(CaveUiStyle.TEXT);setText(initial.toString());selectAll()
            contentDescription=a.getString(R.string.cave_stack_split_amount)
            isEnabled=canSplit;alpha=if(canSplit) 1f else .4f
        }
        fun quantity()=(number.text.toString().toLongOrNull() ?: initial.toLong()).coerceIn(1L,maximum.toLong()).toInt()
        val content=LinearLayout(a).apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(8),dp(8),dp(8))
            addView(TextView(a).apply {
                text=a.blockName(id);textSize=14f;setTextColor(CaveUiStyle.TEXT)
                maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
            })
        }
        val row=LinearLayout(a).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),dp(4),dp(4),dp(4)) }
        fun arrow(delta: Int)=Button(a).apply {
            CaveUiStyle.button(this)
            setText(if(delta>0) R.string.cave_stack_up else R.string.cave_stack_down)
            contentDescription=a.getString(if(delta>0) R.string.cave_stack_increase else R.string.cave_stack_decrease)
            isEnabled=canSplit;alpha=if(canSplit) 1f else .4f
            setOnClickListener { number.setText((quantity().toLong()+delta).coerceIn(1L,maximum.toLong()).toString()) }
        }
        row.addView(arrow(-1),LinearLayout.LayoutParams(dp(44),dp(44)))
        row.addView(number,LinearLayout.LayoutParams(0,dp(48),1f))
        row.addView(arrow(1),LinearLayout.LayoutParams(dp(44),dp(44)))
        content.addView(row)
        val actions=LinearLayout(a)
        fun action(label: Int,primary: Boolean=false,run: () -> Unit)=Button(a).apply {
            CaveUiStyle.button(this,primary);setText(label)
            setOnClickListener { run() }
            actions.addView(this,LinearLayout.LayoutParams(0,dp(44),1f).apply { setMargins(dp(2),0,dp(2),0) })
        }
        action(R.string.cave_catalog_favorites) { cancel();favorite() }.apply {
            isSelected=a.invManager.isFavorite(id)
            if(isSelected) CaveUiStyle.button(this,true)
        }
        action(R.string.cave_stack_confirm,true) {
            val amount=quantity();cancel();if(canSplit) apply(amount)
        }.apply { isEnabled=canSplit;alpha=if(canSplit) 1f else .4f }
        content.addView(actions)
        show(anchor,content,dp(300))
    }

    private fun show(anchor: View,content: View,desiredWidth: Int) {
        val metrics=a.resources.displayMetrics
        val width=minOf(desiredWidth,metrics.widthPixels-dp(16))
        content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((metrics.heightPixels*.65f).toInt(),View.MeasureSpec.AT_MOST))
        val height=content.measuredHeight
        val at=IntArray(2);anchor.getLocationOnScreen(at)
        val x=(at[0]+anchor.width/2-width/2).coerceIn(dp(8),(metrics.widthPixels-width-dp(8)).coerceAtLeast(dp(8)))
        val y=(if(at[1]+anchor.height+height<metrics.heightPixels-dp(8)) at[1]+anchor.height else at[1]-height)
            .coerceIn(dp(8),(metrics.heightPixels-height-dp(8)).coerceAtLeast(dp(8)))
        popup=PopupWindow(content,width,height,true).apply {
            setBackgroundDrawable(CaveUiStyle.bubble(a));elevation=dp(10).toFloat()
            isOutsideTouchable=true;inputMethodMode=PopupWindow.INPUT_METHOD_NEEDED
            setOnDismissListener { popup=null }
            showAtLocation(anchor.rootView,Gravity.TOP or Gravity.LEFT,x,y)
        }
        CaveUiStyle.openBubble(content)
    }
}
