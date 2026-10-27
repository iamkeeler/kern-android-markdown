package com.attachdesign.kern.ui.editor

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.SoundPool
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.attachdesign.kern.R
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TactileFeedbackHelperTest {
    private lateinit var context: Context
    private lateinit var audio: AudioManager
    private lateinit var notifications: NotificationManager
    private lateinit var view: View
    private lateinit var pool: SoundPool
    private lateinit var helper: TactileFeedbackHelper
    private val loaded = slot<SoundPool.OnLoadCompleteListener>()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        audio = context.getSystemService(AudioManager::class.java)
        notifications = context.getSystemService(NotificationManager::class.java)
        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        audio.setStreamVolume(AudioManager.STREAM_SYSTEM, 3, 0)
        Settings.System.putInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1)
        view = mockk(relaxed = true)
        pool = mockk(relaxed = true)
        every { pool.setOnLoadCompleteListener(capture(loaded)) } just Runs
        every { pool.load(any<Context>(), R.raw.paper_crease, 1) } returns 7
        every { pool.play(7, any(), any(), any(), any(), any()) } returns 11
        helper = TactileFeedbackHelper(context, view) { pool }
        helper.setActive(true)
    }

    @After fun cleanup() { helper.release(); unmockkAll() }

    private fun ready() { helper.prepareSound(); loaded.captured.onLoadComplete(pool, 7, 0) }
    private fun noSound() { verify(exactly = 0) { pool.play(any(), any(), any(), any(), any(), any()) } }

    @Test fun `uses native confirmation haptic and quiet non-looping loaded sound`() {
        ready()
        helper.triggerFoldFeedback(true, true)
        verify(exactly = 1) { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
        verify(exactly = 1) { pool.play(7, 0.35f, 0.35f, 1, 0, 1f) }
    }

    @Test @Config(sdk = [28]) fun `older Android uses native clock tick fallback`() {
        helper.triggerFoldFeedback(true, false)
        verify(exactly = 1) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    }

    @Test fun `does not allocate audio until enabled and never queues an unready gesture`() {
        helper.triggerFoldFeedback(true, true)
        verify(exactly = 0) { pool.load(any<Context>(), any(), any()) }
        helper.prepareSound()
        helper.triggerFoldFeedback(false, true)
        noSound()
        loaded.captured.onLoadComplete(pool, 7, 0)
        noSound()
        helper.triggerFoldFeedback(false, true)
        verify(exactly = 1) { pool.play(7, any(), any(), any(), any(), any()) }
    }

    @Test fun `haptics and sound can be disabled independently`() {
        ready()
        helper.triggerFoldFeedback(false, true)
        verify(exactly = 0) { view.performHapticFeedback(any()) }
        clearMocks(pool, answers = false)
        helper.triggerFoldFeedback(true, false)
        noSound()
        verify(exactly = 1) { view.performHapticFeedback(any()) }
    }

    @Test fun `silent and vibrate ringer modes suppress sound`() {
        ready()
        for (mode in listOf(AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE)) {
            audio.ringerMode = mode
            helper.triggerFoldFeedback(false, true)
        }
        noSound()
    }

    @Test fun `DND suppresses sound even with normal ringer and nonzero volume`() {
        ready()
        for (filter in listOf(NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            NotificationManager.INTERRUPTION_FILTER_ALARMS, NotificationManager.INTERRUPTION_FILTER_NONE,
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN)) {
            notifications.setInterruptionFilter(filter)
            helper.triggerFoldFeedback(false, true)
        }
        noSound()
    }

    @Test fun `disabled touch sounds and a zero or muted system stream suppress sound`() {
        ready()
        Settings.System.putInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 0)
        helper.triggerFoldFeedback(false, true)
        Settings.System.putInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1)
        audio.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
        helper.triggerFoldFeedback(false, true)
        audio.setStreamVolume(AudioManager.STREAM_SYSTEM, 3, 0)
        shadowOf(audio).setIsStreamMute(AudioManager.STREAM_SYSTEM, true)
        helper.triggerFoldFeedback(false, true)
        noSound()
    }

    @Test fun `pausing stops playback and resuming does not replay it`() {
        ready()
        helper.triggerFoldFeedback(false, true)
        clearMocks(pool, view, answers = false)
        helper.setActive(false)
        verify(exactly = 1) { pool.stop(11) }
        helper.triggerFoldFeedback(true, true)
        helper.setActive(true)
        noSound()
        verify(exactly = 0) { view.performHapticFeedback(any()) }
    }

    @Test fun `failed loads and callbacks after release cannot play or resurrect resources`() {
        helper.prepareSound()
        val callback = loaded.captured
        callback.onLoadComplete(pool, 7, 1)
        helper.triggerFoldFeedback(false, true)
        noSound()
        helper.release(); helper.release()
        callback.onLoadComplete(pool, 7, 0)
        helper.setActive(true)
        helper.prepareSound()
        helper.triggerFoldFeedback(true, true)
        noSound()
        verify(exactly = 1) { pool.release() }
        verify(exactly = 0) { view.performHapticFeedback(any()) }
    }

    @Test fun `audio failure never blocks the layout command or haptic`() {
        every { pool.load(any<Context>(), any(), any()) } throws IllegalStateException("Audio unavailable")
        helper.prepareSound()
        helper.triggerFoldFeedback(true, true)
        verify(exactly = 1) { view.performHapticFeedback(any()) }
        noSound()
    }

    @Test fun `stale load completion cannot mark a replacement sound pool ready`() {
        helper.prepareSound()
        val oldPool = pool
        val oldCallback = loaded.captured
        helper.unloadSound()
        pool = mockk(relaxed = true)
        val replacementLoaded = slot<SoundPool.OnLoadCompleteListener>()
        every { pool.setOnLoadCompleteListener(capture(replacementLoaded)) } just Runs
        every { pool.load(any<Context>(), R.raw.paper_crease, 1) } returns 7
        helper.prepareSound()
        oldCallback.onLoadComplete(oldPool, 7, 0)
        helper.triggerFoldFeedback(false, true)
        noSound()
        replacementLoaded.captured.onLoadComplete(pool, 7, 0)
        helper.triggerFoldFeedback(false, true)
        verify(exactly = 1) { pool.play(7, any(), any(), any(), any(), any()) }
    }
}
