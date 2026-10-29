package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.lembreteinteligente.R

object AlarmSoundPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false

    fun startAlarmSound(context: Context) {
        if (isPlaying) return
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            // Try raw resource alarm_loud.wav first
            val rawUri = Uri.parse("android.resource://" + context.packageName + "/" + R.raw.alarm_loud)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                isLooping = true
                try {
                    setDataSource(context, rawUri)
                    prepare()
                } catch (e: Exception) {
                    val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    reset()
                    setAudioAttributes(audioAttributes)
                    setDataSource(context, fallbackUri)
                    prepare()
                }
                start()
            }
            isPlaying = true
            vibrate(context)
            Log.d("AlarmSoundPlayer", "Alarm sound and vibration started")
        } catch (e: Exception) {
            Log.e("AlarmSoundPlayer", "Error playing alarm sound: ${e.message}")
        }
    }

    fun stopAlarmSound(context: Context) {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            isPlaying = false
            stopVibration(context)
            Log.d("AlarmSoundPlayer", "Alarm sound and vibration stopped")
        } catch (e: Exception) {
            Log.e("AlarmSoundPlayer", "Error stopping alarm: ${e.message}")
        }
    }

    private fun vibrate(context: Context) {
        val pattern = longArrayOf(0, 500, 250, 500, 250, 800)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(pattern, 0)
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmSoundPlayer", "Vibration failed: ${e.message}")
        }
    }

    private fun stopVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.cancel()
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.cancel()
            }
        } catch (e: Exception) {
            Log.e("AlarmSoundPlayer", "Stop vibration failed: ${e.message}")
        }
    }
}
