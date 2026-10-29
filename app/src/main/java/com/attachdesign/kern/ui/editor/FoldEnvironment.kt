package com.attachdesign.kern.ui.editor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class FoldGeometry(
    val left: Int, val top: Int, val right: Int, val bottom: Int,
    val isVertical: Boolean, val isSeparating: Boolean
)

/** Injectable platform boundary; instrumentation uses the same recognition path as real sensors. */
interface FoldEnvironment {
    val sensorAvailable: Boolean
    val geometry: Flow<FoldGeometry?>
    val angles: Flow<Float>
    fun monotonicTimeMillis(): Long = SystemClock.elapsedRealtime()
}

val LocalFoldEnvironment = compositionLocalOf<FoldEnvironment?> { null }

@Composable
fun rememberFoldEnvironment(): FoldEnvironment {
    val supplied = LocalFoldEnvironment.current
    val context = LocalContext.current
    return remember(supplied, context) { supplied ?: AndroidFoldEnvironment(context) }
}

private class AndroidFoldEnvironment(context: Context) : FoldEnvironment {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = if (Build.VERSION.SDK_INT >= 30) {
        runCatching { manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) }.getOrNull()
    } else null
    override val sensorAvailable = sensor != null
    private val activity = context.activity()
    override val geometry: Flow<FoldGeometry?> = activity?.let { owner ->
        WindowInfoTracker.getOrCreate(context).windowLayoutInfo(owner).map { info ->
            info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()?.let { fold ->
                FoldGeometry(fold.bounds.left, fold.bounds.top, fold.bounds.right, fold.bounds.bottom,
                    fold.orientation == FoldingFeature.Orientation.VERTICAL, fold.isSeparating)
            }
        }
    } ?: flowOf(null)

    override val angles: Flow<Float> = callbackFlow {
        if (sensor == null) { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_HINGE_ANGLE) {
                    event.values.firstOrNull()?.let { trySend(it) }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (!runCatching { manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL) }.getOrDefault(false)) close()
        awaitClose { manager.unregisterListener(listener) }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun rememberFoldGestureProgress(
    environment: FoldEnvironment,
    enabled: Boolean,
    eligible: Boolean,
    resetKey: Any? = null,
    inputSafe: () -> Boolean,
    onToggle: () -> Unit
): State<Float> {
    val owner = LocalLifecycleOwner.current
    val currentSafe = rememberUpdatedState(inputSafe)
    val currentToggle = rememberUpdatedState(onToggle)
    val progress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(owner, environment, enabled, eligible, resetKey) {
        progress.floatValue = 0f
        if (!enabled || !eligible || !environment.sensorAvailable) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val recognizer = FoldGestureRecognizer()
            try {
                coroutineScope {
                    var latestAngle = 0f
                    var pendingToggle: Job? = null
                    environment.angles.collect { angle ->
                        latestAngle = angle
                        val now = environment.monotonicTimeMillis()
                        val recognized = recognizer.onAngle(angle, now)
                        progress.floatValue = recognizer.bendProgress
                        if (!angle.isFinite() || angle !in FoldGestureRecognizer.FLAT_ANGLE..180f) {
                            pendingToggle?.cancel()
                        }
                        if (recognized) {
                            pendingToggle?.cancel()
                            // Only poll after recognition if native input temporarily blocks
                            // the action. Never resample or conflate the recognition stream.
                            pendingToggle = launch(start = CoroutineStart.UNDISPATCHED) {
                                val deadline = now + 500
                                while (isActive && environment.monotonicTimeMillis() <= deadline &&
                                    latestAngle >= FoldGestureRecognizer.FLAT_ANGLE) {
                                    if (currentSafe.value()) {
                                        currentToggle.value()
                                        return@launch
                                    }
                                    delay(50)
                                }
                            }
                        }
                    }
                }
            } finally { progress.floatValue = 0f }
        }
    }
    return progress
}
