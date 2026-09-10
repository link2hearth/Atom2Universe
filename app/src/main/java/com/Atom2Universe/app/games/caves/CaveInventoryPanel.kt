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
    val combatTab = button(R.string.cave_ui_equipment)
    val buildTab = button(R.string.cave_ui_materials)
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
    val assign = button(R.string.cave_ui_assign)
    val remove = button(R.string.cave_ui_unassign)
    val related = button(R.string.cave_ui_recipes_for_item)
    val craftOne = button(R.string.cave_inv_craft_btn)
    val craftFive = button(R.string.cave_ui_craft_five)
    val cancel = button(R.string.cave_ui_cancel)
    val status = label(12f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
    val empty = label().apply { gravity = Gravity.CENTER; setText(R.string.cave_ui_empty_search) }
    lateinit var pager: ViewPager2
    lateinit var recipeList: RecyclerView
    lateinit var pageLabel: TextView
    lateinit var footer: LinearLayout

    fun populate(root: FrameLayout) {
        root.removeAllViews()
        val dim = FrameLayout(activity).apply {
            id = R.id.cave_inv_dim_area; setBackgroundColor(0x3514211B); isClickable = true
            layoutParams = FrameLayout.LayoutParams(-1, -1).also { it.bottomMargin = dp(80) }
        }
        root.addView(dim)
        val panel = column().apply {
            id = R.id.cave_inv_panel; isClickable = true
            background = CaveUiStyle.panel(activity, 0xBA23332D.toInt(), 0x887C9983.toInt())
            setPadding(dp(6), dp(4), dp(6), dp(4))
            val metrics = activity.resources.displayMetrics
            layoutParams = FrameLayout.LayoutParams(minOf((metrics.widthPixels * .94f).toInt(), dp(1100)), -1).also {
                it.gravity = Gravity.CENTER; it.setMargins(0, dp(14), 0, dp(6))
            }
        }
        dim.addView(panel)
        panel.addView(row().apply {
            addView(inventoryTab); addView(craftTab)
            addView(Space(activity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(combatTab); addView(buildTab); addView(close)
        })
        val body = row().apply { gravity = Gravity.TOP }
        panel.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        val library = column()
        body.addView(library, LinearLayout.LayoutParams(0, -1, 1.7f))
        library.addView(row().apply {
            addView(search, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(category, LinearLayout.LayoutParams(dp(124), dp(48)))
            addView(craftable, LinearLayout.LayoutParams(-2, dp(48)))
        })
        val content = FrameLayout(activity)
        library.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        pager = ViewPager2(activity).apply { id = R.id.cave_inv_pager }
        recipeList = RecyclerView(activity).apply { id = R.id.cave_inv_crafting_recycler; clipToPadding = false; setPadding(dp(3), dp(4), dp(3), dp(4)) }
        content.addView(pager, FrameLayout.LayoutParams(-1, -1))
        content.addView(recipeList, FrameLayout.LayoutParams(-1, -1))
        empty.id = R.id.cave_inv_crafting_empty
        content.addView(empty, FrameLayout.LayoutParams(-1, -1))
        pageLabel = label(12f).apply { id = R.id.cave_inv_page_indicator; gravity = Gravity.CENTER }
        footer = row().apply {
            addView(previous); addView(pageLabel, LinearLayout.LayoutParams(0, dp(48), 1f)); addView(next); addView(sort)
        }
        library.addView(footer)
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        body.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f).also { it.marginStart = dp(8) })
        val detail = column().apply {
            id = R.id.cave_inv_info_column; background = CaveUiStyle.panel(activity, 0x7030453B)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        scroll.addView(detail)
        detail.addView(label(12f).apply { id = R.id.cave_inv_right_header; setText(R.string.cave_ui_details); setTextColor(CaveUiStyle.MUTED) })
        detail.addView(row().apply {
            addView(View(activity).apply { id = R.id.cave_inv_info_sprite }, LinearLayout.LayoutParams(dp(64), dp(64)))
            addView(column().apply {
                addView(label(16f).apply { id = R.id.cave_inv_info_name; setTypeface(typeface, 1) })
                addView(label(12f).apply { id = R.id.cave_inv_info_count; setTextColor(CaveUiStyle.MUTED) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        detail.addView(View(activity).apply { id = R.id.cave_inv_info_divider; setBackgroundColor(CaveUiStyle.BORDER) }, LinearLayout.LayoutParams(-1, dp(1)))
        detail.addView(label().apply { id = R.id.cave_inv_info_ingredients; setLineSpacing(dp(3).toFloat(), 1f) })
        for (b in listOf(assign, remove, related, craftOne, craftFive, cancel)) {
            b.layoutParams = LinearLayout.LayoutParams(-1, dp(48)).also { it.topMargin = dp(6) }; detail.addView(b)
        }
        CaveUiStyle.button(assign, true); CaveUiStyle.button(craftOne, true)
        detail.addView(column().apply {
            id = R.id.cave_inv_sell_panel
            addView(label().apply { id = R.id.cave_inv_sell_price; setTextColor(CaveUiStyle.MUTED) })
            addView(button(R.string.cave_inv_sell_btn).apply { id = R.id.cave_inv_sell_btn; setTextColor(CaveUiStyle.WARNING) })
        })
        panel.addView(status, LinearLayout.LayoutParams(-1, -2))
    }
}
