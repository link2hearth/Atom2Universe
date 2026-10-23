package com.Atom2Universe.app.update

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Atom2Universe.app.BuildConfig
import com.Atom2Universe.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class AppUpdateViewModel(app: Application) : AndroidViewModel(app) {
    data class State(
        val busy: Boolean = false,
        val release: UpdateRelease? = null,
        val file: File? = null,
        val percent: Int? = null,
        val message: Int = R.string.update_checking
    )

    private val repository = AppUpdateRepository(app)
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    init { check() }

    fun check() {
        if (mutableState.value.busy) return
        if (BuildConfig.DEBUG) {
            mutableState.value = State(message = R.string.update_debug)
            return
        }
        mutableState.value = State(busy = true)
        viewModelScope.launch {
            try {
                val release = repository.check(force = true)
                mutableState.value = State(release = release,
                    message = if (release == null) R.string.update_no_release else R.string.update_available)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("AppUpdate", "Release check failed", error)
                mutableState.value = State(release = repository.cachedRelease(), message = R.string.update_check_error)
            }
        }
    }

    fun download() {
        val release = mutableState.value.release ?: return
        if (mutableState.value.busy) return
        mutableState.value = State(busy = true, release = release, percent = 0, message = R.string.update_downloading)
        viewModelScope.launch {
            try {
                val file = repository.download(release) { percent ->
                    mutableState.value = mutableState.value.copy(percent = percent)
                }
                mutableState.value = State(release = release, file = file, message = R.string.update_ready)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("AppUpdate", "APK download failed", error)
                mutableState.value = State(release = release, message =
                    if (error is AppUpdateRepository.InvalidUpdateException) R.string.update_invalid_apk
                    else R.string.update_download_error)
            }
        }
    }
}
