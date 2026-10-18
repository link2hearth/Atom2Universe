package com.Atom2Universe.app.music.lyrics.api

/**
 * Résultat d'une recherche de paroles depuis une API.
 */
sealed class LyricsResult {
    data class Success(
        val lyrics: String,
        val source: String,
        val isSynced: Boolean = false,
        val alternatives: List<AlternativeLyrics> = emptyList()
    ) : LyricsResult()

    data class Error(val message: String, val source: String) : LyricsResult()
    object NotFound : LyricsResult()
    object RateLimited : LyricsResult()
}

/**
 * Un résultat alternatif de paroles (autre que le meilleur résultat retenu).
 */
data class AlternativeLyrics(
    val lyrics: String,
    val source: String,
    val isSynced: Boolean = false
)

/**
 * Résultat d'un test de connexion à une API de paroles.
 */
sealed class ApiTestResult {
    /** API is reachable and responding correctly */
    object Success : ApiTestResult()

    /** API returned rate limit error (429) - still means API is working */
    object RateLimited : ApiTestResult()

    /** Host could not be resolved */
    object UnknownHost : ApiTestResult()

    /** Connection timed out */
    object Timeout : ApiTestResult()

    /** HTTP error with specific code */
    data class HttpError(val code: Int) : ApiTestResult()

    /** Other error */
    data class Error(val message: String) : ApiTestResult()

    /** Check if the API is considered working (success or rate limited) */
    fun isWorking(): Boolean = this is Success || this is RateLimited
}
