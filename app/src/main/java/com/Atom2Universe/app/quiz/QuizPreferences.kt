package com.Atom2Universe.app.quiz

import android.content.Context
import android.content.SharedPreferences
import com.Atom2Universe.app.quiz.data.AnswerType
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.edit

/**
 * Gestionnaire des préférences et de la sauvegarde pour le module Quiz.
 * Gère :
 * - Les préférences utilisateur (nombre de questions, modes activés)
 * - L'historique des questions posées (pour éviter les répétitions)
 * - La sauvegarde de la partie en cours
 */
class QuizPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "quiz_preferences"

        // Clés pour les préférences utilisateur
        private const val KEY_QUESTION_COUNT = "question_count"
        private const val KEY_SHOW_EXPLANATIONS = "show_explanations"
        private const val KEY_THINK_MODE = "think_mode"
        private const val KEY_TROLL_MODE = "troll_mode"

        // Clés pour l'historique des questions
        private const val KEY_QUESTION_HISTORY = "question_history"
        private const val MAX_HISTORY_SIZE = 100

        // Clés pour la partie en cours
        private const val KEY_GAME_IN_PROGRESS = "game_in_progress"
        private const val KEY_GAME_STATE = "game_state"

        // Valeurs par défaut
        const val DEFAULT_QUESTION_COUNT = 10
        const val DEFAULT_SHOW_EXPLANATIONS = true
        const val DEFAULT_THINK_MODE = false
        const val DEFAULT_TROLL_MODE = false
    }

    // ========================================
    // PRÉFÉRENCES UTILISATEUR
    // ========================================

    /**
     * Récupère le nombre de questions configuré
     */
    fun getQuestionCount(): Int {
        return prefs.getInt(KEY_QUESTION_COUNT, DEFAULT_QUESTION_COUNT)
    }

    /**
     * Définit le nombre de questions
     */
    fun setQuestionCount(count: Int) {
        prefs.edit { putInt(KEY_QUESTION_COUNT, count) }
    }

    /**
     * Récupère l'état du mode explications
     */
    fun getShowExplanations(): Boolean {
        return prefs.getBoolean(KEY_SHOW_EXPLANATIONS, DEFAULT_SHOW_EXPLANATIONS)
    }

    /**
     * Définit l'état du mode explications
     */
    fun setShowExplanations(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_EXPLANATIONS, enabled) }
    }

    /**
     * Récupère l'état du mode réflexion (think mode)
     */
    fun getThinkMode(): Boolean {
        return prefs.getBoolean(KEY_THINK_MODE, DEFAULT_THINK_MODE)
    }

    /**
     * Définit l'état du mode réflexion
     */
    fun setThinkMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_THINK_MODE, enabled) }
    }

    /**
     * Récupère l'état du mode troll
     */
    fun getTrollMode(): Boolean {
        return prefs.getBoolean(KEY_TROLL_MODE, DEFAULT_TROLL_MODE)
    }

    /**
     * Définit l'état du mode troll
     */
    fun setTrollMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_TROLL_MODE, enabled) }
    }

    // ========================================
    // HISTORIQUE DES QUESTIONS
    // ========================================

    /**
     * Récupère la liste des IDs des questions récemment posées
     */
    fun getQuestionHistory(): List<Int> {
        val historyJson = prefs.getString(KEY_QUESTION_HISTORY, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(historyJson)
            (0 until jsonArray.length()).map { jsonArray.getInt(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Ajoute des IDs de questions à l'historique.
     * Garde uniquement les MAX_HISTORY_SIZE derniers.
     */
    fun addToQuestionHistory(questionIds: List<Int>) {
        val currentHistory = getQuestionHistory().toMutableList()

        // Ajouter les nouveaux IDs
        currentHistory.addAll(questionIds)

        // Garder uniquement les derniers MAX_HISTORY_SIZE
        val trimmedHistory = if (currentHistory.size > MAX_HISTORY_SIZE) {
            currentHistory.takeLast(MAX_HISTORY_SIZE)
        } else {
            currentHistory
        }

        // Sauvegarder
        val jsonArray = JSONArray(trimmedHistory)
        prefs.edit { putString(KEY_QUESTION_HISTORY, jsonArray.toString()) }
    }

    /**
     * Vérifie si une question est dans l'historique récent
     */
    fun isQuestionInHistory(questionId: Int): Boolean {
        return getQuestionHistory().contains(questionId)
    }

    /**
     * Efface l'historique des questions
     */
    fun clearQuestionHistory() {
        prefs.edit { remove(KEY_QUESTION_HISTORY) }
    }

    // ========================================
    // PARTIE EN COURS
    // ========================================

    /**
     * Vérifie si une partie est en cours
     */
    fun hasGameInProgress(): Boolean {
        return prefs.getBoolean(KEY_GAME_IN_PROGRESS, false)
    }

    /**
     * Sauvegarde l'état de la partie en cours
     */
    fun saveGameState(state: GameState) {
        val json = JSONObject().apply {
            put("isChallengeMode", state.isChallengeMode)
            put("challengeQuestionCount", state.challengeQuestionCount)
            put("currentQuestionIndex", state.currentQuestionIndex)
            put("questionIds", JSONArray(state.questionIds))

            // Sauvegarder les réponses déjà données
            val answersArray = JSONArray()
            state.answeredQuestions.forEach { answered ->
                val answerObj = JSONObject().apply {
                    put("questionId", answered.questionId)
                    put("userAnswerIndex", answered.userAnswerIndex)
                    put("isCorrect", answered.isCorrect)
                    put("answerType", answered.answerType.name)
                    put("userAnswerText", answered.userAnswerText)
                    put("correctAnswerText", answered.correctAnswerText)
                }
                answersArray.put(answerObj)
            }
            put("answeredQuestions", answersArray)
        }

        prefs.edit()
            .putBoolean(KEY_GAME_IN_PROGRESS, true)
            .putString(KEY_GAME_STATE, json.toString())
            .apply()
    }

    /**
     * Récupère l'état de la partie en cours
     */
    fun getGameState(): GameState? {
        if (!hasGameInProgress()) return null

        val stateJson = prefs.getString(KEY_GAME_STATE, null) ?: return null

        return try {
            val json = JSONObject(stateJson)

            val questionIdsArray = json.getJSONArray("questionIds")
            val questionIds = (0 until questionIdsArray.length()).map { questionIdsArray.getInt(it) }

            val answersArray = json.getJSONArray("answeredQuestions")
            val answeredQuestions = (0 until answersArray.length()).map { i ->
                val answerObj = answersArray.getJSONObject(i)
                SavedAnswer(
                    questionId = answerObj.getInt("questionId"),
                    userAnswerIndex = answerObj.getInt("userAnswerIndex"),
                    isCorrect = answerObj.getBoolean("isCorrect"),
                    answerType = AnswerType.valueOf(answerObj.getString("answerType")),
                    userAnswerText = answerObj.optString("userAnswerText", ""),
                    correctAnswerText = answerObj.optString("correctAnswerText", "")
                )
            }

            GameState(
                isChallengeMode = json.getBoolean("isChallengeMode"),
                challengeQuestionCount = json.optInt("challengeQuestionCount", 50),
                currentQuestionIndex = json.getInt("currentQuestionIndex"),
                questionIds = questionIds,
                answeredQuestions = answeredQuestions
            )
        } catch (e: Exception) {
            e.printStackTrace()
            clearGameState()
            null
        }
    }

    /**
     * Efface la partie en cours
     */
    fun clearGameState() {
        prefs.edit()
            .putBoolean(KEY_GAME_IN_PROGRESS, false)
            .remove(KEY_GAME_STATE)
            .apply()
    }

    /**
     * État d'une partie sauvegardée
     */
    data class GameState(
        val isChallengeMode: Boolean,
        val challengeQuestionCount: Int,
        val currentQuestionIndex: Int,
        val questionIds: List<Int>,
        val answeredQuestions: List<SavedAnswer>
    )

    /**
     * Réponse sauvegardée (version simplifiée sans l'objet Question complet)
     */
    data class SavedAnswer(
        val questionId: Int,
        val userAnswerIndex: Int,
        val isCorrect: Boolean,
        val answerType: AnswerType,
        val userAnswerText: String,
        val correctAnswerText: String
    )
}
