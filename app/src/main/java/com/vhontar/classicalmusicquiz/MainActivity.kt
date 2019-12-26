package com.vhontar.classicalmusicquiz

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.vhontar.classicalmusicquiz.databinding.ActivityMainBinding
import com.vhontar.classicalmusicquiz.utils.QuizUtils
import com.vhontar.classicalmusicquiz.utils.Sample

class MainActivity : AppCompatActivity() {

    private lateinit var viewDataBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewDataBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        viewDataBinding.lifecycleOwner = this
        viewDataBinding.highScore = QuizUtils.getHighScore(this)
        viewDataBinding.maxScore = Sample.getAllSampleIDs(this).size - 1
        viewDataBinding.isGameFinished = intent.hasExtra(GAME_FINISHED)
        viewDataBinding.yourScore = QuizUtils.getCurrentScore(this)

        viewDataBinding.btnNewGame.setOnClickListener {
            startActivity(Intent(this, QuizActivity::class.java))
        }
    }

    companion object {
        private const val GAME_FINISHED = "game_finished"
    }
}
