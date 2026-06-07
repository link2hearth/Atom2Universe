package com.Atom2Universe.app.quiz

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.quiz.data.AnsweredQuestion
import com.Atom2Universe.app.quiz.data.AnswerType
import com.Atom2Universe.app.quiz.data.Question
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.Atom2Universe.app.util.enableImmersiveMode

class QuizActivity : ThemedActivity() {

    // State
    private enum class QuizState { MENU, PLAYING, RESULT, CHALLENGE_RESULT }
    private var currentState = QuizState.MENU

    // Quiz settings
    private var questionCount = 10
    private var showExplanations = true
    private var thinkMode = false
    private var trollMode = false

    // Challenge mode
    private var isChallengeMode = false
    private var challengeQuestionCount = CHALLENGE_QUESTION_COUNT_FULL // 50 or 15 questions
    private var originalAnswerIndices: Set<Int> = emptySet() // Indices of original answers (correct + 3 wrong) in currentChoices

    // Quiz game state
    private var allQuestions: List<Question> = emptyList()  // All questions for troll mode
    private var questions: List<Question> = emptyList()
    private var currentQuestionIndex = 0
    private var selectedAnswerIndex: Int? = null
    private var hasValidated = false
    private var answersRevealed = false
    private val answeredQuestions = mutableListOf<AnsweredQuestion>()

    // Troll mode state
    private var currentChoices: List<String> = emptyList()
    private var correctAnswerIndexInChoices: Int = -1

    // Repository and Preferences
    private lateinit var repository: QuizRepository
    private lateinit var preferences: QuizPreferences

    // Views - Menu
    private lateinit var menuSection: View
    private lateinit var questionCountGroup: RadioGroup
    private lateinit var showExplanationsSwitch: SwitchMaterial
    private lateinit var thinkModeSwitch: SwitchMaterial
    private lateinit var trollModeSwitch: SwitchMaterial
    private lateinit var showExplanationsStatus: TextView
    private lateinit var thinkModeStatus: TextView
    private lateinit var trollModeStatus: TextView
    private lateinit var startButton: MaterialButton

    // Views - Question
    private lateinit var questionSection: View
    private lateinit var questionCounter: TextView
    private lateinit var categoryBadge: TextView
    private lateinit var questionText: TextView
    private lateinit var showAnswersButton: MaterialButton
    private lateinit var answersContainer: LinearLayout
    private var answerButtons: MutableList<MaterialButton> = mutableListOf()
    private lateinit var feedbackSection: View
    private lateinit var feedbackResult: TextView
    private lateinit var feedbackExplanation: TextView
    private lateinit var feedbackSearchButton: MaterialButton
    private lateinit var validateButton: MaterialButton
    private lateinit var nextButton: MaterialButton

    // Views - Result
    private lateinit var resultSection: View
    private lateinit var resultScore: TextView
    private lateinit var resultPercentage: TextView
    private lateinit var mistakesTitle: TextView
    private lateinit var mistakesContainer: LinearLayout
    private lateinit var noMistakesText: TextView
    private lateinit var playAgainButton: MaterialButton
    private lateinit var backToMenuButton: MaterialButton

    // Views - Challenge Result
    private lateinit var challengeResultSection: View
    private lateinit var challengeScore: TextView
    private lateinit var challengeScoreMax: TextView
    private lateinit var challengeCorrectCount: TextView
    private lateinit var challengeWrongCount: TextView
    private lateinit var challengeTrollCount: TextView
    private lateinit var challengeReviewContainer: LinearLayout
    private lateinit var challengePlayAgainButton: MaterialButton
    private lateinit var challengeBackToMenuButton: MaterialButton

    // Views - Challenge buttons in menu
    private lateinit var challengeButton: MaterialButton
    private lateinit var challengeExpressButton: MaterialButton

    // Language
    private val currentLang: String
        get() = LocaleHelper.getLanguage(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_quiz)

        repository = QuizRepository(this)
        preferences = QuizPreferences(this)

        initViews()
        loadPreferences()
        setupListeners()
        showState(QuizState.MENU)

        onBackPressedDispatcher.addCallback(this) {
            when (currentState) {
                QuizState.MENU -> finish()
                QuizState.PLAYING -> showState(QuizState.MENU)
                QuizState.RESULT -> showState(QuizState.MENU)
                QuizState.CHALLENGE_RESULT -> showState(QuizState.MENU)
            }
        }

        // Check for game in progress
        checkForSavedGame()
    }

    private fun initViews() {
        // Menu section
        menuSection = findViewById(R.id.menu_section)
        questionCountGroup = findViewById(R.id.question_count_group)
        showExplanationsSwitch = findViewById(R.id.show_explanations_switch)
        thinkModeSwitch = findViewById(R.id.think_mode_switch)
        trollModeSwitch = findViewById(R.id.troll_mode_switch)
        showExplanationsStatus = findViewById(R.id.show_explanations_status)
        thinkModeStatus = findViewById(R.id.think_mode_status)
        trollModeStatus = findViewById(R.id.troll_mode_status)
        startButton = findViewById(R.id.start_button)

        // Question section
        questionSection = findViewById(R.id.question_section)
        questionCounter = findViewById(R.id.question_counter)
        categoryBadge = findViewById(R.id.category_badge)
        questionText = findViewById(R.id.question_text)
        showAnswersButton = findViewById(R.id.show_answers_button)
        answersContainer = findViewById(R.id.answers_container)
        feedbackSection = findViewById(R.id.feedback_section)
        feedbackResult = findViewById(R.id.feedback_result)
        feedbackExplanation = findViewById(R.id.feedback_explanation)
        feedbackSearchButton = findViewById(R.id.feedback_search_button)
        validateButton = findViewById(R.id.validate_button)
        nextButton = findViewById(R.id.next_button)

        // Result section
        resultSection = findViewById(R.id.result_section)
        resultScore = findViewById(R.id.result_score)
        resultPercentage = findViewById(R.id.result_percentage)
        mistakesTitle = findViewById(R.id.mistakes_title)
        mistakesContainer = findViewById(R.id.mistakes_container)
        noMistakesText = findViewById(R.id.no_mistakes_text)
        playAgainButton = findViewById(R.id.play_again_button)
        backToMenuButton = findViewById(R.id.back_to_menu_button)

        // Challenge buttons in menu
        challengeButton = findViewById(R.id.challenge_button)
        challengeExpressButton = findViewById(R.id.challenge_express_button)

        // Challenge result section
        challengeResultSection = findViewById(R.id.challenge_result_section)
        challengeScore = findViewById(R.id.challenge_score)
        challengeScoreMax = findViewById(R.id.challenge_score_max)
        challengeCorrectCount = findViewById(R.id.challenge_correct_count)
        challengeWrongCount = findViewById(R.id.challenge_wrong_count)
        challengeTrollCount = findViewById(R.id.challenge_troll_count)
        challengeReviewContainer = findViewById(R.id.challenge_review_container)
        challengePlayAgainButton = findViewById(R.id.challenge_play_again_button)
        challengeBackToMenuButton = findViewById(R.id.challenge_back_to_menu_button)
    }

    private fun loadPreferences() {
        // Load saved preferences
        questionCount = preferences.getQuestionCount()
        showExplanations = preferences.getShowExplanations()
        thinkMode = preferences.getThinkMode()
        trollMode = preferences.getTrollMode()

        // Apply to UI - Question count radio buttons
        val radioId = when (questionCount) {
            10 -> R.id.radio_10
            15 -> R.id.radio_15
            20 -> R.id.radio_20
            else -> R.id.radio_10
        }
        questionCountGroup.check(radioId)

        // Apply to UI - Switches
        showExplanationsSwitch.isChecked = showExplanations
        thinkModeSwitch.isChecked = thinkMode
        trollModeSwitch.isChecked = trollMode

        // Update switch status texts
        updateSwitchStatus(showExplanationsStatus, showExplanationsSwitch, showExplanations)
        updateSwitchStatus(thinkModeStatus, thinkModeSwitch, thinkMode)
        updateSwitchStatus(trollModeStatus, trollModeSwitch, trollMode)
    }

    private fun checkForSavedGame() {
        if (preferences.hasGameInProgress()) {
            val gameState = preferences.getGameState()
            if (gameState != null) {
                showResumeGameDialog(gameState)
            }
        }
    }

    private fun showResumeGameDialog(gameState: QuizPreferences.GameState) {
        val modeText = if (gameState.isChallengeMode) {
            if (gameState.challengeQuestionCount == CHALLENGE_QUESTION_COUNT_EXPRESS) "Challenge Express" else "Challenge"
        } else {
            getString(R.string.quiz_title)
        }

        val progress = "${gameState.currentQuestionIndex}/${gameState.questionIds.size}"

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.quiz_resume_game_title))
            .setMessage(getString(R.string.quiz_resume_game_message, modeText, progress))
            .setPositiveButton(getString(R.string.quiz_resume_game_yes)) { _, _ ->
                resumeGame(gameState)
            }
            .setNegativeButton(getString(R.string.quiz_resume_game_no)) { _, _ ->
                preferences.clearGameState()
            }
            .setCancelable(false)
            .show()
    }

    private fun resumeGame(gameState: QuizPreferences.GameState) {
        // Load all questions first (for troll mode)
        allQuestions = repository.getAllQuestions()

        // Load the saved questions
        questions = repository.getQuestionsByIds(gameState.questionIds)

        if (questions.isEmpty()) {
            Toast.makeText(this, R.string.quiz_restore_failed, Toast.LENGTH_SHORT).show()
            preferences.clearGameState()
            return
        }

        // Restore state
        isChallengeMode = gameState.isChallengeMode
        challengeQuestionCount = gameState.challengeQuestionCount
        currentQuestionIndex = gameState.currentQuestionIndex

        // Restore answered questions
        answeredQuestions.clear()
        gameState.answeredQuestions.forEach { saved ->
            val question = questions.find { it.id == saved.questionId }
            if (question != null) {
                answeredQuestions.add(
                    AnsweredQuestion(
                        question = question,
                        userAnswerIndex = saved.userAnswerIndex,
                        isCorrect = saved.isCorrect,
                        answerType = saved.answerType,
                        userAnswerText = saved.userAnswerText,
                        correctAnswerText = saved.correctAnswerText
                    )
                )
            }
        }

        // Continue playing
        selectedAnswerIndex = null
        hasValidated = false
        showState(QuizState.PLAYING)
        displayCurrentQuestion()
    }

    private fun setupListeners() {
        // Menu back button
        findViewById<View>(R.id.menu_back_button).setOnClickListener {
            finish()
        }

        // Question count selection
        questionCountGroup.setOnCheckedChangeListener { _, checkedId ->
            questionCount = when (checkedId) {
                R.id.radio_10 -> 10
                R.id.radio_15 -> 15
                R.id.radio_20 -> 20
                else -> 10
            }
            preferences.setQuestionCount(questionCount)
        }

        // Show explanations switch
        showExplanationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            showExplanations = isChecked
            updateSwitchStatus(showExplanationsStatus, showExplanationsSwitch, isChecked)
            preferences.setShowExplanations(isChecked)
        }

        // Think mode switch
        thinkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            thinkMode = isChecked
            updateSwitchStatus(thinkModeStatus, thinkModeSwitch, isChecked)
            preferences.setThinkMode(isChecked)
        }

        // Troll mode switch
        trollModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            trollMode = isChecked
            updateSwitchStatus(trollModeStatus, trollModeSwitch, isChecked)
            preferences.setTrollMode(isChecked)
        }

        // Start button
        startButton.setOnClickListener {
            isChallengeMode = false
            startQuiz()
        }

        // Challenge button (50 questions)
        challengeButton.setOnClickListener {
            startChallengeMode(isExpress = false)
        }

        // Challenge Express button (15 questions)
        challengeExpressButton.setOnClickListener {
            startChallengeMode(isExpress = true)
        }

        // Question back button
        findViewById<View>(R.id.question_back_button).setOnClickListener {
            showState(QuizState.MENU)
        }

        // Show answers button (think mode)
        showAnswersButton.setOnClickListener {
            revealAnswers()
        }

        // Validate button
        validateButton.setOnClickListener {
            validateAnswer()
        }

        // Next button
        nextButton.setOnClickListener {
            nextQuestion()
        }

        // Result buttons
        playAgainButton.setOnClickListener {
            isChallengeMode = false
            startQuiz()
        }

        backToMenuButton.setOnClickListener {
            showState(QuizState.MENU)
        }

        // Challenge result buttons
        challengePlayAgainButton.setOnClickListener {
            // Replay the same type of challenge (express or full)
            val wasExpress = challengeQuestionCount == CHALLENGE_QUESTION_COUNT_EXPRESS
            startChallengeMode(isExpress = wasExpress)
        }

        challengeBackToMenuButton.setOnClickListener {
            showState(QuizState.MENU)
        }
    }

    private fun updateSwitchStatus(statusView: TextView, switchView: SwitchMaterial, isChecked: Boolean) {
        statusView.text = if (isChecked) {
            getString(R.string.quiz_option_enabled)
        } else {
            getString(R.string.quiz_option_disabled)
        }
    }

    private fun showState(state: QuizState) {
        currentState = state
        menuSection.visibility = if (state == QuizState.MENU) View.VISIBLE else View.GONE
        questionSection.visibility = if (state == QuizState.PLAYING) View.VISIBLE else View.GONE
        resultSection.visibility = if (state == QuizState.RESULT) View.VISIBLE else View.GONE
        challengeResultSection.visibility = if (state == QuizState.CHALLENGE_RESULT) View.VISIBLE else View.GONE

        // Reset challenge mode when going back to menu
        if (state == QuizState.MENU) {
            isChallengeMode = false
        }
    }

    private fun startQuiz() {
        // Load all questions first (for troll mode)
        allQuestions = repository.getAllQuestions()

        // Load questions for this quiz, avoiding recently used ones
        val count = if (isChallengeMode) challengeQuestionCount else questionCount
        val recentQuestionIds = preferences.getQuestionHistory().toSet()
        questions = repository.getRandomQuestionsAvoidingRecent(count, recentQuestionIds)

        if (questions.isEmpty()) {
            Toast.makeText(this, R.string.quiz_no_questions, Toast.LENGTH_SHORT).show()
            return
        }

        // Reset state
        currentQuestionIndex = 0
        answeredQuestions.clear()
        selectedAnswerIndex = null
        hasValidated = false

        // Save game state
        saveGameProgress()

        showState(QuizState.PLAYING)
        displayCurrentQuestion()
    }

    private fun saveGameProgress() {
        val gameState = QuizPreferences.GameState(
            isChallengeMode = isChallengeMode,
            challengeQuestionCount = challengeQuestionCount,
            currentQuestionIndex = currentQuestionIndex,
            questionIds = questions.map { it.id },
            answeredQuestions = answeredQuestions.map { answered ->
                QuizPreferences.SavedAnswer(
                    questionId = answered.question.id,
                    userAnswerIndex = answered.userAnswerIndex,
                    isCorrect = answered.isCorrect,
                    answerType = answered.answerType,
                    userAnswerText = answered.userAnswerText,
                    correctAnswerText = answered.correctAnswerText
                )
            }
        )
        preferences.saveGameState(gameState)
    }

    private fun startChallengeMode(isExpress: Boolean = false) {
        isChallengeMode = true
        challengeQuestionCount = if (isExpress) CHALLENGE_QUESTION_COUNT_EXPRESS else CHALLENGE_QUESTION_COUNT_FULL
        // Challenge mode: troll and think are forced ON, explanations are OFF
        startQuiz()
    }

    companion object {
        private const val CHALLENGE_QUESTION_COUNT_FULL = 50
        private const val CHALLENGE_QUESTION_COUNT_EXPRESS = 15
    }

    private fun displayCurrentQuestion() {
        val question = questions[currentQuestionIndex]

        // Update counter
        questionCounter.text = getString(
            R.string.quiz_question_format,
            currentQuestionIndex + 1,
            questions.size
        )

        // Update category badge (just the category name, no prefix)
        categoryBadge.text = question.category

        // Update question text
        questionText.text = question.getQuestion(currentLang)

        // Generate answer buttons dynamically
        // In challenge mode, troll mode is always active
        generateAnswerButtons(question)

        // Reset UI state
        selectedAnswerIndex = null
        hasValidated = false
        answersRevealed = false
        validateButton.isEnabled = false
        validateButton.visibility = View.VISIBLE
        nextButton.visibility = View.GONE
        feedbackSection.visibility = View.GONE

        // Reset next button style (in case it was grayed in challenge mode)
        nextButton.setBackgroundColor(ContextCompat.getColor(this, R.color.startup_button_background))
        nextButton.setTextColor(ContextCompat.getColor(this, R.color.startup_button_text))

        // Handle think mode (always active in challenge mode)
        val useThinkMode = if (isChallengeMode) true else thinkMode
        if (useThinkMode) {
            answersContainer.visibility = View.GONE
            showAnswersButton.visibility = View.VISIBLE
        } else {
            answersContainer.visibility = View.VISIBLE
            showAnswersButton.visibility = View.GONE
        }
    }

    private fun generateAnswerButtons(question: Question) {
        // Clear existing buttons
        answersContainer.removeAllViews()
        answerButtons.clear()

        val choices: List<String>
        // Use troll mode if enabled OR if in challenge mode
        val useTrollMode = trollMode || isChallengeMode

        if (useTrollMode) {
            // Troll mode: 4 correct answers + 8 random answers from other questions
            val correctChoices = question.getChoices(currentLang).toMutableList()
            val correctAnswer = correctChoices[question.getCorrectIndex()]
            val correctChoicesTrimmed = correctChoices.map { it.trim() }

            // Get 8 random wrong answers from other questions
            val otherQuestions = allQuestions.filter { it.id != question.id }
            val randomWrongChoices = mutableListOf<String>()

            // Collect all wrong choices from other questions
            val allOtherChoices = mutableListOf<String>()
            otherQuestions.forEach { otherQ ->
                val otherChoices = otherQ.getChoices(currentLang)
                otherChoices.forEachIndexed { index, choice ->
                    // Exclude correct answers from other questions and any text already present
                    // in the current question's choices (trim to catch whitespace differences)
                    if (index != otherQ.getCorrectIndex() && !correctChoicesTrimmed.contains(choice.trim())) {
                        allOtherChoices.add(choice)
                    }
                }
            }

            // Shuffle and take up to 8 unique wrong choices (distinct by trimmed text)
            allOtherChoices.shuffle()
            val seen = mutableSetOf<String>()
            val distinctWrongChoices = allOtherChoices.filter { seen.add(it.trim()) }.take(8)
            randomWrongChoices.addAll(distinctWrongChoices)

            // Combine all choices: 4 from current question + 8 random
            val allChoices = correctChoices.toMutableList()
            allChoices.addAll(randomWrongChoices)

            // Shuffle all choices
            allChoices.shuffle()

            // Find the correct answer index in shuffled list
            correctAnswerIndexInChoices = allChoices.indexOf(correctAnswer)

            // Track which indices are original answers (for challenge scoring)
            // Original answers are the 4 choices from the current question
            originalAnswerIndices = allChoices.mapIndexedNotNull { index, choice ->
                if (correctChoices.contains(choice)) index else null
            }.toSet()

            choices = allChoices
            currentChoices = choices
        } else {
            // Normal mode: 4 choices, shuffled
            val originalChoices = question.getChoices(currentLang)
            val correctAnswer = originalChoices[question.getCorrectIndex()]

            // Shuffle the choices
            val shuffledChoices = originalChoices.shuffled()

            // Find the correct answer index in shuffled list
            correctAnswerIndexInChoices = shuffledChoices.indexOf(correctAnswer)

            choices = shuffledChoices
            currentChoices = choices
            // In normal mode, all answers are original (indices 0-3)
            originalAnswerIndices = setOf(0, 1, 2, 3)
        }

        // Create buttons
        choices.forEachIndexed { index, choiceText ->
            val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = if (index == 0) 0 else resources.getDimensionPixelSize(R.dimen.quiz_button_margin)
                }
                minHeight = resources.getDimensionPixelSize(R.dimen.quiz_button_height)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.quiz_button_padding),
                    0,
                    resources.getDimensionPixelSize(R.dimen.quiz_button_padding),
                    0
                )
                text = choiceText
                textSize = 16f
                isAllCaps = false
                setTextColor(ContextCompat.getColor(context, R.color.startup_text_primary))
                isEnabled = true

                setOnClickListener {
                    if (!hasValidated) {
                        selectAnswer(index)
                    }
                }
            }
            resetButtonStyle(button)
            answerButtons.add(button)
            answersContainer.addView(button)
        }
    }

    private fun revealAnswers() {
        answersRevealed = true
        showAnswersButton.visibility = View.GONE
        answersContainer.visibility = View.VISIBLE
    }

    private fun selectAnswer(index: Int) {
        // Deselect previous
        selectedAnswerIndex?.let { prevIndex ->
            resetButtonStyle(answerButtons[prevIndex])
        }

        // Select new
        selectedAnswerIndex = index
        answerButtons[index].setBackgroundColor(
            ContextCompat.getColor(this, R.color.startup_button_background)
        )
        answerButtons[index].setTextColor(
            ContextCompat.getColor(this, R.color.startup_button_text)
        )

        validateButton.isEnabled = true
    }

    private fun resetButtonStyle(button: MaterialButton) {
        button.setBackgroundColor(Color.TRANSPARENT)
        button.setTextColor(ContextCompat.getColor(this, R.color.startup_text_primary))
        button.strokeWidth = 2
        button.strokeColor = ContextCompat.getColorStateList(this, R.color.startup_text_secondary)
    }

    private fun validateAnswer() {
        val answerIndex = selectedAnswerIndex ?: return
        val question = questions[currentQuestionIndex]
        val isCorrect = answerIndex == correctAnswerIndexInChoices

        hasValidated = true

        // Determine answer type for scoring
        val answerType = when {
            isCorrect -> AnswerType.CORRECT
            originalAnswerIndices.contains(answerIndex) -> AnswerType.WRONG_ORIGINAL
            else -> AnswerType.WRONG_TROLL
        }

        // Get the text of user's answer and correct answer
        val userAnswerText = currentChoices.getOrNull(answerIndex) ?: ""
        val correctAnswerText = currentChoices.getOrNull(correctAnswerIndexInChoices) ?: ""

        // Record answer (store the original question's correct index for result display)
        answeredQuestions.add(
            AnsweredQuestion(
                question = question,
                userAnswerIndex = answerIndex,
                isCorrect = isCorrect,
                answerType = answerType,
                userAnswerText = userAnswerText,
                correctAnswerText = correctAnswerText
            )
        )

        // Disable all answer buttons
        answerButtons.forEach { it.isEnabled = false }

        if (isChallengeMode) {
            // Challenge mode: subtle gray feedback to indicate answer was recorded
            val grayColor = Color.parseColor("#E0E0E0") // Light gray
            answerButtons[answerIndex].setBackgroundColor(grayColor)
            answerButtons[answerIndex].setTextColor(Color.DKGRAY)
        } else {
            // Normal mode: show correct answer in green
            answerButtons[correctAnswerIndexInChoices].setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            )
            answerButtons[correctAnswerIndexInChoices].setTextColor(Color.WHITE)

            // Show user's wrong answer in red (if different from correct)
            if (!isCorrect) {
                answerButtons[answerIndex].setBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.holo_red_dark)
                )
                answerButtons[answerIndex].setTextColor(Color.WHITE)
            }

            // Show feedback (not in challenge mode)
            if (showExplanations) {
                feedbackSection.visibility = View.VISIBLE
                feedbackResult.text = if (isCorrect) {
                    getString(R.string.quiz_correct)
                } else {
                    getString(R.string.quiz_incorrect)
                }
                feedbackResult.setTextColor(
                    if (isCorrect) {
                        ContextCompat.getColor(this, android.R.color.holo_green_dark)
                    } else {
                        ContextCompat.getColor(this, android.R.color.holo_red_dark)
                    }
                )
                feedbackExplanation.text = question.getExplanation(currentLang)
                feedbackSearchButton.setOnClickListener { openQuizSearch(question) }
            }
        }

        // Show next button
        validateButton.visibility = View.GONE
        nextButton.visibility = View.VISIBLE

        // In challenge mode, style the next button with same gray
        if (isChallengeMode) {
            val grayColor = Color.parseColor("#E0E0E0")
            nextButton.setBackgroundColor(grayColor)
            nextButton.setTextColor(Color.DKGRAY)
        }

        // Change next button text for last question
        if (currentQuestionIndex == questions.size - 1) {
            nextButton.text = if (isChallengeMode) {
                getString(R.string.quiz_challenge_result_title)
            } else {
                getString(R.string.quiz_result_title)
            }
        } else {
            nextButton.text = getString(R.string.quiz_next)
        }
    }

    private fun nextQuestion() {
        if (currentQuestionIndex < questions.size - 1) {
            currentQuestionIndex++
            // Save progress after each question
            saveGameProgress()
            displayCurrentQuestion()
        } else {
            // Game finished - finalize and clear saved game
            finishGame()
            if (isChallengeMode) {
                showChallengeResults()
            } else {
                showResults()
            }
        }
    }

    private fun finishGame() {
        // Add questions to history to avoid repeating them too soon
        val questionIds = questions.map { it.id }
        preferences.addToQuestionHistory(questionIds)

        // Clear the saved game state
        preferences.clearGameState()
    }

    private fun showResults() {
        showState(QuizState.RESULT)

        val correctCount = answeredQuestions.count { it.isCorrect }
        val totalCount = answeredQuestions.size
        val percentage = if (totalCount > 0) (correctCount * 100) / totalCount else 0

        // Update score display
        resultScore.text = getString(R.string.quiz_score_format, correctCount, totalCount)
        resultPercentage.text = getString(R.string.quiz_percentage_format, percentage)

        // Show mistakes
        mistakesContainer.removeAllViews()
        val mistakes = answeredQuestions.filter { !it.isCorrect }

        if (mistakes.isEmpty()) {
            mistakesTitle.visibility = View.GONE
            noMistakesText.visibility = View.VISIBLE
        } else {
            mistakesTitle.visibility = View.VISIBLE
            noMistakesText.visibility = View.GONE

            mistakes.forEach { answered ->
                addMistakeCard(answered)
            }
        }
    }

    private fun addMistakeCard(answered: AnsweredQuestion) {
        val question = answered.question
        val choices = question.getChoices(currentLang)

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundResource(R.drawable.quiz_card_background)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 16
            layoutParams = params
        }

        // Question text
        val questionTextView = TextView(this).apply {
            text = question.getQuestion(currentLang)
            setTextColor(ContextCompat.getColor(context, R.color.startup_text_primary))
            textSize = 14f
        }
        cardLayout.addView(questionTextView)

        // User's wrong answer
        val userAnswerText = TextView(this).apply {
            val userChoice = choices.getOrNull(answered.userAnswerIndex) ?: "?"
            text = getString(R.string.quiz_your_answer, userChoice)
            setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            textSize = 13f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 12
            layoutParams = params
        }
        cardLayout.addView(userAnswerText)

        // Correct answer
        val correctAnswerText = TextView(this).apply {
            val correctChoice = choices.getOrNull(question.getCorrectIndex()) ?: "?"
            text = getString(R.string.quiz_correct_answer, correctChoice)
            setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
            textSize = 13f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 4
            layoutParams = params
        }
        cardLayout.addView(correctAnswerText)

        // Search button
        cardLayout.addView(makeSearchButton(question))

        mistakesContainer.addView(cardLayout)
    }

    private fun showChallengeResults() {
        showState(QuizState.CHALLENGE_RESULT)

        // Calculate scores
        val correctCount = answeredQuestions.count { it.answerType == AnswerType.CORRECT }
        val wrongOriginalCount = answeredQuestions.count { it.answerType == AnswerType.WRONG_ORIGINAL }
        val trollCount = answeredQuestions.count { it.answerType == AnswerType.WRONG_TROLL }

        // Calculate total score: +1 for correct, 0 for wrong original, -1 for troll
        val totalScore = correctCount - trollCount
        val maxScore = answeredQuestions.size

        // Update score display
        challengeScore.text = getString(R.string.quiz_challenge_score_format, totalScore)
        challengeScoreMax.text = getString(R.string.quiz_challenge_score_max, maxScore)

        // Update breakdown counts
        challengeCorrectCount.text = correctCount.toString()
        challengeWrongCount.text = wrongOriginalCount.toString()
        challengeTrollCount.text = trollCount.toString()

        // Clear and populate review container
        challengeReviewContainer.removeAllViews()
        answeredQuestions.forEachIndexed { index, answered ->
            addChallengeReviewCard(index + 1, answered)
        }
    }

    private fun addChallengeReviewCard(questionNumber: Int, answered: AnsweredQuestion) {
        val question = answered.question

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundResource(R.drawable.quiz_card_background)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 12
            layoutParams = params
        }

        // Header row with question number and score indicator
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Question number badge
        val questionBadge = TextView(this).apply {
            text = getString(R.string.quiz_challenge_question_number, questionNumber)
            setTextColor(ContextCompat.getColor(context, R.color.startup_text_secondary))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        headerLayout.addView(questionBadge)

        // Category
        val categoryText = TextView(this).apply {
            text = " • ${question.category}"
            setTextColor(ContextCompat.getColor(context, R.color.startup_text_secondary))
            textSize = 12f
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = params
        }
        headerLayout.addView(categoryText)

        // Score badge
        val (scoreText, scoreColor) = when (answered.answerType) {
            AnswerType.CORRECT -> Pair(
                getString(R.string.quiz_challenge_points_plus),
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            )
            AnswerType.WRONG_ORIGINAL -> Pair(
                getString(R.string.quiz_challenge_points_zero),
                ContextCompat.getColor(this, R.color.startup_text_secondary)
            )
            AnswerType.WRONG_TROLL -> Pair(
                getString(R.string.quiz_challenge_points_minus),
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            )
        }
        val scoreBadge = TextView(this).apply {
            text = scoreText
            setTextColor(scoreColor)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        headerLayout.addView(scoreBadge)

        cardLayout.addView(headerLayout)

        // Question text
        val questionTextView = TextView(this).apply {
            text = question.getQuestion(currentLang)
            setTextColor(ContextCompat.getColor(context, R.color.startup_text_primary))
            textSize = 14f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 8
            layoutParams = params
        }
        cardLayout.addView(questionTextView)

        // User's answer
        val userAnswerView = TextView(this).apply {
            text = getString(R.string.quiz_your_answer, answered.userAnswerText)
            val answerColor = when (answered.answerType) {
                AnswerType.CORRECT -> ContextCompat.getColor(context, android.R.color.holo_green_dark)
                AnswerType.WRONG_ORIGINAL -> ContextCompat.getColor(context, android.R.color.holo_orange_dark)
                AnswerType.WRONG_TROLL -> ContextCompat.getColor(context, android.R.color.holo_red_dark)
            }
            setTextColor(answerColor)
            textSize = 13f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 8
            layoutParams = params
        }
        cardLayout.addView(userAnswerView)

        // Correct answer (only if user was wrong)
        if (!answered.isCorrect) {
            val correctAnswerView = TextView(this).apply {
                text = getString(R.string.quiz_correct_answer, answered.correctAnswerText)
                setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                textSize = 13f
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = 4
                layoutParams = params
            }
            cardLayout.addView(correctAnswerView)
        }

        // Answer type indicator for wrong answers
        if (!answered.isCorrect) {
            val typeIndicator = TextView(this).apply {
                text = when (answered.answerType) {
                    AnswerType.WRONG_ORIGINAL -> getString(R.string.quiz_challenge_answer_wrong)
                    AnswerType.WRONG_TROLL -> getString(R.string.quiz_challenge_answer_troll)
                    else -> ""
                }
                setTextColor(
                    if (answered.answerType == AnswerType.WRONG_TROLL)
                        ContextCompat.getColor(context, android.R.color.holo_red_dark)
                    else
                        ContextCompat.getColor(context, R.color.startup_text_secondary)
                )
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.ITALIC)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = 4
                layoutParams = params
            }
            cardLayout.addView(typeIndicator)
        }

        // Search button
        cardLayout.addView(makeSearchButton(question))

        challengeReviewContainer.addView(cardLayout)
    }

    private fun makeSearchButton(question: Question): MaterialButton {
        return MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.quiz_search_google)
            textSize = 12f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 12
            layoutParams = params
            setOnClickListener { openQuizSearch(question) }
        }
    }

    private fun openQuizSearch(question: Question) {
        val questionText = question.getQuestion(currentLang)
        val choices = question.getChoices(currentLang)
        val correctAnswer = choices.getOrNull(question.getCorrectIndex()) ?: ""
        val answerLabel = getString(R.string.quiz_correct_answer, correctAnswer)
        startActivity(Intent(this, QuizWebViewActivity::class.java).apply {
            putExtra(QuizWebViewActivity.EXTRA_QUESTION, questionText)
            putExtra(QuizWebViewActivity.EXTRA_ANSWER, answerLabel)
        })
    }

}
