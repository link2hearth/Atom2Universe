package com.example.atom2univers

import android.content.Context
import android.webkit.JavascriptInterface

class AndroidSaveBridge(context: Context) {

    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    @JavascriptInterface
    fun saveData(payload: String?) {
        if (payload.isNullOrEmpty()) {
            preferences.edit().remove(KEY_SAVE).apply()
        } else {
            preferences.edit().putString(KEY_SAVE, payload).apply()
        }
    }

    @JavascriptInterface
    fun loadData(): String? = preferences.getString(KEY_SAVE, null)

    @JavascriptInterface
    fun clearData() {
        preferences.edit().remove(KEY_SAVE).apply()
    }

    private companion object {
        private const val PREF_NAME = "atom2univers_storage"
        private const val KEY_SAVE = "atom2univers_save"
    }
}
