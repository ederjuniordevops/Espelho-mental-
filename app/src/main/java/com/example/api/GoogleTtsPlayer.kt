package com.example.api

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

object GoogleTtsPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null

    @Synchronized
    fun playBase64Audio(context: Context, base64Audio: String, mimeType: String? = null, onFinished: () -> Unit = {}) {
        stop()
        try {
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            
            val suffix = when {
                mimeType?.contains("aac", ignoreCase = true) == true -> ".mp3" // Use .mp3 as suffix since many Android extractors fail on raw .aac extension !
                mimeType?.contains("wav", ignoreCase = true) == true -> ".wav"
                mimeType?.contains("ogg", ignoreCase = true) == true -> ".ogg"
                else -> ".mp3"
            }
            
            tempFile = File.createTempFile("tts_temp_", suffix, context.cacheDir).apply {
                deleteOnExit()
            }
            FileOutputStream(tempFile).use { fos ->
                fos.write(audioBytes)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile!!.absolutePath)
                prepare()
                setOnCompletionListener {
                    onFinished()
                    stop()
                }
                setOnErrorListener { _, what, extra ->
                    android.util.Log.e("GoogleTtsPlayer", "MediaPlayer error: what=$what, extra=$extra")
                    onFinished()
                    stop()
                    true
                }
                start()
            }
        } catch (e: Exception) {
            android.util.Log.e("GoogleTtsPlayer", "Failed to play base64 audio: ${e.message}")
            e.printStackTrace()
            onFinished()
        }
    }

    @Synchronized
    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
        }

        try {
            tempFile?.let {
                if (it.exists()) {
                    it.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            tempFile = null
        }
    }
}
