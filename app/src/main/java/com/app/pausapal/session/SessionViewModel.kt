package com.app.pausapal.session

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.app.pausapal.presage.PresageSession
import com.app.pausapal.ui.components.MascotMood
import com.app.pausapal.ui.theme.ThemeMode

/**
 * All app state, held IN MEMORY ONLY. No files, SharedPreferences, database or backup.
 * A finished session is erased as soon as the user leaves the summary; process death wipes everything.
 */
class SessionViewModel : ViewModel(), PresageSession.Listener {

    val presage = PresageSession(this)

    /** Display preference only (not session data): survives "Start new session", resets on app close. */
    var themeMode by mutableStateOf(ThemeMode.System)

    // ---- navigation / consent ----
    var screen by mutableStateOf(Screen.Consent)
    var consentGiven by mutableStateOf(false)
    var expressionsOptIn by mutableStateOf(false)
    var telemetryOptIn by mutableStateOf(false)
    var permissionMessage by mutableStateOf<String?>(null)

    // ---- session setup ----
    var length by mutableStateOf(SessionLength.Min3)

    // ---- live ----
    var active by mutableStateOf(false)
        private set
    private var startRealtime = 0L
    var elapsedMs by mutableLongStateOf(0L)
        private set
    var status by mutableStateOf(PresageSession.Status.Idle)
        private set
    var validationHint by mutableStateOf<String?>(null)
        private set
    var errorText by mutableStateOf<String?>(null)
        private set
    var pulse by mutableStateOf<Float?>(null)
        private set
    var breathing by mutableStateOf<Float?>(null)
        private set
    var hrv by mutableStateOf<Double?>(null)
        private set
    var expression by mutableStateOf<String?>(null)
        private set
    var chestTrace by mutableStateOf<List<Float>>(emptyList())
        private set
    var bellyTrace by mutableStateOf<List<Float>>(emptyList())
        private set
    var showPreview by mutableStateOf(false)
    var sheet by mutableStateOf<BreakSheet>(BreakSheet.Hidden)
    var breaksTaken by mutableIntStateOf(0)
        private set
    var baseline by mutableStateOf<Double?>(null)
        private set

    /** User tapped "Pause camera": SDK stopped, timer keeps running. */
    var cameraPaused by mutableStateOf(false)
        private set
    private var pausedAtRealtime = 0L

    /** "Gentle vibration" switch on the live screen. Off by default; nothing vibrates unless on. */
    var hapticsOn by mutableStateOf(false)

    /** "Voice guidance" switch on the live screen. Off by default; TTS is created only when on. */
    var voiceOn by mutableStateOf(false)

    /** Shown under the voice switch, e.g. "No offline voice available on this phone". */
    var voiceMessage by mutableStateOf<String?>(null)

    /** True when TTS works but has no offline English voice: shows the "Install voice" button. */
    var voiceNeedsInstall by mutableStateOf(false)

    /** Spoken framing guidance from validation codes (only acted on when voice is on). */
    private var framing = FramingCoach()
    private var lastValidationCode: String? = null

    /** True while breathing-wave samples are arriving (camera on, newest sample < 2 s old). */
    var signalLive by mutableStateOf(false)
        private set
    private var lastTraceRealtime = 0L

    private var coach = BreakCoach(SessionLength.Min3)
    private var pulseSum = 0.0
    private var pulseCount = 0
    private var breathSum = 0.0
    private var breathCount = 0

    // Per-reading samples for the summary's sparklines / range. Memory only, capped.
    private val pulseSamples = ArrayList<Float>()
    private val breathSamples = ArrayList<Float>()
    private val hrvSamples = ArrayList<Float>()

    var summary by mutableStateOf<SessionSummary?>(null)
        private set

    val remainingMs: Long get() = (length.millis - elapsedMs).coerceAtLeast(0)

    /**
     * The mascot's own mood, from breathing vs the user's OWN baseline only. It describes the
     * mascot, never the person: neutral before a baseline exists, lively above it, calm otherwise.
     */
    val mascotMood: MascotMood
        get() {
            val base = baseline ?: return MascotMood.Neutral
            if (cameraPaused) return MascotMood.Neutral
            val rate = breathing ?: return MascotMood.Calm
            return if (rate > base * 1.15 && rate >= base + 2.0) MascotMood.Lively else MascotMood.Calm
        }

    val learningHint: String
        get() = when {
            cameraPaused -> "Camera off: readings paused"
            baseline != null -> "Baseline set: ~${baseline!!.toInt()} /min"
            coach.warmingUp(elapsedMs) -> "Warming up…"
            else -> "Learning your normal breathing…"
        }

    fun beginSession() {
        clearLive()
        summary = null
        coach = BreakCoach(length)
        framing = FramingCoach(startMs = 0L)
        startRealtime = SystemClock.elapsedRealtime()
        status = PresageSession.Status.Starting
        active = true
        screen = Screen.Live
    }

    /** Called every second by the UI ticker. Returns true when the countdown has finished. */
    fun tick(): Boolean {
        if (!active) return false
        val now = SystemClock.elapsedRealtime()
        elapsedMs = now - startRealtime
        signalLive = !cameraPaused && lastTraceRealtime > 0 && now - lastTraceRealtime < SIGNAL_STALE_MS
        (sheet as? BreakSheet.Breathing)?.let {
            if (it.secondsLeft > 0) {
                val left = it.secondsLeft - 1
                // At the end of the 60 s reset, record the latest breathing rate as "after".
                sheet = it.copy(secondsLeft = left, after = if (left == 0) breathing else it.after)
            }
        }
        return remainingMs == 0L
    }

    /** "Pause camera". The caller stops the SDK. The countdown keeps running. */
    fun pauseCamera() {
        if (!active || cameraPaused) return
        cameraPaused = true
        pausedAtRealtime = SystemClock.elapsedRealtime()
        coach.onCameraGap()
        signalLive = false
        status = PresageSession.Status.Idle
        validationHint = null; errorText = null
    }

    /**
     * "Resume camera". The caller restarts the SDK. The baseline is discarded (and re-learned from
     * now) only if the camera was off for more than [BASELINE_KEEP_MS].
     */
    fun resumeCamera() {
        if (!active || !cameraPaused) return
        val now = SystemClock.elapsedRealtime()
        if (now - pausedAtRealtime > BASELINE_KEEP_MS) {
            coach.resetBaseline(now - startRealtime)
            baseline = null
        }
        cameraPaused = false
        status = PresageSession.Status.Starting
        chestTrace = emptyList(); bellyTrace = emptyList()
        lastValidationCode = null
        framing.restart(now - startRealtime)
    }

    /** Ends the session (camera already stopped by the caller), builds the summary. */
    fun endSession() {
        if (!active) return
        elapsedMs = SystemClock.elapsedRealtime() - startRealtime
        summary = SessionSummary(
            elapsedMs = elapsedMs.coerceAtMost(length.millis),
            breaksTaken = breaksTaken,
            avgPulse = if (pulseCount > 0) pulseSum / pulseCount else null,
            avgBreathing = if (breathCount > 0) breathSum / breathCount else null,
            lastHrv = hrv?.toDouble(),
            baseline = baseline,
            pulseSamples = pulseSamples.toList(),
            breathingSamples = breathSamples.toList(),
            hrvSamples = hrvSamples.toList(),
        )
        active = false
        clearLive()
        screen = Screen.Summary
    }

    // ---- break sheet actions ----
    fun notNow() {
        coach.quietFor(elapsedMs)
        sheet = BreakSheet.Hidden
    }

    fun takeBreak() {
        breaksTaken++
        coach.quietFor(elapsedMs)
        val tip = BreakTips.tips[(breaksTaken - 1) % BreakTips.tips.size]
        sheet = BreakSheet.Breathing(secondsLeft = 60, tip = tip, before = breathing)
    }

    /**
     * A fixed framing line to speak now, or null. Polled about once a second by the UI.
     * @param canSpeak false while the break sheet is open or other speech is playing.
     */
    fun pollFramingPrompt(canSpeak: Boolean): String? =
        framing.poll(SystemClock.elapsedRealtime() - startRealtime, canSpeak && live && voiceOn)

    /** Tick / untick one of the break checklist steps. */
    fun toggleStep(index: Int) {
        val b = sheet as? BreakSheet.Breathing ?: return
        sheet = b.copy(ticked = if (index in b.ticked) b.ticked - index else b.ticked + index)
    }

    fun imBack() {
        coach.quietFor(elapsedMs)
        sheet = BreakSheet.Hidden
    }

    // ---- summary actions ----
    /**
     * Erases the finished session's numbers and returns to the session picker. Called whenever the
     * user leaves the summary (button, back, or the app going to the background): nothing is kept.
     */
    fun startNew() {
        summary = null
        clearLive()
        screen = Screen.ChooseSession
    }

    private fun clearLive() {
        elapsedMs = 0; status = PresageSession.Status.Idle
        validationHint = null; errorText = null
        pulse = null; breathing = null; hrv = null; expression = null
        chestTrace = emptyList(); bellyTrace = emptyList()
        showPreview = false; sheet = BreakSheet.Hidden
        breaksTaken = 0; baseline = null
        cameraPaused = false; pausedAtRealtime = 0L
        signalLive = false; lastTraceRealtime = 0L
        lastValidationCode = null
        pulseSum = 0.0; pulseCount = 0; breathSum = 0.0; breathCount = 0
        pulseSamples.clear(); breathSamples.clear(); hrvSamples.clear()
    }

    // ---- PresageSession.Listener (main thread). Values only replace the previous value. ----
    /** SDK callbacks are only accepted while a session is running with the camera on. */
    private val live: Boolean get() = active && !cameraPaused

    override fun onStatus(status: PresageSession.Status) {
        if (!live) return
        this.status = status
        if (status == PresageSession.Status.Measuring) errorText = null
    }

    /** Acts only when the code changes. Same text on screen as the spoken line (SDK hint as fallback). */
    override fun onValidation(code: String?, hint: String) {
        if (!live || code == lastValidationCode) return
        lastValidationCode = code
        framing.onCode(code, SystemClock.elapsedRealtime() - startRealtime)
        validationHint = code?.let { FramingCoach.displayLine(it, hint) }
    }

    override fun onError(message: String) { if (live) errorText = message }

    override fun onPulse(bpm: Float) {
        if (!live) return
        pulse = bpm; pulseSum += bpm; pulseCount++
        pulseSamples.addCapped(bpm)
    }

    override fun onBreathing(perMinute: Float) {
        if (!live) return
        breathing = perMinute; breathSum += perMinute; breathCount++
        breathSamples.addCapped(perMinute)
        val now = SystemClock.elapsedRealtime() - startRealtime
        val suggest = coach.onBreathing(perMinute.toDouble(), now)
        baseline = coach.baseline
        if (suggest && sheet == BreakSheet.Hidden) sheet = BreakSheet.Nudge
    }

    override fun onHrv(rmssdMs: Double) {
        if (!live) return
        hrv = rmssdMs
        hrvSamples.addCapped(rmssdMs.toFloat())
    }

    override fun onExpression(label: String) { if (live && expressionsOptIn) expression = label }

    override fun onTraces(chest: List<Float>, belly: List<Float>) {
        if (!live) return
        chestTrace = chest; bellyTrace = belly
        lastTraceRealtime = SystemClock.elapsedRealtime()
        signalLive = true
    }

    private fun ArrayList<Float>.addCapped(v: Float) {
        add(v)
        if (size > MAX_SAMPLES) removeAt(0)
    }

    private companion object {
        /** Per-reading sample cap (a 10 min session at ~1 reading/s fits). */
        const val MAX_SAMPLES = 900

        /** Camera off for longer than this => old baseline is discarded on resume. */
        const val BASELINE_KEEP_MS = 60_000L

        /** No new wave sample for this long => treat the live signal as missing. */
        const val SIGNAL_STALE_MS = 2_000L
    }
}
