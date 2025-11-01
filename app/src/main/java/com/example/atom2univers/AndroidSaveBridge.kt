package com.example.atom2univers

import android.content.Context
import android.webkit.JavascriptInterface
import androidx.core.content.edit

class AndroidSaveBridge(context: Context) {

    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    @JavascriptInterface
    fun saveData(payload: String?) {
        preferences.edit(commit = true) {
            if (payload.isNullOrEmpty()) {
                remove(KEY_SAVE)
            } else {
                putString(KEY_SAVE, payload)
            }
        }
    }

    @JavascriptInterface
    fun loadData(): String? = preferences.getString(KEY_SAVE, null)

    @JavascriptInterface
    fun clearData() {
        preferences.edit(commit = true) {
            remove(KEY_SAVE)
        }
    }

    private companion object {
        private const val PREF_NAME = "atom2univers_storage"
        private const val KEY_SAVE = "atom2univers_save"
    }
}
