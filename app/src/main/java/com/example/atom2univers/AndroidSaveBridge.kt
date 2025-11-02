package com.example.atom2univers

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.core.content.edit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import kotlin.text.Charsets

class AndroidSaveBridge(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val saveDirectory: File = appContext.filesDir
    private val saveFile: File = File(saveDirectory, SAVE_FILE_NAME)
    private val saveLock = Any()

    @JavascriptInterface
    fun saveData(payload: String?) {
        synchronized(saveLock) {
            if (payload.isNullOrEmpty()) {
                clearDataInternal()
                return
            }

            try {
                writeToFile(payload)
                removeLegacyPreference()
            } catch (error: IOException) {
                Log.e(TAG, "Unable to persist save data", error)
            }
        }
    }

    @JavascriptInterface
    fun loadData(): String? {
        synchronized(saveLock) {
            val fileData = readFromFile()
            if (!fileData.isNullOrEmpty()) {
                return fileData
            }

            val legacyData = preferences.getString(KEY_SAVE, null)
            if (!legacyData.isNullOrEmpty()) {
                try {
                    writeToFile(legacyData)
                    removeLegacyPreference()
                } catch (error: IOException) {
                    Log.w(TAG, "Unable to migrate legacy save data", error)
                }
                return legacyData
            }

            return null
        }
    }

    @JavascriptInterface
    fun clearData() {
        synchronized(saveLock) {
            clearDataInternal()
        }
    }

    private fun readFromFile(): String? {
        if (!saveFile.exists()) {
            return null
        }
        return try {
            saveFile.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
        } catch (error: IOException) {
            Log.e(TAG, "Unable to read save file", error)
            null
        }
    }

    @Throws(IOException::class)
    private fun writeToFile(payload: String) {
        if (!saveDirectory.exists() && !saveDirectory.mkdirs()) {
            throw IOException("Unable to create save directory: ${saveDirectory.absolutePath}")
        }

        val tempFile = File(saveDirectory, "$SAVE_FILE_NAME.tmp")

        try {
            FileOutputStream(tempFile).use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }
                outputStream.fd.sync()
            }

            if (saveFile.exists() && !saveFile.delete()) {
                throw IOException("Unable to delete existing save file")
            }

            if (!tempFile.renameTo(saveFile)) {
                throw IOException("Unable to move temp save file into place")
            }
        } finally {
            if (tempFile.exists() && tempFile != saveFile) {
                tempFile.delete()
            }
        }
    }

    private fun clearDataInternal() {
        if (saveFile.exists() && !saveFile.delete()) {
            Log.w(TAG, "Unable to delete save file")
        }

        val tempFile = File(saveDirectory, "$SAVE_FILE_NAME.tmp")
        if (tempFile.exists() && !tempFile.delete()) {
            Log.w(TAG, "Unable to delete temporary save file")
        }

        removeLegacyPreference()
    }

    private fun removeLegacyPreference() {
        preferences.edit(commit = true) {
            remove(KEY_SAVE)
        }
    }

    private companion object {
        private const val TAG = "AndroidSaveBridge"
        private const val PREF_NAME = "atom2univers_storage"
        private const val KEY_SAVE = "atom2univers_save"
        private const val SAVE_FILE_NAME = "atom2univers_save.json"
    }
}
