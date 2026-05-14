package karballo.util

class Utils {
    companion object {
        val instance: PlatformUtils by lazy { JvmPlatformUtils() }
    }
}