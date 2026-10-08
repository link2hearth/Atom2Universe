package com.Atom2Universe.app.dictaphone

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.Atom2Universe.app.audioeditor.MicRecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DictaphoneViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()
    private val recorder = DictaphoneRecorder(app)
    private val prefs = app.getSharedPreferences("dictaphone_prefs", Context.MODE_PRIVATE)

    private val _isRecording = MutableLiveData(false)
    val isRecording: LiveData<Boolean> = _isRecording

    private val _isSaving = MutableLiveData(false)
    val isSaving: LiveData<Boolean> = _isSaving

    private val _durationMs = MutableLiveData(0L)
    val durationMs: LiveData<Long> = _durationMs

    private val _amplitude = MutableLiveData(0f)
    val amplitude: LiveData<Float> = _amplitude

    private val _quality = MutableLiveData(loadQuality())
    val quality: LiveData<DictaphoneRecorder.Quality> = _quality

    private val _recordings = MutableLiveData<List<DictaphoneRecording>>(emptyList())
    val recordings: LiveData<List<DictaphoneRecording>> = _recordings

    private val _playingUri = MutableLiveData<Uri?>(null)
    val playingUri: LiveData<Uri?> = _playingUri

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var mediaPlayer: MediaPlayer? = null
    private var recordingStartMs = 0L
    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null

    init {
        observeRecorderState()
        loadRecordings()
    }

    private fun observeRecorderState() {
        viewModelScope.launch {
            recorder.state.collect { state ->
                when (state) {
                    is DictaphoneRecorder.State.Recording -> {
                        _isRecording.value = true
                        _isSaving.value = false
                    }
                    is DictaphoneRecorder.State.Saving -> {
                        _isRecording.value = false
                        _isSaving.value = true
                        stopTimers()
                    }
                    is DictaphoneRecorder.State.Completed -> {
                        _isRecording.value = false
                        _isSaving.value = false
                        stopTimers()
                        loadRecordings()
                    }
                    is DictaphoneRecorder.State.Error -> {
                        _isRecording.value = false
                        _isSaving.value = false
                        stopTimers()
                        _errorMessage.value = state.message
                    }
                    is DictaphoneRecorder.State.Idle -> {
                        _isRecording.value = false
                        _isSaving.value = false
                        stopTimers()
                    }
                }
            }
        }
    }

    fun startRecording() {
        val q = _quality.value ?: DictaphoneRecorder.Quality.M4A
        if (recorder.startRecording(q)) {
            recordingStartMs = System.currentTimeMillis()
            startTimers()
            MicRecordingService.start(app)
        }
    }

    fun stopRecording() {
        recorder.stopRecording()
        MicRecordingService.stop(app)
    }

    fun cancelRecording() {
        recorder.cancelRecording()
        MicRecordingService.stop(app)
        stopTimers()
        _durationMs.value = 0L
        _amplitude.value = 0f
    }

    fun toggleQuality() {
        if (_isRecording.value == true || _isSaving.value == true) return
        val current = _quality.value ?: DictaphoneRecorder.Quality.M4A
        val new = if (current == DictaphoneRecorder.Quality.M4A) DictaphoneRecorder.Quality.WAV else DictaphoneRecorder.Quality.M4A
        _quality.value = new
        prefs.edit().putString(PREF_QUALITY, new.name).apply()
    }

    fun togglePlayback(recording: DictaphoneRecording) {
        if (_playingUri.value == recording.uri) {
            val mp = mediaPlayer
            if (mp != null && mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
            } else {
                mp?.start()
                _isPlaying.value = true
            }
        } else {
            stopPlayback()
            playRecording(recording)
        }
    }

    fun stopPlayback() {
        mediaPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            it.release()
        }
        mediaPlayer = null
        _playingUri.value = null
        _isPlaying.value = false
    }

    fun deleteRecording(recording: DictaphoneRecording) {
        if (_playingUri.value == recording.uri) stopPlayback()
        viewModelScope.launch(Dispatchers.IO) {
            try { app.contentResolver.delete(recording.uri, null, null) } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                _recordings.value = _recordings.value?.filter { it.uri != recording.uri } ?: emptyList()
            }
        }
    }

    fun renameRecording(recording: DictaphoneRecording, newName: String) {
        val trimmed = newName.trim().ifBlank { return }
        val finalName = if (!trimmed.contains('.')) {
            val ext = recording.displayName.substringAfterLast('.', "")
            if (ext.isNotEmpty()) "$trimmed.$ext" else trimmed
        } else trimmed
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cv = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, finalName)
                }
                app.contentResolver.update(recording.uri, cv, null, null)
                loadRecordings()
            } catch (_: Exception) {}
        }
    }

    fun loadRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = queryRecordings()
            withContext(Dispatchers.Main) { _recordings.value = list }
        }
    }

    private fun playRecording(recording: DictaphoneRecording) {
        _playingUri.value = recording.uri
        viewModelScope.launch {
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(app, recording.uri)
                    setOnCompletionListener {
                        _playingUri.value = null
                        _isPlaying.value = false
                        it.release()
                        mediaPlayer = null
                    }
                    prepare()
                    start()
                }
                mediaPlayer = mp
                _isPlaying.value = true
            } catch (e: Exception) {
                _playingUri.value = null
                _isPlaying.value = false
                _errorMessage.value = e.message
            }
        }
    }

    private fun startTimers() {
        timerJob = viewModelScope.launch {
            while (true) {
                delay(100)
                _durationMs.value = System.currentTimeMillis() - recordingStartMs
            }
        }
        amplitudeJob = viewModelScope.launch {
            while (true) {
                delay(50)
                _amplitude.value = recorder.getAmplitude()
            }
        }
    }

    private fun stopTimers() {
        timerJob?.cancel(); timerJob = null
        amplitudeJob?.cancel(); amplitudeJob = null
        _amplitude.value = 0f
    }

    private fun loadQuality(): DictaphoneRecorder.Quality {
        val saved = prefs.getString(PREF_QUALITY, DictaphoneRecorder.Quality.M4A.name)
        return try { DictaphoneRecorder.Quality.valueOf(saved!!) } catch (_: Exception) { DictaphoneRecorder.Quality.M4A }
    }

    private fun queryRecordings(): List<DictaphoneRecording> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" to arrayOf("%${DictaphoneRecorder.FOLDER_NAME}%")
        } else {
            "${MediaStore.Audio.Media.DATA} LIKE ?" to arrayOf("%${DictaphoneRecorder.FOLDER_NAME}%")
        }
        val results = mutableListOf<DictaphoneRecording>()
        try {
            app.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, args,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    results.add(DictaphoneRecording(
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        displayName = cursor.getString(nameCol) ?: "",
                        durationMs = cursor.getLong(durCol),
                        dateAddedSec = cursor.getLong(dateCol),
                        mimeType = cursor.getString(mimeCol) ?: ""
                    ))
                }
            }
        } catch (_: Exception) {}
        return results
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
        if (_isRecording.value == true || _isSaving.value == true) {
            recorder.cancelRecording()
            MicRecordingService.stop(app)
        }
        stopTimers()
    }

    companion object {
        private const val PREF_QUALITY = "quality"
    }
}
