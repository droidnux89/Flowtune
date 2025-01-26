package com.abhiram.flowtune

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.imageLoader
import coil.request.ImageRequest
import com.abhiram.flowtune.constants.AppBarHeight
import com.abhiram.flowtune.constants.AutomaticScannerKey
import com.abhiram.flowtune.constants.DarkModeKey
import com.abhiram.flowtune.constants.DefaultOpenTabKey
import com.abhiram.flowtune.constants.DefaultOpenTabNewKey
import com.abhiram.flowtune.constants.DynamicThemeKey
import com.abhiram.flowtune.constants.EnabledTabsKey
import com.abhiram.flowtune.constants.ExcludedScanPathsKey
import com.abhiram.flowtune.constants.FirstSetupPassed
import com.abhiram.flowtune.constants.LastPosKey
import com.abhiram.flowtune.constants.LibraryFilter
import com.abhiram.flowtune.constants.LibraryFilterKey
import com.abhiram.flowtune.constants.LocalLibraryEnableKey
import com.abhiram.flowtune.constants.LookupYtmArtistsKey
import com.abhiram.flowtune.constants.MiniPlayerHeight
import com.abhiram.flowtune.constants.NavigationBarAnimationSpec
import com.abhiram.flowtune.constants.NavigationBarHeight
import com.abhiram.flowtune.constants.NewInterfaceKey
import com.abhiram.flowtune.constants.PauseSearchHistoryKey
import com.abhiram.flowtune.constants.PlayerBackgroundStyleKey
import com.abhiram.flowtune.constants.PureBlackKey
import com.abhiram.flowtune.constants.ScanPathsKey
import com.abhiram.flowtune.constants.ScannerImpl
import com.abhiram.flowtune.constants.ScannerImplKey
import com.abhiram.flowtune.constants.ScannerMatchCriteria
import com.abhiram.flowtune.constants.ScannerSensitivityKey
import com.abhiram.flowtune.constants.ScannerStrictExtKey
import com.abhiram.flowtune.constants.SearchSource
import com.abhiram.flowtune.constants.SearchSourceKey
import com.abhiram.flowtune.constants.SlimNavBarKey
import com.abhiram.flowtune.constants.StopMusicOnTaskClearKey
import com.abhiram.flowtune.db.MusicDatabase
import com.abhiram.flowtune.db.entities.SearchHistory
import com.abhiram.flowtune.extensions.toEnum
import com.abhiram.flowtune.playback.DownloadUtil
import com.abhiram.flowtune.playback.MusicService
import com.abhiram.flowtune.playback.MusicService.MusicBinder
import com.abhiram.flowtune.playback.PlayerConnection
import com.abhiram.flowtune.ui.component.BottomSheetMenu
import com.abhiram.flowtune.ui.component.LocalMenuState
import com.abhiram.flowtune.ui.component.SearchBar
import com.abhiram.flowtune.ui.component.rememberBottomSheetState
import com.abhiram.flowtune.ui.component.shimmer.ShimmerTheme
import com.abhiram.flowtune.ui.menu.YouTubeSongMenu
import com.abhiram.flowtune.ui.player.BottomSheetPlayer
import com.abhiram.flowtune.ui.screens.AccountScreen
import com.abhiram.flowtune.ui.screens.AlbumScreen
import com.abhiram.flowtune.ui.screens.HistoryScreen
import com.abhiram.flowtune.ui.screens.HomeScreen
import com.abhiram.flowtune.ui.screens.LoginScreen
import com.abhiram.flowtune.ui.screens.MoodAndGenresScreen
import com.abhiram.flowtune.ui.screens.NewReleaseScreen
import com.abhiram.flowtune.ui.screens.Screens
import com.abhiram.flowtune.ui.screens.SetupWizard
import com.abhiram.flowtune.ui.screens.StatsScreen
import com.abhiram.flowtune.ui.screens.YouTubeBrowseScreen
import com.abhiram.flowtune.ui.screens.artist.ArtistAlbumsScreen
import com.abhiram.flowtune.ui.screens.artist.ArtistItemsScreen
import com.abhiram.flowtune.ui.screens.artist.ArtistScreen
import com.abhiram.flowtune.ui.screens.artist.ArtistSongsScreen
import com.abhiram.flowtune.ui.screens.library.LibraryAlbumsScreen
import com.abhiram.flowtune.ui.screens.library.LibraryArtistsScreen
import com.abhiram.flowtune.ui.screens.library.LibraryFoldersScreen
import com.abhiram.flowtune.ui.screens.library.LibraryPlaylistsScreen
import com.abhiram.flowtune.ui.screens.library.LibraryScreen
import com.abhiram.flowtune.ui.screens.library.LibrarySongsScreen
import com.abhiram.flowtune.ui.screens.playlist.AutoPlaylistScreen
import com.abhiram.flowtune.ui.screens.playlist.LocalPlaylistScreen
import com.abhiram.flowtune.ui.screens.playlist.OnlinePlaylistScreen
import com.abhiram.flowtune.ui.screens.search.LocalSearchScreen
import com.abhiram.flowtune.ui.screens.search.OnlineSearchResult
import com.abhiram.flowtune.ui.screens.search.OnlineSearchScreen
import com.abhiram.flowtune.ui.screens.settings.AboutScreen
import com.abhiram.flowtune.ui.screens.settings.AppearanceSettings
import com.abhiram.flowtune.ui.screens.settings.BackupAndRestore
import com.abhiram.flowtune.ui.screens.settings.ContentSettings
import com.abhiram.flowtune.ui.screens.settings.DEFAULT_ENABLED_TABS
import com.abhiram.flowtune.ui.screens.settings.DarkMode
import com.abhiram.flowtune.ui.screens.settings.ExperimentalSettings
import com.abhiram.flowtune.ui.screens.settings.LocalPlayerSettings
import com.abhiram.flowtune.ui.screens.settings.LyricsSettings
import com.abhiram.flowtune.ui.screens.settings.MEDIA_PERMISSION_LEVEL
import com.abhiram.flowtune.ui.screens.settings.NavigationTab
import com.abhiram.flowtune.ui.screens.settings.NavigationTabNew
import com.abhiram.flowtune.ui.screens.settings.PlayerBackgroundStyle
import com.abhiram.flowtune.ui.screens.settings.PlayerSettings
import com.abhiram.flowtune.ui.screens.settings.PrivacySettings
import com.abhiram.flowtune.ui.screens.settings.SettingsScreen
import com.abhiram.flowtune.ui.screens.settings.StorageSettings
import com.abhiram.flowtune.ui.theme.ColorSaver
import com.abhiram.flowtune.ui.theme.DefaultThemeColor
import com.abhiram.flowtune.ui.theme.flowtuneTheme
import com.abhiram.flowtune.ui.theme.extractThemeColor
import com.abhiram.flowtune.ui.utils.DEFAULT_SCAN_PATH
import com.abhiram.flowtune.ui.utils.appBarScrollBehavior
import com.abhiram.flowtune.ui.utils.cacheDirectoryTree
import com.abhiram.flowtune.ui.utils.getLocalThumbnail
import com.abhiram.flowtune.ui.utils.resetHeightOffset
import com.abhiram.flowtune.utils.ActivityLauncherHelper
import com.abhiram.flowtune.utils.NetworkConnectivityObserver
import com.abhiram.flowtune.utils.SyncUtils
import com.abhiram.flowtune.utils.dataStore
import com.abhiram.flowtune.utils.get
import com.abhiram.flowtune.utils.purgeCache
import com.abhiram.flowtune.utils.rememberEnumPreference
import com.abhiram.flowtune.utils.rememberPreference
import com.abhiram.flowtune.utils.reportException
import com.abhiram.flowtune.utils.scanners.LocalMediaScanner
import com.abhiram.flowtune.utils.scanners.LocalMediaScanner.Companion.destroyScanner
import com.abhiram.flowtune.utils.scanners.LocalMediaScanner.Companion.scannerActive
import com.abhiram.flowtune.utils.scanners.ScannerAbortException
import com.abhiram.flowtune.utils.urlEncode
import com.valentinilk.shimmer.LocalShimmerTheme
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    lateinit var activityLauncher: ActivityLauncherHelper
    lateinit var connectivityObserver: NetworkConnectivityObserver

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MusicBinder) {
                playerConnection = PlayerConnection(service, database, lifecycleScope)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerConnection?.dispose()
            playerConnection = null
        }
    }

    // storage permission helpers
    val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
//                Toast.makeText(this, "Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startService(Intent(this, MusicService::class.java))
        } else {
            startService(Intent(this, MusicService::class.java))
        }
        bindService(Intent(this, MusicService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        unbindService(serviceConnection)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        runBlocking {
            // save last position
            dataStore.edit { settings ->
                settings[LastPosKey] = playerConnection!!.player.currentPosition
            }
        }

        if (dataStore.get(StopMusicOnTaskClearKey, false) && playerConnection?.isPlaying?.value == true
            && isFinishing
        ) {
            stopService(Intent(this, MusicService::class.java))
            unbindService(serviceConnection)
            playerConnection = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint(
        "UnusedMaterial3ScaffoldPaddingParameter", "CoroutineCreationDuringComposition",
        "StateFlowValueCalledInComposition", "UnusedBoxWithConstraintsScope"
    )
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        activityLauncher = ActivityLauncherHelper(this)
        connectivityObserver = NetworkConnectivityObserver(this)

        setContent {
            val haptic = LocalHapticFeedback.current

            val isNetworkConnected by connectivityObserver.networkStatus.collectAsState(false)

            val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
            val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
            val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
            val newInterfaceStyle by rememberPreference(NewInterfaceKey, defaultValue = true)
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            }
            LaunchedEffect(useDarkTheme) {
                setSystemBarAppearance(useDarkTheme)
            }
            var themeColor by rememberSaveable(stateSaver = ColorSaver) {
                mutableStateOf(DefaultThemeColor)
            }

            val playerBackground by rememberEnumPreference(
                key = PlayerBackgroundStyleKey,
                defaultValue = PlayerBackgroundStyle.DEFAULT
            )

            LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme) {
                val playerConnection = playerConnection
                if (!enableDynamicTheme || playerConnection == null) {
                    themeColor = DefaultThemeColor
                    return@LaunchedEffect
                }
                playerConnection.service.currentMediaMetadata.collectLatest { song ->
                    themeColor = if (song != null) {
                        withContext(Dispatchers.IO) {
                            if (!song.isLocal) {
                                val result = imageLoader.execute(
                                    ImageRequest.Builder(this@MainActivity)
                                        .data(song.thumbnailUrl)
                                        .allowHardware(false) // pixel access is not supported on Config#HARDWARE bitmaps
                                        .build()
                                )
                                (result.drawable as? BitmapDrawable)?.bitmap?.extractThemeColor()
                                    ?: DefaultThemeColor
                            } else {
                                getLocalThumbnail(song.localPath)?.extractThemeColor()
                                    ?: DefaultThemeColor
                            }
                        }
                    } else DefaultThemeColor
                }
            }

            val (firstSetupPassed) = rememberPreference(FirstSetupPassed, defaultValue = false)
            val (localLibEnable) = rememberPreference(LocalLibraryEnableKey, defaultValue = true)

            // auto scanner
            val (scannerSensitivity) = rememberEnumPreference(
                key = ScannerSensitivityKey,
                defaultValue = ScannerMatchCriteria.LEVEL_2
            )
            val (scannerImpl) = rememberEnumPreference(
                key = ScannerImplKey,
                defaultValue = ScannerImpl.TAGLIB
            )
            val (scanPaths) = rememberPreference(ScanPathsKey, defaultValue = DEFAULT_SCAN_PATH)
            val (excludedScanPaths) = rememberPreference(ExcludedScanPathsKey, defaultValue = "")
            val (strictExtensions) = rememberPreference(ScannerStrictExtKey, defaultValue = false)
            val (lookupYtmArtists) = rememberPreference(LookupYtmArtistsKey, defaultValue = true)
            val (autoScan) = rememberPreference(AutomaticScannerKey, defaultValue = false)
            LaunchedEffect(Unit) {
                downloadUtil.resumeDownloadsOnStart()

                CoroutineScope(Dispatchers.IO).launch {
                    // Check if the permissions for local media access
                    if (!scannerActive.value && autoScan && firstSetupPassed && localLibEnable
                        && checkSelfPermission(MEDIA_PERMISSION_LEVEL) == PackageManager.PERMISSION_GRANTED) {

                        // equivalent to (quick scan)
                        try {
                            val scanner = LocalMediaScanner.getScanner(this@MainActivity, scannerImpl)
                            val directoryStructure = scanner.scanLocal(
                                database,
                                scanPaths.split('\n'),
                                excludedScanPaths.split('\n'),
                                pathsOnly = true
                            ).value
                            scanner.quickSync(
                                database, directoryStructure.toList(), scannerSensitivity,
                                strictExtensions,
                            )

                            // start artist linking job
                            if (lookupYtmArtists && !scannerActive.value) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        scanner.localToRemoteArtist(database)
                                    } catch (e: ScannerAbortException) {
                                        Looper.prepare()
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Scanner (background task) failed: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        } catch (e: ScannerAbortException) {
                            Looper.prepare()
                            Toast.makeText(
                                this@MainActivity,
                                "Scanner failed: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            destroyScanner()
                        }
                        purgeCache() // juuuust to be sure
                        cacheDirectoryTree(null)
                    } else if (checkSelfPermission(MEDIA_PERMISSION_LEVEL) == PackageManager.PERMISSION_DENIED) {
                        // Request the permission using the permission launcher
                        permissionLauncher.launch(MEDIA_PERMISSION_LEVEL)
                    }
                }
            }
            flowtuneTheme(
                darkTheme = useDarkTheme,
                pureBlack = pureBlack,
                themeColor = themeColor
            ) {
                BoxWithConstraints( // Deprecated. please use the scaffold
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    val focusManager = LocalFocusManager.current
                    val density = LocalDensity.current
                    val windowsInsets = WindowInsets.systemBars
                    val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }

                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val inSelectMode = navBackStackEntry?.savedStateHandle?.getStateFlow("inSelectMode", false)?.collectAsState()
                    val (previousTab, setPreviousTab) = rememberSaveable { mutableStateOf("home") }

                    val (slimNav) = rememberPreference(SlimNavBarKey, defaultValue = false)
                    val (enabledTabs) = rememberPreference(EnabledTabsKey, defaultValue = DEFAULT_ENABLED_TABS)
                    val navigationItems =
                        if (!newInterfaceStyle) Screens.getScreens(enabledTabs) else Screens.MainScreensNew
                    val defaultOpenTab = remember {
                        if (newInterfaceStyle) dataStore[DefaultOpenTabNewKey].toEnum(defaultValue = NavigationTabNew.HOME)
                        else dataStore[DefaultOpenTabKey].toEnum(defaultValue = NavigationTab.HOME)
                    }
                    val tabOpenedFromShortcut = remember {
                        // reroute to library page for new layout is handled in NavHost section
                        when (intent?.action) {
                            ACTION_SONGS -> if (newInterfaceStyle) NavigationTabNew.LIBRARY else NavigationTab.SONG
                            ACTION_ALBUMS -> if (newInterfaceStyle) NavigationTabNew.LIBRARY else NavigationTab.ALBUM
                            ACTION_PLAYLISTS -> if (newInterfaceStyle) NavigationTabNew.LIBRARY else NavigationTab.PLAYLIST
                            else -> null
                        }
                    }
                    // setup filters for new layout
                    if (tabOpenedFromShortcut != null && newInterfaceStyle) {
                        var filter by rememberEnumPreference(LibraryFilterKey, LibraryFilter.ALL)
                        filter = when (intent?.action) {
                            ACTION_SONGS -> LibraryFilter.SONGS
                            ACTION_ALBUMS -> LibraryFilter.ALBUMS
                            ACTION_PLAYLISTS -> LibraryFilter.PLAYLISTS
                            ACTION_SEARCH -> filter // do change filter for search
                            else -> LibraryFilter.ALL
                        }
                    }

                    val (query, onQueryChange) = rememberSaveable(stateSaver = TextFieldValue.Saver) {
                        mutableStateOf(TextFieldValue())
                    }
                    var active by rememberSaveable {
                        mutableStateOf(false)
                    }
                    val onActiveChange: (Boolean) -> Unit = { newActive ->
                        active = newActive
                        if (!newActive) {
                            focusManager.clearFocus()
                            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                onQueryChange(TextFieldValue())
                            }
                        }
                    }
                    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)

                    val searchBarFocusRequester = remember { FocusRequester() }

                    val onSearch: (String) -> Unit = {
                        if (it.isNotEmpty()) {
                            onActiveChange(false)
                            navController.navigate("search/${it.urlEncode()}")
                            if (dataStore[PauseSearchHistoryKey] != true) {
                                database.query {
                                    insert(SearchHistory(query = it))
                                }
                            }
                        }
                    }

                    var openSearchImmediately: Boolean by remember {
                        mutableStateOf(intent?.action == ACTION_SEARCH)
                    }

                    val shouldShowSearchBar = remember(active, navBackStackEntry, inSelectMode?.value) {
                        (active || navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                                navBackStackEntry?.destination?.route?.startsWith("search/") == true) &&
                                inSelectMode?.value != true
                    }
                    val shouldShowNavigationBar = remember(navBackStackEntry, active) {
                        navBackStackEntry?.destination?.route == null ||
                                navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } && !active
                    }

                    fun getNavPadding(): Dp {
                        return if (shouldShowNavigationBar) {
                            if (slimNav) 52.dp else 68.dp
                        } else {
                            0.dp
                        }
                    }

                    val navigationBarHeight by animateDpAsState(
                        targetValue = if (shouldShowNavigationBar) NavigationBarHeight else 0.dp,
                        animationSpec = NavigationBarAnimationSpec,
                        label = ""
                    )

                    val playerBottomSheetState = rememberBottomSheetState(
                        dismissedBound = 0.dp,
                        collapsedBound = bottomInset + getNavPadding() + MiniPlayerHeight,
                        expandedBound = maxHeight,
                    )

                    val playerAwareWindowInsets =
                        remember(bottomInset, shouldShowNavigationBar, playerBottomSheetState.isDismissed) {
                            var bottom = bottomInset
                            if (shouldShowNavigationBar) bottom += NavigationBarHeight
                            if (!playerBottomSheetState.isDismissed) bottom += MiniPlayerHeight
                            windowsInsets
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                                .add(WindowInsets(top = AppBarHeight, bottom = bottom))
                        }

                    val scrollBehavior = appBarScrollBehavior(
                        canScroll = {
                            navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        }
                    )

                    val searchBarScrollBehavior = appBarScrollBehavior(
                        state = rememberTopAppBarState(),
                        canScroll = {
                            navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        }
                    )

                    LaunchedEffect(navBackStackEntry) {
                        if (navBackStackEntry?.destination?.route?.startsWith("search/") == true) {
                            val searchQuery = withContext(Dispatchers.IO) {
                                URLDecoder.decode(navBackStackEntry?.arguments?.getString("query")!!, "UTF-8")
                            }
                            onQueryChange(TextFieldValue(searchQuery, TextRange(searchQuery.length)))
                        } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                            onQueryChange(TextFieldValue())
                        }

                        if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route })
                            if (navigationItems.fastAny { it.route == previousTab })
                                searchBarScrollBehavior.state.resetHeightOffset()

                        navController.currentBackStackEntry?.destination?.route?.let {
                            setPreviousTab(it)
                        }

                        /*
                         * If the current back stack entry matches one of the main screens, but
                         * is not in the current navigation items, we need to remove the entry
                         * to avoid entering a "ghost" screen.
                         */
                        if (Screens.MainScreens.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                            Screens.MainScreensNew.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                            if (!navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                navController.popBackStack()
                                navController.navigate(Screens.Home.route)
                            }
                        }
                    }

                    LaunchedEffect(playerConnection) {
                        val player = playerConnection?.player ?: return@LaunchedEffect
                        if (player.currentMediaItem == null) {
                            if (!playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.dismiss()
                            }
                        } else {
                            if (playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.collapseSoft()
                            }
                        }
                    }

                    DisposableEffect(playerConnection, playerBottomSheetState) {
                        val player = playerConnection?.player ?: return@DisposableEffect onDispose { }
                        val listener = object : Player.Listener {
                            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem != null && playerBottomSheetState.isDismissed) {
                                    playerBottomSheetState.collapseSoft()
                                }
                            }
                        }
                        player.addListener(listener)
                        onDispose {
                            player.removeListener(listener)
                        }
                    }

                    val coroutineScope = rememberCoroutineScope()
                    var sharedSong: SongItem? by remember {
                        mutableStateOf(null)
                    }
                    DisposableEffect(Unit) {
                        val listener = Consumer<Intent> { intent ->
                            val uri =
                                intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return@Consumer
                            when (val path = uri.pathSegments.firstOrNull()) {
                                "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                                    if (playlistId.startsWith("OLAK5uy_")) {
                                        coroutineScope.launch {
                                            YouTube.albumSongs(playlistId).onSuccess { songs ->
                                                songs.firstOrNull()?.album?.id?.let { browseId ->
                                                    navController.navigate("album/$browseId")
                                                }
                                            }.onFailure {
                                                reportException(it)
                                            }
                                        }
                                    } else {
                                        navController.navigate("online_playlist/$playlistId")
                                    }
                                }

                                "channel", "c" -> uri.lastPathSegment?.let { artistId ->
                                    navController.navigate("artist/$artistId")
                                }

                                else -> when {
                                    path == "watch" -> uri.getQueryParameter("v")
                                    uri.host == "youtu.be" -> path
                                    else -> null
                                }?.let { videoId ->
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) {
                                            YouTube.queue(listOf(videoId))
                                        }.onSuccess {
                                            sharedSong = it.firstOrNull()
                                        }.onFailure {
                                            reportException(it)
                                        }
                                    }
                                }
                            }
                        }

                        addOnNewIntentListener(listener)
                        onDispose { removeOnNewIntentListener(listener) }
                    }

                    CompositionLocalProvider(
                        LocalDatabase provides database,
                        LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.surface),
                        LocalPlayerConnection provides playerConnection,
                        LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                        LocalDownloadUtil provides downloadUtil,
                        LocalShimmerTheme provides ShimmerTheme,
                        LocalSyncUtils provides syncUtils,
                        LocalIsNetworkConnected provides isNetworkConnected
                    ) {
                        Scaffold(
                            topBar = {
                                AnimatedVisibility(
                                    visible = shouldShowSearchBar,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    SearchBar(
                                        query = query,
                                        onQueryChange = onQueryChange,
                                        onSearch = onSearch,
                                        active = active,
                                        onActiveChange = onActiveChange,
                                        scrollBehavior = searchBarScrollBehavior,
                                        placeholder = {
                                            Text(
                                                text = stringResource(
                                                    if (!active) R.string.search
                                                    else when (searchSource) {
                                                        SearchSource.LOCAL -> R.string.search_library
                                                        SearchSource.ONLINE -> R.string.search_yt_music
                                                    }
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            IconButton(
                                                onClick = {
                                                    when {
                                                        active -> onActiveChange(false)

                                                        !active && navBackStackEntry?.destination?.route?.startsWith("search") == true -> {
                                                            navController.navigateUp()
                                                        }

                                                        else -> onActiveChange(true)
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    imageVector =
                                                    if (active || navBackStackEntry?.destination?.route?.startsWith("search") == true) {
                                                        Icons.AutoMirrored.Rounded.ArrowBack
                                                    } else {
                                                        Icons.Rounded.Search
                                                    },
                                                    contentDescription = null
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            if (active) {
                                                if (query.text.isNotEmpty()) {
                                                    IconButton(
                                                        onClick = { onQueryChange(TextFieldValue("")) }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Close,
                                                            contentDescription = null
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        searchSource =
                                                            if (searchSource == SearchSource.ONLINE) SearchSource.LOCAL else SearchSource.ONLINE
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = when (searchSource) {
                                                            SearchSource.LOCAL -> Icons.Rounded.LibraryMusic
                                                            SearchSource.ONLINE -> Icons.Rounded.Language
                                                        },
                                                        contentDescription = null
                                                    )
                                                }
                                            } else if (navBackStackEntry?.destination?.route in listOf(
                                                    Screens.Home.route,
                                                    Screens.Songs.route,
                                                    Screens.Folders.route,
                                                    Screens.Artists.route,
                                                    Screens.Albums.route,
                                                    Screens.Playlists.route,
                                                    Screens.Library.route
                                                )
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        navController.navigate("settings")
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Settings,
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                        },
                                        focusRequester = searchBarFocusRequester,
                                        modifier = Modifier.align(Alignment.TopCenter),
                                    ) {
                                        Crossfade(
                                            targetState = searchSource,
                                            label = "",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(bottom = if (!playerBottomSheetState.isDismissed) MiniPlayerHeight else 0.dp)
                                                .navigationBarsPadding()
                                        ) { searchSource ->
                                            when (searchSource) {
                                                SearchSource.LOCAL -> LocalSearchScreen(
                                                    query = query.text,
                                                    navController = navController,
                                                    onDismiss = { onActiveChange(false) }
                                                )

                                                SearchSource.ONLINE -> OnlineSearchScreen(
                                                    query = query.text,
                                                    onQueryChange = onQueryChange,
                                                    navController = navController,
                                                    onSearch = {
                                                        navController.navigate("search/${it.urlEncode()}")
                                                        if (dataStore[PauseSearchHistoryKey] != true) {
                                                            database.query {
                                                                insert(SearchHistory(query = it))
                                                            }
                                                        }
                                                    },
                                                    onDismiss = { onActiveChange(false) }
                                                )
                                            }
                                        }
                                    }
                                }

                                if (BuildConfig.DEBUG) {
                                    val debugColour = Color.Red
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(y = 100.dp)
                                    ) {
                                        Text(
                                            text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) | ${BuildConfig.FLAVOR}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = debugColour
                                        )
                                        Text(
                                            text = "${BuildConfig.APPLICATION_ID} | ${BuildConfig.BUILD_TYPE}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = debugColour
                                        )
                                        Text(
                                            text = "${Build.BRAND} ${Build.DEVICE} (${Build.MODEL})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = debugColour
                                        )
                                        Text(
                                            text = "${Build.VERSION.SDK_INT} (${Build.ID})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = debugColour
                                        )
                                    }
                                }
                            },
                            bottomBar = {
                                Box() {
                                    if (firstSetupPassed) {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController
                                        )
                                    }

                                    LaunchedEffect(playerBottomSheetState.isExpanded) {
                                        setSystemBarAppearance(
                                            (playerBottomSheetState.isExpanded
                                                    && playerBackground != PlayerBackgroundStyle.DEFAULT) || useDarkTheme
                                        )
                                    }
                                    NavigationBar(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .height(bottomInset + getNavPadding())
                                            .offset {
                                                if (navigationBarHeight == 0.dp) {
                                                    IntOffset(
                                                        x = 0,
                                                        y = (bottomInset + NavigationBarHeight).roundToPx()
                                                    )
                                                } else {
                                                    val slideOffset =
                                                        (bottomInset + NavigationBarHeight) * playerBottomSheetState.progress.coerceIn(
                                                            0f,
                                                            1f
                                                        )
                                                    val hideOffset =
                                                        (bottomInset + NavigationBarHeight) * (1 - navigationBarHeight / NavigationBarHeight)
                                                    IntOffset(
                                                        x = 0,
                                                        y = (slideOffset + hideOffset).roundToPx()
                                                    )
                                                }
                                            }
                                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
                                    ) {
                                        navigationItems.fastForEach { screen ->
                                            NavigationBarItem(
                                                selected = navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true,
                                                icon = {
                                                    Icon(
                                                        screen.icon,
                                                        contentDescription = null
                                                    )
                                                },
                                                label = {
                                                    if (!slimNav) {
                                                        Text(
                                                            text = stringResource(screen.titleId),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    if (navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true) {
                                                        navBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                                        coroutineScope.launch {
                                                            searchBarScrollBehavior.state.resetHeightOffset()
                                                        }
                                                    } else {
                                                        navController.navigate(screen.route) {
                                                            popUpTo(navController.graph.startDestinationId) {
                                                                saveState = true
                                                            }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }

                                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(searchBarScrollBehavior.nestedScrollConnection)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            var transitionDirection = AnimatedContentTransitionScope.SlideDirection.Left

                            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                if (navigationItems.fastAny { it.route == previousTab }) {
                                    val curIndex = navigationItems.indexOf(
                                        navigationItems.fastFirstOrNull {
                                            it.route == navBackStackEntry?.destination?.route
                                        }
                                    )

                                    val prevIndex = navigationItems.indexOf(
                                        navigationItems.fastFirstOrNull {
                                            it.route == previousTab
                                        }
                                    )

                                    if (prevIndex > curIndex)
                                        transitionDirection =
                                            AnimatedContentTransitionScope.SlideDirection.Right
                                }
                            }

                            NavHost(
                                navController = navController,
                                startDestination = when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                    NavigationTab.HOME -> Screens.Home
                                    NavigationTab.SONG -> Screens.Songs
                                    NavigationTab.FOLDERS -> Screens.Folders
                                    NavigationTab.ARTIST -> Screens.Artists
                                    NavigationTab.ALBUM -> Screens.Albums
                                    NavigationTab.PLAYLIST -> Screens.Playlists
                                    NavigationTabNew.HOME -> Screens.Home
                                    NavigationTabNew.LIBRARY -> Screens.Library
                                    else -> Screens.Home
                                }.route,
                                enterTransition = {
                                    slideIntoContainer(
                                        transitionDirection,
                                        animationSpec = tween(200)
                                    )
                                },
                                exitTransition = {
                                    slideOutOfContainer(
                                        transitionDirection,
                                        animationSpec = tween(200)
                                    )
                                },
                                popEnterTransition = {
                                    slideIntoContainer(
                                        AnimatedContentTransitionScope.SlideDirection.Right,
                                        animationSpec = tween(200)
                                    )
                                },
                                popExitTransition = {
                                    slideOutOfContainer(
                                        AnimatedContentTransitionScope.SlideDirection.Right,
                                        animationSpec = tween(200)
                                    )
                                },
                                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                            ) {
                                composable(Screens.Home.route) {
                                    HomeScreen(navController)
                                }
                                composable(Screens.Songs.route) {
                                    LibrarySongsScreen(navController)
                                }
                                composable(Screens.Folders.route) {
                                    LibraryFoldersScreen(navController)
                                }
                                composable(Screens.Artists.route) {
                                    LibraryArtistsScreen(navController)
                                }
                                composable(Screens.Albums.route) {
                                    LibraryAlbumsScreen(navController)
                                }
                                composable(Screens.Playlists.route) {
                                    LibraryPlaylistsScreen(navController)
                                }
                                composable(Screens.Library.route) {
                                    LibraryScreen(navController)
                                }
                                composable("history") {
                                    HistoryScreen(navController)
                                }
                                composable("stats") {
                                    StatsScreen(navController)
                                }
                                composable("mood_and_genres") {
                                    MoodAndGenresScreen(navController, scrollBehavior)
                                }
                                composable("account") {
                                    AccountScreen(navController, scrollBehavior)
                                }
                                composable("new_release") {
                                    NewReleaseScreen(navController, scrollBehavior)
                                }

                                composable(
                                    route = "search/{query}",
                                    arguments = listOf(
                                        navArgument("query") {
                                            type = NavType.StringType
                                        }
                                    )
                                ) {
                                    OnlineSearchResult(navController)
                                }
                                composable(
                                    route = "album/{albumId}",
                                    arguments = listOf(
                                        navArgument("albumId") {
                                            type = NavType.StringType
                                        },
                                    )
                                ) {
                                    AlbumScreen(navController, scrollBehavior)
                                }
                                composable(
                                    route = "artist/{artistId}",
                                    arguments = listOf(
                                        navArgument("artistId") {
                                            type = NavType.StringType
                                        }
                                    )
                                ) {
                                    ArtistScreen(navController, scrollBehavior)
                                }
                                composable(
                                    route = "artist/{artistId}/songs",
                                    arguments = listOf(
                                        navArgument("artistId") {
                                            type = NavType.StringType
                                        }
                                    )
                                ) {
                                    ArtistSongsScreen(navController, scrollBehavior)
                                }
                                composable(
                                    route = "artist/{artistId}/albums",
                                    arguments = listOf(
                                        navArgument("artistId") {
                                            type = NavType.StringType
                                        }
                                    )
                                ) {
                                    ArtistAlbumsScreen(navController, scrollBehavior)
                                }
                                composable(
                                    route = "artist/{artistId}/items?browseId={browseId}?params={params}",
                                    arguments = listOf(
                                        navArgument("artistId") {
                                            type = NavType.StringType
                                        },
                                        navArgument("browseId") {
                                            type = NavType.StringType
                                            nullable = true
                                        },
                                        navArgument("params") {
                                            type = NavType.StringType
                                            nullable = true
                                        }
                                    )
                                ) {
                                    ArtistItemsScreen(navController, scrollBehavior)
                                }
                                composable(
                                    route = "online_playlist/{playlistId}",
                                    arguments = listOf(
                                        navArgument("playlistId") {
                                            type = NavType.StringType
                                        }
                                    )
                                ) {
                                    OnlinePlaylistScreen(navController, scrollBehavior)
                                }
                                composable(
                                    route = "local_playlist/{playlistId}",
                                    arguments = listOf(
                                        navArgument("playlistId") {
                                            type = NavType.StringType
                                        }
                                    )
                                ) {
                                    LocalPlaylistScreen(navController, scrollBehavior)
                                }
                                composable(
                                    route = "auto_playlist/{playlistId}",
                                    arguments = listOf(
                                        navArgument("playlistId") {
                                            type = NavType.StringType
                                        }
                                    )
                                ) {
                                    AutoPlaylistScreen(navController, scrollBehavior)
                                }
                                composable(
                                    route = "youtube_browse/{browseId}?params={params}",
                                    arguments = listOf(
                                        navArgument("browseId") {
                                            type = NavType.StringType
                                            nullable = true
                                        },
                                        navArgument("params") {
                                            type = NavType.StringType
                                            nullable = true
                                        }
                                    )
                                ) {
                                    YouTubeBrowseScreen(navController, scrollBehavior)
                                }
                                composable("settings") {
                                    SettingsScreen(navController, scrollBehavior)
                                }
                                composable("settings/appearance") {
                                    AppearanceSettings(navController, scrollBehavior)
                                }
                                composable("settings/content") {
                                    ContentSettings(navController, scrollBehavior)
                                }
                                composable("settings/player") {
                                    PlayerSettings(navController, scrollBehavior)
                                }
                                composable("settings/player/lyrics") {
                                    LyricsSettings(navController, scrollBehavior)
                                }
                                composable("settings/storage") {
                                    StorageSettings(navController, scrollBehavior)
                                }
                                composable("settings/privacy") {
                                    PrivacySettings(navController, scrollBehavior)
                                }
                                composable("settings/backup_restore") {
                                    BackupAndRestore(navController, scrollBehavior)
                                }
                                composable("settings/local") {
                                    LocalPlayerSettings(navController, scrollBehavior)
                                }
                                composable("settings/experimental") {
                                    ExperimentalSettings(navController, scrollBehavior)
                                }
                                composable("settings/about") {
                                    AboutScreen(navController, scrollBehavior)
                                }
                                composable("login") {
                                    LoginScreen(navController)
                                }

                                composable("setup_wizard",) {
                                    SetupWizard(navController)
                                }
                            }
                        }

                        BottomSheetMenu(
                            state = LocalMenuState.current,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        // Setup wizard
                        LaunchedEffect(Unit) {
                            if (!firstSetupPassed) {
                                navController.navigate("setup_wizard")
                            }
                        }

                        sharedSong?.let { song ->
                            playerConnection?.let {
                                Dialog(
                                    onDismissRequest = { sharedSong = null },
                                    properties = DialogProperties(usePlatformDefaultWidth = false)
                                ) {
                                    Surface(
                                        modifier = Modifier.padding(24.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = AlertDialogDefaults.containerColor,
                                        tonalElevation = AlertDialogDefaults.TonalElevation
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            YouTubeSongMenu(
                                                song = song,
                                                navController = navController,
                                                onDismiss = { sharedSong = null }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LaunchedEffect(shouldShowSearchBar, openSearchImmediately) {
                        if (shouldShowSearchBar && openSearchImmediately) {
                            onActiveChange(true)
                            searchBarFocusRequester.requestFocus()
                            openSearchImmediately = false
                        }
                    }
                }
            }
        }
    }

    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    companion object {
        const val ACTION_SEARCH = "com.abhiram.flowtune.action.SEARCH"
        const val ACTION_SONGS = "com.abhiram.flowtune.action.SONGS"
        const val ACTION_ALBUMS = "com.abhiram.flowtune.action.ALBUMS"
        const val ACTION_PLAYLISTS = "com.abhiram.flowtune.action.PLAYLISTS"
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalIsNetworkConnected = staticCompositionLocalOf<Boolean> { error("No Network Status provided") }
