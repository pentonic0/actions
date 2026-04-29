package com.mas.streamplayer

import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mas.streamplayer.data.AppSettings
import com.mas.streamplayer.data.Channel
import com.mas.streamplayer.data.DrmInfo
import com.mas.streamplayer.player.ExoPlayerView
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

class MainActivity : ComponentActivity() {
    // Shared state so onUserLeaveHint can check if we're in player mode
    var isPlayerActive = false
    var autoPipEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { NetworkStreamApp(activity = this) }
    }

    /** Enter PiP automatically when the user presses Home during playback */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayerActive && autoPipEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }
}

private data class StreamDraft(
    val title: String = "",
    val url: String = "",
    val kid: String = "",
    val key: String = "",
    val cookie: String = "",
    val referer: String = "",
    val origin: String = "",
    val userAgent: String = ""
)

private sealed interface ScreenState {
    data object Home : ScreenState
    data object Saved : ScreenState
    data object Settings : ScreenState
    data class Player(val channel: Channel) : ScreenState
}

@Composable
private fun NetworkStreamApp(activity: MainActivity? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("network_stream_prefs", Context.MODE_PRIVATE) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var screen by remember { mutableStateOf<ScreenState>(ScreenState.Home) }
    var draft by remember { mutableStateOf(loadLastDraft(prefs)) }
    val saved = remember { mutableStateListOf<StreamDraft>() }
    var appSettings by remember { mutableStateOf(loadAppSettings(prefs)) }

    LaunchedEffect(Unit) { saved.addAll(loadSavedStreams(prefs)) }
    LaunchedEffect(draft) { saveLastDraft(prefs, draft) }
    LaunchedEffect(darkMode) { prefs.edit().putBoolean("dark_mode", darkMode).apply() }
    LaunchedEffect(appSettings) { saveAppSettings(prefs, appSettings) }

    val colors = if (darkMode) darkColors() else lightColors()
    val scheme = if (darkMode) darkColorScheme(
        background = colors.bg,
        surface = colors.card,
        primary = colors.accent,
        onSurface = colors.text
    ) else lightColorScheme(
        background = colors.bg,
        surface = colors.card,
        primary = colors.accent,
        onSurface = colors.text
    )

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = colors.bg) {
            when (val s = screen) {
                ScreenState.Home -> MainScaffold(
                    selectedTab = 0,
                    colors = colors,
                    onHome = { screen = ScreenState.Home },
                    onSaved = { screen = ScreenState.Saved },
                    onSettings = { screen = ScreenState.Settings }
                ) { padding ->
                    HomeScreen(
                        modifier = Modifier.padding(padding),
                        colors = colors,
                        draft = draft,
                        onDraftChange = { draft = it },
                        onPlay = {
                            val channel = draft.toChannel()
                            if (channel == null) {
                                Toast.makeText(context, "Please enter a stream URL", Toast.LENGTH_SHORT).show()
                            } else {
                                screen = ScreenState.Player(channel)
                            }
                        },
                        onReset = {
                            draft = StreamDraft()
                            Toast.makeText(context, "Form cleared", Toast.LENGTH_SHORT).show()
                        },
                        onSave = {
                            val channel = draft.toChannel()
                            if (channel == null) {
                                Toast.makeText(context, "Please enter a stream URL before saving", Toast.LENGTH_SHORT).show()
                            } else {
                                val clean = draft.normalizedTitle()
                                saved.removeAll { old -> old.url == clean.url }
                                saved.add(0, clean)
                                persistSavedStreams(prefs, saved)
                                Toast.makeText(context, "Stream saved", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                ScreenState.Saved -> MainScaffold(
                    selectedTab = 1,
                    colors = colors,
                    onHome = { screen = ScreenState.Home },
                    onSaved = { screen = ScreenState.Saved },
                    onSettings = { screen = ScreenState.Settings }
                ) { padding ->
                    SavedScreen(
                        modifier = Modifier.padding(padding),
                        colors = colors,
                        saved = saved,
                        onPlay = { screen = ScreenState.Player(it.toChannel()!!) },
                        onEdit = { draft = it; screen = ScreenState.Home },
                        onDelete = { item -> saved.remove(item); persistSavedStreams(prefs, saved) }
                    )
                }
                ScreenState.Settings -> MainScaffold(
                    selectedTab = 2,
                    colors = colors,
                    onHome = { screen = ScreenState.Home },
                    onSaved = { screen = ScreenState.Saved },
                    onSettings = { screen = ScreenState.Settings }
                ) { padding ->
                    SettingsScreen(
                        modifier = Modifier.padding(padding),
                        colors = colors,
                        darkMode = darkMode,
                        onDarkModeChange = { darkMode = it },
                        appSettings = appSettings,
                        onAppSettingsChange = { appSettings = it }
                    )
                }
                is ScreenState.Player -> {
                    LaunchedEffect(Unit) {
                        activity?.isPlayerActive = true
                        activity?.autoPipEnabled = appSettings.autoPip
                    }
                    DisposableEffect(Unit) { onDispose { activity?.isPlayerActive = false } }
                    BackHandler { screen = ScreenState.Home }
                    ExoPlayerView(
                        channel = s.channel,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { screen = ScreenState.Home },
                        appSettings = appSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(
    selectedTab: Int,
    colors: AppColors,
    onHome: () -> Unit,
    onSaved: () -> Unit,
    onSettings: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = colors.bg,
        bottomBar = {
            NavigationBar(
                containerColor = colors.nav,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, colors.border.copy(alpha = 0.48f), RoundedCornerShape(22.dp))
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = onHome,
                    icon = { Icon(Icons.Filled.Home, null, modifier = Modifier.size(20.dp)) },
                    label = { Text("Home", fontSize = 10.sp, maxLines = 1) },
                    alwaysShowLabel = false
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = onSaved,
                    icon = { Icon(Icons.Filled.BookmarkBorder, null, modifier = Modifier.size(20.dp)) },
                    label = { Text("Saved", fontSize = 10.sp, maxLines = 1) },
                    alwaysShowLabel = false
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = onSettings,
                    icon = { Icon(Icons.Filled.Settings, null, modifier = Modifier.size(20.dp)) },
                    label = { Text("Settings", fontSize = 10.sp, maxLines = 1) },
                    alwaysShowLabel = false
                )
            }
        },
        content = content
    )
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    colors: AppColors,
    draft: StreamDraft,
    onDraftChange: (StreamDraft) -> Unit,
    onPlay: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sidePadding = if (isLandscape) 24.dp else 14.dp
    val verticalPadding = if (isLandscape) 10.dp else 12.dp
    val itemGap = if (isLandscape) 10.dp else 12.dp

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.bgTop, colors.bg)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = sidePadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(itemGap)
        ) {
            HeroHeader(colors = colors, onReset = onReset)

            FormSection("Stream Info", "Add a playable network stream URL", colors) {
                StreamField("Stream title", draft.title, colors) { onDraftChange(draft.copy(title = it)) }
                StreamField("Media Stream URL", draft.url, colors, singleLine = false) { onDraftChange(draft.copy(url = it)) }
            }

            FormSection("DRM ClearKey", "Optional KID and KEY for protected streams", colors) {
                StreamField("KID", draft.kid, colors) { onDraftChange(draft.copy(kid = it)) }
                StreamField("KEY", draft.key, colors) { onDraftChange(draft.copy(key = it)) }
            }

            FormSection("Request Headers", "Optional Cookie, Referer, Origin and UserAgent", colors) {
                StreamField("Cookie Value", draft.cookie, colors, singleLine = false) { onDraftChange(draft.copy(cookie = it)) }
                StreamField("Referer Value", draft.referer, colors) { onDraftChange(draft.copy(referer = it)) }
                StreamField("Origin Value", draft.origin, colors) { onDraftChange(draft.copy(origin = it)) }
                UserAgentSelector(value = draft.userAgent, colors = colors, onValueChange = { onDraftChange(draft.copy(userAgent = it)) })
            }

            Spacer(Modifier.height(if (isLandscape) 70.dp else 78.dp))
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 220.dp)
                .padding(bottom = if (isLandscape) 10.dp else 12.dp),
            shape = RoundedCornerShape(26.dp),
            color = colors.card.copy(alpha = 0.97f),
            shadowElevation = 9.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.58f))
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSave,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(colors.chip)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = "Save", tint = colors.text, modifier = Modifier.size(21.dp))
                }
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(colors.fab)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = colors.fabText, modifier = Modifier.size(27.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(colors: AppColors, onReset: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.Transparent, shadowElevation = 4.dp) {
        Box(
            Modifier
                .background(Brush.linearGradient(listOf(colors.heroStart, colors.heroEnd)))
                .border(1.dp, colors.border.copy(alpha = 0.42f), RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = colors.accent.copy(alpha = 0.16f), contentColor = colors.accent) {
                    Icon(Icons.Filled.CloudQueue, null, modifier = Modifier.padding(9.dp).size(23.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Network Stream", color = colors.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Paste URL, add DRM/headers, then play instantly", color = colors.muted, fontSize = 11.sp, lineHeight = 14.sp)
                }
                Surface(
                    modifier = Modifier.size(38.dp).clickable(onClick = onReset),
                    shape = CircleShape,
                    color = colors.card.copy(alpha = 0.82f),
                    contentColor = colors.accent,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.65f))
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Refresh, contentDescription = "Reset form", modifier = Modifier.size(19.dp)) } }
            }
        }
    }
}

@Composable
private fun FormSection(title: String, subtitle: String, colors: AppColors, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.card,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(colors.accent))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = colors.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            content()
        }
    }
}


private val userAgentOptions = listOf(
    "Default" to "",
    "Chrome(Android)" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    "Chrome(PC)" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "IE(PC)" to "Mozilla/5.0 (Windows NT 10.0; WOW64; Trident/7.0; rv:11.0) like Gecko",
    "Firefox(PC)" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
    "iPhone" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
    "Nokia" to "Mozilla/5.0 (Series40; Nokia501/10.0.0) Profile/MIDP-2.1 Configuration/CLDC-1.1 NokiaBrowser/3.0",
    "Custom" to "__custom__"
)

@Composable
private fun UserAgentSelector(value: String, colors: AppColors, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customText by remember(value) { mutableStateOf(value) }

    val selectedName = userAgentOptions.firstOrNull { (name, ua) -> name != "Custom" && ua == value }?.first
        ?: if (value.isBlank()) "Default" else "Custom"

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedName,
                onValueChange = {},
                readOnly = true,
                label = { Text("UserAgent") },
                trailingIcon = {
                    Text(
                        text = if (expanded) "▴" else "▾",
                        color = colors.accent,
                        fontSize = 16.sp,
                        modifier = Modifier.clickable { expanded = !expanded }.padding(horizontal = 12.dp)
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text,
                    focusedLabelColor = colors.muted,
                    unfocusedLabelColor = colors.muted,
                    cursorColor = colors.accent,
                    focusedContainerColor = colors.field,
                    unfocusedContainerColor = colors.field
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Box(Modifier.matchParentSize().clickable { expanded = true })
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(maxWidth).background(colors.card)
        ) {
            userAgentOptions.forEach { (name, ua) ->
                DropdownMenuItem(
                    text = { Text(name, color = colors.text, fontSize = 15.sp) },
                    onClick = {
                        expanded = false
                        if (name == "Custom") {
                            customText = value
                            showCustomDialog = true
                        } else {
                            onValueChange(ua)
                        }
                    }
                )
            }
        }
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.text,
            title = { Text("Custom UserAgent", fontSize = 22.sp) },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    singleLine = false,
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                        cursorColor = colors.accent,
                        focusedContainerColor = colors.field,
                        unfocusedContainerColor = colors.field
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = { TextButton(onClick = { showCustomDialog = false }) { Text("Cancel", color = colors.accent) } },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(customText.trim())
                    showCustomDialog = false
                }) { Text("OK", color = colors.accent) }
            }
        )
    }
}

@Composable
private fun StreamField(label: String, value: String, colors: AppColors, singleLine: Boolean = true, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.border,
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
            focusedLabelColor = colors.muted,
            unfocusedLabelColor = colors.muted,
            cursorColor = colors.accent,
            focusedContainerColor = colors.field,
            unfocusedContainerColor = colors.field
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SavedScreen(
    modifier: Modifier,
    colors: AppColors,
    saved: List<StreamDraft>,
    onPlay: (StreamDraft) -> Unit,
    onEdit: (StreamDraft) -> Unit,
    onDelete: (StreamDraft) -> Unit
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.bgTop, colors.bg)))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle("Saved Streams", "Tap a card to edit, or press play to start", colors) {
            Icon(Icons.Filled.BookmarkBorder, null, modifier = Modifier.size(24.dp))
        }
        if (saved.isEmpty()) {
            EmptyState(colors)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(saved, key = { it.url }) { item ->
                    SavedStreamCard(item = item, colors = colors, onPlay = onPlay, onEdit = onEdit, onDelete = onDelete)
                }
            }
        }
    }
}

@Composable
private fun SavedStreamCard(item: StreamDraft, colors: AppColors, onPlay: (StreamDraft) -> Unit, onEdit: (StreamDraft) -> Unit, onDelete: (StreamDraft) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onEdit(item) },
        shape = RoundedCornerShape(22.dp),
        color = colors.card,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.55f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = colors.accent.copy(alpha = 0.14f), contentColor = colors.accent) {
                Icon(Icons.Filled.Link, null, modifier = Modifier.padding(11.dp).size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.title.ifBlank { "Untitled Stream" }, color = colors.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.url, color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.kid.isNotBlank() && item.key.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(50), color = colors.accent.copy(alpha = 0.12f)) {
                        Text("ClearKey DRM", color = colors.accent, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            IconButton(onClick = { onPlay(item) }) { Icon(Icons.Filled.PlayCircle, "Play", tint = colors.accent, modifier = Modifier.size(30.dp)) }
            IconButton(onClick = { onDelete(item) }) { Icon(Icons.Filled.DeleteOutline, "Delete", tint = colors.muted) }
        }
    }
}

@Composable
private fun EmptyState(colors: AppColors) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.card, border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.55f))) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.BookmarkBorder, null, tint = colors.accent, modifier = Modifier.size(34.dp))
                Text("No saved stream yet", color = colors.text, fontWeight = FontWeight.Bold)
                Text("Saved streams will appear here", color = colors.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    colors: AppColors,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    appSettings: AppSettings,
    onAppSettingsChange: (AppSettings) -> Unit
) {
    var showSeekDialog by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.bgTop, colors.bg)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle("Settings", "Customize playback behavior and app appearance", colors) {
            Icon(Icons.Filled.Tune, null, modifier = Modifier.size(24.dp))
        }

        SettingsGroup("Appearance", colors) {
            SettingToggleRow("Dark Mode", "Switch between light and dark theme", darkMode, colors) { onDarkModeChange(it) }
        }

        SettingsGroup("Playback", colors) {
            SettingToggleRow("Auto picture-in-picture", "Enter PiP automatically when leaving player", appSettings.autoPip, colors) { onAppSettingsChange(appSettings.copy(autoPip = it)) }
            SettingToggleRow("Landscape player start", "Directly launch the player in landscape mode", appSettings.landscapeMode, colors) { onAppSettingsChange(appSettings.copy(landscapeMode = it)) }
            SettingToggleRow("Resume playing", "Continue playing after interruptions", appSettings.resumePlaying, colors) { onAppSettingsChange(appSettings.copy(resumePlaying = it)) }
            SettingToggleRow("Start as mute", "Video will start with audio muted", appSettings.startAsMute, colors) { onAppSettingsChange(appSettings.copy(startAsMute = it)) }

            Surface(
                modifier = Modifier.fillMaxWidth().clickable { showSeekDialog = true },
                shape = RoundedCornerShape(18.dp),
                color = colors.field,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.48f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Seek duration", color = colors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${appSettings.seekDurationSeconds} seconds for rewind/forward", color = colors.muted, fontSize = 12.sp)
                    }
                    Text("Change", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }

    if (showSeekDialog) {
        AlertDialog(
            onDismissRequest = { showSeekDialog = false },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.text,
            title = { Text("Seek duration", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15, 20, 30, 60).forEach { sec ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (appSettings.seekDurationSeconds == sec) colors.accent.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable {
                                    onAppSettingsChange(appSettings.copy(seekDurationSeconds = sec))
                                    showSeekDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("$sec seconds", color = colors.text, fontSize = 14.sp)
                            if (appSettings.seekDurationSeconds == sec) Icon(Icons.Filled.Check, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSeekDialog = false }) { Text("Cancel", color = colors.accent) } }
        )
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String, colors: AppColors, icon: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = colors.card,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.55f))
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = colors.accent.copy(alpha = 0.14f), contentColor = colors.accent) {
                Box(Modifier.padding(11.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = colors.text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, colors: AppColors, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.card,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = colors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SettingToggleRow(title: String, subtitle: String?, checked: Boolean, colors: AppColors, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.field,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border.copy(alpha = 0.48f))
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = colors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (subtitle != null) Text(subtitle, color = colors.muted, fontSize = 12.sp, lineHeight = 15.sp)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.fabText,
                    checkedTrackColor = colors.fab,
                    uncheckedThumbColor = colors.muted,
                    uncheckedTrackColor = colors.chip
                )
            )
        }
    }
}

private data class AppColors(
    val bg: Color,
    val bgTop: Color,
    val card: Color,
    val field: Color,
    val nav: Color,
    val chip: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val accent: Color,
    val fab: Color,
    val fabText: Color,
    val heroStart: Color,
    val heroEnd: Color
)

private fun lightColors() = AppColors(
    bg = Color(0xFFF7F8FF),
    bgTop = Color(0xFFEFF3FF),
    card = Color(0xFFFFFFFF),
    field = Color(0xFFF7F8FE),
    nav = Color(0xFFFFFFFF),
    chip = Color(0xFFECEFFF),
    text = Color(0xFF121522),
    muted = Color(0xFF687085),
    border = Color(0xFFD8DDEC),
    accent = Color(0xFF4B5DDB),
    fab = Color(0xFF4B5DDB),
    fabText = Color.White,
    heroStart = Color(0xFFFFFFFF),
    heroEnd = Color(0xFFEAF0FF)
)

private fun darkColors() = AppColors(
    bg = Color(0xFF090D15),
    bgTop = Color(0xFF10182A),
    card = Color(0xFF151B28),
    field = Color(0xFF101622),
    nav = Color(0xFF151B28),
    chip = Color(0xFF242C3D),
    text = Color(0xFFF4F7FF),
    muted = Color(0xFFA8B0C2),
    border = Color(0xFF30384B),
    accent = Color(0xFF9AA8FF),
    fab = Color(0xFF9AA8FF),
    fabText = Color(0xFF070B16),
    heroStart = Color(0xFF171F31),
    heroEnd = Color(0xFF111827)
)


private fun StreamDraft.toChannel(): Channel? {
    val parsedUrl = parseNsPlayerStreamUrl(url)
    if (parsedUrl.streamUrl.isBlank()) return null

    // Manual fields get priority. If they are empty, use NS Player / OTT style inline params.
    val manualKid = kid.trim()
    val manualKey = key.trim()
    val drmInfo = when {
        manualKid.isNotBlank() && manualKey.isNotBlank() -> DrmInfo(kid = manualKid, key = manualKey, scheme = "clearkey")
        parsedUrl.drmKid.isNotBlank() && parsedUrl.drmKey.isNotBlank() -> DrmInfo(
            kid = parsedUrl.drmKid,
            key = parsedUrl.drmKey,
            scheme = parsedUrl.drmScheme.ifBlank { "clearkey" }
        )
        parsedUrl.drmLicenseUrl.isNotBlank() && parsedUrl.drmScheme.isNotBlank() -> DrmInfo(
            scheme = parsedUrl.drmScheme,
            licenseUrl = parsedUrl.drmLicenseUrl
        )
        else -> null
    }

    val inlineHeaders = parsedUrl.headers
    val finalCookie = cookie.trim().ifBlank { inlineHeaders["Cookie"].orEmpty() }.ifBlank { null }
    val finalReferer = referer.trim().ifBlank { inlineHeaders["Referer"].orEmpty() }.ifBlank { null }
    val finalOrigin = origin.trim().ifBlank { inlineHeaders["Origin"].orEmpty() }.ifBlank { null }
    val finalUserAgent = userAgent.trim().ifBlank { inlineHeaders["User-Agent"].orEmpty() }.ifBlank { null }
    val extraHeaders = inlineHeaders.filterKeys { it !in setOf("Cookie", "Referer", "Origin", "User-Agent") }

    return Channel(
        name = title.trim().ifBlank { "Network Stream" },
        stream = parsedUrl.streamUrl,
        cookie = finalCookie,
        referer = finalReferer,
        origin = finalOrigin,
        userAgent = finalUserAgent,
        drm = drmInfo,
        extraHeaders = extraHeaders
    )
}

private data class ParsedStreamUrl(
    val streamUrl: String,
    val drmScheme: String = "",
    val drmKid: String = "",
    val drmKey: String = "",
    val drmLicenseUrl: String = "",
    val headers: Map<String, String> = emptyMap()
)

private fun parseNsPlayerStreamUrl(rawUrl: String): ParsedStreamUrl {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return ParsedStreamUrl("")

    val pipeIndex = trimmed.indexOf('|')
    if (pipeIndex < 0) return ParsedStreamUrl(trimmed)

    val streamUrl = trimmed.substring(0, pipeIndex).trim().trimEnd('?', '&')
    val paramText = trimmed.substring(pipeIndex + 1).trim().trimStart('?', '&')
    val params = paramText
        .split('&')
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val rawKey = urlDecode(part.substring(0, separator).trim())
            val value = urlDecode(part.substring(separator + 1).trim())
            normalizeParamName(rawKey) to value
        }
        .filter { (_, value) -> value.isNotBlank() }
        .toMap()

    val drmScheme = firstParam(params, "drmscheme", "drm", "licensetype", "inputstreamadaptivelicensetype")
        .lowercase()
        .let { scheme ->
            when (scheme) {
                "org.w3.clearkey", "clearkey", "clear_key", "clear-key", "clearkeydrm" -> "clearkey"
                "com.widevine.alpha", "widevine" -> "widevine"
                "com.microsoft.playready", "playready" -> "playready"
                else -> scheme
            }
        }

    val licenseValue = firstParam(
        params,
        "drmlicense", "drmkey", "license", "licensekey", "drmlicenseurl", "licenseurl",
        "inputstreamadaptivelicensekey", "inputstreamadaptivelicenseurl"
    )

    val inferredScheme = when {
        drmScheme.isNotBlank() -> drmScheme
        licenseValue.looksLikeClearKeyPair() -> "clearkey"
        else -> ""
    }

    val headers = params.mapNotNull { (key, value) ->
        val headerName = when (key) {
            "useragent", "ua" -> "User-Agent"
            "referer", "referrer" -> "Referer"
            "origin" -> "Origin"
            "cookie" -> "Cookie"
            "authorization", "auth" -> "Authorization"
            else -> null
        }
        headerName?.let { it to value }
    }.toMap()

    if (inferredScheme == "clearkey" && licenseValue.looksLikeClearKeyPair()) {
        val drmParts = licenseValue.split(':', limit = 2)
        return ParsedStreamUrl(
            streamUrl = streamUrl,
            drmScheme = "clearkey",
            drmKid = drmParts.getOrNull(0).orEmpty().trim(),
            drmKey = drmParts.getOrNull(1).orEmpty().trim(),
            headers = headers
        )
    }

    val licenseUrl = if (inferredScheme in setOf("widevine", "playready") && licenseValue.startsWith("http", ignoreCase = true)) {
        licenseValue.trim()
    } else ""

    return ParsedStreamUrl(
        streamUrl = streamUrl,
        drmScheme = inferredScheme,
        drmLicenseUrl = licenseUrl,
        headers = headers
    )
}

private fun firstParam(params: Map<String, String>, vararg names: String): String =
    names.firstNotNullOfOrNull { name -> params[name]?.trim()?.takeIf { value -> value.isNotBlank() } }.orEmpty()

private fun normalizeParamName(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9]"), "")

private fun String.looksLikeClearKeyPair(): Boolean {
    val clean = trim()
    if (':' !in clean) return false
    if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) return false
    val parts = clean.split(':', limit = 2)
    return parts.getOrNull(0).orEmpty().isNotBlank() && parts.getOrNull(1).orEmpty().isNotBlank()
}

private fun urlDecode(value: String): String = runCatching {
    URLDecoder.decode(value, "UTF-8")
}.getOrDefault(value)

private fun StreamDraft.normalizedTitle() = copy(title = title.trim().ifBlank { "Network Stream" }, url = url.trim())

private fun loadSavedStreams(prefs: android.content.SharedPreferences): List<StreamDraft> = runCatching {
    val arr = JSONArray(prefs.getString("saved_streams", "[]"))
    List(arr.length()) { i -> arr.getJSONObject(i).toDraft() }.filter { it.url.isNotBlank() }
}.getOrDefault(emptyList())

private fun persistSavedStreams(prefs: android.content.SharedPreferences, list: List<StreamDraft>) {
    val arr = JSONArray()
    list.take(100).forEach { arr.put(it.toJson()) }
    prefs.edit().putString("saved_streams", arr.toString()).apply()
}

private fun loadLastDraft(prefs: android.content.SharedPreferences): StreamDraft = runCatching {
    JSONObject(prefs.getString("last_draft", "{}") ?: "{}").toDraft()
}.getOrDefault(StreamDraft())

private fun saveLastDraft(prefs: android.content.SharedPreferences, draft: StreamDraft) {
    prefs.edit().putString("last_draft", draft.toJson().toString()).apply()
}

private fun StreamDraft.toJson() = JSONObject().apply {
    put("title", title); put("url", url); put("kid", kid); put("key", key); put("cookie", cookie); put("referer", referer); put("origin", origin); put("userAgent", userAgent)
}

private fun JSONObject.toDraft() = StreamDraft(
    title = optString("title"), url = optString("url"), kid = optString("kid"), key = optString("key"),
    cookie = optString("cookie"), referer = optString("referer"), origin = optString("origin"), userAgent = optString("userAgent")
)

private fun loadAppSettings(prefs: android.content.SharedPreferences): AppSettings = runCatching {
    AppSettings(
        autoPip = prefs.getBoolean("setting_auto_pip", false),
        landscapeMode = prefs.getBoolean("setting_landscape_mode", false),
        resumePlaying = prefs.getBoolean("setting_resume_playing", false),
        seekDurationSeconds = prefs.getInt("setting_seek_duration", 10),
        startAsMute = prefs.getBoolean("setting_start_mute", false)
    )
}.getOrDefault(AppSettings())

private fun saveAppSettings(prefs: android.content.SharedPreferences, s: AppSettings) {
    prefs.edit()
        .putBoolean("setting_auto_pip", s.autoPip)
        .putBoolean("setting_landscape_mode", s.landscapeMode)
        .putBoolean("setting_resume_playing", s.resumePlaying)
        .putInt("setting_seek_duration", s.seekDurationSeconds)
        .putBoolean("setting_start_mute", s.startAsMute)
        .apply()
}
