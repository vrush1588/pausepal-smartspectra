package com.app.pausapal.session

import kotlin.math.max

/**
 * Decides when to speak a framing prompt from the SDK's validation codes. Pure logic (no Android,
 * no SDK types), driven by session time in ms, so it is unit-tested.
 *
 * Rules:
 *  - Only code CHANGES are recorded ([onCode] ignores repeats of the current code).
 *  - Codes are ignored during the first [IGNORE_MS] of a session (and again after a camera resume).
 *  - A problem counts as real only after the problem streak has lasted [PERSIST_MS]. If the code
 *    flickers inside that window, the highest-[PRIORITY] code seen in it wins.
 *  - At most one prompt per [COOLDOWN_MS]. The same problem is not repeated until a different
 *    problem, or a steady OK, comes in between.
 *  - A steady OK after a spoken problem says [BACK_IN_VIEW] once.
 *  - CAMERA_TUNING, FRAME_RATE_TOO_LOW (and any unlisted code) are text only, never spoken.
 * Lines are fixed text. Nothing here ever sees a measurement.
 */
class FramingCoach(startMs: Long = 0L) {

    private data class Segment(val code: String?, val startMs: Long)

    private var ignoreUntilMs = startMs + IGNORE_MS
    private val segments = ArrayDeque<Segment>()
    private var lastSpokenCode: String? = null
    private var spokeProblem = false
    private var lastSpeakMs = Long.MIN_VALUE / 2

    /** Camera restarted: ignore codes again for [IGNORE_MS]. Spoken state is kept. */
    fun restart(nowMs: Long) {
        ignoreUntilMs = nowMs + IGNORE_MS
        segments.clear()
    }

    /** @param code SDK validation code name, or null for OK. */
    fun onCode(code: String?, nowMs: Long) {
        if (segments.isNotEmpty() && segments.last().code == code) return
        segments.addLast(Segment(code, nowMs))
        // Keep only what the persistence window can still look at (plus the current segment).
        while (segments.size > 1 && segments[1].startMs < nowMs - 4 * PERSIST_MS) segments.removeFirst()
    }

    /**
     * Called about once a second. Returns a fixed line to speak now, or null.
     * @param canSpeak false while voice is off, the break sheet is open or speech is playing.
     */
    fun poll(nowMs: Long, canSpeak: Boolean): String? {
        if (!canSpeak || nowMs - lastSpeakMs < COOLDOWN_MS) return null
        val current = segments.lastOrNull() ?: return null
        val windowStart = nowMs - PERSIST_MS

        if (current.code == null) {
            if (spokeProblem && max(current.startMs, ignoreUntilMs) <= windowStart) {
                spokeProblem = false
                lastSpokenCode = null
                lastSpeakMs = nowMs
                return BACK_IN_VIEW
            }
            return null
        }

        // Problem streak = everything after the last OK segment.
        val lastOk = segments.indexOfLast { it.code == null }
        val streakStart = segments[lastOk + 1].startMs
        if (max(streakStart, ignoreUntilMs) > windowStart) return null

        val best = segments.indices
            .filter { i -> i > lastOk && (segments.getOrNull(i + 1)?.startMs ?: Long.MAX_VALUE) > windowStart }
            .mapNotNull { segments[it].code }
            .filter { it in SPOKEN }
            .minByOrNull { PRIORITY.indexOf(it) }
            ?: return null
        if (best == lastSpokenCode) return null

        lastSpokenCode = best
        spokeProblem = true
        lastSpeakMs = nowMs
        return SPOKEN.getValue(best)
    }

    companion object {
        const val IGNORE_MS = 5_000L
        const val PERSIST_MS = 2_000L
        const val COOLDOWN_MS = 10_000L
        const val BACK_IN_VIEW = "Great, I can see you."

        /** Spoken (and on-screen) lines, in priority order: face found, one face, framing, light, motion. */
        private val SPOKEN: Map<String, String> = linkedMapOf(
            "NO_FACE_FOUND" to "I can't see your face. Please face the camera.",
            "MULTIPLE_FACES_FOUND" to "Please make sure only one person is in view.",
            "FACE_NOT_CENTERED" to "Please center your face.",
            "FACE_TOO_CLOSE" to "Move a little back.",
            "FACE_TOO_FAR" to "Move a little closer.",
            "FACE_TOO_HIGH" to "Move down, or tilt the phone up.",
            "FACE_TOO_LOW" to "Move up, or tilt the phone down.",
            "FACE_NOT_FORWARD" to "Please face the camera.",
            "CHEST_NOT_VISIBLE" to "Please show your upper chest too.",
            "TOO_DARK" to "Add some light.",
            "TOO_BRIGHT" to "Reduce the light behind you.",
            "EXCESSIVE_MOTION" to "Please sit still.",
        )
        private val PRIORITY = SPOKEN.keys.toList()

        /** Text-only fallbacks for codes that are never spoken, if the SDK gives no hint. */
        private val TEXT_ONLY = mapOf(
            "CAMERA_TUNING" to "Adjusting the camera…",
            "FRAME_RATE_TOO_LOW" to "The camera is running slowly. Try closing other apps.",
        )

        /** On-screen line for a code: the spoken line, else the SDK hint, else a generic line. */
        fun displayLine(code: String, sdkHint: String): String =
            SPOKEN[code] ?: sdkHint.ifBlank { TEXT_ONLY[code] ?: "Adjusting… keep face and upper chest in view" }
    }
}
