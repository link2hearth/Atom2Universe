package com.Atom2Universe.app.stats.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.stats.data.ReadingCalendarDayHistory
import com.Atom2Universe.app.stats.data.ReadingTitleStats
import com.Atom2Universe.app.stats.data.StatsRepository
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar

class ReadingStatsActivity : ThemedActivity() {

    private val viewModel: ReadingStatsViewModel by viewModels()

    // Views
    private lateinit var tabDay: TextView
    private lateinit var tabWeek: TextView
    private lateinit var tabMonth: TextView
    private lateinit var tabYear: TextView
    private lateinit var tabCalendar: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var contentContainer: ScrollView
    private lateinit var emptyView: TextView
    private lateinit var calendarContentContainer: FrameLayout

    // Stats views
    private lateinit var totalTimeText: TextView
    private lateinit var booksTimeText: TextView
    private lateinit var comicsTimeText: TextView
    private lateinit var topTitlesContainer: LinearLayout

    // Calendar views
    private var calendarMonthView: CalendarMonthView? = null
    private var calendarHistoryContainer: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_reading_stats)

        initViews()
        setupTabs()
        observeViewModel()
    }

    private fun initViews() {
        tabDay = findViewById(R.id.tab_day)
        tabWeek = findViewById(R.id.tab_week)
        tabMonth = findViewById(R.id.tab_month)
        tabYear = findViewById(R.id.tab_year)
        tabCalendar = findViewById(R.id.tab_calendar)

        progressBar = findViewById(R.id.progress_bar)
        contentContainer = findViewById(R.id.content_container)
        emptyView = findViewById(R.id.empty_view)
        calendarContentContainer = findViewById(R.id.calendar_content_container)

        totalTimeText = findViewById(R.id.total_time_text)
        booksTimeText = findViewById(R.id.books_time_text)
        comicsTimeText = findViewById(R.id.comics_time_text)
        topTitlesContainer = findViewById(R.id.top_titles_container)

        findViewById<ImageButton>(R.id.back_button).setOnClickListener { finish() }
    }

    private fun setupTabs() {
        tabDay.setOnClickListener { selectPeriod(StatsPeriod.DAY) }
        tabWeek.setOnClickListener { selectPeriod(StatsPeriod.WEEK) }
        tabMonth.setOnClickListener { selectPeriod(StatsPeriod.MONTH) }
        tabYear.setOnClickListener { selectPeriod(StatsPeriod.YEAR) }
        tabCalendar.setOnClickListener { selectPeriod(StatsPeriod.CALENDAR) }

        // Semaine sélectionnée par défaut
        updateTabSelection(StatsPeriod.WEEK)
    }

    private fun selectPeriod(period: StatsPeriod) {
        viewModel.selectPeriod(period)
        updateTabSelection(period)

        if (period == StatsPeriod.CALENDAR) {
            contentContainer.visibility = View.GONE
            emptyView.visibility = View.GONE
            calendarContentContainer.visibility = View.VISIBLE
            ensureCalendarInflated()
        } else {
            calendarContentContainer.visibility = View.GONE
        }
    }

    private fun ensureCalendarInflated() {
        if (calendarMonthView != null) return

        val calendarView = layoutInflater.inflate(R.layout.calendar_content, calendarContentContainer, false)
        calendarContentContainer.addView(calendarView)

        calendarMonthView = calendarView.findViewById(R.id.calendar_month_view)
        calendarHistoryContainer = calendarView.findViewById(R.id.calendar_history_container)

        calendarMonthView?.setOnDaySelectedListener { day ->
            viewModel.selectCalendarDay(day)
        }

        calendarMonthView?.setOnMonthChangedListener { year, month ->
            viewModel.setCalendarMonth(year, month)
        }
    }

    private fun updateTabSelection(period: StatsPeriod) {
        val selectedColor = getColor(R.color.accent)
        val normalColor = getColor(R.color.text_secondary)

        tabDay.setTextColor(if (period == StatsPeriod.DAY) selectedColor else normalColor)
        tabWeek.setTextColor(if (period == StatsPeriod.WEEK) selectedColor else normalColor)
        tabMonth.setTextColor(if (period == StatsPeriod.MONTH) selectedColor else normalColor)
        tabYear.setTextColor(if (period == StatsPeriod.YEAR) selectedColor else normalColor)
        tabCalendar.setTextColor(if (period == StatsPeriod.CALENDAR) selectedColor else normalColor)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.statsData.collect { data ->
                if (viewModel.selectedPeriod.value == StatsPeriod.CALENDAR) return@collect
                if (data == null || data.isEmpty) {
                    showEmpty()
                } else {
                    showStats(data)
                }
            }
        }

        // Calendar observers
        lifecycleScope.launch {
            viewModel.activeDays.collect { days ->
                calendarMonthView?.setActiveDays(days)
            }
        }

        lifecycleScope.launch {
            viewModel.calendarYear.collect { year ->
                val month = viewModel.calendarMonth.value
                calendarMonthView?.setMonth(year, month)
            }
        }

        lifecycleScope.launch {
            viewModel.calendarMonth.collect { month ->
                val year = viewModel.calendarYear.value
                calendarMonthView?.setMonth(year, month)
            }
        }

        lifecycleScope.launch {
            viewModel.calendarHistory.collect { history ->
                displayCalendarHistory(history)
            }
        }

        lifecycleScope.launch {
            viewModel.selectedDate.collect { date ->
                if (date != null) {
                    calendarMonthView?.setSelectedDay(date.day)
                }
            }
        }
    }

    private fun showEmpty() {
        contentContainer.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
    }

    private fun showStats(data: ReadingStatsData) {
        contentContainer.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        totalTimeText.text = formatDuration(data.totalDurationMs)
        booksTimeText.text = formatDuration(data.bookDurationMs)
        comicsTimeText.text = formatDuration(data.comicDurationMs)

        displayTopTitles(data.topTitles)
    }

    private fun displayTopTitles(items: List<ReadingTitleStats>) {
        topTitlesContainer.removeAllViews()

        if (items.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = getString(R.string.stats_no_data)
                setTextColor(getColor(R.color.text_secondary))
                textSize = 14f
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
            }
            topTitlesContainer.addView(emptyText)
            return
        }

        items.forEachIndexed { index, item ->
            val itemView = layoutInflater.inflate(R.layout.item_stat_entry, topTitlesContainer, false)
            val icon = if (item.moduleType == StatsRepository.MODULE_COMIC) "📕" else "📖"
            itemView.findViewById<TextView>(R.id.rank_text).text = "${index + 1}."
            itemView.findViewById<TextView>(R.id.title_text).text = "$icon ${item.readingTitle}"
            itemView.findViewById<TextView>(R.id.duration_text).text = formatDuration(item.totalDuration)
            topTitlesContainer.addView(itemView)
        }
    }

    private fun displayCalendarHistory(history: ReadingCalendarDayHistory?) {
        val container = calendarHistoryContainer ?: return
        container.removeAllViews()

        if (history == null) {
            val selectedDate = viewModel.selectedDate.value
            if (selectedDate != null) {
                val emptyText = TextView(this).apply {
                    text = getString(R.string.stats_calendar_no_data)
                    setTextColor(getColor(R.color.text_secondary))
                    textSize = 14f
                    setPadding(0, 16.dpToPx(), 0, 0)
                }
                container.addView(emptyText)
            }
            return
        }

        // Header : date + durée totale
        val headerView = layoutInflater.inflate(R.layout.item_calendar_history_header, container, false)
        val cal = Calendar.getInstance().apply {
            set(history.year, history.month - 1, history.day)
        }
        val dateFormat = DateFormat.getDateInstance(DateFormat.LONG)
        headerView.findViewById<TextView>(R.id.date_text).text = dateFormat.format(cal.time)
        headerView.findViewById<TextView>(R.id.total_duration_text).text =
            getString(R.string.stats_calendar_total_format, formatDuration(history.totalDurationMs))
        container.addView(headerView)

        // Livres
        val bookEntries = history.entries.filter { !it.isComic }
        if (bookEntries.isNotEmpty()) {
            addSectionHeader(container, getString(R.string.reading_stats_books_title))
            for (entry in bookEntries.take(10)) {
                addHistoryItem(container, entry.title, formatDuration(entry.durationMs))
            }
        }

        // BD
        val comicEntries = history.entries.filter { it.isComic }
        if (comicEntries.isNotEmpty()) {
            addSectionHeader(container, getString(R.string.reading_stats_comics_title))
            for (entry in comicEntries.take(10)) {
                addHistoryItem(container, entry.title, formatDuration(entry.durationMs))
            }
        }
    }

    private fun addSectionHeader(container: LinearLayout, title: String) {
        val sectionView = layoutInflater.inflate(R.layout.item_calendar_history_module_section, container, false)
        (sectionView as TextView).text = title
        container.addView(sectionView)
    }

    private fun addHistoryItem(container: LinearLayout, name: String, detail: String) {
        val itemView = layoutInflater.inflate(R.layout.item_calendar_history, container, false)
        itemView.findViewById<TextView>(R.id.history_name_text).text = name
        itemView.findViewById<TextView>(R.id.history_detail_text).text = detail
        container.addView(itemView)
    }

    private fun formatDuration(durationMs: Long): String {
        val totalMinutes = (durationMs / 1000 / 60).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            getString(R.string.stats_hours_format, hours, minutes)
        } else {
            getString(R.string.stats_minutes_format, minutes)
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
