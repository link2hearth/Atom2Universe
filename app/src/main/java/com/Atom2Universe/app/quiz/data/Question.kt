package com.Atom2Universe.app.quiz.data

/**
 * Represents a quiz question loaded in the active app language.
 */
data class Question(
    val id: Int,
    val category: String,
    val difficulty: Int,
    val question: String,
    val choices: List<String>,
    val correctAnswer: String,
    val explanation: String,
    val source: String
) {
    /**
     * Returns the question text.
     */
    fun getQuestion(lang: String): String = question

    /**
     * Returns the choices.
     */
    fun getChoices(lang: String): List<String> = choices

    /**
     * Returns the explanation.
     */
    fun getExplanation(lang: String): String = explanation

    /**
     * Returns the correct answer index (0-3) based on the correctAnswer letter (A-D).
     */
    fun getCorrectIndex(): Int = when (correctAnswer.uppercase()) {
        "A" -> 0
        "B" -> 1
        "C" -> 2
        "D" -> 3
        else -> 0
    }

    /**
     * Checks if the given answer index is correct.
     */
    fun isCorrect(answerIndex: Int): Boolean = answerIndex == getCorrectIndex()
}

/**
 * Type of answer given by the user in troll mode.
 */
enum class AnswerType {
    CORRECT,       // +1 point - The correct answer
    WRONG_ORIGINAL, // 0 points - One of the 3 wrong answers from the original question
    WRONG_TROLL    // -1 point - A troll answer from another question
}

/**
 * Represents a question that has been answered by the user.
 */
data class AnsweredQuestion(
    val question: Question,
    val userAnswerIndex: Int,
    val isCorrect: Boolean,
    val answerType: AnswerType = if (isCorrect) AnswerType.CORRECT else AnswerType.WRONG_ORIGINAL,
    val userAnswerText: String = "", // The actual text of the user's answer (for Challenge mode review)
    val correctAnswerText: String = "" // The correct answer text (for Challenge mode review)
) {
    fun getUserAnswerLetter(): String = when (userAnswerIndex) {
        0 -> "A"
        1 -> "B"
        2 -> "C"
        3 -> "D"
        else -> "?"
    }

    /**
     * Get the score for this answer based on the answer type.
     * +1 for correct, 0 for wrong original, -1 for troll answer.
     */
    fun getChallengeScore(): Int = when (answerType) {
        AnswerType.CORRECT -> 1
        AnswerType.WRONG_ORIGINAL -> 0
        AnswerType.WRONG_TROLL -> -1
    }
}
