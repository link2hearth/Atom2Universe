package com.example.atom2univers

import android.webkit.JavascriptInterface

class AndroidSaveCoreBridge(private val saveCore: SaveCore) {

    @JavascriptInterface
    fun get(key: String?): String? {
        return saveCore.get(key)
    }

    @JavascriptInterface
    fun set(key: String?, value: String?): Boolean {
        return saveCore.set(key, value)
    }

    @JavascriptInterface
    fun getAll(): String? {
        return saveCore.getAll()
    }

    @JavascriptInterface
    fun mergeSave(saveBlob: String?): Boolean {
        return saveCore.mergeSave(saveBlob)
    }

    @JavascriptInterface
    fun export(): String? {
        return saveCore.export()
    }

    @JavascriptInterface
    fun importData(blob: String?): Boolean {
        return saveCore.import(blob)
    }

    @JavascriptInterface
    fun transaction(blob: String?): String? {
        return saveCore.transaction {
            if (!blob.isNullOrBlank()) {
                mergeSave(blob)
            }
        }
    }
}
