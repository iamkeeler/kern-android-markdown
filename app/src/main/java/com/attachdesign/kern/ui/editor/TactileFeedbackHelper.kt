package com.attachdesign.kern.ui.editor

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.attachdesign.kern.R

interface FoldFeedback {
    fun triggerFoldFeedback(hapticsEnabled: Boolean, soundEnabled: Boolean)
}

/** A test boundary for observing committed gestures without playing real feedback. */
val LocalFoldFeedback = compositionLocalOf<FoldFeedback?> { null }

/**
 * Adapted from StudyCompanion's paper-fold feedback. Native view haptics retain
 * device tuning and touch-feedback preferences without a VIBRATE permission.
 * Audio is preloaded only when opted in, never queued, and never changes volume/focus.
 */
@MainThread
class TactileFeedbackHelper internal constructor(
    context: Context,
    private val view: View,
    private val createSoundPool: () -> SoundPool = {
        SoundPool.Builder().setMaxStreams(1)
            .setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .build()).build()
    }
) : FoldFeedback {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
    private var soundPool: SoundPool? = null
    private var soundId = 0
    private var streamId = 0
    private var soundReady = false
    private var active = false
    private var released = false

    fun prepareSound() {
        if (released || soundPool != null) return
        try {
            val pool = createSoundPool()
            soundPool = pool
            pool.setOnLoadCompleteListener { loadedPool, sampleId, status ->
                if (!released && loadedPool === soundPool && sampleId == soundId) {
                    soundReady = status == 0
                }
            }
            soundId = pool.load(appContext, R.raw.paper_crease, 1)
            if (soundId == 0) unloadSound()
        } catch (_: RuntimeException) {
            unloadSound()
        }
    }

    fun setActive(isActive: Boolean) {
        active = isActive && !released
        if (!active) stopSound()
    }

    override fun triggerFoldFeedback(hapticsEnabled: Boolean, soundEnabled: Boolean) {
        if (!active || released) return
        if (hapticsEnabled) {
            // No ignore-setting flags or direct vibrator calls: Android owns intensity
            // and whether feedback is permitted for this view and device.
            runCatching {
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30)
                    HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)
            }
        }
        if (soundEnabled && soundReady && soundAllowed()) {
            runCatching {
                stopSound()
                streamId = soundPool?.play(soundId, PAPER_VOLUME, PAPER_VOLUME, 1, 0, 1f) ?: 0
            }
        }
    }

    private fun soundAllowed(): Boolean = runCatching {
        audioManager?.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            notificationManager?.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL &&
            Settings.System.getInt(appContext.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1 &&
            audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM) > 0 &&
            !audioManager.isStreamMute(AudioManager.STREAM_SYSTEM)
    }.getOrDefault(false)

    private fun stopSound() {
        if (streamId != 0) runCatching { soundPool?.stop(streamId) }
        streamId = 0
    }

    fun unloadSound() {
        soundReady = false
        stopSound()
        soundId = 0
        val pool = soundPool
        soundPool = null
        runCatching { pool?.setOnLoadCompleteListener(null) }
        runCatching { pool?.release() }
    }

    fun release() {
        if (released) return
        released = true
        active = false
        unloadSound()
    }

    private companion object { const val PAPER_VOLUME = 0.35f }
}

@Composable
fun rememberTactileFeedbackHelper(soundEnabled: Boolean): FoldFeedback {
    val supplied = LocalFoldFeedback.current
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val helper = remember(supplied, context, view, owner) {
        if (supplied == null) TactileFeedbackHelper(context, view) else null
    }
    DisposableEffect(helper, owner) {
        val observer = LifecycleEventObserver { _, _ ->
            helper?.setActive(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        owner.lifecycle.addObserver(observer)
        helper?.setActive(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            owner.lifecycle.removeObserver(observer)
            helper?.release()
        }
    }
    DisposableEffect(helper, soundEnabled) {
        if (soundEnabled) helper?.prepareSound() else helper?.unloadSound()
        onDispose { helper?.unloadSound() }
    }
    return supplied ?: checkNotNull(helper)
}
