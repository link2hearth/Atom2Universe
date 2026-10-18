package com.Atom2Universe.app.stats.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Atom2Universe.app.stats.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StatsRepository(application)

    // État de chargement
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Période sélectionnée
    private val _selectedPeriod = MutableStateFlow(StatsPeriod.WEEK)
    val selectedPeriod: StateFlow<StatsPeriod> = _selectedPeriod.asStateFlow()

    // Données statistiques
    private val _statsData = MutableStateFlow<StatsData?>(null)
    val statsData: StateFlow<StatsData?> = _statsData.asStateFlow()

    // Calendrier
    private val _calendarYear = MutableStateFlow(LocalDate.now().year)
    val calendarYear: StateFlow<Int> = _calendarYear.asStateFlow()

    private val _calendarMonth = MutableStateFlow(LocalDate.now().monthValue)
    val calendarMonth: StateFlow<Int> = _calendarMonth.asStateFlow()

    private val _activeDays = MutableStateFlow<List<Int>>(emptyList())
    val activeDays: StateFlow<List<Int>> = _activeDays.asStateFlow()

    private val _selectedDate = MutableStateFlow<CalendarDate?>(null)
    val selectedDate: StateFlow<CalendarDate?> = _selectedDate.asStateFlow()

    private val _calendarHistory = MutableStateFlow<CalendarDayHistory?>(null)
    val calendarHistory: StateFlow<CalendarDayHistory?> = _calendarHistory.asStateFlow()

    init {
        loadStats(StatsPeriod.WEEK)
    }

    fun selectPeriod(period: StatsPeriod) {
        if (_selectedPeriod.value != period) {
            _selectedPeriod.value = period
            if (period == StatsPeriod.CALENDAR) {
                loadActiveDays()
            } else {
                loadStats(period)
            }
        }
    }

    fun refresh() {
        val period = _selectedPeriod.value
        if (period == StatsPeriod.CALENDAR) {
            loadActiveDays()
        } else {
            loadStats(period)
        }
    }

    fun setCalendarMonth(year: Int, month: Int) {
        _calendarYear.value = year
        _calendarMonth.value = month
        _selectedDate.value = null
        _calendarHistory.value = null
        loadActiveDays()
    }

    fun selectCalendarDay(day: Int) {
        val year = _calendarYear.value
        val month = _calendarMonth.value
        _selectedDate.value = CalendarDate(year, month, day)
        loadDayHistory(year, month, day)
    }

    private fun loadActiveDays() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.ensureDailySummariesPopulated()
                val days = repository.getActiveDaysInMonth(_calendarYear.value, _calendarMonth.value)
                _activeDays.value = days
            } catch (e: Exception) {
                android.util.Log.e("StatsViewModel", "Error loading active days", e)
                _activeDays.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadDayHistory(year: Int, month: Int, day: Int) {
        viewModelScope.launch {
            try {
                val history = repository.getDayHistory(year, month, day)
                _calendarHistory.value = history
            } catch (e: Exception) {
                android.util.Log.e("StatsViewModel", "Error loading day history", e)
                _calendarHistory.value = null
            }
        }
    }

    private fun loadStats(period: StatsPeriod) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val (startDate, endDate) = getPeriodRange(period)

                val musicTime = repository.getMusicListeningTime(startDate, endDate)
                val midiTime = repository.getMidiPracticeTime(startDate, endDate)
                val radioTime = repository.getRadioListeningTime(startDate, endDate)

                val topArtists = repository.getTopArtists(startDate, endDate, 5)
                val topAlbums = repository.getTopAlbums(startDate, endDate, 5)
                val topMidiFiles = repository.getTopMidiFiles(startDate, endDate, 5)
                val averageScore = repository.getAveragePracticeScore(startDate, endDate)

                val stats = StatsData(
                    musicListeningTimeMs = musicTime,
                    midiPracticeTimeMs = midiTime,
                    radioListeningTimeMs = radioTime,
                    topArtists = topArtists,
                    topAlbums = topAlbums,
                    topMidiFiles = topMidiFiles,
                    averagePracticeScore = averageScore
                )

                _statsData.value = stats
            } catch (e: Exception) {
                android.util.Log.e("StatsViewModel", "Error loading stats", e)
                _statsData.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun getPeriodRange(period: StatsPeriod): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis

        when (period) {
            StatsPeriod.DAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            StatsPeriod.WEEK -> {
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            StatsPeriod.MONTH -> {
                calendar.add(Calendar.DAY_OF_YEAR, -29)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            StatsPeriod.YEAR -> {
                calendar.add(Calendar.DAY_OF_YEAR, -364)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            StatsPeriod.CALENDAR -> {
                // Non utilisé pour CALENDAR
                return Pair(0L, 0L)
            }
        }

        val startDate = calendar.timeInMillis
        return Pair(startDate, endDate)
    }
}

enum class StatsPeriod {
    DAY,
    WEEK,
    MONTH,
    YEAR,
    CALENDAR
}

data class CalendarDate(
    val year: Int,
    val month: Int,
    val day: Int
)

data class StatsData(
    val musicListeningTimeMs: Long,
    val midiPracticeTimeMs: Long,
    val radioListeningTimeMs: Long,
    val topArtists: List<ArtistStats>,
    val topAlbums: List<AlbumStats>,
    val topMidiFiles: List<MidiFileStats>,
    val averagePracticeScore: Float?
) {
    val totalTimeMs: Long
        get() = musicListeningTimeMs + midiPracticeTimeMs + radioListeningTimeMs

    fun formatDuration(durationMs: Long): Pair<Int, Int> {
        val totalMinutes = (durationMs / 1000 / 60).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return Pair(hours, minutes)
    }

    val isEmpty: Boolean
        get() = totalTimeMs == 0L && topArtists.isEmpty() && topAlbums.isEmpty() && topMidiFiles.isEmpty()
}
