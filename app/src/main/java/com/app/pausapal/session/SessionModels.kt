package com.app.pausapal.session

enum class Screen { Consent, ChooseSession, Live, Summary }

/** Break suggestions shown for every session (fixed text, no AI). */
object BreakTips {
    /** Shown in the break suggestion sheet. */
    val tips = listOf(
        "Look at something 20 feet away for 20 seconds.",
        "Stand up and stretch your wrists, neck and shoulders.",
        "Sip some water.",
        "Blink slowly ten times and rest your eyes.",
        "Take a short walk to the window or the next room.",
    )

    /** Short tap-to-tick checklist shown during a break (2–3 items). */
    val steps = listOf("Look away for 20 s", "Stretch your neck and wrists", "Sip water")
}

enum class SessionLength(
    val minutes: Int,
    val label: String,
    val sub: String,
    /** End of the baseline window (it always starts 30 s in). */
    val baselineEndMs: Long,
    /** Quiet period after "Not now" / "Take a break". */
    val snoozeMs: Long,
) {
    Min3(3, "3 min", "Quick check-in", baselineEndMs = 60_000, snoozeMs = 2 * 60_000),
    Min5(5, "5 min", "Short reset", baselineEndMs = 90_000, snoozeMs = 5 * 60_000),
    Min10(10, "10 min", "Longer", baselineEndMs = 90_000, snoozeMs = 5 * 60_000);

    val millis: Long get() = minutes * 60_000L
    val snoozeMinutes: Int get() = (snoozeMs / 60_000).toInt()
}

sealed interface BreakSheet {
    data object Hidden : BreakSheet
    data object Nudge : BreakSheet
    /**
     * Guided 60 s breathing after "Take a break".
     * @param before latest breathing rate when "Take a break" was tapped (null = no reading yet)
     * @param after latest breathing rate when the 60 s ended (null = not ended, or no reading)
     * @param ticked indexes of the break [BreakTips.steps] the user has ticked
     * @param tip the break tip chosen for this break (shown, and spoken when voice guidance is on)
     */
    data class Breathing(
        val secondsLeft: Int,
        val tip: String,
        val before: Float?,
        val after: Float? = null,
        val ticked: Set<Int> = emptySet(),
    ) : BreakSheet {
        val finished: Boolean get() = secondsLeft == 0
    }
}

/** Built when a session ends. Memory only; erased as soon as the user leaves the summary screen. */
data class SessionSummary(
    val elapsedMs: Long,
    val breaksTaken: Int,
    val avgPulse: Double?,
    val avgBreathing: Double?,
    val lastHrv: Double?,
    val baseline: Double?,
    /** In-memory readings from this session, oldest first (for sparklines and min-max range). */
    val pulseSamples: List<Float> = emptyList(),
    val breathingSamples: List<Float> = emptyList(),
    val hrvSamples: List<Float> = emptyList(),
)
