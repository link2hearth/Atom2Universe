package karballo.log

import android.util.Log

class Logger private constructor(internal var prefix: String) {

    fun info(`in`: Any) {
        if (noLog) return
        Log.i("Karballo-$prefix", `in`.toString())
    }

    fun debug(`in`: Any) {
        if (noLog) return
        Log.d("Karballo-$prefix", `in`.toString())
    }

    fun error(`in`: Any) {
        if (noLog) return
        Log.e("Karballo-$prefix", `in`.toString())
    }

    companion object {
        var noLog = true // Désactivé par défaut pour ne pas spammer les logs

        fun getLogger(prefix: String): Logger {
            return Logger(prefix)
        }
    }
}
