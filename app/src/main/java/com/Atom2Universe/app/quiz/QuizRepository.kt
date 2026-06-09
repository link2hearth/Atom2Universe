package com.Atom2Universe.app.quiz

import android.content.Context
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.quiz.data.Question

/**
 * Repository for loading quiz questions from CSV assets.
 */
class QuizRepository(private val context: Context) {

    private var cachedQuestions: List<Question>? = null
    private val language: String = getSupportedQuizLanguage(context)

    /**
     * Loads all questions from all CSV files in the active language quiz folder.
     * Results are cached after first load.
     */
    fun loadAllQuestions(): List<Question> {
        cachedQuestions?.let { return it }

        val questions = mutableListOf<Question>()

        try {
            // List all CSV files in the active language quiz folder
            val csvFiles = context.assets.list("$QUIZ_FOLDER/$language")
                ?.filter { it.endsWith(".csv") }
                ?: emptyList()

            for (csvFile in csvFiles) {
                loadQuestionsFromFile("$QUIZ_FOLDER/$language/$csvFile", questions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        cachedQuestions = questions
        return questions
    }

    /**
     * Loads questions from a single CSV file and adds them to the list.
     */
    private fun loadQuestionsFromFile(filePath: String, questions: MutableList<Question>) {
        try {
            context.assets.open(filePath).bufferedReader().use { reader ->
                val lines = reader.readLines()

                // Skip header line
                for (i in 1 until lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty()) continue

                    parseQuestion(line)?.let { questions.add(it) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Returns all questions (public alias for loadAllQuestions).
     */
    fun getAllQuestions(): List<Question> = loadAllQuestions()

    /**
     * Returns a random subset of questions.
     */
    fun getRandomQuestions(count: Int): List<Question> {
        val allQuestions = loadAllQuestions()
        return allQuestions.shuffled().take(count.coerceAtMost(allQuestions.size))
    }

    /**
     * Returns a random subset of questions, avoiding recently used ones.
     * @param count Number of questions to return
     * @param recentQuestionIds IDs of questions to avoid (recently used)
     */
    fun getRandomQuestionsAvoidingRecent(count: Int, recentQuestionIds: Set<Int>): List<Question> {
        val allQuestions = loadAllQuestions()

        // Separate questions into fresh and recent
        val freshQuestions = allQuestions.filter { it.id !in recentQuestionIds }
        val recentQuestions = allQuestions.filter { it.id in recentQuestionIds }

        // Prioritize fresh questions, fall back to recent ones if needed
        val shuffledFresh = freshQuestions.shuffled()
        val shuffledRecent = recentQuestions.shuffled()

        val result = mutableListOf<Question>()

        // First, add fresh questions
        result.addAll(shuffledFresh.take(count))

        // If we don't have enough, add recent questions
        if (result.size < count) {
            val remaining = count - result.size
            result.addAll(shuffledRecent.take(remaining))
        }

        return result.shuffled() // Shuffle final result to mix fresh and recent
    }

    /**
     * Returns questions by their IDs (for restoring a saved game).
     */
    fun getQuestionsByIds(ids: List<Int>): List<Question> {
        val allQuestions = loadAllQuestions()
        val questionsMap = allQuestions.associateBy { it.id }
        return ids.mapNotNull { questionsMap[it] }
    }

    /**
     * Returns questions filtered by category.
     */
    fun getQuestionsByCategory(category: String, count: Int): List<Question> {
        val filtered = loadAllQuestions().filter {
            it.category.equals(category, ignoreCase = true)
        }
        return filtered.shuffled().take(count.coerceAtMost(filtered.size))
    }

    /**
     * Returns all unique categories.
     */
    fun getCategories(): List<String> {
        return loadAllQuestions().map { it.category }.distinct().sorted()
    }

    /**
     * Parses a single CSV line into a Question object.
     * Format: id;category;difficulty;question;choices;correct;explanation;source
     */
    private fun parseQuestion(line: String): Question? {
        try {
            val fields = parseCsvLine(line)
            if (fields.size < 8) return null

            val id = fields[0].toIntOrNull() ?: return null
            val category = fields[1]
            val difficulty = fields[2].toIntOrNull() ?: 1
            val question = fields[3]
            val choices = parseChoices(fields[4])
            val correct = fields[5].uppercase()
            val explanation = fields[6]
            val source = fields[7]

            if (choices.size != 4) return null

            return Question(
                id = id,
                category = category,
                difficulty = difficulty,
                question = question,
                choices = choices,
                correctAnswer = correct,
                explanation = explanation,
                source = source
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Parses choices string like "A) Sydney | B) Melbourne | C) Canberra | D) Perth"
     * and removes the letter prefixes (A), B), C), D))
     */
    private fun parseChoices(choicesStr: String): List<String> {
        // Regex to remove letter prefixes like "A) ", "B) ", etc.
        val letterPrefixRegex = Regex("^[A-Da-d]\\)\\s*")
        return choicesStr.split("|").map { choice ->
            choice.trim().replace(letterPrefixRegex, "")
        }
    }

    /**
     * Parses a CSV line handling quoted fields with semicolons.
     */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' -> {
                    if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                        current.append('"')
                        index++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                char == ';' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        fields.add(current.toString())

        return fields
    }

    companion object {
        private const val QUIZ_FOLDER = "quiz"

        private fun getSupportedQuizLanguage(context: Context): String {
            val preferredLanguage = LocaleHelper.getLanguage(context)
            val availableLanguages = try {
                context.assets.list(QUIZ_FOLDER)?.toSet().orEmpty()
            } catch (e: Exception) {
                emptySet()
            }
            return when {
                preferredLanguage in availableLanguages -> preferredLanguage
                "fr" in availableLanguages -> "fr"
                "en" in availableLanguages -> "en"
                else -> preferredLanguage
            }
        }
    }
}
