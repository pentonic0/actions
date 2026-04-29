package com.mas.streamplayer.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import androidx.annotation.OptIn as AndroidXOptIn
import kotlin.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.mas.streamplayer.data.AppSettings
import com.mas.streamplayer.data.Channel
import kotlinx.coroutines.delay

// ── Design tokens ──────────────────────────────────────────────────────────────
private val AccentCyan    = Color(0xFF00E5FF)
private val AccentCyanDim = Color(0x4400E5FF)
private val GlassDark     = Color(0xCC0A0D14)
private val GlassMid      = Color(0x990D1219)
private val GlassRim      = Color(0x33FFFFFF)
private val PanelBg       = Color(0xFF0B1220)
private val TextPrimary   = Color(0xFFECF4FF)
private val TextMuted     = Color(0xFF7A95B0)
private val TrackBg       = Color(0x30FFFFFF)

// ── Data models ────────────────────────────────────────────────────────────────
private data class VideoQualityOption(
    val label: String, val group: TrackGroup,
    val trackIndex: Int, val width: Int, val height: Int, val bitrate: Int
)
private data class AudioTrackOption(
    val label: String, val group: TrackGroup, val trackIndex: Int, val language: String?
)

// ── Root Composable ────────────────────────────────────────────────────────────
@AndroidXOptIn(UnstableApi::class)
@Composable
fun ExoPlayerView(
    channel: Channel,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    appSettings: AppSettings = AppSettings()
) {
    val context  = LocalContext.current
    val activity = context as? Activity

    // ── PiP helpers ──────────────────────────────────────────────────────────
    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            activity?.enterPictureInPictureMode(params)
        }
    }

    var isInPipMode by remember { mutableStateOf(false) }

    // Apply landscape mode setting
    LaunchedEffect(Unit) {
        if (appSettings.landscapeMode) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    // Track PiP state via lifecycle
    DisposableEffect(activity) {
        val observer = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(a: android.app.Activity) {
                if (a === activity) isInPipMode = a.isInPictureInPictureMode
            }
            override fun onActivityResumed(a: android.app.Activity) {
                if (a === activity) isInPipMode = a.isInPictureInPictureMode
            }
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityStarted(a: android.app.Activity) {}
            override fun onActivityStopped(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        }
        activity?.application?.registerActivityLifecycleCallbacks(observer)
        onDispose { activity?.application?.unregisterActivityLifecycleCallbacks(observer) }
    }

    var showControls    by remember(channel.stream) { mutableStateOf(true) }
    var isPlaying       by remember(channel.stream) { mutableStateOf(false) }
    var isBuffering     by remember(channel.stream) { mutableStateOf(true) }
    var hasError        by remember(channel.stream) { mutableStateOf(false) }
    var duration        by remember(channel.stream) { mutableLongStateOf(0L) }
    var currentPosition by remember(channel.stream) { mutableLongStateOf(0L) }

    var qualityOptions  by remember(channel.stream) { mutableStateOf<List<VideoQualityOption>>(emptyList()) }
    var audioOptions    by remember(channel.stream) { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var selectedQuality by remember(channel.stream) { mutableStateOf("Auto") }
    var selectedAudio   by remember(channel.stream) { mutableStateOf("Default") }
    var playbackSpeed   by remember(channel.stream) { mutableFloatStateOf(1.0f) }
    var isMuted         by remember(channel.stream) { mutableStateOf(appSettings.startAsMute) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showQualityDialog  by remember { mutableStateOf(false) }
    var showAudioDialog    by remember { mutableStateOf(false) }
    var showSpeedDialog    by remember { mutableStateOf(false) }

    val trackSelector = remember(channel.stream) { DefaultTrackSelector(context) }

    val player = remember(channel.stream) {
        ExoPlayer.Builder(context).setTrackSelector(trackSelector).build().apply {
            // Resume playing setting: if false, pause when audio focus is lost (phone calls, etc.)
            setHandleAudioBecomingNoisy(!appSettings.resumePlaying)
            val mediaItem = MediaItem.Builder().setUri(channel.stream).apply {
                when {
                    channel.stream.contains(".mpd",  true) -> setMimeType(MimeTypes.APPLICATION_MPD)
                    channel.stream.contains(".m3u8", true) -> setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }.build()

            val requestHeaders = mutableMapOf<String, String>().apply {
                putAll(channel.extraHeaders.filterValues { it.isNotBlank() })
                channel.cookie?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
                channel.referer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
                channel.origin?.takeIf { it.isNotBlank() }?.let { put("Origin", it) }
            }
            val httpFactory = DefaultHttpDataSource.Factory().apply {
                if (requestHeaders.isNotEmpty()) setDefaultRequestProperties(requestHeaders)
                channel.userAgent?.takeIf { it.isNotBlank() }?.let { setUserAgent(it) }
            }
            val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

            val drmSessionManager = channel.drm?.let { drm ->
                when (drm.scheme.lowercase()) {
                    "clearkey" -> if (drm.kid.isNotBlank() && drm.key.isNotBlank()) {
                        val clearKeyJson = ClearKeyUtil.buildClearKeyJson(drm.kid, drm.key)
                        DefaultDrmSessionManager.Builder()
                            .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                            .build(LocalMediaDrmCallback(clearKeyJson.toByteArray(Charsets.UTF_8)))
                    } else null
                    "widevine" -> drm.licenseUrl?.takeIf { it.isNotBlank() }?.let { licenseUrl ->
                        DefaultDrmSessionManager.Builder()
                            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                            .build(HttpMediaDrmCallback(licenseUrl, httpFactory))
                    }
                    "playready" -> drm.licenseUrl?.takeIf { it.isNotBlank() }?.let { licenseUrl ->
                        DefaultDrmSessionManager.Builder()
                            .setUuidAndExoMediaDrmProvider(C.PLAYREADY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                            .build(HttpMediaDrmCallback(licenseUrl, httpFactory))
                    }
                    else -> null
                }
            }

            if (drmSessionManager != null && channel.stream.contains(".mpd", true)) {
                setMediaSource(
                    DashMediaSource.Factory(dataSourceFactory)
                        .setDrmSessionManagerProvider { drmSessionManager }
                        .createMediaSource(mediaItem)
                )
            } else if (channel.stream.contains(".mpd", true)) {
                setMediaSource(DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem))
            } else if (channel.stream.contains(".m3u8", true)) {
                setMediaSource(HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem))
            } else {
                setMediaSource(ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem))
            }
            prepare(); playWhenReady = true
            // Apply start-as-mute setting
            if (appSettings.startAsMute) volume = 0f
        }
    }

    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(200)
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) { delay(4000); showControls = false }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                qualityOptions = buildAvailableVideoQualities(tracks)
                audioOptions   = buildAvailableAudioTracks(tracks)
            }
            override fun onIsPlayingChanged(playing: Boolean)    { isPlaying   = playing }
            override fun onPlaybackStateChanged(state: Int)      { isBuffering = state == Player.STATE_BUFFERING }
            override fun onPlayerError(error: PlaybackException) { hasError    = true }
        }
        player.addListener(listener)
        if (!player.currentTracks.isEmpty) {
            qualityOptions = buildAvailableVideoQualities(player.currentTracks)
            audioOptions   = buildAvailableAudioTracks(player.currentTracks)
        }
        isPlaying   = player.isPlaying
        isBuffering = player.playbackState == Player.STATE_BUFFERING
        hasError    = player.playerError != null
        onDispose { player.removeListener(listener); player.release() }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { showControls = !showControls }) }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    this.player = player; useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { it.player = player }
        )

        if (isBuffering || hasError) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BufferingPill(hasError)
             }
        }

        if (!isInPipMode) {
            AnimatedVisibility(
                visible = showControls,
                enter   = fadeIn(tween(180)),
                exit    = fadeOut(tween(280))
            ) {
                ModernPlayerControls(
                    channelTitle      = channel.name,
                    selectedQuality   = selectedQuality,
                    hasMultipleAudios = audioOptions.size > 1,
                    selectedAudio     = selectedAudio,
                    playbackSpeed     = playbackSpeed,
                    isPlaying         = isPlaying,
                    isMuted           = isMuted,
                    currentPosition   = currentPosition,
                    duration          = duration,
                    progress          = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                    onSeek            = { if (duration > 0) player.seekTo((it * duration).toLong()) },
                    onBack = {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        onBack?.invoke()
                    },
                    onPipClick        = { enterPip() },
                    onMuteClick       = {
                        val newMuted = !isMuted
                        isMuted = newMuted
                        player.volume = if (newMuted) 0f else 1f
                    },
                    onSettingsClick   = { showSettingsDialog = true },
                    onPlayPauseClick  = { if (player.isPlaying) player.pause() else player.play() },
                    onSkipBackward    = { player.seekTo((player.currentPosition - appSettings.seekDurationSeconds * 1000L).coerceAtLeast(0L)) },
                    onSkipForward     = {
                        player.seekTo(
                            (player.currentPosition + appSettings.seekDurationSeconds * 1000L)
                                .coerceAtMost(duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
                        )
                    },
                    seekDurationSeconds = appSettings.seekDurationSeconds
                )
            }
        }

        if (showSettingsDialog) {
            SettingsDialog(
                hasMultipleAudios = audioOptions.size > 1,
                selectedQuality   = selectedQuality,
                selectedAudio     = selectedAudio,
                playbackSpeed     = playbackSpeed,
                onDismiss   = { showSettingsDialog = false },
                onVideoClick = { showSettingsDialog = false; showQualityDialog = true },
                onAudioClick = { showSettingsDialog = false; showAudioDialog   = true },
                onSpeedClick = { showSettingsDialog = false; showSpeedDialog   = true }
            )
        }

        if (showQualityDialog) {
            SelectionDialog(
                title         = "Video Quality",
                selectedLabel = selectedQuality,
                options       = buildList { add("Auto"); addAll(qualityOptions.map { it.label }) },
                onDismiss     = { showQualityDialog = false },
                emptyMessage  = if (qualityOptions.isEmpty()) "No selectable quality found" else null,
                onSelect      = { label ->
                    if (label == "Auto") {
                        trackSelector.setParameters(
                            trackSelector.buildUponParameters()
                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                                .setMaxVideoBitrate(Int.MAX_VALUE)
                        ); selectedQuality = "Auto"
                    } else {
                        qualityOptions.firstOrNull { it.label == label }?.let { opt ->
                            trackSelector.setParameters(
                                trackSelector.buildUponParameters()
                                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                    .setOverrideForType(TrackSelectionOverride(opt.group, listOf(opt.trackIndex)))
                            ); selectedQuality = opt.label
                        }
                    }
                    showQualityDialog = false
                }
            )
        }

        if (showAudioDialog) {
            SelectionDialog(
                title         = "Audio Track",
                selectedLabel = selectedAudio,
                options       = buildList { add("Default"); addAll(audioOptions.map { it.label }) },
                onDismiss     = { showAudioDialog = false },
                emptyMessage  = if (audioOptions.isEmpty()) "Only one audio track available" else null,
                onSelect      = { label ->
                    if (label == "Default") {
                        trackSelector.setParameters(
                            trackSelector.buildUponParameters().clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        ); selectedAudio = "Default"
                    } else {
                        audioOptions.firstOrNull { it.label == label }?.let { opt ->
                            trackSelector.setParameters(
                                trackSelector.buildUponParameters()
                                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                    .setOverrideForType(TrackSelectionOverride(opt.group, listOf(opt.trackIndex)))
                            ); selectedAudio = opt.label
                        }
                    }
                    showAudioDialog = false
                }
            )
        }

        if (showSpeedDialog) {
            val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
            SelectionDialog(
                title         = "Playback Speed",
                selectedLabel = speedLabel(playbackSpeed),
                options       = speedOptions.map { speedLabel(it) },
                onDismiss     = { showSpeedDialog = false },
                emptyMessage  = null,
                onSelect      = { label ->
                    val speed = speedOptions.firstOrNull { speedLabel(it) == label } ?: 1.0f
                    playbackSpeed = speed
                    player.playbackParameters = PlaybackParameters(speed)
                    showSpeedDialog = false
                }
            )
        }
    }
}

// ── Buffering Pill ─────────────────────────────────────────────────────────────
@Composable
private fun BufferingPill(hasError: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(GlassDark)
            .border(0.5.dp, GlassRim, RoundedCornerShape(50.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (!hasError) {
            CircularProgressIndicator(
                modifier    = Modifier.size(14.dp),
                color       = AccentCyan,
                strokeWidth = 2.dp
            )
        }
        Text(
            text       = if (hasError) "⚠  Stream error" else "Buffering…",
            color      = if (hasError) Color(0xFFFF6B6B) else TextPrimary,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Player Controls ────────────────────────────────────────────────────────────
@Composable
private fun ModernPlayerControls(
    channelTitle: String,
    selectedQuality: String,
    hasMultipleAudios: Boolean,
    selectedAudio: String,
    playbackSpeed: Float,
    isPlaying: Boolean,
    isMuted: Boolean,
    currentPosition: Long,
    duration: Long,
    progress: Float,
    seekDurationSeconds: Int = 10,
    onSeek: (Float) -> Unit,
    onBack: () -> Unit,
    onPipClick: () -> Unit,
    onMuteClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {

        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xC0000000), Color.Transparent),
                        startY = 0f, endY = 200f
                    )
                )
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(onClick = onBack, size = 34.dp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                        tint = TextPrimary, modifier = Modifier.size(17.dp))
                }
                Text(
                    text = channelTitle,
                    color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassIconButton(onClick = onMuteClick, size = 34.dp) {
                        Icon(
                            imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    GlassIconButton(onClick = onPipClick, size = 34.dp) {
                        Icon(Icons.Filled.PictureInPicture, "PiP",
                            tint = TextPrimary, modifier = Modifier.size(16.dp))
                    }
                    GlassIconButton(onClick = onSettingsClick, size = 34.dp) {
                        Icon(Icons.Filled.Settings, "Settings",
                            tint = TextPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Centre transport
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(onClick = onSkipBackward, size = 44.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.FastRewind, "−${seekDurationSeconds}s",
                            tint = TextPrimary, modifier = Modifier.size(20.dp))
                        Text("$seekDurationSeconds", color = TextMuted, fontSize = 8.sp, lineHeight = 8.sp)
                    }
                }
                // Play / Pause
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(AccentCyan)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPlayPauseClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFF03111A),
                        modifier = Modifier.size(28.dp)
                    )
                }
                GlassIconButton(onClick = onSkipForward, size = 44.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.FastForward, "+${seekDurationSeconds}s",
                            tint = TextPrimary, modifier = Modifier.size(20.dp))
                        Text("$seekDurationSeconds", color = TextMuted, fontSize = 8.sp, lineHeight = 8.sp)
                    }
                }
            }
        }

        // Bottom seek bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xC0000000)),
                        startY = 0f, endY = 280f
                    )
                )
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp, top = 20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatMillis(currentPosition), color = TextPrimary,
                        fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = if (duration > 0) formatMillis(duration) else "LIVE",
                        color = if (duration <= 0) AccentCyan else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (duration <= 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
                CompactSlider(progress = progress, onSeek = onSeek)
            }
        }
    }
}

// ── Slim Seek Slider ───────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactSlider(progress: Float, onSeek: (Float) -> Unit) {
    Slider(
        value         = progress.coerceIn(0f, 1f),
        onValueChange = onSeek,
        modifier      = Modifier.fillMaxWidth().height(22.dp),
        colors = SliderDefaults.colors(
            thumbColor         = AccentCyan,
            activeTrackColor   = AccentCyan,
            inactiveTrackColor = TrackBg
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(AccentCyan)
                    .border(2.dp, Color(0xFF03111A), CircleShape)
            )
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                modifier    = Modifier.height(3.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor   = AccentCyan,
                    inactiveTrackColor = TrackBg
                )
            )
        }
    )
}

// ── Glass Pill ─────────────────────────────────────────────────────────────────
@Composable
private fun GlassPill(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(GlassMid)
            .border(0.5.dp, GlassRim, RoundedCornerShape(50.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onClick
            )
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

// ── Glass Icon Button ──────────────────────────────────────────────────────────
@Composable
private fun GlassIconButton(onClick: () -> Unit, size: Dp = 36.dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(GlassMid)
            .border(0.5.dp, GlassRim, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) { content() }
}

// ── Settings Dialog ────────────────────────────────────────────────────────────
@Composable
private fun SettingsDialog(
    hasMultipleAudios: Boolean,
    selectedQuality: String,
    selectedAudio: String,
    playbackSpeed: Float,
    onDismiss: () -> Unit,
    onVideoClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSpeedClick: () -> Unit
) {
    ModalSheet(title = "Settings", onDismiss = onDismiss) {
        SettingsRow("Playback speed", speedLabel(playbackSpeed), Icons.Filled.Speed, onSpeedClick)
        SettingsRow("Video quality", selectedQuality, Icons.Filled.HighQuality, onVideoClick)
        if (hasMultipleAudios) SettingsRow("Audio track", selectedAudio, Icons.Filled.GraphicEq, onAudioClick)
    }
}

// ── Selection Dialog ───────────────────────────────────────────────────────────
@Composable
private fun SelectionDialog(
    title: String, selectedLabel: String, options: List<String>,
    onDismiss: () -> Unit, onSelect: (String) -> Unit, emptyMessage: String?
) {
    ModalSheet(title = title, onDismiss = onDismiss) {
        if (emptyMessage != null && options.size <= 1) {
            Text(
                emptyMessage,
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x221B2A40))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        } else {
            options.forEachIndexed { i, label ->
                OptionRow(text = label, selected = label == selectedLabel, onClick = { onSelect(label) })
                if (i != options.lastIndex) HorizontalDivider(color = Color(0x14FFFFFF), thickness = 0.5.dp)
            }
        }
    }
}

// ── Modal Bottom Sheet ─────────────────────────────────────────────────────────
@Composable
private fun ModalSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        val sheetMaxHeight = maxHeight * 0.86f
        val listMaxHeight = maxHeight * 0.66f

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(PanelBg, Color(0xFF0A111E))
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(GlassRim, Color.Transparent)),
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, onClick = {}
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(30.dp).height(3.dp)
                    .clip(CircleShape)
                    .background(Color(0x40FFFFFF))
            )
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = listMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Settings Row ───────────────────────────────────────────────────────────────
@Composable
private fun SettingsRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x1A9CB8FF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Icon(Icons.Filled.KeyboardArrowRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

// ── Option Row ─────────────────────────────────────────────────────────────────
@Composable
private fun OptionRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AccentCyanDim else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text, color = if (selected) AccentCyan else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (selected) {
            Icon(Icons.Filled.Check, null, tint = AccentCyan, modifier = Modifier.size(15.dp))
        }
    }
}

// ── Utilities ──────────────────────────────────────────────────────────────────
private fun speedLabel(speed: Float): String = if (speed == 1.0f) "1.0×" else "${speed}×"

private fun formatMillis(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

@AndroidXOptIn(UnstableApi::class)
private fun buildAvailableVideoQualities(tracks: Tracks): List<VideoQualityOption> {
    val all = mutableListOf<VideoQualityOption>()
    tracks.groups.forEach { group ->
        if (group.type == C.TRACK_TYPE_VIDEO) {
            for (i in 0 until group.length) {
                if (group.isTrackSupported(i)) {
                    val f = group.getTrackFormat(i)
                    val label = buildString {
                        when { f.height > 0 -> append("${f.height}p"); f.width > 0 -> append("${f.width}w"); else -> append("Video ${i + 1}") }
                        if (f.bitrate > 0) append(" · ${f.bitrate / 1000}k")
                    }
                    all += VideoQualityOption(label, group.mediaTrackGroup, i, f.width, f.height, f.bitrate)
                }
            }
        }
    }
    return all
        .groupBy { if (it.height > 0) it.height else it.width }
        .map { (_, same) -> same.maxBy { it.bitrate } }
        .sortedWith(compareByDescending<VideoQualityOption> { it.height }.thenByDescending { it.width }.thenByDescending { it.bitrate })
}

@AndroidXOptIn(UnstableApi::class)
private fun buildAvailableAudioTracks(tracks: Tracks): List<AudioTrackOption> {
    val all = mutableListOf<AudioTrackOption>()
    tracks.groups.forEach { group ->
        if (group.type == C.TRACK_TYPE_AUDIO) {
            for (i in 0 until group.length) {
                if (group.isTrackSupported(i)) {
                    val f    = group.getTrackFormat(i)
                    val lang = f.language?.takeUnless { it.equals("und", true) || it.isBlank() }
                    val label = buildString {
                        when { !f.label.isNullOrBlank() -> append(f.label); lang != null -> append(lang.uppercase()); else -> append("Audio ${i + 1}") }
                        if (f.channelCount > 0) append(" · ${f.channelCount}ch")
                    }
                    all += AudioTrackOption(label, group.mediaTrackGroup, i, lang)
                }
            }
        }
    }
    return all.distinctBy { it.label }
}
