package ani.dantotsu.media.anime

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Animatable
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.AudioManager.AUDIOFOCUS_GAIN
import android.media.AudioManager.AUDIOFOCUS_LOSS
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
import android.media.AudioManager.STREAM_MUSIC
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings.System
import android.util.AttributeSet
import android.util.Rational
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_B
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_N
import android.view.KeyEvent.KEYCODE_SPACE
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.math.MathUtils.clamp
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.GesturesListener
import ani.dantotsu.NoPaddingArrayAdapter
import ani.dantotsu.R
import ani.dantotsu.addons.download.DownloadAddonManager
import ani.dantotsu.brightnessConverter
import ani.dantotsu.circularReveal
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.connections.crashlytics.CrashlyticsInterface
import ani.dantotsu.connections.discord.Discord
import ani.dantotsu.connections.discord.DiscordService
import ani.dantotsu.connections.discord.DiscordServiceRunningSingleton
import ani.dantotsu.connections.discord.RPC
import ani.dantotsu.connections.updateProgress
import ani.dantotsu.databinding.ActivityExoplayerBinding
import ani.dantotsu.defaultHeaders
import ani.dantotsu.download.DownloadsManager.Companion.getSubDirectory
import ani.dantotsu.dp
import ani.dantotsu.getCurrentBrightnessValue
import ani.dantotsu.hideSystemBars
import ani.dantotsu.hideSystemBarsExtendView
import ani.dantotsu.isOnline
import ani.dantotsu.logError
import ani.dantotsu.media.Media
import ani.dantotsu.media.MediaDetailsViewModel
import ani.dantotsu.media.MediaNameAdapter
import ani.dantotsu.media.MediaType
import ani.dantotsu.media.SubtitleDownloader
import ani.dantotsu.others.AniSkip
import ani.dantotsu.others.AniSkip.getType
import ani.dantotsu.others.ResettableTimer
import ani.dantotsu.others.Xubtitle
import ani.dantotsu.others.getSerialized
import ani.dantotsu.parsers.AnimeSources
import ani.dantotsu.parsers.HAnimeSources
import ani.dantotsu.parsers.Subtitle
import ani.dantotsu.parsers.SubtitleType
import ani.dantotsu.parsers.Video
import ani.dantotsu.parsers.VideoExtractor
import ani.dantotsu.parsers.VideoType
import ani.dantotsu.settings.PlayerSettingsActivity
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.snackString
import ani.dantotsu.startMainActivity
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.toPx
import ani.dantotsu.toast
import ani.dantotsu.tryWithSuspend
import ani.dantotsu.util.Logger
import ani.dantotsu.util.customAlertDialog
import com.anggrayudi.storage.file.extension
import com.bumptech.glide.Glide
import com.google.android.material.slider.Slider
import is.xyz.mpv.BaseMPVView
import is.xyz.mpv.KeyMapping
import is.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.reflect.KProperty

@SuppressLint("ClickableViewAccessibility")
class MPVView :
    AppCompatActivity() {
    private val resumeWindow = "resumeWindow"
    private val resumePosition = "resumePosition"
    private val playerFullscreen = "playerFullscreen"
    private val playerOnPlay = "playerOnPlay"
    private var disappeared: Boolean = false
    private var functionstarted: Boolean = false

    private lateinit var mpvView: AniyomiMPVView
    private lateinit var playbackParameters: PlaybackParameters

    private lateinit var binding: ActivityExoplayerBinding
    private lateinit var playerView: ViewGroup
    private lateinit var exoPlay: ImageButton
    private lateinit var exoSource: ImageButton
    private lateinit var exoSettings: ImageButton
    private lateinit var exoSubtitle: ImageButton
    private lateinit var exoAudioTrack: ImageButton
    private lateinit var exoRotate: ImageButton
    private lateinit var exoSpeed: ImageButton
    private lateinit var exoScreen: ImageButton
    private lateinit var exoNext: ImageButton
    private lateinit var exoPrev: ImageButton
    private lateinit var exoSkipOpEd: ImageButton
    private lateinit var exoPip: ImageButton
    private lateinit var exoBrightness: Slider
    private lateinit var exoVolume: Slider
    private lateinit var exoBrightnessCont: View
    private lateinit var exoVolumeCont: View
    private lateinit var exoSkip: View
    private lateinit var skipTimeButton: View
    private lateinit var skipTimeText: TextView
    private lateinit var timeStampText: TextView
    private lateinit var animeTitle: TextView
    private lateinit var videoInfo: TextView
    private lateinit var episodeTitle: Spinner
    private lateinit var customSubtitleView: Xubtitle

    private var orientationListener: OrientationEventListener? = null

    private var hasExtSubtitles = false
    private var audioLanguages = mutableListOf<Pair<String, String>>()

    companion object {
        var initialized = false
        lateinit var media: Media
    }

    private lateinit var episode: Episode
    private lateinit var episodes: MutableMap<String, Episode>
    private lateinit var episodeArr: List<String>
    private lateinit var episodeTitleArr: ArrayList<String>
    private var currentEpisodeIndex = 0
    private var epChanging = false

    private var extractor: VideoExtractor? = null
    private var video: Video? = null
    private var subtitle: Subtitle? = null

    private var notchHeight: Int = 0
    private var currentWindow = 0
    private var playbackPosition: Long = 0
    private var episodeLength: Float = 0f
    private var isFullscreen: Int = 0
    private var isInitialized = false
    private var isPlayerPlaying = true
    private var changingServer = false
    private var interacted = false

    private var pipEnabled = false
    private var aspectRatio = Rational(16, 9)

    private val handler = Handler(Looper.getMainLooper())
    val model: MediaDetailsViewModel by viewModels()

    private var isTimeStampsLoaded = false
    private var isSeeking = false
    private var isFastForwarding = false

    var rotation = 0

    override fun onAttachedToWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val displayCutout = window.decorView.rootWindowInsets.displayCutout
            if (displayCutout != null) {
                if (displayCutout.boundingRects.size > 0) {
                    notchHeight =
                        min(
                            displayCutout.boundingRects[0].width(),
                            displayCutout.boundingRects[0].height(),
                        )
                    checkNotch()
                }
            }
        }
        super.onAttachedToWindow()
    }

    private fun checkNotch() {
        if (notchHeight != 0) {
            val orientation = resources.configuration.orientation
            playerView
                .findViewById<View>(R.id.exo_controller_margin)
                .updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        marginStart = notchHeight
                        marginEnd = notchHeight
                        topMargin = 0
                    } else {
                        topMargin = notchHeight
                        marginStart = 0
                        marginEnd = 0
                    }
                }
            playerView.findViewById<View>(androidx.media3.ui.R.id.exo_buffering)?.translationY =
                (if (orientation == Configuration.ORIENTATION_LANDSCAPE) 0 else (notchHeight + 8.toPx)).dp
            exoBrightnessCont.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginEnd =
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) notchHeight else 0
            }
            exoVolumeCont.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart =
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) notchHeight else 0
            }
        }
    }

    private fun applySubtitleStyles(textView: Xubtitle) {
        val primaryColor = PrefManager.getVal<Int>(PrefName.PrimaryColor)

        val subBackground = PrefManager.getVal<Int>(PrefName.SubBackground)

        val secondaryColor = PrefManager.getVal<Int>(PrefName.SecondaryColor)

        val subStroke = PrefManager.getVal<Float>(PrefName.SubStroke)

        val fontSize = PrefManager.getVal<Int>(PrefName.FontSize).toFloat()

        val font =
            when (PrefManager.getVal<Int>(PrefName.Font)) {
                0 -> ResourcesCompat.getFont(this, R.font.poppins_semi_bold)
                1 -> ResourcesCompat.getFont(this, R.font.poppins_bold)
                2 -> ResourcesCompat.getFont(this, R.font.poppins)
                3 -> ResourcesCompat.getFont(this, R.font.poppins_thin)
                4 -> ResourcesCompat.getFont(this, R.font.century_gothic_regular)
                5 -> ResourcesCompat.getFont(this, R.font.levenim_mt_bold)
                6 -> ResourcesCompat.getFont(this, R.font.blocky)
                else -> ResourcesCompat.getFont(this, R.font.poppins_semi_bold)
            }

        textView.setBackgroundColor(subBackground)
        textView.setTextColor(primaryColor)
        textView.typeface = font
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)

        textView.apply {
            when (PrefManager.getVal<Int>(PrefName.Outline)) {
                0 -> applyOutline(secondaryColor, subStroke)
                1 -> applyShineEffect(secondaryColor)
                2 -> applyDropShadow(secondaryColor, subStroke)
                3 -> {}
                else -> applyOutline(secondaryColor, subStroke)
            }
        }

        textView.alpha =
            when (PrefManager.getVal<Boolean>(PrefName.Subtitles)) {
                true -> PrefManager.getVal(PrefName.SubAlpha)
                false -> 0f
            }

        val textElevation =
            PrefManager.getVal<Float>(PrefName.SubBottomMargin) / 50 * resources.displayMetrics.heightPixels
        textView.translationY = -textElevation
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityExoplayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemBarsExtendView()

        onBackPressedDispatcher.addCallback(this) {
            finishAndRemoveTask()
        }

        // Create MPV view
        mpvView = AniyomiMPVView(this, null)
        mpvView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        mpvView.id = R.id.mpv_view
        
        // Add MPV view to the player container
        val playerContainer = findViewById<ViewGroup>(R.id.player_container)
        playerContainer.addView(mpvView, 0)

        playerView = findViewById(R.id.player_view)
        exoPlay = playerView.findViewById(androidx.media3.ui.R.id.exo_play)
        exoSource = playerView.findViewById(R.id.exo_source)
        exoSettings = playerView.findViewById(R.id.exo_settings)
        exoSubtitle = playerView.findViewById(R.id.exo_sub)
        exoAudioTrack = playerView.findViewById(R.id.exo_audio)
        exoRotate = playerView.findViewById(R.id.exo_rotate)
        exoSpeed = playerView.findViewById(androidx.media3.ui.R.id.exo_playback_speed)
        exoScreen = playerView.findViewById(R.id.exo_screen)
        exoBrightness = playerView.findViewById(R.id.exo_brightness)
        exoVolume = playerView.findViewById(R.id.exo_volume)
        exoBrightnessCont = playerView.findViewById(R.id.exo_brightness_cont)
        exoVolumeCont = playerView.findViewById(R.id.exo_volume_cont)
        exoPip = playerView.findViewById(R.id.exo_pip)
        exoSkipOpEd = playerView.findViewById(R.id.exo_skip_op_ed)
        exoSkip = playerView.findViewById(R.id.exo_skip)
        skipTimeButton = playerView.findViewById(R.id.exo_skip_timestamp)
        skipTimeText = skipTimeButton.findViewById(R.id.exo_skip_timestamp_text)
        timeStampText = playerView.findViewById(R.id.exo_time_stamp_text)
        customSubtitleView = playerView.findViewById(R.id.customSubtitleView)

        animeTitle = playerView.findViewById(R.id.exo_anime_title)
        episodeTitle = playerView.findViewById(R.id.exo_ep_sel)

        playerView.controllerShowTimeoutMs = 5000

        val audioManager = applicationContext.getSystemService(AUDIO_SERVICE) as AudioManager

        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus({ focus ->
            when (focus) {
                AUDIOFOCUS_LOSS_TRANSIENT, AUDIOFOCUS_LOSS -> if (isInitialized) pause()
            }
        }, AudioManager.AUDIO_CONTENT_TYPE_MOVIE, AUDIOFOCUS_GAIN)

        if (System.getInt(contentResolver, System.ACCELEROMETER_ROTATION, 0) != 1) {
            if (PrefManager.getVal(PrefName.RotationPlayer)) {
                orientationListener =
                    object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_UI) {
                        override fun onOrientationChanged(orientation: Int) {
                            when (orientation) {
                                in 45..135 -> {
                                    if (rotation != ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                                        exoRotate.visibility = View.VISIBLE
                                    }
                                    rotation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                                }

                                in 225..315 -> {
                                    if (rotation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                        exoRotate.visibility = View.VISIBLE
                                    }
                                    rotation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }

                                in 315..360, in 0..45 -> {
                                    if (rotation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                                        exoRotate.visibility = View.VISIBLE
                                    }
                                    rotation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            }
                        }
                    }
                orientationListener?.enable()
            }

            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            exoRotate.setOnClickListener {
                requestedOrientation = rotation
                it.visibility = View.GONE
            }
        }

        if (savedInstanceState != null) {
            currentWindow = savedInstanceState.getInt(resumeWindow)
            playbackPosition = savedInstanceState.getLong(resumePosition)
            isFullscreen = savedInstanceState.getInt(playerFullscreen)
            isPlayerPlaying = savedInstanceState.getBoolean(playerOnPlay)
        }

        // BackButton
        playerView.findViewById<ImageButton>(R.id.exo_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // TimeStamps
        model.timeStamps.observe(this) { it ->
            isTimeStampsLoaded = true
            exoSkipOpEd.visibility =
                if (it != null) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }

        exoSkipOpEd.alpha = if (PrefManager.getVal(PrefName.AutoSkipOPED)) 1f else 0.3f
        exoSkipOpEd.setOnClickListener {
            if (PrefManager.getVal(PrefName.AutoSkipOPED)) {
                snackString(getString(R.string.disabled_auto_skip))
                PrefManager.setVal(PrefName.AutoSkipOPED, false)
            } else {
                snackString(getString(R.string.auto_skip))
                PrefManager.setVal(PrefName.AutoSkipOPED, true)
            }
            exoSkipOpEd.alpha = if (PrefManager.getVal(PrefName.AutoSkipOPED)) 1f else 0.3f
        }

        // Play Pause
        exoPlay.setOnClickListener {
            if (isInitialized) {
                isPlayerPlaying = !isPaused()
                (exoPlay.drawable as Animatable?)?.start()
                if (!isPaused()) {
                    Glide.with(this).load(R.drawable.anim_play_to_pause).into(exoPlay)
                    pause()
                } else {
                    Glide.with(this).load(R.drawable.anim_pause_to_play).into(exoPlay)
                    play()
                }
            }
        }

        // Picture-in-picture
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            pipEnabled =
                packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
                        PrefManager.getVal(PrefName.Pip)
            if (pipEnabled) {
                exoPip.visibility = View.VISIBLE
                exoPip.setOnClickListener {
                    enterPipMode()
                }
            } else {
                exoPip.visibility = View.GONE
            }
        }

        // Lock Button
        var locked = false
        val container = playerView.findViewById<View>(R.id.exo_controller_cont)
        val screen = playerView.findViewById<View>(R.id.exo_black_screen)
        val lockButton = playerView.findViewById<ImageButton>(R.id.exo_unlock)
        playerView.findViewById<ImageButton>(R.id.exo_lock).setOnClickListener {
            locked = true
            screen.visibility = View.GONE
            container.visibility = View.GONE
            lockButton.visibility = View.VISIBLE
        }
        lockButton.setOnClickListener {
            locked = false
            screen.visibility = View.VISIBLE
            container.visibility = View.VISIBLE
            it.visibility = View.GONE
        }

        // Skip Time Button
        var skipTime = PrefManager.getVal<Int>(PrefName.SkipTime)
        if (skipTime > 0) {
            exoSkip.findViewById<TextView>(R.id.exo_skip_time).text = skipTime.toString()
            exoSkip.setOnClickListener {
                if (isInitialized) {
                    seekTo(getCurrentPosition() + skipTime * 1000)
                }
            }
            exoSkip.setOnLongClickListener {
                val dialog = Dialog(this, R.style.MyPopup)
                dialog.setContentView(R.layout.item_seekbar_dialog)
                dialog.setCancelable(true)
                dialog.setCanceledOnTouchOutside(true)
                dialog.window?.setLayout(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                if (skipTime <= 120) {
                    dialog.findViewById<Slider>(R.id.seekbar).value = skipTime.toFloat()
                } else {
                    dialog.findViewById<Slider>(R.id.seekbar).value = 120f
                }
                dialog.findViewById<Slider>(R.id.seekbar).addOnChangeListener { _, value, _ ->
                    skipTime = value.toInt()
                    PrefManager.setVal(PrefName.SkipTime, skipTime)
                    playerView.findViewById<TextView>(R.id.exo_skip_time).text =
                        skipTime.toString()
                    dialog.findViewById<TextView>(R.id.seekbar_value).text =
                        skipTime.toString()
                }
                dialog
                    .findViewById<Slider>(R.id.seekbar)
                    .addOnSliderTouchListener(
                        object : Slider.OnSliderTouchListener {
                            override fun onStartTrackingTouch(slider: Slider) {}

                            override fun onStopTrackingTouch(slider: Slider) {
                                dialog.dismiss()
                            }
                        },
                    )
                dialog.findViewById<TextView>(R.id.seekbar_title).text =
                    getString(R.string.skip_time)
                dialog.findViewById<TextView>(R.id.seekbar_value).text =
                    skipTime.toString()
                @Suppress("DEPRECATION")
                dialog.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                dialog.show()
                true
            }
        } else {
            exoSkip.visibility = View.GONE
        }

        val gestureSpeed = (300 * PrefManager.getVal<Float>(PrefName.AnimationSpeed)).toLong()
        // Player UI Visibility Handler
        val brightnessRunnable =
            Runnable {
                if (exoBrightnessCont.alpha == 1f) {
                    lifecycleScope.launch {
                        ObjectAnimator
                            .ofFloat(exoBrightnessCont, "alpha", 1f, 0f)
                            .setDuration(gestureSpeed)
                            .start()
                        delay(gestureSpeed)
                        exoBrightnessCont.visibility = View.GONE
                        checkNotch()
                    }
                }
            }
        val volumeRunnable =
            Runnable {
                if (exoVolumeCont.alpha == 1f) {
                    lifecycleScope.launch {
                        ObjectAnimator
                            .ofFloat(exoVolumeCont, "alpha", 1f, 0f)
                            .setDuration(gestureSpeed)
                            .start()
                        delay(gestureSpeed)
                        exoVolumeCont.visibility = View.GONE
                        checkNotch()
                    }
                }
            }

        val overshoot = AnimationUtils.loadInterpolator(this, R.anim.over_shoot)
        val controllerDuration = (300 * PrefManager.getVal<Float>(PrefName.AnimationSpeed)).toLong()

        fun handleController() {
            if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) !isInPictureInPictureMode else true) {
                if (playerView.isControllerFullyVisible) {
                    ObjectAnimator
                        .ofFloat(
                            playerView.findViewById(R.id.exo_controller),
                            "alpha",
                            1f,
                            0f,
                        ).setDuration(controllerDuration)
                        .start()
                    ObjectAnimator
                        .ofFloat(
                            playerView.findViewById(R.id.exo_bottom_cont),
                            "translationY",
                            0f,
                            128f,
                        ).apply {
                            interpolator = overshoot
                            duration = controllerDuration
                            start()
                        }
                    ObjectAnimator
                        .ofFloat(
                            playerView.findViewById(R.id.exo_timeline_cont),
                            "translationY",
                            0f,
                            128f,
                        ).apply {
                            interpolator = overshoot
                            duration = controllerDuration
                            start()
                        }
                    ObjectAnimator
                        .ofFloat(
                            playerView.findViewById(R.id.exo_top_cont),
                            "translationY",
                            0f,
                            -128f,
                        ).apply {
                            interpolator = overshoot
                            duration = controllerDuration
                            start()
                        }
                    playerView.postDelayed({ playerView.hideController() }, controllerDuration)
                } else {
                    checkNotch()
                    playerView.showController()
                    ObjectAnimator
                        .ofFloat(
                            playerView.findViewById(R.id.exo_controller),
                            "alpha",
                            0f,
                            1f,
                        ).setDuration(controllerDuration)
                        .start()
                    ObjectAnimator
                        .ofFloat(
                            playerView.findViewById(R.id.exo_bottom_cont),
                            "translationY",
                            128f,
                            0f,
                        ).apply {
                            interpolator = overshoot
                            duration = controllerDuration
                            start()
                        }
                    ObjectAnimator
                        .ofFloat(
                            playerView.findViewById(R.id.exo_timeline_cont),
                            "translationY",
                            128f,
                            0f,
                        ).apply {
                            interpolator = overshoot
                            duration = controllerDuration
                            start()
                        }
                    ObjectAnimator
                        .ofFloat(
                            playerView.findViewById(R.id.exo_top_cont),
                            "translationY",
                            -128f,
                            0f,
                        ).apply {
                            interpolator = overshoot
                            duration = controllerDuration
                            start()
                        }
                }
            }
        }

        playerView.findViewById<View>(R.id.exo_full_area).setOnClickListener {
            handleController()
        }

        val rewindText = playerView.findViewById<TextView>(R.id.exo_fast_rewind_anim)
        val forwardText = playerView.findViewById<TextView>(R.id.exo_fast_forward_anim)
        val fastForwardCard = playerView.findViewById<View>(R.id.exo_fast_forward)
        val fastRewindCard = playerView.findViewById<View>(R.id.exo_fast_rewind)

        // Seeking
        val seekTimerF = ResettableTimer()
        val seekTimerR = ResettableTimer()
        var seekTimesF = 0
        var seekTimesR = 0

        fun seek(
            forward: Boolean,
            event: MotionEvent? = null,
        ) {
            val seekTime = PrefManager.getVal<Int>(PrefName.SeekTime)
            val (card, text) =
                if (forward) {
                    val text = "+${seekTime * ++seekTimesF}"
                    forwardText.text = text
                    handler.post { seekTo(getCurrentPosition() + seekTime * 1000) }
                    fastForwardCard to forwardText
                } else {
                    val text = "-${seekTime * ++seekTimesR}"
                    rewindText.text = text
                    handler.post { seekTo(getCurrentPosition() - seekTime * 1000) }
                    fastRewindCard to rewindText
                }

            val showCardAnim = ObjectAnimator.ofFloat(card, "alpha", 0f, 1f).setDuration(300)
            val showTextAnim = ObjectAnimator.ofFloat(text, "alpha", 0f, 1f).setDuration(150)

            fun startAnim() {
                showTextAnim.start()

                (text.compoundDrawables[1] as Animatable).apply {
                    if (!isRunning) start()
                }

                if (!isSeeking && event != null) {
                    playerView.hideController()
                    card.circularReveal(event.x.toInt(), event.y.toInt(), !forward, 800)
                    showCardAnim.start()
                }
            }

            fun stopAnim() {
                handler.post {
                    showCardAnim.cancel()
                    showTextAnim.cancel()
                    ObjectAnimator.ofFloat(card, "alpha", card.alpha, 0f).setDuration(150).start()
                    ObjectAnimator.ofFloat(text, "alpha", 1f, 0f).setDuration(150).start()
                }
            }

            startAnim()

            isSeeking = true

            if (forward) {
                seekTimerR.reset(
                    object : TimerTask() {
                        override fun run() {
                            isSeeking = false
                            stopAnim()
                            seekTimesF = 0
                        }
                    },
                    850,
                )
            } else {
                seekTimerF.reset(
                    object : TimerTask() {
                        override fun run() {
                            isSeeking = false
                            stopAnim()
                            seekTimesR = 0
                        }
                    },
                    850,
                )
            }
        }

        if (!PrefManager.getVal<Boolean>(PrefName.DoubleTap)) {
            playerView.findViewById<View>(R.id.exo_fast_forward_button_cont).visibility =
                View.VISIBLE
            playerView.findViewById<View>(R.id.exo_fast_rewind_button_cont).visibility =
                View.VISIBLE
            playerView.findViewById<ImageButton>(R.id.exo_fast_forward_button).setOnClickListener {
                if (isInitialized) {
                    seek(true)
                }
            }
            playerView.findViewById<ImageButton>(R.id.exo_fast_rewind_button).setOnClickListener {
                if (isInitialized) {
                    seek(false)
                }
            }
        }

        keyMap[KEYCODE_DPAD_RIGHT] = { seek(true) }
        keyMap[KEYCODE_DPAD_LEFT] = { seek(false) }

        // Screen Gestures
        if (PrefManager.getVal<Boolean>(PrefName.Gestures) || PrefManager.getVal<Boolean>(PrefName.DoubleTap)) {
            fun doubleTap(
                forward: Boolean,
                event: MotionEvent,
            ) {
                if (!locked && isInitialized && PrefManager.getVal<Boolean>(PrefName.DoubleTap)) {
                    seek(forward, event)
                }
            }

            // Brightness
            var brightnessTimer = Timer()
            exoBrightnessCont.visibility = View.GONE

            fun brightnessHide() {
                brightnessTimer.cancel()
                brightnessTimer.purge()
                val timerTask: TimerTask =
                    object : TimerTask() {
                        override fun run() {
                            handler.post(brightnessRunnable)
                        }
                    }
                brightnessTimer = Timer()
                brightnessTimer.schedule(timerTask, 3000)
            }
            exoBrightness.value = (getCurrentBrightnessValue(this) * 10f)

            exoBrightness.addOnChangeListener { _, value, _ ->
                val lp = window.attributes
                lp.screenBrightness =
                    brightnessConverter((value.takeIf { !it.isNaN() } ?: 0f) / 10, false)
                window.attributes = lp
                brightnessHide()
            }

            // Volume
            var volumeTimer = Timer()
            exoVolumeCont.visibility = View.GONE

            val volumeMax = audioManager.getStreamMaxVolume(STREAM_MUSIC)
            exoVolume.value = audioManager.getStreamVolume(STREAM_MUSIC).toFloat() / volumeMax * 10

            fun volumeHide() {
                volumeTimer.cancel()
                volumeTimer.purge()
                val timerTask: TimerTask =
                    object : TimerTask() {
                        override fun run() {
                            handler.post(volumeRunnable)
                        }
                    }
                volumeTimer = Timer()
                volumeTimer.schedule(timerTask, 3000)
            }
            exoVolume.addOnChangeListener { _, value, _ ->
                val volume = ((value.takeIf { !it.isNaN() } ?: 0f) / 10 * volumeMax).roundToInt()
                audioManager.setStreamVolume(STREAM_MUSIC, volume, 0)
                volumeHide()
            }
            val fastForward = playerView.findViewById<TextView>(R.id.exo_fast_forward_text)

            fun fastForward() {
                isFastForwarding = true
                setSpeed(getSpeed() * 2)
                fastForward.visibility = View.VISIBLE
                val speedText = "${getSpeed()}x"
                fastForward.text = speedText
            }

            fun stopFastForward() {
                if (isFastForwarding) {
                    isFastForwarding = false
                    setSpeed(getSpeed() / 2)
                    fastForward.visibility = View.GONE
                }
            }

            // FastRewind (Left Panel)
            val fastRewindDetector =
                GestureDetector(
                    this,
                    object : GesturesListener() {
                        override fun onLongClick(event: MotionEvent) {
                            if (PrefManager.getVal(PrefName.FastForward)) fastForward()
                        }

                        override fun onDoubleClick(event: MotionEvent) {
                            doubleTap(false, event)
                        }

                        override fun onScrollYClick(y: Float) {
                            if (!locked && PrefManager.getVal(PrefName.Gestures)) {
                                exoBrightness.value = clamp(exoBrightness.value + y / 100, 0f, 10f)
                                if (exoBrightnessCont.visibility != View.VISIBLE) {
                                    exoBrightnessCont.visibility = View.VISIBLE
                                }
                                exoBrightnessCont.alpha = 1f
                            }
                        }

                        override fun onSingleClick(event: MotionEvent) =
                            if (isSeeking) doubleTap(false, event) else handleController()
                    },
                )
            val rewindArea = playerView.findViewById<View>(R.id.exo_rewind_area)
            rewindArea.isClickable = true
            rewindArea.setOnTouchListener { v, event ->
                fastRewindDetector.onTouchEvent(event)
                if (event.action == MotionEvent.ACTION_UP) stopFastForward()
                v.performClick()
                true
            }

            // FastForward (Right Panel)
            val fastForwardDetector =
                GestureDetector(
                    this,
                    object : GesturesListener() {
                        override fun onLongClick(event: MotionEvent) {
                            if (PrefManager.getVal(PrefName.FastForward)) fastForward()
                        }

                        override fun onDoubleClick(event: MotionEvent) {
                            doubleTap(true, event)
                        }

                        override fun onScrollYClick(y: Float) {
                            if (!locked && PrefManager.getVal(PrefName.Gestures)) {
                                exoVolume.value = clamp(exoVolume.value + y / 100, 0f, 10f)
                                if (exoVolumeCont.visibility != View.VISIBLE) {
                                    exoVolumeCont.visibility = View.VISIBLE
                                }
                                exoVolumeCont.alpha = 1f
                            }
                        }

                        override fun onSingleClick(event: MotionEvent) =
                            if (isSeeking) doubleTap(true, event) else handleController()
                    },
                )
            val forwardArea = playerView.findViewById<View>(R.id.exo_forward_area)
            forwardArea.isClickable = true
            forwardArea.setOnTouchListener { v, event ->
                fastForwardDetector.onTouchEvent(event)
                if (event.action == MotionEvent.ACTION_UP) stopFastForward()
                v.performClick()
                true
            }
        }

        // Handle Media
        if (!initialized) return startMainActivity(this)
        model.setMedia(media)
        title = media.userPreferredName
        episodes = media.anime?.episodes ?: return startMainActivity(this)

        videoInfo = playerView.findViewById(R.id.exo_video_info)

        model.watchSources = if (media.isAdult) HAnimeSources else AnimeSources

        model.epChanged.observe(this) {
            epChanging = !it
        }

        // Anime Title
        animeTitle.text = media.userPreferredName

        episodeArr = episodes.keys.toList()
        currentEpisodeIndex = episodeArr.indexOf(media.anime!!.selectedEpisode!!)

        episodeTitleArr = arrayListOf()
        episodes.forEach {
            val episode = it.value
            val cleanedTitle = MediaNameAdapter.removeEpisodeNumberCompletely(episode.title ?: "")
            episodeTitleArr.add(
                "Episode ${episode.number}${if (episode.filler) " [Filler]" else ""}${if (cleanedTitle.isNotBlank() && cleanedTitle != "null") ": $cleanedTitle" else ""}",
            )
        }

        // Episode Change
        fun change(index: Int) {
            if (isInitialized) {
                changingServer = false
                PrefManager.setCustomVal(
                    "${media.id}_${episodeArr[currentEpisodeIndex]}",
                    getCurrentPosition(),
                )
                val prev = episodeArr[currentEpisodeIndex]
                isTimeStampsLoaded = false
                episodeLength = 0f
                media.anime!!.selectedEpisode = episodeArr[index]
                model.setMedia(media)
                model.epChanged.postValue(false)
                model.setEpisode(episodes[media.anime!!.selectedEpisode!!]!!, "change")
                model.onEpisodeClick(
                    media,
                    media.anime!!.selectedEpisode!!,
                    this.supportFragmentManager,
                    false,
                    prev,
                )
            }
        }

        // EpisodeSelector
        episodeTitle.adapter = NoPaddingArrayAdapter(this, R.layout.item_dropdown, episodeTitleArr)
        episodeTitle.setSelection(currentEpisodeIndex)
        episodeTitle.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    position: Int,
                    p3: Long,
                ) {
                    if (position != currentEpisodeIndex) {
                        disappeared = false
                        functionstarted = false
                        change(position)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        // Next Episode
        exoNext = playerView.findViewById(R.id.exo_next_ep)
        exoNext.setOnClickListener {
            if (isInitialized) {
                nextEpisode { i ->
                    updateAniProgress()
                    disappeared = false
                    functionstarted = false
                    change(currentEpisodeIndex + i)
                }
            }
        }
        // Prev Episode
        exoPrev = playerView.findViewById(R.id.exo_prev_ep)
        exoPrev.setOnClickListener {
            if (currentEpisodeIndex > 0) {
                disappeared = false
                change(currentEpisodeIndex - 1)
            } else {
                snackString(getString(R.string.first_episode))
            }
        }

        model.getEpisode().observe(this) { ep ->
            hideSystemBars()
            if (ep != null && !epChanging) {
                episode = ep
                media.selected = model.loadSelected(media)
                model.setMedia(media)
                currentEpisodeIndex = episodeArr.indexOf(ep.number)
                episodeTitle.setSelection(currentEpisodeIndex)
                if (isInitialized) releasePlayer()
                playbackPosition =
                    PrefManager.getCustomVal(
                        "${media.id}_${ep.number}",
                        0,
                    )
                initPlayer()
                preloading = false
            }
        }

        // FullScreen
        isFullscreen = PrefManager.getCustomVal("${media.id}_fullscreenInt", isFullscreen)
        // MPV doesn't have built-in resize modes, we'll handle this differently
        // For now, just store the value

        exoScreen.setOnClickListener {
            if (isFullscreen < 2) isFullscreen += 1 else isFullscreen = 0
            snackString(
                when (isFullscreen) {
                    0 -> "Original"
                    1 -> "Zoom"
                    2 -> "Stretch"
                    else -> "Original"
                },
            )
            PrefManager.setCustomVal("${media.id}_fullscreenInt", isFullscreen)
            // TODO: Implement resize modes with MPV
        }

        // Settings
        exoSettings.setOnClickListener {
            PrefManager.setCustomVal(
                "${media.id}_${media.anime!!.selectedEpisode}",
                getCurrentPosition(),
            )
            val intent =
                Intent(this, PlayerSettingsActivity::class.java).apply {
                    putExtra("subtitle", subtitle)
                }
            pause()
            onChangeSettings.launch(intent)
        }

        // Speed
        val speeds =
            if (PrefManager.getVal(PrefName.CursedSpeeds)) {
                arrayOf(1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f, 5f, 10f, 25f, 50f)
            } else {
                arrayOf(
                    0.25f,
                    0.33f,
                    0.5f,
                    0.66f,
                    0.75f,
                    1f,
                    1.15f,
                    1.25f,
                    1.33f,
                    1.5f,
                    1.66f,
                    1.75f,
                    2f,
                )
            }

        val speedsName = speeds.map { "${it}x" }.toTypedArray()
        val savedIndex = PrefManager.getCustomVal(
            "${media.id}_speed",
            PrefManager.getVal<Int>(PrefName.DefaultSpeed),
        )
        var curSpeed = savedIndex.coerceIn(0, speedsLength - 1)

        playbackParameters = PlaybackParameters(speeds[curSpeed])
        var speed: Float
        exoSpeed.setOnClickListener {
            customAlertDialog().apply {
                setTitle(R.string.speed)
                singleChoiceItems(speedsName, curSpeed) { i ->
                    PrefManager.setCustomVal("${media.id}_speed", i)
                    speed = speeds.getOrNull(i) ?: 1f
                    curSpeed = i
                    setSpeed(speed)
                    hideSystemBars()
                }
                setOnCancelListener { hideSystemBars() }
                show()
            }
        }

        if (PrefManager.getVal(PrefName.AutoPlay)) {
            var touchTimer = Timer()

            fun touched() {
                interacted = true
                touchTimer.apply {
                    cancel()
                    purge()
                }
                touchTimer = Timer()
                touchTimer.schedule(
                    object : TimerTask() {
                        override fun run() {
                            interacted = false
                        }
                    },
                    1000 * 60 * 60,
                )
            }
            playerView.findViewById<View>(R.id.exo_touch_view).setOnTouchListener { _, _ ->
                touched()
                false
            }
        }

        isFullscreen = PrefManager.getVal(PrefName.Resize)

        preloading = false
        val incognito: Boolean = PrefManager.getVal(PrefName.Incognito)
        val showProgressDialog =
            if (PrefManager.getVal(PrefName.AskIndividualPlayer)) {
                PrefManager.getCustomVal(
                    "${media.id}_progressDialog",
                    true,
                )
            } else {
                false
            }
        if (!incognito &&
            showProgressDialog &&
            Anilist.userid != null &&
            if (media.isAdult) {
                PrefManager.getVal(
                    PrefName.UpdateForHPlayer,
                )
            } else {
                true
            }
        ) {
            customAlertDialog().apply {
                setTitle(getString(R.string.auto_update, media.userPreferredName))
                setCancelable(false)
                setPosButton(R.string.yes) {
                    PrefManager.setCustomVal(
                        "${media.id}_progressDialog",
                        false,
                    )
                    PrefManager.setCustomVal(
                        "${media.id}_save_progress",
                        true,
                    )
                    model.setEpisode(episodes[media.anime!!.selectedEpisode!!]!!, "invoke")
                }
                setNegButton(R.string.no) {
                    PrefManager.setCustomVal(
                        "${media.id}_progressDialog",
                        false,
                    )
                    PrefManager.setCustomVal(
                        "${media.id}_save_progress",
                        false,
                    )
                    toast(getString(R.string.reset_auto_update))
                    model.setEpisode(episodes[media.anime!!.selectedEpisode!!]!!, "invoke")
                }
                setOnCancelListener { hideSystemBars() }
                show()
            }
        } else {
            model.setEpisode(episodes[media.anime!!.selectedEpisode!!]!!, "invoke")
        }

        // Start the recursive Fun
        if (PrefManager.getVal(PrefName.TimeStampsEnabled)) {
            updateTimeStamp()
        }

        // Set up MPV event observer
        setupMPVObserver()
    }

    private fun setupMPVObserver() {
        lifecycleScope.launch {
            // Observe MPV properties
            // This would need to be implemented based on mpv-android's event system
            // For now, we'll use a simple polling approach
            while (isInitialized) {
                delay(100)
                updatePlaybackState()
            }
        }
    }

    private fun updatePlaybackState() {
        if (!isInitialized) return
        
        val pos = getCurrentPosition()
        val dur = getDuration()
        
        if (dur > 0) {
            if (episodeLength == 0f) {
                episodeLength = dur.toFloat()
                discordRPC()
            }
            
            // Update video info
            val height = mpvView.videoH
            if (height != null) {
                videoInfo.text = getString(R.string.video_quality, height)
            }
            
            // Check if we should show the first frame
            if (!isTimeStampsLoaded && PrefManager.getVal(PrefName.TimeStampsEnabled)) {
                val mediaIdMAL = media.idMAL
                val episodeNum = media.anime?.selectedEpisode?.trim()?.toIntOrNull()
                if (mediaIdMAL != null && episodeNum != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        model.loadTimeStamps(
                            mediaIdMAL,
                            episodeNum,
                            dur / 1000,
                            PrefManager.getVal(PrefName.UseProxyForTimeStamps),
                        )
                    }
                }
            }
            
            // Check if we should preload next episode
            if (pos.toFloat() / dur > PrefManager.getVal<Float>(PrefName.WatchPercentage)) {
                if (!preloading) {
                    preloading = true
                    nextEpisode(false) { i ->
                        val ep = episodes[episodeArr[currentEpisodeIndex + i]] ?: return@nextEpisode
                        val selected = media.selected ?: return@nextEpisode
                        lifecycleScope.launch(Dispatchers.IO) {
                            if (media.selected!!.server != null) {
                                model.loadEpisodeSingleVideo(ep, selected, false)
                            } else {
                                model.loadEpisodeVideos(ep, selected.sourceIndex, false)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun discordRPC() {
        val context = this
        val ep = episode
        val offline: Boolean = PrefManager.getVal(PrefName.OfflineMode)
        val incognito: Boolean = PrefManager.getVal(PrefName.Incognito)
        val rpcenabled: Boolean = PrefManager.getVal(PrefName.rpcEnabled)
        if ((isOnline(context) && !offline) && Discord.token != null && !incognito && rpcenabled) {
            lifecycleScope.launch {
                val discordMode = PrefManager.getCustomVal("discord_mode", "dantotsu")
                val buttons =
                    when (discordMode) {
                        "nothing" ->
                            mutableListOf(
                                RPC.Link(getString(R.string.view_anime), media.shareLink ?: ""),
                            )

                        "dantotsu" ->
                            mutableListOf(
                                RPC.Link(getString(R.string.view_anime), media.shareLink ?: ""),
                                RPC.Link("Watch on Dantotsu", getString(R.string.dantotsu)),
                            )

                        "anilist" -> {
                            val userId = PrefManager.getVal<String>(PrefName.AnilistUserId)
                            val anilistLink = "https://anilist.co/user/$userId/"
                            mutableListOf(
                                RPC.Link(getString(R.string.view_anime), media.shareLink ?: ""),
                                RPC.Link("View My AniList", anilistLink),
                            )
                        }

                        else -> mutableListOf()
                    }
                val startTimestamp = Calendar.getInstance()
                val durationInSeconds =
                    if (getDuration() != 0L) (getDuration() / 1000).toInt() else 1440

                val endTimestamp =
                    Calendar.getInstance().apply {
                        timeInMillis = startTimestamp.timeInMillis
                        add(Calendar.SECOND, durationInSeconds)
                    }
                val presence =
                    RPC.createPresence(
                        RPC.Companion.RPCData(
                            applicationId = Discord.application_Id,
                            type = RPC.Type.WATCHING,
                            activityName = media.userPreferredName,
                            details =
                                ep.title?.takeIf { it.isNotEmpty() } ?: getString(
                                    R.string.episode_num,
                                    ep.number,
                                ),
                            startTimestamp = startTimestamp.timeInMillis,
                            stopTimestamp = endTimestamp.timeInMillis,
                            state = "Episode : ${ep.number}/${media.anime?.totalEpisodes ?: "??"}",
                            largeImage =
                                media.cover?.let {
                                    RPC.Link(
                                        media.userPreferredName,
                                        it,
                                    )
                                },
                            smallImage = RPC.Link("Dantotsu", Discord.small_Image),
                            buttons = buttons,
                        ),
                    )
                val intent =
                    Intent(context, DiscordService::class.java).apply {
                        putExtra("presence", presence)
                    }
                DiscordServiceRunningSingleton.running = true
                startService(intent)
            }
        }
    }

    private fun initPlayer() {
        checkNotch()

        PrefManager.setCustomVal(
            "${media.id}_current_ep",
            media.anime!!.selectedEpisode!!,
        )

        @Suppress("UNCHECKED_CAST")
        val list =
            (
                    PrefManager.getNullableCustomVal(
                        "continueAnimeList",
                        listOf<Int>(),
                        List::class.java,
                    ) as List<Int>
                    ).toMutableList()
        if (list.contains(media.id)) list.remove(media.id)
        list.add(media.id)
        PrefManager.setCustomVal("continueAnimeList", list)

        lifecycleScope.launch(Dispatchers.IO) {
            extractor?.onVideoStopped(video)
        }

        val ext = episode.extractors?.find { it.server.name == episode.selectedExtractor } ?: return
        extractor = ext
        video = ext.videos.getOrNull(episode.selectedVideo) ?: return
        val subLanguages =
            arrayOf(
                "Albanian",
                "Arabic",
                "Bosnian",
                "Bulgarian",
                "Chinese",
                "Croatian",
                "Czech",
                "Danish",
                "Dutch",
                "English",
                "Estonian",
                "Finnish",
                "French",
                "Georgian",
                "German",
                "Greek",
                "Hebrew",
                "Hindi",
                "Indonesian",
                "Irish",
                "Italian",
                "Japanese",
                "Korean",
                "Lithuanian",
                "Luxembourgish",
                "Macedonian",
                "Mongolian",
                "Norwegian",
                "Polish",
                "Portuguese",
                "Punjabi",
                "Romanian",
                "Russian",
                "Serbian",
                "Slovak",
                "Slovenian",
                "Spanish",
                "Turkish",
                "Ukrainian",
                "Urdu",
                "Vietnamese",
            )
        val lang = subLanguages[PrefManager.getVal(PrefName.SubLanguage)]
        subtitle = intent.getSerialized("subtitle")
            ?: when (
                val subLang: String? =
                    PrefManager.getNullableCustomVal(
                        "subLang_${media.id}",
                        null,
                        String::class.java
                    )
            ) {
                null -> {
                    when (episode.selectedSubtitle) {
                        null -> null
                        -1 ->
                            ext.subtitles.find {
                                it.language.contains(lang, ignoreCase = true) ||
                                        it.language.contains(
                                            getLanguageCode(lang),
                                            ignoreCase = true
                                        )
                            }

                        else -> ext.subtitles.getOrNull(episode.selectedSubtitle!!)
                    }
                }

                "None" -> ext.subtitles.let { null }
                else -> ext.subtitles.find { it.language == subLang }
            }

        // Subtitles
        hasExtSubtitles = ext.subtitles.isNotEmpty()
        if (hasExtSubtitles) {
            exoSubtitle.isVisible = hasExtSubtitles
            exoSubtitle.setOnClickListener {
                subClick()
            }
        }

        // Setup audio tracks
        audioLanguages.clear()
        ext.audioTracks.forEach {
            var code = getLanguageCode(it.lang)
            if (code == "all") code = "un"
            audioLanguages.add(Pair(it.lang, code))
        }

        // Source
        exoSource.setOnClickListener {
            sourceClick()
        }

        // Build the file path
        val videoUrl = if (ext.server.offline) {
            val titleName = ext.server.name.split("/").first()
            val episodeName = ext.server.name.split("/").last()
            val directory = getSubDirectory(this, MediaType.ANIME, false, titleName, episodeName)
            if (directory != null) {
                val files = directory.listFiles()
                val docFile = directory.listFiles().firstOrNull {
                    it.name?.endsWith(".mp4") == true ||
                    it.name?.endsWith(".mkv") == true ||
                    it.name?.endsWith(".${
                        Injekt.get<DownloadAddonManager>()
                            .extension
                            ?.extension
                            ?.getFileExtension()
                            ?.first ?: "ts"
                    }") == true
                }
                docFile?.absolutePath
            } else null
        } else {
            video!!.file.url
        }

        if (videoUrl == null) {
            snackString("File not found")
            return
        }

        // Load into MPV
        mpvView.loadUrl(videoUrl)

        // Set initial position
        if (playbackPosition > 0) {
            val time = String.format(
                "%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(playbackPosition),
                TimeUnit.MILLISECONDS.toMinutes(playbackPosition) -
                        TimeUnit.HOURS.toMinutes(
                            TimeUnit.MILLISECONDS.toHours(
                                playbackPosition,
                            ),
                        ),
                TimeUnit.MILLISECONDS.toSeconds(playbackPosition) -
                        TimeUnit.MINUTES.toSeconds(
                            TimeUnit.MILLISECONDS.toMinutes(
                                playbackPosition,
                            ),
                        ),
            )
            customAlertDialog().apply {
                setTitle(getString(R.string.continue_from, time))
                setCancelable(false)
                setPosButton(getString(R.string.yes)) {
                    mpvView.seekTo(playbackPosition)
                    mpvView.play()
                    isInitialized = true
                    isPlayerPlaying = true
                }
                setNegButton(getString(R.string.no)) {
                    playbackPosition = 0L
                    mpvView.play()
                    isInitialized = true
                    isPlayerPlaying = true
                }
                show()
            }
        } else {
            mpvView.play()
            isInitialized = true
            isPlayerPlaying = true
        }

        // Set playback speed
        setSpeed(playbackParameters.speed)
    }

    private fun releasePlayer() {
        isPlayerPlaying = !isPaused()
        playbackPosition = getCurrentPosition()
        disappeared = false
        functionstarted = false
        mpvView.stop()
        if (DiscordServiceRunningSingleton.running) {
            val stopIntent = Intent(this, DiscordService::class.java)
            DiscordServiceRunningSingleton.running = false
            stopService(stopIntent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (isInitialized) {
            outState.putInt(resumeWindow, 0) // Not applicable for MPV
            outState.putLong(resumePosition, getCurrentPosition())
        }
        outState.putInt(playerFullscreen, isFullscreen)
        outState.putBoolean(playerOnPlay, isPlayerPlaying)
        super.onSaveInstanceState(outState)
    }

    private fun sourceClick() {
        changingServer = true

        media.selected!!.server = null
        PrefManager.setCustomVal(
            "${media.id}_${media.anime!!.selectedEpisode}",
            getCurrentPosition(),
        )
        model.saveSelected(media.id, media.selected!!)
        model.onEpisodeClick(
            media,
            episode.number,
            this.supportFragmentManager,
            launch = false,
        )
    }

    private fun subClick() {
        PrefManager.setCustomVal(
            "${media.id}_${media.anime!!.selectedEpisode}",
            getCurrentPosition(),
        )
        model.saveSelected(media.id, media.selected!!)
        SubtitleDialogFragment().show(supportFragmentManager, "dialog")
    }

    override fun onPause() {
        super.onPause()
        orientationListener?.disable()
        if (isInitialized) {
            pause()
            if (getCurrentPosition() > 5000) {
                PrefManager.setCustomVal(
                    "${media.id}_${media.anime!!.selectedEpisode}",
                    getCurrentPosition(),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        orientationListener?.enable()
        hideSystemBars()
        if (isInitialized) {
            // Player view resumed
        }
    }

    override fun onStop() {
        pause()
        super.onStop()
    }

    private var wasPlaying = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (PrefManager.getVal(PrefName.FocusPause) && !epChanging) {
            if (isInitialized && !hasFocus) wasPlaying = !isPaused()
            if (hasFocus) {
                if (isInitialized && wasPlaying) play()
            } else {
                if (isInitialized) pause()
            }
        }
        super.onWindowFocusChanged(hasFocus)
    }

    // Link Preloading
    private var preloading = false

    private fun nextEpisode(
        toast: Boolean = true,
        runnable: ((Int) -> Unit),
    ) {
        var isFiller = true
        var i = 1
        while (isFiller) {
            if (episodeArr.size > currentEpisodeIndex + i) {
                isFiller =
                    if (PrefManager.getVal(PrefName.AutoSkipFiller)) {
                        episodes[episodeArr[currentEpisodeIndex + i]]?.filler
                            ?: false
                    } else {
                        false
                    }
                if (!isFiller) runnable.invoke(i)
                i++
            } else {
                if (toast) {
                    toast(getString(R.string.no_next_episode))
                }
                isFiller = false
            }
        }
    }

    // TimeStamp Updating
    private var currentTimeStamp: AniSkip.Stamp? = null
    private var skippedTimeStamps: MutableList<AniSkip.Stamp> = mutableListOf()

    private fun updateTimeStamp() {
        if (isInitialized) {
            val playerCurrentTime = getCurrentPosition() / 1000
            currentTimeStamp =
                model.timeStamps.value?.find { timestamp ->
                    timestamp.interval.startTime < playerCurrentTime &&
                            playerCurrentTime < (timestamp.interval.endTime - 1)
                }

            val new = currentTimeStamp
            timeStampText.text =
                if (new != null) {
                    fun disappearSkip() {
                        functionstarted = true
                        skipTimeButton.visibility = View.VISIBLE
                        exoSkip.visibility = View.GONE
                        skipTimeText.text = new.skipType.getType()
                        skipTimeButton.setOnClickListener {
                            seekTo((new.interval.endTime * 1000).toLong())
                        }
                        var timer: CountDownTimer? = null

                        fun cancelTimer() {
                            timer?.cancel()
                            timer = null
                            return
                        }
                        timer =
                            object : CountDownTimer(5000, 1000) {
                                override fun onTick(millisUntilFinished: Long) {
                                    if (new == null) {
                                        skipTimeButton.visibility = View.GONE
                                        exoSkip.isVisible =
                                            PrefManager.getVal<Int>(PrefName.SkipTime) > 0
                                        disappeared = false
                                        functionstarted = false
                                        cancelTimer()
                                    }
                                }

                                override fun onFinish() {
                                    skipTimeButton.visibility = View.GONE
                                    exoSkip.isVisible =
                                        PrefManager.getVal<Int>(PrefName.SkipTime) > 0
                                    disappeared = true
                                    functionstarted = false
                                    cancelTimer()
                                }
                            }
                        timer?.start()
                    }
                    if (PrefManager.getVal(PrefName.ShowTimeStampButton)) {
                        if (!functionstarted && !disappeared && PrefManager.getVal(PrefName.AutoHideTimeStamps)) {
                            disappearSkip()
                        } else if (!PrefManager.getVal<Boolean>(PrefName.AutoHideTimeStamps)) {
                            skipTimeButton.visibility = View.VISIBLE
                            exoSkip.visibility = View.GONE
                            skipTimeText.text = new.skipType.getType()
                            skipTimeButton.setOnClickListener {
                                seekTo((new.interval.endTime * 1000).toLong())
                            }
                        }
                    }
                    if (PrefManager.getVal(PrefName.AutoSkipOPED) &&
                        (new.skipType == "op" || new.skipType == "ed") &&
                        !skippedTimeStamps.contains(new)
                    ) {
                        seekTo((new.interval.endTime * 1000).toLong())
                        skippedTimeStamps.add(new)
                    }
                    if (PrefManager.getVal(PrefName.AutoSkipRecap) &&
                        new.skipType == "recap" &&
                        !skippedTimeStamps.contains(
                            new,
                        )
                    ) {
                        seekTo((new.interval.endTime * 1000).toLong())
                        skippedTimeStamps.add(new)
                    }
                    new.skipType.getType()
                } else {
                    disappeared = false
                    functionstarted = false
                    skipTimeButton.visibility = View.GONE
                    exoSkip.isVisible = PrefManager.getVal<Int>(PrefName.SkipTime) > 0
                    ""
                }
        }
        handler.postDelayed({
            updateTimeStamp()
        }, 500)
    }

    private val onChangeSettings =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { _: ActivityResult ->
            if (isInitialized) play()
        }

    private fun updateAniProgress() {
        val incognito: Boolean = PrefManager.getVal(PrefName.Incognito)
        val episodeEnd =
            getCurrentPosition().toFloat() / getDuration().toFloat() >
                    PrefManager.getVal<Float>(
                        PrefName.WatchPercentage,
                    )
        val episode0 = currentEpisodeIndex == 0 && PrefManager.getVal(PrefName.ChapterZeroPlayer)
        if (!incognito && (episodeEnd || episode0) && Anilist.userid != null
        ) {
            if (PrefManager.getCustomVal(
                    "${media.id}_save_progress",
                    true,
                ) &&
                (if (media.isAdult) PrefManager.getVal(PrefName.UpdateForHPlayer) else true)
            ) {
                if (episode0 && !episodeEnd) {
                    updateProgress(media, "0")
                } else {
                    media.anime!!.selectedEpisode?.apply {
                        updateProgress(media, this)
                    }
                }
            }
        }
    }

    @SuppressLint("UnsafeIntentLaunch")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        finishAndRemoveTask()
        startActivity(intent)
    }

    override fun onDestroy() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        CoroutineScope(Dispatchers.IO).launch {
            tryWithSuspend(true) {
                extractor?.onVideoStopped(video)
            }
        }

        if (isInitialized) {
            updateAniProgress()
            disappeared = false
            functionstarted = false
            releasePlayer()
        }

        super.onDestroy()
        finishAndRemoveTask()
    }

    // Enter PiP Mode
    @Suppress("DEPRECATION")
    private fun enterPipMode() {
        wasPlaying = !isPaused()
        if (!pipEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPictureInPictureMode(
                    PictureInPictureParams
                        .Builder()
                        .setAspectRatio(aspectRatio)
                        .build(),
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                enterPictureInPictureMode()
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun onPiPChanged(isInPictureInPictureMode: Boolean) {
        if (isInPictureInPictureMode) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            orientationListener?.disable()
        } else {
            orientationListener?.enable()
        }
        if (isInitialized) {
            PrefManager.setCustomVal(
                "${media.id}_${episode.number}",
                getCurrentPosition(),
            )
            if (wasPlaying) play()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        onPiPChanged(isInPictureInPictureMode)
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        onPiPChanged(isInPictureInPictureMode)
        super.onPictureInPictureUiStateChanged(pipState)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        onPiPChanged(isInPictureInPictureMode)
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    private val keyMap: MutableMap<Int, (() -> Unit)?> =
        mutableMapOf(
            KEYCODE_DPAD_RIGHT to null,
            KEYCODE_DPAD_LEFT to null,
            KEYCODE_SPACE to { exoPlay.performClick() },
            KEYCODE_N to { exoNext.performClick() },
            KEYCODE_B to { exoPrev.performClick() },
        )

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        if (keyMap.containsKey(event.keyCode)) {
            (event.action == ACTION_UP).also {
                if (isInitialized && it) keyMap[event.keyCode]?.invoke()
            }
            true
        } else {
            // Also pass to MPV
            mpvView.onKey(event) || super.dispatchKeyEvent(event)
        }

    // MPV wrapper functions to maintain compatibility
    private fun getCurrentPosition(): Long = mpvView.timePos?.toLong() ?: 0
    private fun getDuration(): Long = mpvView.duration?.toLong() ?: 0
    private fun isPaused(): Boolean = mpvView.paused ?: true
    private fun play() = mpvView.play()
    private fun pause() = mpvView.pause()
    private fun seekTo(position: Long) = mpvView.seekTo(position)
    private fun setSpeed(speed: Float) = mpvView.setSpeed(speed)
    private fun getSpeed(): Float = mpvView.speed ?: 1f

    data class PlaybackParameters(val speed: Float)
}

class CustomCastButton : androidx.mediarouter.app.MediaRouteButton {
    private var castCallback: (() -> Unit)? = null

    fun setCastCallback(castCallback: () -> Unit) {
        this.castCallback = castCallback
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
    )

    override fun performClick(): Boolean =
        if (PrefManager.getVal(PrefName.UseInternalCast)) {
            super.performClick()
        } else {
            castCallback?.let { it() }
            true
        }
}

// MPV View class based on Aniyomi's implementation
class AniyomiMPVView(context: Context, attrs: AttributeSet?) : BaseMPVView(context, attrs) {

    var isExiting = false

    private fun getPropertyInt(property: String): Int? {
        return MPVLib.getPropertyInt(property) as Int?
    }

    private fun getPropertyBoolean(property: String): Boolean? {
        return MPVLib.getPropertyBoolean(property) as Boolean?
    }

    private fun getPropertyDouble(property: String): Double? {
        return MPVLib.getPropertyDouble(property) as Double?
    }

    private fun getPropertyString(property: String): String? {
        return MPVLib.getPropertyString(property) as String?
    }

    val duration: Int?
        get() = getPropertyInt("duration")

    var timePos: Int?
        get() = getPropertyInt("time-pos")
        set(position) = MPVLib.setPropertyInt("time-pos", position!!)

    var paused: Boolean?
        get() = getPropertyBoolean("pause")
        set(paused) = MPVLib.setPropertyBoolean("pause", paused!!)

    var speed: Float?
        get() = getPropertyDouble("speed")?.toFloat()
        set(speed) = MPVLib.setPropertyDouble("speed", speed!!.toDouble())

    val hwdecActive: String
        get() = getPropertyString("hwdec-current") ?: "no"

    val videoH: Int?
        get() = getPropertyInt("video-params/h")

    /**
     * Returns the video aspect ratio. Rotation is taken into account.
     */
    fun getVideoOutAspect(): Double? {
        return getPropertyDouble("video-params/aspect")?.let {
            if (it < 0.001) return 0.0
            if ((getPropertyInt("video-params/rotate") ?: 0) % 180 == 90) 1.0 / it else it
        }
    }

    inner class TrackDelegate(private val name: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val v = getPropertyString(name)
            // we can get null here for "no" or other invalid value
            return v?.toIntOrNull() ?: -1
        }
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (value == -1) {
                MPVLib.setPropertyString(name, "no")
            } else {
                MPVLib.setPropertyInt(name, value)
            }
        }
    }

    var sid: Int by TrackDelegate("sid")
    var secondarySid: Int by TrackDelegate("secondary-sid")
    var aid: Int by TrackDelegate("aid")

    fun loadUrl(url: String) {
        MPVLib.setOptionString("ytdl", "no")
        MPVLib.setOptionString("tls-verify", "yes")
        MPVLib.setOptionString("tls-ca-file", "${context.filesDir.path}/cacert.pem")
        MPVLib.loadUrl(url)
    }

    fun seekTo(position: Long) {
        MPVLib.setPropertyInt("time-pos", position.toInt())
    }

    fun play() {
        MPVLib.setPropertyBoolean("pause", false)
    }

    fun pause() {
        MPVLib.setPropertyBoolean("pause", true)
    }

    fun stop() {
        MPVLib.command(arrayOf("stop"))
    }

    fun setSpeed(speed: Float) {
        MPVLib.setPropertyDouble("speed", speed.toDouble())
    }

    fun setVo(vo: String) {
        MPVLib.setOptionString("vo", vo)
    }

    override fun initOptions(vo: String) {
        setVo("gpu")
        MPVLib.setPropertyBoolean("pause", true)
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("hwdec", "auto")
        
        // Enable debanding for better visual quality
        MPVLib.setOptionString("deband", "yes")
        
        MPVLib.setOptionString("msg-level", "all=warn")

        MPVLib.setPropertyBoolean("keep-open", true)
        MPVLib.setPropertyBoolean("input-default-bindings", true)

        // Limit demuxer cache for mobile devices
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")
        
        // Set screenshot directory
        val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        screenshotDir.mkdirs()
        MPVLib.setOptionString("screenshot-directory", screenshotDir.path)

        // Setup subtitle options based on preferences
        setupSubtitlesOptions()
    }

    override fun observeProperties() {
        for ((name, format) in observedProps) MPVLib.observeProperty(name, format)
    }

    override fun postInitOptions() {
        // Nothing needed here
    }

    fun onKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_MULTIPLE || KeyEvent.isModifierKey(event.keyCode)) {
            return false
        }

        var mapped = KeyMapping.map.get(event.keyCode)
        if (mapped == null) {
            // Fallback to produced glyph
            if (!event.isPrintingKey) {
                return false
            }

            val ch = event.unicodeChar
            if (ch.and(KeyCharacterMap.COMBINING_ACCENT) != 0) {
                return false // dead key
            }
            mapped = ch.toChar().toString()
        }

        if (event.repeatCount > 0) {
            return true // eat event but ignore it, mpv has its own key repeat
        }

        val mod: MutableList<String> = mutableListOf()
        event.isShiftPressed && mod.add("shift")
        event.isCtrlPressed && mod.add("ctrl")
        event.isAltPressed && mod.add("alt")
        event.isMetaPressed && mod.add("meta")

        val action = if (event.action == KeyEvent.ACTION_DOWN) "keydown" else "keyup"
        mod.add(mapped)
        MPVLib.command(arrayOf(action, mod.joinToString("+")))

        return true
    }

    private val observedProps = mapOf(
        "chapter" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "chapter-list" to MPVLib.mpvFormat.MPV_FORMAT_NONE,
        "track-list" to MPVLib.mpvFormat.MPV_FORMAT_NONE,
        "time-pos" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "demuxer-cache-time" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "duration" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "volume" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "volume-max" to MPVLib.mpvFormat.MPV_FORMAT_INT64,
        "sid" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "secondary-sid" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "aid" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "speed" to MPVLib.mpvFormat.MPV_FORMAT_DOUBLE,
        "video-params/aspect" to MPVLib.mpvFormat.MPV_FORMAT_DOUBLE,
        "hwdec-current" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "pause" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
        "paused-for-cache" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
        "seeking" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
        "eof-reached" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
    )

    private fun setupSubtitlesOptions() {
        // Load subtitle preferences from PrefManager
        val fontSize = PrefManager.getVal<Int>(PrefName.FontSize)
        val font = when (PrefManager.getVal<Int>(PrefName.Font)) {
            0 -> "Poppins SemiBold"
            1 -> "Poppins Bold"
            2 -> "Poppins"
            3 -> "Poppins Thin"
            4 -> "Century Gothic"
            5 -> "Levenim MT Bold"
            6 -> "Blocky"
            else -> "Poppins SemiBold"
        }
        
        val primaryColor = PrefManager.getVal<Int>(PrefName.PrimaryColor)
        val borderColor = PrefManager.getVal<Int>(PrefName.SecondaryColor)
        val borderSize = PrefManager.getVal<Float>(PrefName.SubStroke)
        
        MPVLib.setOptionString("sub-font", font)
        MPVLib.setOptionString("sub-font-size", fontSize.toString())
        MPVLib.setOptionString("sub-color", toColorHexString(primaryColor))
        MPVLib.setOptionString("sub-border-color", toColorHexString(borderColor))
        MPVLib.setOptionString("sub-border-size", borderSize.toString())
        
        when (PrefManager.getVal<Int>(PrefName.Outline)) {
            0 -> MPVLib.setOptionString("sub-border-style", "outline")
            1 -> MPVLib.setOptionString("sub-border-style", "opaque")
            2 -> MPVLib.setOptionString("sub-border-style", "background")
        }
        
        // Set subtitle position
        val subBottomMargin = PrefManager.getVal<Float>(PrefName.SubBottomMargin)
        val pos = 100 - (subBottomMargin * 100).toInt()
        MPVLib.setOptionString("sub-pos", pos.coerceIn(0, 100).toString())
    }

    private fun toColorHexString(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }
}