package com.Atom2Universe.app.stats.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.AudioHubActivity
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.stats.data.CalendarDayHistory
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar

class StatsActivity : ThemedActivity() {

    private val viewModel: StatsViewModel by viewModels()

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
    private lateinit var musicTimeText: TextView
    private lateinit var midiTimeText: TextView
    private lateinit var radioTimeText: TextView
    private lateinit var averageScoreText: TextView
    private lateinit var topArtistsContainer: LinearLayout
    private lateinit var topAlbumsContainer: LinearLayout
    private lateinit var topMidiContainer: LinearLayout

    // Calendar views
    private var calendarMonthView: CalendarMonthView? = null
    private var calendarHistoryContainer: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_stats)

        initViews()
        setupTabs()
        observeViewModel()
    }

    private fun initViews() {
        // Tabs
        tabDay = findViewById(R.id.tab_day)
        tabWeek = findViewById(R.id.tab_week)
        tabMonth = findViewById(R.id.tab_month)
        tabYear = findViewById(R.id.tab_year)
        tabCalendar = findViewById(R.id.tab_calendar)

        // Loading & content
        progressBar = findViewById(R.id.progress_bar)
        contentContainer = findViewById(R.id.content_container)
        emptyView = findViewById(R.id.empty_view)
        calendarContentContainer = findViewById(R.id.calendar_content_container)

        // Stats views
        totalTimeText = findViewById(R.id.total_time_text)
        musicTimeText = findViewById(R.id.music_time_text)
        midiTimeText = findViewById(R.id.midi_time_text)
        radioTimeText = findViewById(R.id.radio_time_text)
        averageScoreText = findViewById(R.id.average_score_text)
        topArtistsContainer = findViewById(R.id.top_artists_container)
        topAlbumsContainer = findViewById(R.id.top_albums_container)
        topMidiContainer = findViewById(R.id.top_midi_container)

        // Back button
        findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            navigateBackToHub()
        }
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

    private fun showStats(data: StatsData) {
        contentContainer.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        // Temps total
        val (totalHours, totalMinutes) = data.formatDuration(data.totalTimeMs)
        totalTimeText.text = getString(R.string.stats_hours_format, totalHours, totalMinutes)

        // Temps par module
        val (musicHours, musicMinutes) = data.formatDuration(data.musicListeningTimeMs)
        musicTimeText.text = getString(R.string.stats_hours_format, musicHours, musicMinutes)

        val (midiHours, midiMinutes) = data.formatDuration(data.midiPracticeTimeMs)
        midiTimeText.text = getString(R.string.stats_hours_format, midiHours, midiMinutes)

        val (radioHours, radioMinutes) = data.formatDuration(data.radioListeningTimeMs)
        radioTimeText.text = getString(R.string.stats_hours_format, radioHours, radioMinutes)

        // Score moyen
        if (data.averagePracticeScore != null) {
            averageScoreText.text = String.format("%.0f", data.averagePracticeScore)
            averageScoreText.visibility = View.VISIBLE
            findViewById<TextView>(R.id.average_score_label).visibility = View.VISIBLE
        } else {
            averageScoreText.visibility = View.GONE
            findViewById<TextView>(R.id.average_score_label).visibility = View.GONE
        }

        // Top artistes
        displayTopItems(
            topArtistsContainer,
            data.topArtists,
            { it.trackArtist },
            { formatDuration(it.totalDuration) }
        )

        // Top albums
        displayTopItems(
            topAlbumsContainer,
            data.topAlbums,
            { "${it.trackAlbum} - ${it.trackAlbumArtist ?: ""}" },
            { formatDuration(it.totalDuration) }
        )

        // Top fichiers MIDI
        displayTopItems(
            topMidiContainer,
            data.topMidiFiles,
            { it.midiFileName },
            { formatDuration(it.totalDuration) }
        )
    }

    private fun displayCalendarHistory(history: CalendarDayHistory?) {
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

        // Section Musique - top 10 artistes (triés par durée décroissante)
        if (history.musicEntries.isNotEmpty()) {
            addSectionHeader(container, getString(R.string.stats_calendar_music_section))
            for (entry in history.musicEntries.take(10)) {
                addHistoryItem(
                    container,
                    entry.artist,
                    getString(R.string.stats_calendar_tracks_format, entry.trackCount, formatDuration(entry.durationMs))
                )
            }
        }

        // Section MIDI - top 5 fichiers (triés par durée décroissante)
        if (history.midiEntries.isNotEmpty()) {
            addSectionHeader(container, getString(R.string.stats_calendar_midi_section))
            for (entry in history.midiEntries.take(5)) {
                val detail = if (entry.averageScore != null) {
                    getString(R.string.stats_calendar_score_format, formatDuration(entry.durationMs), entry.averageScore)
                } else {
                    formatDuration(entry.durationMs)
                }
                addHistoryItem(container, entry.fileName, detail)
            }
        }

        // Section Radio - top 5 stations (triées par durée décroissante)
        if (history.radioEntries.isNotEmpty()) {
            addSectionHeader(container, getString(R.string.stats_calendar_radio_section))
            for (entry in history.radioEntries.take(5)) {
                addHistoryItem(container, entry.stationName, formatDuration(entry.durationMs))
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

    private fun <T> displayTopItems(
        container: LinearLayout,
        items: List<T>,
        getTitle: (T) -> String,
        getDuration: (T) -> String
    ) {
        container.removeAllViews()

        if (items.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = getString(R.string.stats_no_data)
                setTextColor(getColor(R.color.text_secondary))
                textSize = 14f
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
            }
            container.addView(emptyText)
            return
        }

        items.forEachIndexed { index, item ->
            val itemView = layoutInflater.inflate(R.layout.item_stat_entry, container, false)
            val rankText = itemView.findViewById<TextView>(R.id.rank_text)
            val titleText = itemView.findViewById<TextView>(R.id.title_text)
            val durationText = itemView.findViewById<TextView>(R.id.duration_text)

            rankText.text = "${index + 1}."
            titleText.text = getTitle(item)
            durationText.text = getDuration(item)

            container.addView(itemView)
        }
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

    private fun navigateBackToHub() {
        if (isTaskRoot) {
            startActivity(Intent(this, AudioHubActivity::class.java))
        }
        finish()
    }
}
