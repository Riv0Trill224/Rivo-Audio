package com.riv0trill.rivoaudio

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_AUDIO = 1001
    }

    private lateinit var player: ExoPlayer

    private lateinit var albumArt: ImageView
    private lateinit var songTitle: TextView
    private lateinit var songArtist: TextView
    private lateinit var selectAudioButton: Button
    private lateinit var playPauseButton: Button
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var deviceProfileText: TextView
    private lateinit var outputText: TextView

    private val handler = Handler(Looper.getMainLooper())

    private var userSeeking = false

    private val progressUpdater = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        bindViews()
        setupPlayer()
        setupDeviceProfile()
        setupControls()

        handler.post(progressUpdater)
    }

    private fun bindViews() {
        albumArt = findViewById(R.id.albumArt)
        songTitle = findViewById(R.id.songTitle)
        songArtist = findViewById(R.id.songArtist)
        selectAudioButton = findViewById(R.id.selectAudioButton)
        playPauseButton = findViewById(R.id.playPauseButton)
        seekBar = findViewById(R.id.seekBar)
        currentTime = findViewById(R.id.currentTime)
        totalTime = findViewById(R.id.totalTime)
        deviceProfileText = findViewById(R.id.deviceProfileText)
        outputText = findViewById(R.id.outputText)
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()

        player.addListener(
            object : Player.Listener {

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseButton()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updatePlayPauseButton()
                    updateProgress()
                }
            }
        )
    }

    private fun setupDeviceProfile() {
        val profile = DeviceProfile.detect()

        deviceProfileText.text =
            when (profile) {
                DeviceProfile.Type.RGDS ->
                    "Dispositivo: RG DS"

                DeviceProfile.Type.STANDARD_ANDROID ->
                    "Dispositivo: Android estándar"
            }

        outputText.text = "Salida: Sistema Android"
    }

    private fun setupControls() {

        selectAudioButton.setOnClickListener {
            openAudioPicker()
        }

        playPauseButton.setOnClickListener {

            if (player.mediaItemCount == 0) {
                openAudioPicker()
                return@setOnClickListener
            }

            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        currentTime.text =
                            formatTime(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    userSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {

                    userSeeking = false

                    seekBar?.let {
                        player.seekTo(it.progress.toLong())
                    }
                }
            }
        )
    }

    private fun openAudioPicker() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {

            addCategory(Intent.CATEGORY_OPENABLE)

            type = "audio/*"

            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }

        startActivityForResult(intent, REQUEST_AUDIO)
    }

    @Deprecated("Deprecated in Android API, retained for broad device compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode == REQUEST_AUDIO &&
            resultCode == RESULT_OK
        ) {

            val uri = data?.data ?: return

            try {

                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

            } catch (_: Exception) {
                // Algunos proveedores no permiten permisos persistentes.
            }

            loadAudio(uri)
        }
    }

    private fun loadAudio(uri: Uri) {

        readMetadata(uri)

        val mediaItem =
            MediaItem.fromUri(uri)

        player.setMediaItem(mediaItem)

        player.prepare()

        player.play()

        playPauseButton.visibility = View.VISIBLE
    }

    private fun readMetadata(uri: Uri) {

        var fallbackName =
            getFileName(uri) ?: "Audio"

        var title: String? = null
        var artist: String? = null

        val retriever =
            MediaMetadataRetriever()

        try {

            retriever.setDataSource(
                this,
                uri
            )

            title =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_TITLE
                )

            artist =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ARTIST
                )

            val artwork =
                retriever.embeddedPicture

            if (artwork != null) {

                val bitmap =
                    BitmapFactory.decodeByteArray(
                        artwork,
                        0,
                        artwork.size
                    )

                albumArt.setImageBitmap(bitmap)

                albumArt.scaleType =
                    ImageView.ScaleType.CENTER_CROP

            } else {

                albumArt.setImageResource(
                    android.R.drawable.ic_media_play
                )

                albumArt.scaleType =
                    ImageView.ScaleType.CENTER
            }

        } catch (_: Exception) {

            albumArt.setImageResource(
                android.R.drawable.ic_media_play
            )

        } finally {

            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        if (title.isNullOrBlank()) {

            fallbackName =
                fallbackName.substringBeforeLast(".")

            title = fallbackName
        }

        if (artist.isNullOrBlank()) {
            artist = "Artista desconocido"
        }

        songTitle.text = title
        songArtist.text = artist
    }

    private fun getFileName(uri: Uri): String? {

        var result: String? = null

        if (uri.scheme == "content") {

            contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )?.use { cursor ->

                val index =
                    cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )

                if (
                    index >= 0 &&
                    cursor.moveToFirst()
                ) {

                    result =
                        cursor.getString(index)
                }
            }
        }

        if (result == null) {
            result =
                uri.path
                    ?.substringAfterLast("/")
        }

        return result
    }

    private fun updatePlayPauseButton() {

        playPauseButton.text =
            if (player.isPlaying) {
                "❚❚"
            } else {
                "▶"
            }
    }

    private fun updateProgress() {

        if (!::player.isInitialized) {
            return
        }

        val duration =
            player.duration

        val position =
            player.currentPosition

        if (
            duration != C.TIME_UNSET &&
            duration > 0
        ) {

            seekBar.max =
                duration
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()

            if (!userSeeking) {

                seekBar.progress =
                    position
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt()
            }

            currentTime.text =
                formatTime(position)

            totalTime.text =
                formatTime(duration)

        } else {

            currentTime.text = "00:00"
            totalTime.text = "00:00"
        }
    }

    private fun formatTime(
        milliseconds: Long
    ): String {

        if (milliseconds < 0) {
            return "00:00"
        }

        val totalSeconds =
            milliseconds / 1000

        val seconds =
            totalSeconds % 60

        val minutes =
            (totalSeconds / 60) % 60

        val hours =
            totalSeconds / 3600

        return if (hours > 0) {

            String.format(
                Locale.getDefault(),
                "%d:%02d:%02d",
                hours,
                minutes,
                seconds
            )

        } else {

            String.format(
                Locale.getDefault(),
                "%02d:%02d",
                minutes,
                seconds
            )
        }
    }

    override fun onDestroy() {

        handler.removeCallbacks(
            progressUpdater
        )

        player.release()

        super.onDestroy()
    }
}