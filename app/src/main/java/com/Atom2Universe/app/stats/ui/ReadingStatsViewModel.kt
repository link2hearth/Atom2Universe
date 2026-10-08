package com.Atom2Universe.app.stats.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Atom2Universe.app.stats.data.ReadingCalendarDayHistory
import com.Atom2Universe.app.stats.data.ReadingTitleStats
import com.Atom2Universe.app.stats.data.StatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

class ReadingStatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StatsRepository(application)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(StatsPeriod.WEEK)
    val selectedPeriod: StateFlow<StatsPeriod> = _selectedPeriod.asStateFlow()

    private val _statsData = MutableStateFlow<ReadingStatsData?>(null)
    val statsData: StateFlow<ReadingStatsData?> = _statsData.asStateFlow()

    // Calendrier
    private val _calendarYear = MutableStateFlow(LocalDate.now().year)
    val calendarYear: StateFlow<Int> = _calendarYear.asStateFlow()

    private val _calendarMonth = MutableStateFlow(LocalDate.now().monthValue)
    val calendarMonth: StateFlow<Int> = _calendarMonth.asStateFlow()

    private val _activeDays = MutableStateFlow<List<Int>>(emptyList())
    val activeDays: StateFlow<List<Int>> = _activeDays.asStateFlow()

    private val _selectedDate = MutableStateFlow<CalendarDate?>(null)
    val selectedDate: StateFlow<CalendarDate?> = _selectedDate.asStateFlow()

    private val _calendarHistory = MutableStateFlow<ReadingCalendarDayHistory?>(null)
    val calendarHistory: StateFlow<ReadingCalendarDayHistory?> = _calendarHistory.asStateFlow()

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
                _activeDays.value = repository.getActiveReadingDaysInMonth(_calendarYear.value, _calendarMonth.value)
            } catch (e: Exception) {
                android.util.Log.e("ReadingStatsViewModel", "Error loading active days", e)
                _activeDays.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadDayHistory(year: Int, month: Int, day: Int) {
        viewModelScope.launch {
            try {
                _calendarHistory.value = repository.getReadingDayHistory(year, month, day)
            } catch (e: Exception) {
                android.util.Log.e("ReadingStatsViewModel", "Error loading day history", e)
                _calendarHistory.value = null
            }
        }
    }

    private fun loadStats(period: StatsPeriod) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val (startDate, endDate) = getPeriodRange(period)

                val bookTime = repository.getReadingTime(StatsRepository.MODULE_BOOK, startDate, endDate)
                val comicTime = repository.getReadingTime(StatsRepository.MODULE_COMIC, startDate, endDate)
                val topTitles = repository.getTopReadingTitles(startDate, endDate, 5)

                _statsData.value = ReadingStatsData(
                    bookDurationMs = bookTime,
                    comicDurationMs = comicTime,
                    topTitles = topTitles
                )
            } catch (e: Exception) {
                android.util.Log.e("ReadingStatsViewModel", "Error loading stats", e)
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
            StatsPeriod.CALENDAR -> return Pair(0L, 0L) // Non utilisé pour CALENDAR
        }

        return Pair(calendar.timeInMillis, endDate)
    }
}

data class ReadingStatsData(
    val bookDurationMs: Long,
    val comicDurationMs: Long,
    val topTitles: List<ReadingTitleStats>
) {
    val totalDurationMs: Long get() = bookDurationMs + comicDurationMs

    fun formatDuration(durationMs: Long): Pair<Int, Int> {
        val totalMinutes = (durationMs / 1000 / 60).toInt()
        return Pair(totalMinutes / 60, totalMinutes % 60)
    }

    val isEmpty: Boolean get() = totalDurationMs == 0L && topTitles.isEmpty()
}
