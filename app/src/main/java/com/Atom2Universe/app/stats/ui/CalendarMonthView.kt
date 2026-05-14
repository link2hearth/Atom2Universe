package com.Atom2Universe.app.stats.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class CalendarMonthView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var currentYear = LocalDate.now().year
    private var currentMonth = LocalDate.now().monthValue
    private var activeDays = setOf<Int>()
    private var selectedDay = -1

    private val accentColor = ContextCompat.getColor(context, R.color.accent)
    private val textPrimaryColor = ContextCompat.getColor(context, R.color.text_primary)
    private val textSecondaryColor = ContextCompat.getColor(context, R.color.text_secondary)

    private var onDaySelectedListener: ((Int) -> Unit)? = null
    private var onMonthChangedListener: ((Int, Int) -> Unit)? = null

    private val monthTitleText: TextView
    private val gridContainer: LinearLayout

    init {
        orientation = VERTICAL

        // Header : < Mois Année >
        val headerLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val prevButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(textPrimaryColor)
            background = null
            layoutParams = LayoutParams(48.dp(), 48.dp())
            setOnClickListener { navigateMonth(-1) }
        }

        monthTitleText = TextView(context).apply {
            textSize = 18f
            setTextColor(textPrimaryColor)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        val nextButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_arrow_forward)
            setColorFilter(textPrimaryColor)
            background = null
            layoutParams = LayoutParams(48.dp(), 48.dp())
            setOnClickListener { navigateMonth(1) }
        }

        headerLayout.addView(prevButton)
        headerLayout.addView(monthTitleText)
        headerLayout.addView(nextButton)
        addView(headerLayout)

        // Jours de la semaine
        val dayNamesRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12.dp()
                bottomMargin = 8.dp()
            }
        }

        val firstDayOfWeek = java.util.Calendar.getInstance().firstDayOfWeek
        val dayNames = buildDayNamesList(firstDayOfWeek)

        for (name in dayNames) {
            val tv = TextView(context).apply {
                text = name
                textSize = 12f
                setTextColor(textSecondaryColor)
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            dayNamesRow.addView(tv)
        }
        addView(dayNamesRow)

        // Grille
        gridContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        addView(gridContainer)

        rebuildGrid()
    }

    fun setMonth(year: Int, month: Int) {
        currentYear = year
        currentMonth = month
        selectedDay = -1
        rebuildGrid()
    }

    fun setActiveDays(days: List<Int>) {
        activeDays = days.toSet()
        rebuildGrid()
    }

    fun setSelectedDay(day: Int) {
        selectedDay = day
        rebuildGrid()
    }

    fun setOnDaySelectedListener(listener: (Int) -> Unit) {
        onDaySelectedListener = listener
    }

    fun setOnMonthChangedListener(listener: (Int, Int) -> Unit) {
        onMonthChangedListener = listener
    }

    private fun navigateMonth(delta: Int) {
        var m = currentMonth + delta
        var y = currentYear
        if (m < 1) { m = 12; y-- }
        if (m > 12) { m = 1; y++ }
        currentYear = y
        currentMonth = m
        selectedDay = -1
        rebuildGrid()
        onMonthChangedListener?.invoke(y, m)
    }

    private fun rebuildGrid() {
        val yearMonth = YearMonth.of(currentYear, currentMonth)
        val monthName = yearMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        monthTitleText.text = "$monthName $currentYear"

        gridContainer.removeAllViews()

        val daysInMonth = yearMonth.lengthOfMonth()
        val firstDay = yearMonth.atDay(1)
        val firstDayOfWeek = java.util.Calendar.getInstance().firstDayOfWeek

        // Calcul de l'offset
        val javaDow = firstDay.dayOfWeek.value // 1=Mon .. 7=Sun
        val offset = if (firstDayOfWeek == java.util.Calendar.MONDAY) {
            javaDow - 1
        } else {
            javaDow % 7
        }

        val today = LocalDate.now()
        val isCurrentMonth = today.year == currentYear && today.monthValue == currentMonth

        var dayCounter = 1
        val cellSize = 40.dp()

        for (week in 0 until 6) {
            if (dayCounter > daysInMonth) break

            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }

            for (col in 0 until 7) {
                val cellIndex = week * 7 + col

                val cell = FrameLayout(context).apply {
                    layoutParams = LayoutParams(0, cellSize, 1f)
                }

                if (cellIndex >= offset && dayCounter <= daysInMonth) {
                    val day = dayCounter
                    val isToday = isCurrentMonth && day == today.dayOfMonth
                    val isSelected = day == selectedDay
                    val isActive = day in activeDays

                    // Fond
                    if (isSelected) {
                        cell.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(accentColor and 0x40FFFFFF)
                        }
                    } else if (isToday) {
                        cell.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.TRANSPARENT)
                            setStroke(2.dp(), accentColor)
                        }
                    }

                    // Numéro du jour
                    val dayText = TextView(context).apply {
                        text = day.toString()
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setTextColor(if (isSelected) Color.WHITE else textPrimaryColor)
                        if (isToday || isSelected) typeface = Typeface.DEFAULT_BOLD
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        ).apply { gravity = Gravity.CENTER }
                    }
                    cell.addView(dayText)

                    // Point d'activité
                    if (isActive) {
                        val dot = android.view.View(context).apply {
                            val dotSize = 5.dp()
                            layoutParams = FrameLayout.LayoutParams(dotSize, dotSize).apply {
                                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                                bottomMargin = 2.dp()
                            }
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(accentColor)
                            }
                        }
                        cell.addView(dot)
                    }

                    cell.setOnClickListener {
                        selectedDay = day
                        rebuildGrid()
                        onDaySelectedListener?.invoke(day)
                    }

                    dayCounter++
                }

                row.addView(cell)
            }

            gridContainer.addView(row)
        }
    }

    private fun buildDayNamesList(firstDayOfWeek: Int): List<String> {
        val daysOfWeek = java.time.DayOfWeek.values()
        val names = mutableListOf<String>()
        val startIndex = if (firstDayOfWeek == java.util.Calendar.MONDAY) 0 else 6
        for (i in 0 until 7) {
            val dow = daysOfWeek[(startIndex + i) % 7]
            names.add(dow.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault()))
        }
        return names
    }

    private fun Int.dp(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
