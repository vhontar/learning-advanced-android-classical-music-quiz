package com.vhontar.classicalmusicquiz

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.vhontar.classicalmusicquiz.databinding.ActivityQuizBinding
import com.vhontar.classicalmusicquiz.utils.QuizUtils.endGame
import com.vhontar.classicalmusicquiz.utils.QuizUtils.generateQuestion
import com.vhontar.classicalmusicquiz.utils.QuizUtils.getCorrectAnswerID
import com.vhontar.classicalmusicquiz.utils.QuizUtils.getCurrentScore
import com.vhontar.classicalmusicquiz.utils.QuizUtils.getHighScore
import com.vhontar.classicalmusicquiz.utils.QuizUtils.setCurrentScore
import com.vhontar.classicalmusicquiz.utils.QuizUtils.setHighScore
import com.vhontar.classicalmusicquiz.utils.QuizUtils.userCorrect
import com.vhontar.classicalmusicquiz.utils.Sample
import kotlin.collections.ArrayList

class QuizActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var viewDataBinding: ActivityQuizBinding
    private lateinit var remainingSampleIDs: ArrayList<Int>
    private lateinit var buttonIDs: IntArray

    private var currentScore = 0
    private var highScore = 0
    private var questionSampleIDs: ArrayList<Int> = ArrayList()
    private var answerSampleID = 0

    private var buttons: Array<Button?> = arrayOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewDataBinding = DataBindingUtil.setContentView(this, R.layout.activity_quiz)
        viewDataBinding.lifecycleOwner = this

        buttonIDs = intArrayOf(viewDataBinding.buttonA.id,
            viewDataBinding.buttonB.id,
            viewDataBinding.buttonC.id,
            viewDataBinding.buttonD.id
        )

        // Get current and high scores.
        currentScore = getCurrentScore(this)
        highScore = getHighScore(this)

        val isNewGame = !intent.hasExtra(REMAINING_SONGS_KEY)
        // If it's a new game, set the current score to 0 and load all samples.
        remainingSampleIDs = if (isNewGame) {
            setCurrentScore(this, 0)
            Sample.getAllSampleIDs(this)
            // Otherwise, get the remaining songs from the Intent.
        } else {
            intent.getIntegerArrayListExtra(REMAINING_SONGS_KEY) as ArrayList<Int>
        }
        // Generate a question and get the correct answer.
        questionSampleIDs = generateQuestion(remainingSampleIDs)
        answerSampleID = getCorrectAnswerID(questionSampleIDs)
        // Load the image of the composer for the answer into the ImageView.

        viewDataBinding.composerView.setImageBitmap(
            Sample.getComposerArtBySampleID(this, answerSampleID)
        )
        // If there is only one answer left, end the game.
        if (questionSampleIDs.size < 2) {
            endGame(this)
            finish()
        }
        // Initialize the buttons with the composers names.
        buttons = initializeButtons(questionSampleIDs)
    }

    /**
     * Initializes the button to the correct views, and sets the text to the composers names,
     * and set's the OnClick listener to the buttons.
     *
     * @param answerSampleIDs The IDs of the possible answers to the question.
     * @return The Array of initialized buttons.
     */
    private fun initializeButtons(answerSampleIDs: ArrayList<Int>): Array<Button?> {
        val buttons =
            arrayOfNulls<Button>(buttonIDs.size)
        for (i in answerSampleIDs.indices) {
            val currentButton =
                findViewById<View>(buttonIDs[i]) as Button
            val currentSample = Sample.getSampleByID(this, answerSampleIDs[i])
            buttons[i] = currentButton
            currentButton.setOnClickListener(this)
            if (currentSample != null) {
                currentButton.text = currentSample.composer
            }
        }
        return buttons
    }

    /**
     * The OnClick method for all of the answer buttons. The method uses the index of the button
     * in button array to to get the ID of the sample from the array of question IDs. It also
     * toggles the UI to show the correct answer.
     *
     * @param v The button that was clicked.
     */
    override fun onClick(v: View) { // Show the correct answer.
        showCorrectAnswer()
        // Get the button that was pressed.
        val pressedButton = v as Button
        // Get the index of the pressed button
        var userAnswerIndex = -1
        for (i in buttons.indices) {
            if (pressedButton.id == buttonIDs[i]) {
                userAnswerIndex = i
            }
        }
        // Get the ID of the sample that the user selected.
        val userAnswerSampleID = questionSampleIDs[userAnswerIndex]
        // If the user is correct, increase there score and update high score.
        if (userCorrect(answerSampleID, userAnswerSampleID)) {
            currentScore++
            setCurrentScore(this, currentScore)
            if (currentScore > highScore) {
                highScore = currentScore
                setHighScore(this, highScore)
            }
        }
        // Remove the answer sample from the list of all samples, so it doesn't get asked again.
        remainingSampleIDs.remove(Integer.valueOf(answerSampleID))
        // Wait some time so the user can see the correct answer, then go to the next question.
        val handler = Handler()
        handler.postDelayed({
            val nextQuestionIntent = Intent(this, QuizActivity::class.java)
            nextQuestionIntent.putExtra(
                REMAINING_SONGS_KEY,
                remainingSampleIDs
            )
            finish()
            startActivity(nextQuestionIntent)
        }, CORRECT_ANSWER_DELAY_MILLIS.toLong())
    }

    /**
     * Disables the buttons and changes the background colors to show the correct answer.
     */
    private fun showCorrectAnswer() {
        for (i in questionSampleIDs.indices) {
            val buttonSampleID = questionSampleIDs[i]
            buttons[i]!!.isEnabled = false
            if (buttonSampleID == answerSampleID) {
                buttons[i]!!.background.setColorFilter(
                    ContextCompat.getColor(this, android.R.color.holo_green_light),
                    PorterDuff.Mode.MULTIPLY
                )
                buttons[i]!!.setTextColor(Color.WHITE)
            } else {
                buttons[i]!!.background.setColorFilter(
                    ContextCompat.getColor(this, android.R.color.holo_red_light),
                    PorterDuff.Mode.MULTIPLY
                )
                buttons[i]!!.setTextColor(Color.WHITE)
            }
        }
    }

    companion object {
        private const val CORRECT_ANSWER_DELAY_MILLIS = 1000
        private const val REMAINING_SONGS_KEY = "remaining_songs"
    }
}