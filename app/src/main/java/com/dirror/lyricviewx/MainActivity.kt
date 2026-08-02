package com.dirror.lyricviewx

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isUpdatingProgress = false

    private lateinit var lyricViewX: LyricViewX
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlayPause: MaterialButton
    private lateinit var btnLoadAudio: MaterialButton
    private lateinit var btnLoadLrc: MaterialButton
    private lateinit var switchTranslation: SwitchCompat
    private lateinit var enableBlurEffect: SwitchCompat

    private var audioUri: Uri? = null

    // Register file pickers using modern Activity Result API
    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            setupAudio(uri)
        } else {
            Toast.makeText(this, "No audio file selected", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickLrcLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            setupLrc(uri)
        } else {
            Toast.makeText(this, "No LRC file selected", Toast.LENGTH_SHORT).show()
        }
    }

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    val currentPos = player.currentPosition.toLong()
                    seekBar.progress = currentPos.toInt()
                    tvCurrentTime.text = formatTime(currentPos)
                    lyricViewX.updateTime(currentPos)
                }
            }
            if (isUpdatingProgress) {
                handler.postDelayed(this, 100)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Views
        lyricViewX = findViewById(R.id.lyricViewX)
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnLoadAudio = findViewById(R.id.btnLoadAudio)
        btnLoadLrc = findViewById(R.id.btnLoadLrc)
        switchTranslation = findViewById(R.id.switchTranslation)
        enableBlurEffect = findViewById(R.id.enableBlurEffect)

        // Setup switches
        switchTranslation.setOnCheckedChangeListener { _, isChecked ->
            lyricViewX.setIsDrawTranslation(isChecked)
        }
        enableBlurEffect.setOnCheckedChangeListener { _, isChecked ->
            lyricViewX.setIsEnableBlurEffect(isChecked)
        }

        // Configure default visual parameters of LyricViewX
        lyricViewX.setTextGravity(GRAVITY_CENTER)
        lyricViewX.setNormalTextSize(48f)
        lyricViewX.setCurrentTextSize(64f)
        lyricViewX.setTranslateTextScaleValue(0.8f)
        lyricViewX.setHorizontalOffset(0f)
        lyricViewX.setHorizontalOffsetPercent(0.5f)
        lyricViewX.setItemOffsetPercent(0.5f)

        // Load onboarding lyrics instruction
        loadDefaultLyrics()

        // Set up draggable timeline syncing
        lyricViewX.setDraggable(true, object : OnPlayClickListener {
            override fun onPlayClick(time: Long): Boolean {
                mediaPlayer?.let { player ->
                    player.seekTo(time.toInt())
                    seekBar.progress = time.toInt()
                    tvCurrentTime.text = formatTime(time)
                    if (!player.isPlaying) {
                        startPlayback()
                    }
                } ?: run {
                    Toast.makeText(this@MainActivity, "Please load audio first to seek", Toast.LENGTH_SHORT).show()
                }
                return true
            }
        })

        // File pickers setup
        btnLoadAudio.setOnClickListener {
            // Pick audio files
            pickAudioLauncher.launch("audio/*")
        }

        btnLoadLrc.setOnClickListener {
            // Pick any text/LRC document
            pickLrcLauncher.launch("*/*")
        }

        // Play / Pause handling
        btnPlayPause.setOnClickListener {
            togglePlayback()
        }

        // SeekBar changes handling
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    lyricViewX.updateTime(progress.toLong())
                    tvCurrentTime.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun loadDefaultLyrics() {
        val welcomeLrc = """
            [00:00.00]Welcome to LyricViewX!
            [00:02.00]Step 1: Click 'Load Audio' to select an audio file.
            [00:05.00]Step 2: Click 'Load LRC' to select a lyrics file (.lrc).
            [00:08.00]Step 3: Click 'Play' to start listening with synced lyrics!
            [00:12.00]Enjoy your music.
        """.trimIndent()
        lyricViewX.loadLyric(welcomeLrc)
    }

    private fun setupAudio(uri: Uri) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                prepare()
            }

            audioUri = uri
            val duration = mediaPlayer?.duration?.toLong() ?: 0L
            seekBar.max = duration.toInt()
            seekBar.progress = 0
            tvCurrentTime.text = formatTime(0L)
            tvTotalTime.text = formatTime(duration)

            btnPlayPause.text = "Play"
            Toast.makeText(this, "Audio loaded successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load audio: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupLrc(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val lrcContent = reader.readText()
                    if (lrcContent.isNotBlank()) {
                        lyricViewX.loadLyric(lrcContent)
                        Toast.makeText(this, "Lyrics loaded successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "LRC file is empty", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load LRC file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun togglePlayback() {
        val player = mediaPlayer
        if (player == null) {
            Toast.makeText(this, "Please load an audio file first", Toast.LENGTH_SHORT).show()
            return
        }

        if (player.isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        mediaPlayer?.let { player ->
            player.start()
            btnPlayPause.text = "Pause"
            isUpdatingProgress = true
            handler.post(updateProgressRunnable)
        }
    }

    private fun pausePlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            }
            btnPlayPause.text = "Play"
            isUpdatingProgress = false
            handler.removeCallbacks(updateProgressRunnable)
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onStop() {
        super.onStop()
        pausePlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        isUpdatingProgress = false
        handler.removeCallbacks(updateProgressRunnable)
    }
}