package com.Atom2Universe.app.games.caves

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.Atom2Universe.app.R

/** Native, scalable inventory shell. InventoryManager owns actions and selection. */
internal class CaveInventoryPanel(private val activity: CaveActivity) {
    private fun dp(n: Int) = CaveUiStyle.dp(activity, n)
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun label(size: Float = 13f) = TextView(activity).apply {
        textSize = size; setTextColor(CaveUiStyle.TEXT); setPadding(dp(8), dp(4), dp(8), dp(4))
    }
    private fun button(res: Int) = Button(activity).apply {
        setText(res); CaveUiStyle.button(this)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
    }
    val inventoryTab = button(R.string.cave_ui_bag)
    val craftTab = button(R.string.cave_ui_workshop)
    val close = button(R.string.cave_ui_close)
    val search = EditText(activity).apply {
        setSingleLine(); textSize = 14f; setTextColor(CaveUiStyle.TEXT); setHintTextColor(CaveUiStyle.MUTED)
        setHint(R.string.cave_ui_search); setPadding(dp(12), 0, dp(8), 0)
        background = CaveUiStyle.panel(activity)
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        inputType = android.text.InputType.TYPE_CLASS_TEXT
    }
    val category = Spinner(activity)
    val craftable = CheckBox(activity).apply { setText(R.string.cave_ui_craftable); setTextColor(CaveUiStyle.TEXT); textSize = 12f }
    val sort = button(R.string.cave_ui_sort)
    val previous = button(R.string.cave_ui_previous)
    val next = button(R.string.cave_ui_next)
    val related = button(R.string.cave_ui_recipes_for_item)
    val craftOne = button(R.string.cave_inv_craft_btn)
    val craftFive = button(R.string.cave_ui_craft_five)
    val favoritesOnly = button(R.string.cave_catalog_favorites)
    val recentOnly = button(R.string.cave_catalog_recent)
    val clearSearch = button(R.string.cave_catalog_clear)
    val filterAll = button(R.string.cave_ui_all)
    val filterGear = button(R.string.cave_ui_equipment)
    val filterBuild = button(R.string.cave_ui_materials)
    val filterGarden = button(R.string.cave_ui_garden)
    val craftMax = button(R.string.cave_catalog_craft_max)
    val ingredients = column()
    val summary = label(12f)
    val status = label(11f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END }
    val empty = label().apply { gravity = Gravity.CENTER; setText(R.string.cave_ui_empty_search) }
    lateinit var pager: ViewPager2
    lateinit var recipeList: RecyclerView
    lateinit var pageLabel: TextView
    lateinit var footer: LinearLayout
    lateinit var shortcutBar: LinearLayout
    lateinit var bagDropArea: FrameLayout
    lateinit var detailScroll: ScrollView
    private var bubble: PopupWindow? = null

    fun dismissDetails() { bubble?.dismiss() }

    fun showDetails(anchor: View) {
        dismissDetails()
        val metrics=activity.resources.displayMetrics
        val width=minOf(dp(300),(metrics.widthPixels*.85f).toInt())
        val heightLimit=(metrics.heightPixels*.68f).toInt()
        detailScroll.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightLimit,View.MeasureSpec.AT_MOST))
        val height=detailScroll.measuredHeight.coerceAtMost(heightLimit)
        val location=IntArray(2);anchor.getLocationOnScreen(location)
        val x=(location[0]+anchor.width/2-width/2).coerceIn(dp(8),(metrics.widthPixels-width-dp(8)).coerceAtLeast(dp(8)))
        val below=location[1]+anchor.height+dp(4)
        val y=(if(below+height<metrics.heightPixels-dp(68)) below else location[1]-height-dp(4))
            .coerceIn(dp(8),(metrics.heightPixels-height-dp(8)).coerceAtLeast(dp(8)))
        bubble=PopupWindow(detailScroll,width,height,true).apply {
            setBackgroundDrawable(CaveUiStyle.panel(activity,CaveUiStyle.SURFACE))
            elevation=dp(10).toFloat();isOutsideTouchable=true
            inputMethodMode=PopupWindow.INPUT_METHOD_NOT_NEEDED
            showAtLocation(activity.invOverlay,Gravity.TOP or Gravity.LEFT,x,y)
        }
    }

    fun resizeDetails() {
        val popup=bubble?.takeIf { it.isShowing } ?: return
        val limit=(activity.resources.displayMetrics.heightPixels*.68f).toInt()
        detailScroll.measure(View.MeasureSpec.makeMeasureSpec(popup.width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(limit,View.MeasureSpec.AT_MOST))
        popup.update(popup.width,detailScroll.measuredHeight.coerceAtMost(limit))
    }

    fun populate(root: FrameLayout) {
        root.removeAllViews()
        val portrait=activity.resources.displayMetrics.heightPixels>activity.resources.displayMetrics.widthPixels
        val dim = FrameLayout(activity).apply {
            id = R.id.cave_inv_dim_area; setBackgroundColor(0x3514211B); isClickable = true
            layoutParams = FrameLayout.LayoutParams(-1, -1).also { it.bottomMargin = dp(68) }
        }
        root.addView(dim)
        val panel = column().apply {
            id = R.id.cave_inv_panel; isClickable = true
            background = CaveUiStyle.panel(activity, 0xF51C302B.toInt(), 0xFF577263.toInt())
            setPadding(dp(6), dp(4), dp(6), dp(4))
            val metrics = activity.resources.displayMetrics
            layoutParams = FrameLayout.LayoutParams(minOf((metrics.widthPixels * .94f).toInt(), dp(1100)), -1).also {
                it.gravity = Gravity.CENTER; it.setMargins(0, dp(4), 0, dp(4))
            }
        }
        dim.addView(panel)
        fun icon(b: Button,key: String,res: Int) {
            CaveUiStyle.icon(b,key,activity.getString(res))
            b.layoutParams=LinearLayout.LayoutParams(dp(44),dp(44)).also { it.setMargins(dp(2),dp(2),dp(2),dp(2)) }
        }
        for((b,key,res) in listOf(Triple(close,"close",R.string.cave_ui_close),
            Triple(sort,"sort",R.string.cave_ui_sort),Triple(previous,"previous",R.string.cave_ui_previous),Triple(next,"next",R.string.cave_ui_next),
            Triple(clearSearch,"close",R.string.cave_catalog_clear),Triple(favoritesOnly,"star",R.string.cave_catalog_favorites),
            Triple(recentOnly,"clock",R.string.cave_catalog_recent),Triple(filterAll,"all",R.string.cave_ui_all),
            Triple(filterGear,"combat",R.string.cave_ui_equipment),Triple(filterBuild,"place",R.string.cave_ui_materials),
            Triple(filterGarden,"garden",R.string.cave_ui_garden),Triple(related,"previous",R.string.cave_ui_previous))) icon(b,key,res)
        panel.addView(row().apply {
            addView(inventoryTab); addView(craftTab)
            if(!portrait) {
                addView(search,LinearLayout.LayoutParams(0,dp(44),1f));addView(clearSearch);addView(sort)
            } else addView(Space(activity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(close)
        })
        val body = column()
        panel.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        val library = column()
        body.addView(library, LinearLayout.LayoutParams(-1,-1))
        if(portrait) library.addView(row().apply {
            addView(search, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(clearSearch);addView(sort)
        })
        library.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled=false
            addView(row().apply {
                for(b in listOf(filterAll,filterGear,filterBuild,filterGarden,favoritesOnly,recentOnly)) addView(b)
                addView(category,LinearLayout.LayoutParams(dp(130),dp(44)))
                addView(craftable,LinearLayout.LayoutParams(-2,dp(44)))
            })
        })
        summary.visibility=View.GONE
        library.addView(summary)
        val content = FrameLayout(activity).also { bagDropArea=it }
        library.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        pager = ViewPager2(activity).apply { id = R.id.cave_inv_pager }
        recipeList = RecyclerView(activity).apply { id = R.id.cave_inv_crafting_recycler; clipToPadding = false; setPadding(dp(3), dp(4), dp(3), dp(4)) }
        content.addView(pager, FrameLayout.LayoutParams(-1, -1))
        content.addView(recipeList, FrameLayout.LayoutParams(-1, -1))
        empty.id = R.id.cave_inv_crafting_empty
        content.addView(empty, FrameLayout.LayoutParams(-1, -1))
        pageLabel = label(12f).apply { id = R.id.cave_inv_page_indicator; gravity = Gravity.CENTER }
        footer = row().apply {
            addView(previous); addView(pageLabel, LinearLayout.LayoutParams(0, dp(44), 1f)); addView(next)
        }
        library.addView(footer)
        detailScroll = ScrollView(activity).apply { isFillViewport = false }
        val detail = column().apply {
            id = R.id.cave_inv_info_column; background = CaveUiStyle.panel(activity, CaveUiStyle.SURFACE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        detailScroll.addView(detail)
        detail.addView(label(12f).apply { id = R.id.cave_inv_right_header; setText(R.string.cave_ui_details); setTextColor(CaveUiStyle.MUTED) })
        detail.addView(row().apply {
            addView(View(activity).apply { id = R.id.cave_inv_info_sprite }, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(column().apply {
                addView(label(16f).apply { id = R.id.cave_inv_info_name; setTypeface(typeface, 1) })
                addView(label(12f).apply { id = R.id.cave_inv_info_count; setTextColor(CaveUiStyle.MUTED) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        detail.addView(View(activity).apply { id = R.id.cave_inv_info_divider; setBackgroundColor(CaveUiStyle.BORDER) }, LinearLayout.LayoutParams(-1, dp(1)))
        detail.addView(related)
        detail.addView(row().apply { for(b in listOf(craftOne,craftFive,craftMax)) {
            addView(b,LinearLayout.LayoutParams(0,dp(44),1f).apply { setMargins(dp(2),dp(3),dp(2),dp(3)) })
        } })
        detail.addView(ingredients)
        detail.addView(label().apply { id = R.id.cave_inv_info_ingredients; setLineSpacing(dp(3).toFloat(), 1f) })
        CaveUiStyle.button(craftOne, true)
        detail.addView(column().apply {
            id = R.id.cave_inv_sell_panel
            addView(label().apply { id = R.id.cave_inv_sell_price; setTextColor(CaveUiStyle.MUTED) })
            addView(button(R.string.cave_inv_sell_btn).apply { id = R.id.cave_inv_sell_btn; setTextColor(CaveUiStyle.WARNING) })
        })
        panel.addView(status, LinearLayout.LayoutParams(-1, -2))
        shortcutBar=row().apply {
            background=CaveUiStyle.panel(activity,CaveUiStyle.SURFACE)
            setPadding(dp(4),dp(4),dp(4),dp(4))
        }
        root.addView(shortcutBar,FrameLayout.LayoutParams(
            minOf((activity.resources.displayMetrics.widthPixels*.94f).toInt(),dp(540)),dp(60),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=dp(4) })
    }
}
