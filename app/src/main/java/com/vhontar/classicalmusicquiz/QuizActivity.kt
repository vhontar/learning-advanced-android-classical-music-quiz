package com.vhontar.classicalmusicquiz

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.databinding.DataBindingUtil
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
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

class QuizActivity : AppCompatActivity(), View.OnClickListener, Player.EventListener {

    private lateinit var viewDataBinding: ActivityQuizBinding
    private lateinit var remainingSampleIDs: ArrayList<Int>
    private lateinit var buttonIDs: IntArray
    private lateinit var exoPlayer: SimpleExoPlayer
    private lateinit var playBackStateBuilder: PlaybackStateCompat.Builder
    private lateinit var notificationManager: NotificationManager

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

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        initializeMediaSession()

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

        // If there is only one answer left, end the game.
        if (questionSampleIDs.size < 2) {
            endGame(this)
            finish()
        }
        // Initialize the buttons with the composers names.
        buttons = initializeButtons(questionSampleIDs)

        try {
            val sampleObj = Sample.getSampleByID(this, answerSampleID)
            initializeExoPlayer(Uri.parse(sampleObj!!.uri))
        } catch (e: Throwable) {
            Toast.makeText(this, "Media uri is not existed.", Toast.LENGTH_LONG).show()
        }
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

    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, TAG)
        mediaSession.setFlags(
            (MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        )
        // Do not let MediaButtons restart the player when the app is not visible.
        mediaSession.setMediaButtonReceiver(null)
        // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player.
        playBackStateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                (PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE)
            )

        mediaSession.setPlaybackState(playBackStateBuilder.build())
        mediaSession.setCallback(MyCallbackSessions())
        mediaSession.isActive = true
    }

    private fun initializeExoPlayer(mediaUri: Uri) {
        exoPlayer = SimpleExoPlayer.Builder(this).build()
        exoPlayer.addListener(this)

        viewDataBinding.pvComposerMusic.player = exoPlayer

        val dataSourceFactory = DefaultDataSourceFactory(this,
            Util.getUserAgent(this, "ClassicalMusicQuiz")
        )
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaUri)

        exoPlayer.prepare(mediaSource)
        exoPlayer.playWhenReady = true
    }

    private fun releaseExoPlayer() {
        exoPlayer.stop()
        exoPlayer.release()
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        if (playbackState == Player.STATE_READY && playWhenReady) {
            playBackStateBuilder.setState(
                PlaybackStateCompat.STATE_PLAYING,
                exoPlayer.currentPosition,
                1f
            )
        } else if (playbackState == Player.STATE_READY) {
            playBackStateBuilder.setState(
                PlaybackStateCompat.STATE_PAUSED,
                exoPlayer.currentPosition,
                1f
            )
        }
        mediaSession.setPlaybackState(playBackStateBuilder.build())
        showMediaNotification(playBackStateBuilder.build())
    }

    override fun onStop() {
        super.onStop()
        exoPlayer.stop()
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
        viewDataBinding.pvComposerMusic.defaultArtwork =
            Sample.getComposerArtBySampleID(this, answerSampleID).toDrawable(resources)
        // Remove the answer sample from the list of all samples, so it doesn't get asked again.
        remainingSampleIDs.remove(Integer.valueOf(answerSampleID))
        // Wait some time so the user can see the correct answer, then go to the next question.
        val handler = Handler()
        handler.postDelayed({
            exoPlayer.stop()
            val nextQuestionIntent = Intent(this, QuizActivity::class.java)
            nextQuestionIntent.putExtra(
                REMAINING_SONGS_KEY,
                remainingSampleIDs
            )
            finish()
            startActivity(nextQuestionIntent)
        }, CORRECT_ANSWER_DELAY_MILLIS.toLong())
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseExoPlayer()
        mediaSession.isActive = false
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

    private fun showMediaNotification(playBackState: PlaybackStateCompat) {
        val builder = NotificationCompat.Builder(this, "testChannel")

        val pausePlayIcon: Int
        val pausePlayString: String
        if (playBackState.state == PlaybackState.STATE_PLAYING) {
            pausePlayIcon = R.drawable.exo_icon_pause
            pausePlayString = getString(R.string.pause)
        } else {
            pausePlayIcon = R.drawable.exo_icon_play
            pausePlayString = getString(R.string.play)
        }

        val playPauseAction = NotificationCompat.Action(pausePlayIcon, pausePlayString,
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE))

        val previous = NotificationCompat.Action(R.drawable.exo_icon_previous, getString(R.string.previous),
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))

        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, QuizActivity::class.java),
            0
        )
        builder.setContentTitle(getString(R.string.guess))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(contentPendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(previous)
            .addAction(playPauseAction)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1)
            )


        notificationManager.notify(0, builder.build())
    }

    inner class MyCallbackSessions: MediaSessionCompat.Callback() {
        override fun onPlay() {
            exoPlayer.playWhenReady = true
        }

        override fun onPause() {
            exoPlayer.playWhenReady = false
        }

        override fun onSkipToPrevious() {
            exoPlayer.seekTo(0)
        }
    }

    companion object {
        private const val TAG = "QuizActivity"

        private const val CORRECT_ANSWER_DELAY_MILLIS = 1000
        private const val REMAINING_SONGS_KEY = "remaining_songs"

        private lateinit var mediaSession: MediaSessionCompat

        class MediaReceiver : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                MediaButtonReceiver.handleIntent(mediaSession, p1)
            }
        }
    }
}