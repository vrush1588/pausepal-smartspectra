package com.app.pausapal.presage

import android.util.Log
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.app.pausapal.BuildConfig
import com.presagetech.smartspectra.CameraSelection
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.SmartSpectraError
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.ValidationCode
import com.presagetech.smartspectra.ValidationStatus
import com.presagetech.smartspectra.proto.MetricTypesProto.MetricType
import com.presagetech.smartspectra.proto.MetricsProto

/*
 * ============================================================================================
 *  PRESAGE SMARTSPECTRA INTEGRATION — every SmartSpectra SDK call in Pause Pal lives in this file.
 * ============================================================================================
 *
 *  SDK: com.presagetech:smartspectra:3.4.0 (API names checked against the 3.4.0 AAR).
 *
 *  Live signals read (see onMetrics()):
 *    - metrics.cardio.pulseRateList      -> pulse, bpm (SDK ~12 s average)       -> Listener.onPulse
 *    - metrics.cardio.hrvList[].rmssd    -> HRV RMSSD, ms (needs ~60 s)           -> Listener.onHrv
 *    - metrics.breathing.rateList        -> breathing rate, breaths/min           -> Listener.onBreathing
 *    - metrics.breathing.upperTraceList  -> chest motion trace                    -> Listener.onTraces
 *    - metrics.breathing.lowerTraceList  -> belly motion trace                    -> Listener.onTraces
 *    - metrics.face.expressionList       -> top expression (ONLY if user opted in) -> Listener.onExpression
 *  Status signals: sdk.processingStatus, sdk.validationStatus, sdk.error (see bind()).
 *
 *  Privacy rules enforced here:
 *    - imageOutputEnabled = false (the SDK never hands camera bitmaps to the app).
 *    - enableTelemetry is forced OFF at app launch and only set to true if the user opted in.
 *    - Nothing is written to disk and NO vital values are logged.
 *    - start() is only called by MainActivity after consent + "Start session".
 */
class PresageSession(private val listener: Listener) {

    interface Listener {
        fun onStatus(status: Status)
        /** @param code SDK validation code name, or null when OK. @param hint SDK hint (may be blank). */
        fun onValidation(code: String?, hint: String)
        fun onError(message: String)
        fun onPulse(bpm: Float)
        fun onBreathing(perMinute: Float)
        fun onHrv(rmssdMs: Double)
        fun onExpression(label: String)
        fun onTraces(chest: List<Float>, belly: List<Float>)
    }

    enum class Status { Idle, Starting, Measuring, Stopping, Error }

    /** True only between start() and stop(); callbacks outside this window are ignored. */
    var running = false
        private set

    // "Last valid value" bookkeeping: only accept readings with a newer timestamp.
    private var lastPulseTs = Long.MIN_VALUE
    private var lastBreathTs = Long.MIN_VALUE
    private var lastHrvTs = Long.MIN_VALUE
    private var lastExprTs = Long.MIN_VALUE
    private var lastUpperTs = Long.MIN_VALUE
    private var lastLowerTs = Long.MIN_VALUE

    // Local trace buffers (drawn by WaveChart). Memory only.
    private val chest = ArrayDeque<Float>()
    private val belly = ArrayDeque<Float>()

    private fun sdkOrNull(): SmartSpectraSdk? =
        runCatching { SmartSpectraSdk.shared }.getOrNull()

    /**
     * Called once from MainActivity.onCreate. The SDK auto-initialises via androidx.startup and its
     * config defaults telemetry to ON, so we switch it OFF before any session can start.
     */
    fun applyPrivacyDefaults() {
        sdkOrNull()?.config?.apply {
            enableTelemetry = false
            imageOutputEnabled = false
        }
    }

    /** Observe the SDK's LiveData for the lifetime of the Activity. */
    fun bind(owner: LifecycleOwner) {
        val sdk = sdkOrNull() ?: return
        sdk.processingStatus.observe(owner) { s: ProcessingStatus? ->
            if (!running || s == null) return@observe
            listener.onStatus(
                when (s) {
                    ProcessingStatus.IDLE, ProcessingStatus.STARTING -> Status.Starting
                    ProcessingStatus.RUNNING -> Status.Measuring
                    ProcessingStatus.STOPPING -> Status.Stopping
                    ProcessingStatus.ERROR -> Status.Error
                }
            )
        }
        sdk.validationStatus.observe(owner) { v: ValidationStatus? ->
            if (!running) return@observe
            if (v == null || v.code == ValidationCode.OK) listener.onValidation(null, "")
            else listener.onValidation(v.code.name, v.hint)
        }
        sdk.error.observe(owner) { e: SmartSpectraError? ->
            if (!running || e == null) return@observe
            // Diagnostics only: error code + retryable flag, never any vital values.
            Log.w(TAG, "SDK error code=${e.code} retryable=${e.retryable} message=${e.message}")
            listener.onError("${e.message.ifBlank { e.code.name }} (${e.code.name})")
            listener.onStatus(Status.Error)
        }
        sdk.metrics.observe(owner) { m: MetricsProto.Metrics? ->
            if (running && m != null) onMetrics(m)
        }
    }

    /**
     * Start the front camera + on-device processing.
     * @param surfaceProvider PreviewView.surfaceProvider (the view stays attached even when hidden)
     * @param expressions add MetricType.EXPRESSIONS (user opt-in only)
     * @param telemetry SDK aggregate diagnostics (user opt-in only; default false)
     */
    suspend fun start(surfaceProvider: Preview.SurfaceProvider, expressions: Boolean, telemetry: Boolean) {
        val apiKey = BuildConfig.PRESAGE_API_KEY
        if (apiKey.isBlank()) {
            listener.onError("No API key. Add PRESAGE_API_KEY to local.properties and rebuild.")
            listener.onStatus(Status.Error)
            return
        }
        val sdk = sdkOrNull()
        if (sdk == null) {
            listener.onError("SmartSpectra SDK is not available on this device.")
            listener.onStatus(Status.Error)
            return
        }
        resetBuffers()
        running = true
        listener.onStatus(Status.Starting)

        sdk.config.apply {
            this.apiKey = apiKey
            imageOutputEnabled = false
            enableTelemetry = telemetry
            previewSurfaceProvider = surfaceProvider
            requestedMetrics = SmartSpectraConfig.breathingMetrics + SmartSpectraConfig.cardioMetrics +
                (if (expressions) listOf(MetricType.EXPRESSIONS) else emptyList())
        }
        sdk.useCamera(CameraSelection.Front)
        try {
            sdk.start()
            // If the user stopped / left the app while start() was still suspended, make sure the
            // camera does not stay on.
            if (!running) runCatching { sdk.stop() }
        } catch (t: Throwable) {
            Log.w(TAG, "sdk.start() threw", t)
            if (running) {
                listener.onError(t.message ?: "Could not start the camera session.")
                listener.onStatus(Status.Error)
            }
        }
    }

    /** Stop the camera and processing. Safe to call more than once. */
    suspend fun stop() {
        if (!running) return
        running = false
        val sdk = sdkOrNull() ?: return
        runCatching { sdk.stop() }
        sdk.config.previewSurfaceProvider = null
        resetBuffers()
    }

    // ---------------------------------------------------------------------------------------
    //  Metrics parsing. Rate lists may be empty between updates: keep the last valid value
    //  (in the ViewModel), only report a reading with a newer timestamp, never report null.
    // ---------------------------------------------------------------------------------------
    private fun onMetrics(m: MetricsProto.Metrics) {
        if (m.hasCardio()) {
            val cardio = m.cardio
            cardio.pulseRateList.newest(lastPulseTs, { it.timestamp }) { it.value > 0f }?.let {
                lastPulseTs = it.timestamp
                listener.onPulse(it.value)
            }
            cardio.hrvList.newest(lastHrvTs, { it.timestamp }) { it.rmssd > 0.0 }?.let {
                lastHrvTs = it.timestamp
                listener.onHrv(it.rmssd)
            }
        }

        if (m.hasBreathing()) {
            val breathing = m.breathing
            breathing.rateList.newest(lastBreathTs, { it.timestamp }) { it.value > 0f }?.let {
                lastBreathTs = it.timestamp
                listener.onBreathing(it.value)
            }
            var traceChanged = false
            for (s in breathing.upperTraceList) {
                if (s.timestamp > lastUpperTs) {
                    lastUpperTs = s.timestamp; chest.push(s.value); traceChanged = true
                }
            }
            for (s in breathing.lowerTraceList) {
                if (s.timestamp > lastLowerTs) {
                    lastLowerTs = s.timestamp; belly.push(s.value); traceChanged = true
                }
            }
            if (traceChanged) listener.onTraces(chest.toList(), belly.toList())
        }

        if (m.hasFace()) {
            m.face.expressionList.newest(lastExprTs, { it.timestamp }) { it.scoresCount > 0 }?.let { e ->
                lastExprTs = e.timestamp
                val top = e.scoresList.maxByOrNull { it.confidence }?.type
                if (top != null &&
                    top != MetricsProto.ExpressionType.UNSPECIFIED &&
                    top != MetricsProto.ExpressionType.UNRECOGNIZED
                ) {
                    listener.onExpression(top.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
    }

    private fun ArrayDeque<Float>.push(v: Float) {
        addLast(v)
        while (size > TRACE_CAPACITY) removeFirst()
    }

    private fun resetBuffers() {
        lastPulseTs = Long.MIN_VALUE; lastBreathTs = Long.MIN_VALUE; lastHrvTs = Long.MIN_VALUE
        lastExprTs = Long.MIN_VALUE; lastUpperTs = Long.MIN_VALUE; lastLowerTs = Long.MIN_VALUE
        chest.clear(); belly.clear()
    }


    private companion object {
        const val TAG = "PausePalSdk"
        const val TRACE_CAPACITY = 300

        /** Newest element with timestamp > [after] that passes [valid], or null. */
        inline fun <T> List<T>.newest(after: Long, ts: (T) -> Long, valid: (T) -> Boolean): T? {
            var best: T? = null
            var bestTs = after
            for (item in this) {
                val t = ts(item)
                if (t > bestTs && valid(item)) { best = item; bestTs = t }
            }
            return best
        }
    }
}
