package com.app.pausapal

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import com.app.pausapal.ui.theme.isDark
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.app.pausapal.session.BreakSheet
import com.app.pausapal.session.Screen
import com.app.pausapal.session.SessionViewModel
import com.app.pausapal.ui.screens.BreakSheetOverlay
import com.app.pausapal.ui.screens.ConsentScreen
import com.app.pausapal.ui.screens.LiveSessionScreen
import com.app.pausapal.ui.screens.ChooseSessionScreen
import com.app.pausapal.ui.screens.SummaryScreen
import com.app.pausapal.ui.components.rememberBreathPhase
import com.app.pausapal.ui.haptics.Haptics
import com.app.pausapal.voice.VoiceGuide
import com.app.pausapal.ui.theme.PausaPalTheme
import com.app.pausapal.ui.theme.PpBg
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Single Activity. Flow: Consent -> Choose session -> Live session (+ break sheet) -> Summary.
 *
 * Camera / privacy lifecycle:
 *  - The camera starts ONLY from startSessionWithPermission(), reachable only after the consent
 *    checkbox is ticked (Continue is disabled otherwise) and the user taps "Start session".
 *  - onPause() stops the SDK if a session is active (camera never runs in the background) and
 *    shows the summary.
 *  - "Pause camera" stops the SDK mid-session (timer keeps going); "Resume camera" restarts it.
 */
class MainActivity : ComponentActivity() {

    private val vm: SessionViewModel by viewModels()

    /** Kept attached in the live screen (hidden at 1dp/alpha 0 unless the user shows it). */
    private lateinit var previewView: PreviewView

    /** Last SDK start/stop job. A start waits for earlier ops so start() and stop() never overlap. */
    private var cameraJob: Job? = null

    /** Set in onPause: true if the summary was on screen when the app was left. */
    private var leftFromSummary = false

    /** Optional gentle haptics; checks the "Gentle vibration" switch on every call. */
    private val haptics by lazy { Haptics(this) { vm.hapticsOn } }

    /** Optional voice guidance. The TTS engine is only created when the switch is turned on. */
    private val voice by lazy { VoiceGuide(this) }

    /** True from "Install voice" until we're back: leaving the app then pauses instead of ending. */
    private var installingVoice = false

    /** Set in onPause during a voice install if the camera was on, so onResume turns it back on. */
    private var resumeCameraAfterInstall = false

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) beginSession()
            else vm.permissionMessage =
                "Camera permission is needed to read pulse and breathing. You can allow it in Android settings."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Force SDK telemetry OFF before anything else; it is only enabled per session on opt-in.
        vm.presage.applyPrivacyDefaults()
        vm.presage.bind(this)

        previewView = PreviewView(this).apply {
            // TextureView-based so alpha/size hiding works while the SDK keeps a valid surface.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        setContent {
            val dark = vm.themeMode.isDark()
            // Status / nav bar icons follow the in-app theme, not just the system setting.
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            PausaPalTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(PpBg)) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) { AppContent() }
                }
            }
        }
    }

    @Composable
    private fun AppContent() {
        when (vm.screen) {
            Screen.Consent -> ConsentScreen(vm, onContinue = {
                if (vm.consentGiven) vm.screen = Screen.ChooseSession
            })

            Screen.ChooseSession -> {
                BackHandler { vm.screen = Screen.Consent }
                ChooseSessionScreen(vm, onStart = ::startSessionWithPermission)
            }

            Screen.Live -> {
                BackHandler {
                    if (vm.sheet == BreakSheet.Nudge) vm.notNow()
                    else if (vm.sheet is BreakSheet.Breathing) vm.imBack()
                    else finishSession()
                }
                KeepScreenOn(vm.active)
                LaunchedEffect(vm.active) {
                    while (vm.active) {
                        delay(1000)
                        if (vm.tick()) { finishSession(); break }
                        // Framing prompts: never over the break sheet or other speech.
                        vm.pollFramingPrompt(canSpeak = vm.sheet == BreakSheet.Hidden && !voice.isSpeaking)
                            ?.let { voice.speak(it) }
                    }
                }
                val breathPhase = rememberBreathPhase(vm.chestTrace, vm.bellyTrace, vm.signalLive)
                // One soft vibration when the break suggestion appears (only if the switch is on).
                val nudgeShown = vm.sheet == BreakSheet.Nudge
                LaunchedEffect(nudgeShown) { if (nudgeShown) haptics.soft() }
                // Voice: stop when the break sheet closes; speak the chosen tip when the 60 s ends.
                val sheetHidden = vm.sheet == BreakSheet.Hidden
                LaunchedEffect(sheetHidden) { if (sheetHidden) voice.stop() }
                val breathingDone = (vm.sheet as? BreakSheet.Breathing)?.takeIf { it.finished }
                LaunchedEffect(breathingDone != null) {
                    if (breathingDone != null && vm.voiceOn) voice.speak(breathingDone.tip)
                }
                Box(Modifier.fillMaxSize()) {
                    LiveSessionScreen(
                        vm, previewView,
                        onStop = ::finishSession,
                        onToggleCamera = { if (vm.cameraPaused) resumeCamera() else pauseCamera() },
                        breathPhase = breathPhase,
                        onVoiceToggle = ::setVoice,
                        onInstallVoice = ::openVoiceInstall,
                    )
                    BreakSheetOverlay(
                        sheet = vm.sheet,
                        snoozeLabel = "Not now (in ${vm.length.snoozeMinutes}m)",
                        onTakeBreak = vm::takeBreak,
                        onNotNow = vm::notNow,
                        onImBack = vm::imBack,
                        mascotMood = vm.mascotMood,
                        breathPhase = breathPhase,
                        onToggleStep = vm::toggleStep,
                        onGuideInhale = {
                            val s = vm.sheet
                            if (s is BreakSheet.Breathing && !s.finished) {
                                haptics.tick()
                                if (vm.voiceOn) voice.speak("Breathe in")
                            }
                        },
                        onGuideExhale = {
                            val s = vm.sheet
                            if (s is BreakSheet.Breathing && !s.finished && vm.voiceOn) voice.speak("Breathe out slowly")
                        },
                    )
                }
            }

            Screen.Summary -> {
                val s = vm.summary
                if (s == null) {
                    vm.screen = Screen.ChooseSession
                } else {
                    BackHandler { vm.startNew() }
                    SummaryScreen(s, onStartNew = vm::startNew)
                }
            }
        }
    }

    @Composable
    private fun KeepScreenOn(on: Boolean) {
        val view = LocalView.current
        DisposableEffect(on) {
            view.keepScreenOn = on
            onDispose { view.keepScreenOn = false }
        }
    }

    private fun startSessionWithPermission() {
        if (!vm.consentGiven) { vm.screen = Screen.Consent; return }
        vm.permissionMessage = null
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) beginSession() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    /** Consent given + Start tapped + permission granted: open the camera via PresageSession. */
    private fun beginSession() {
        if (!vm.consentGiven || vm.active) return
        vm.beginSession()
        startCamera()
    }

    /** "Pause camera": stop the SDK, keep the session (and countdown) going. */
    private fun pauseCamera() {
        if (!vm.active || vm.cameraPaused) return
        vm.pauseCamera()
        stopCamera()
    }

    /** "Resume camera": restart the SDK with the same session choices. */
    private fun resumeCamera() {
        if (!vm.active || !vm.cameraPaused) return
        vm.resumeCamera()
        startCamera()
    }

    /** "Voice guidance" switch: create TTS on, release it off. Falls back to off with a message. */
    private fun setVoice(on: Boolean) {
        vm.voiceMessage = null
        vm.voiceNeedsInstall = false
        if (!on) {
            vm.voiceOn = false
            voice.shutdown()
            return
        }
        vm.voiceOn = true
        voice.enable { result ->
            if (result == VoiceGuide.Result.Ready) return@enable
            vm.voiceOn = false
            vm.voiceNeedsInstall = result == VoiceGuide.Result.NoOfflineVoice
            vm.voiceMessage = when (result) {
                VoiceGuide.Result.NoOfflineVoice ->
                    "No offline voice on this phone. Install an English voice; the camera pauses while you're away."
                else -> "Text-to-speech is not available on this phone"
            }
        }
    }

    /** "Install voice": opens the TTS engine's voice-download screen, or TTS settings as a fallback. */
    private fun openVoiceInstall() {
        val install = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        val settings = Intent("com.android.settings.TTS_SETTINGS")
        for (intent in listOf(install, settings)) {
            installingVoice = true
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                installingVoice = false // Try the next screen.
            }
        }
        vm.voiceMessage = "Open Settings, search \"Text-to-speech\", and install an English voice."
        vm.voiceNeedsInstall = false
    }

    /** Stop the camera and show the summary. */
    private fun finishSession() {
        voice.stop()
        if (!vm.active) return
        vm.endSession()
        stopCamera()
    }

    private fun startCamera() {
        val previous = cameraJob
        cameraJob = lifecycleScope.launch {
            previous?.join()
            // The user may have paused or ended the session while we waited, or a quick
            // pause/resume may already have restarted the camera.
            if (!vm.active || vm.cameraPaused || vm.presage.running) return@launch
            vm.presage.start(
                surfaceProvider = previewView.surfaceProvider,
                expressions = vm.expressionsOptIn,
                telemetry = vm.telemetryOptIn,
            )
        }
    }

    /** Stops right away (a start still in progress turns itself off), then waits for earlier ops. */
    private fun stopCamera() {
        val previous = cameraJob
        cameraJob = lifecycleScope.launch {
            vm.presage.stop()
            previous?.join()
        }
    }

    override fun onPause() {
        super.onPause()
        // Was the user already looking at a finished session's summary when they left?
        leftFromSummary = vm.screen == Screen.Summary
        voice.stop()
        if (installingVoice && vm.active) {
            // Off to install a voice: keep the session, but the camera still never runs in the background.
            resumeCameraAfterInstall = !vm.cameraPaused
            pauseCamera()
            return
        }
        // Never keep the camera running when the app is not in the foreground.
        finishSession()
    }

    override fun onResume() {
        super.onResume()
        if (!installingVoice) return
        installingVoice = false
        if (!vm.active) return // The session ran out while away; the summary is already showing.
        if (resumeCameraAfterInstall) resumeCamera()
        resumeCameraAfterInstall = false
        setVoice(true) // Try again with the newly installed voice (shows the message again if none).
    }

    override fun onDestroy() {
        voice.shutdown()
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        // Nothing is kept: leaving the app from the summary erases the finished session's numbers.
        // (A session cut short by leaving the app still shows its summary once, on return.)
        if (leftFromSummary) vm.startNew()
    }
}
