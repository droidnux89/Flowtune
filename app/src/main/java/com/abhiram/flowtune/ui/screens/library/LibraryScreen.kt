package com.abhiram.flowtune.ui.screens.library

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.abhiram.flowtune.LocalPlayerAwareWindowInsets
import com.abhiram.flowtune.LocalPlayerConnection
import com.abhiram.flowtune.R
import com.abhiram.flowtune.constants.CONTENT_TYPE_HEADER
import com.abhiram.flowtune.constants.CONTENT_TYPE_LIST
import com.abhiram.flowtune.constants.CONTENT_TYPE_PLAYLIST
import com.abhiram.flowtune.constants.EnabledTabsKey
import com.abhiram.flowtune.constants.GridThumbnailHeight
import com.abhiram.flowtune.constants.LibraryFilter
import com.abhiram.flowtune.constants.LibraryFilterKey
import com.abhiram.flowtune.constants.LibrarySortDescendingKey
import com.abhiram.flowtune.constants.LibrarySortType
import com.abhiram.flowtune.constants.LibrarySortTypeKey
import com.abhiram.flowtune.constants.LibraryViewType
import com.abhiram.flowtune.constants.LibraryViewTypeKey
import com.abhiram.flowtune.constants.ShowLikedAndDownloadedPlaylist
import com.abhiram.flowtune.db.entities.Album
import com.abhiram.flowtune.db.entities.Artist
import com.abhiram.flowtune.db.entities.Playlist
import com.abhiram.flowtune.db.entities.PlaylistEntity
import com.abhiram.flowtune.extensions.move
import com.abhiram.flowtune.ui.component.AutoPlaylistGridItem
import com.abhiram.flowtune.ui.component.AutoPlaylistListItem
import com.abhiram.flowtune.ui.component.ChipsLazyRow
import com.abhiram.flowtune.ui.component.EmptyPlaceholder
import com.abhiram.flowtune.ui.component.LibraryAlbumGridItem
import com.abhiram.flowtune.ui.component.LibraryAlbumListItem
import com.abhiram.flowtune.ui.component.LibraryArtistGridItem
import com.abhiram.flowtune.ui.component.LibraryArtistListItem
import com.abhiram.flowtune.ui.component.LibraryPlaylistGridItem
import com.abhiram.flowtune.ui.component.LibraryPlaylistListItem
import com.abhiram.flowtune.ui.component.LocalMenuState
import com.abhiram.flowtune.ui.component.SortHeader
import com.abhiram.flowtune.ui.screens.settings.DEFAULT_ENABLED_TABS
import com.abhiram.flowtune.ui.screens.settings.NavigationTab
import com.abhiram.flowtune.utils.decodeTabString
import com.abhiram.flowtune.utils.rememberEnumPreference
import com.abhiram.flowtune.utils.rememberPreference
import com.abhiram.flowtune.viewmodels.LibraryViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val context = LocalContext.current

    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    var viewType by rememberEnumPreference(LibraryViewTypeKey, LibraryViewType.GRID)
    val enabledTabs by rememberPreference(EnabledTabsKey, defaultValue = DEFAULT_ENABLED_TABS)
    var filter by rememberEnumPreference(LibraryFilterKey, LibraryFilter.ALL)

    val (sortType, onSortTypeChange) = rememberEnumPreference(LibrarySortTypeKey, LibrarySortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(LibrarySortDescendingKey, true)
    val (showLikedAndDownloadedPlaylist) = rememberPreference(ShowLikedAndDownloadedPlaylist, true)

    val allItems by viewModel.allItems.collectAsState()

    val isSyncingRemotePlaylists by viewModel.isSyncingRemotePlaylists.collectAsState()
    val isSyncingRemoteAlbums by viewModel.isSyncingRemoteAlbums.collectAsState()
    val isSyncingRemoteArtists by viewModel.isSyncingRemoteArtists.collectAsState()
    val isSyncingRemoteSongs by viewModel.isSyncingRemoteSongs.collectAsState()
    val isSyncingRemoteLikedSongs by viewModel.isSyncingRemoteLikedSongs.collectAsState()

    val likedPlaylist = PlaylistEntity(id = "liked", name = stringResource(id = R.string.liked_songs))
    val downloadedPlaylist = PlaylistEntity(id = "downloaded", name = stringResource(id = R.string.downloaded_songs))

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    val filterString = when (filter) {
        LibraryFilter.ALBUMS -> stringResource(R.string.albums)
        LibraryFilter.ARTISTS -> stringResource(R.string.artists)
        LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
        LibraryFilter.SONGS -> stringResource(R.string.songs)
        LibraryFilter.FOLDERS -> stringResource(R.string.folders)
        LibraryFilter.ALL -> ""
    }

    val defaultFilter: Collection<Pair<LibraryFilter, String>> = decodeTabString(enabledTabs).map {
        when(it) {
            NavigationTab.ALBUM -> LibraryFilter.ALBUMS to stringResource(R.string.albums)
            NavigationTab.ARTIST -> LibraryFilter.ARTISTS to stringResource(R.string.artists)
            NavigationTab.PLAYLIST -> LibraryFilter.PLAYLISTS to stringResource(R.string.playlists)
            NavigationTab.SONG -> LibraryFilter.SONGS to stringResource(R.string.songs)
            NavigationTab.FOLDERS -> LibraryFilter.FOLDERS to stringResource(R.string.folders)
            else -> LibraryFilter.ALL to stringResource(R.string.home) // there is no all filter, use as null value
        }
    }.filterNot { it.first == LibraryFilter.ALL }

    val chips = remember { SnapshotStateList<Pair<LibraryFilter, String>>() }

    var filterSelected by remember {
        mutableStateOf(filter)
    }

    LaunchedEffect(Unit) {
        if (filter == LibraryFilter.ALL)
            chips.addAll(defaultFilter)
        else
            chips.add(filter to filterString)
    }

    val animatorDurationScale = Settings.Global.getFloat(context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f).toLong()

    suspend fun animationBasedDelay(value: Long) {
        delay(value * animatorDurationScale)
    }

    // Update the filters list in a proper way so that the animations of the LazyRow can work.
    LaunchedEffect(filter) {
        val filterIndex = defaultFilter.indexOf(defaultFilter.find { it.first == filter })
        val currentPairIndex = if (chips.size > 0) defaultFilter.indexOf(chips[0]) else -1
        val currentPair = if (chips.size > 0) chips[0] else null

        if (filter == LibraryFilter.ALL) {
            defaultFilter.reversed().fastForEachIndexed { index, it ->
                val curFilterIndex = defaultFilter.indexOf(it)
                if (!chips.contains(it)) {
                    chips.add(0, it)
                    if (currentPairIndex > curFilterIndex) animationBasedDelay(100)
                    else {
                        currentPair?.let {
                            animationBasedDelay(2)
                            chips.move(chips.indexOf(it), 0)
                        }
                        animationBasedDelay(80 + (index * 30).toLong())
                    }
                }
            }
            animationBasedDelay(100)
            filterSelected = LibraryFilter.ALL
        } else {
            filterSelected = filter
            chips.filter { it.first != filter }
                .onEachIndexed { index, it ->
                    if (chips.contains(it)) {
                        chips.remove(it)
                        if (index > filterIndex) animationBasedDelay(150 + 30 * index.toLong())
                        else animationBasedDelay(80)
                    }
                }
        }
    }

    val filterContent = @Composable {
        Row {
            ChipsLazyRow(
                chips = chips,
                currentValue = filter,
                onValueUpdate = {
                    filter = if (filter == LibraryFilter.ALL)
                        it
                    else
                        LibraryFilter.ALL
                },
                modifier = Modifier.weight(1f),
                selected = { it == filterSelected },
                isLoading = { filter ->
                    (filter == LibraryFilter.PLAYLISTS && isSyncingRemotePlaylists)
                    || (filter == LibraryFilter.ALBUMS && isSyncingRemoteAlbums)
                    || (filter == LibraryFilter.ARTISTS && isSyncingRemoteArtists)
                    || (filter == LibraryFilter.SONGS && (isSyncingRemoteSongs || isSyncingRemoteLikedSongs))
                }
            )

            if (filter != LibraryFilter.SONGS) {
                IconButton(
                    onClick = {
                        viewType = viewType.toggle()
                    },
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Icon(
                        imageVector =
                        when (viewType) {
                            LibraryViewType.LIST -> Icons.AutoMirrored.Rounded.List
                            LibraryViewType.GRID -> Icons.Rounded.GridView
                        },
                        contentDescription = null
                    )
                }
            }
        }
    }

    val headerContent = @Composable {
        SortHeader(
            sortType = sortType,
            sortDescending = sortDescending,
            onSortTypeChange = onSortTypeChange,
            onSortDescendingChange = onSortDescendingChange,
            sortTypeText = { sortType ->
                when (sortType) {
                    LibrarySortType.CREATE_DATE -> R.string.sort_by_create_date
                    LibrarySortType.NAME -> R.string.sort_by_name
                }
            },
            modifier = Modifier.padding(start = 16.dp)
        )
    }

    if (filter != LibraryFilter.ALL) {
        BackHandler {
            filter = LibraryFilter.ALL
        }
    }

    // scroll to top
    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (filter) {
            LibraryFilter.ALBUMS ->
                LibraryAlbumsScreen(
                    navController,
                    libraryFilterContent = filterContent
                )

            LibraryFilter.ARTISTS ->
                LibraryArtistsScreen(
                    navController,
                    libraryFilterContent = filterContent
                )

            LibraryFilter.PLAYLISTS ->
                LibraryPlaylistsScreen(
                    navController,
                    libraryFilterContent = filterContent
                )

            LibraryFilter.SONGS ->
                LibrarySongsScreen(
                    navController,
                    libraryFilterContent = filterContent
                )

            LibraryFilter.FOLDERS ->
                LibraryFoldersScreen(
                    navController,
                    filterContent = filterContent
                )

            LibraryFilter.ALL ->
                when (viewType) {
                    LibraryViewType.LIST -> {
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                        ) {
                            item(
                                key = "filter",
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                filterContent()
                            }

                            item(
                                key = "header",
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                headerContent()
                            }

                            if (showLikedAndDownloadedPlaylist) {
                                item(
                                    key = likedPlaylist.id,
                                    contentType = { CONTENT_TYPE_PLAYLIST }
                                ) {
                                    AutoPlaylistListItem(
                                        playlist = likedPlaylist,
                                        thumbnail = Icons.Rounded.Favorite,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("auto_playlist/${likedPlaylist.id}")
                                            }
                                            .animateItem()
                                    )
                                }

                                item(
                                    key = downloadedPlaylist.id,
                                    contentType = { CONTENT_TYPE_PLAYLIST }
                                ) {
                                    AutoPlaylistListItem(
                                        playlist = downloadedPlaylist,
                                        thumbnail = Icons.Rounded.CloudDownload,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("auto_playlist/${downloadedPlaylist.id}")
                                            }
                                            .animateItem()
                                    )
                                }
                            }

                            allItems?.let { allItems ->
                                if (allItems.isEmpty()) {
                                    item {
                                        EmptyPlaceholder(
                                            icon = Icons.AutoMirrored.Rounded.List,
                                            text = stringResource(R.string.library_album_empty),
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }

                                items(
                                    items = allItems.distinctBy { it.hashCode() },
                                    key = { it.hashCode() },
                                    contentType = { CONTENT_TYPE_LIST }
                                ) { item ->
                                    when (item) {
                                        is Album -> {
                                            LibraryAlbumListItem(
                                                navController = navController,
                                                menuState = menuState,
                                                album = item,
                                                isActive = item.id == mediaMetadata?.album?.id,
                                                isPlaying = isPlaying,
                                                modifier = Modifier.animateItem()
                                            )
                                        }

                                        is Artist -> {
                                            LibraryArtistListItem(
                                                navController = navController,
                                                menuState = menuState,
                                                coroutineScope = coroutineScope,
                                                modifier = Modifier.animateItem(),
                                                artist = item
                                            )
                                        }

                                        is Playlist -> {
                                            LibraryPlaylistListItem(
                                                navController = navController,
                                                menuState = menuState,
                                                coroutineScope = coroutineScope,
                                                playlist = item,
                                                modifier = Modifier.animateItem()
                                            )
                                        }

                                        else -> {}
                                    }
                                }
                            }
                        }
                    }

                    LibraryViewType.GRID -> {
                        LazyVerticalGrid(
                            state = lazyGridState,
                            columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
                            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                        ) {
                            item(
                                key = "filter",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                filterContent()
                            }

                            item(
                                key = "header",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                headerContent()
                            }

                            if (showLikedAndDownloadedPlaylist) {
                                item(
                                    key = likedPlaylist.id,
                                    contentType = { CONTENT_TYPE_PLAYLIST }
                                ) {
                                    AutoPlaylistGridItem(
                                        playlist = likedPlaylist,
                                        thumbnail = Icons.Rounded.Favorite,
                                        fillMaxWidth = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("auto_playlist/${likedPlaylist.id}")
                                            }
                                            .animateItem()
                                    )
                                }

                                item(
                                    key = downloadedPlaylist.id,
                                    contentType = { CONTENT_TYPE_PLAYLIST }
                                ) {
                                    AutoPlaylistGridItem(
                                        playlist = downloadedPlaylist,
                                        thumbnail = Icons.Rounded.CloudDownload,
                                        fillMaxWidth = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("auto_playlist/${downloadedPlaylist.id}")
                                            }
                                            .animateItem()
                                    )
                                }
                            }

                            allItems?.let { allItems ->
                                if (allItems.isEmpty()) {
                                    item {
                                        EmptyPlaceholder(
                                            icon = Icons.AutoMirrored.Rounded.List,
                                            text = stringResource(R.string.library_album_empty),
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }

                                items(
                                    items = allItems.distinctBy { it.hashCode() },
                                    key = { it.hashCode() },
                                    contentType = { CONTENT_TYPE_LIST }
                                ) { item ->
                                    when (item) {
                                        is Album -> {
                                            LibraryAlbumGridItem(
                                                navController = navController,
                                                menuState = menuState,
                                                coroutineScope = coroutineScope,
                                                album = item,
                                                isActive = item.id == mediaMetadata?.album?.id,
                                                isPlaying = isPlaying,
                                                modifier = Modifier.animateItem()
                                            )
                                        }

                                        is Artist -> {
                                            LibraryArtistGridItem(
                                                navController = navController,
                                                menuState = menuState,
                                                coroutineScope = coroutineScope,
                                                modifier = Modifier.animateItem(),
                                                artist = item
                                            )
                                        }

                                        is Playlist -> {
                                            LibraryPlaylistGridItem(
                                                navController = navController,
                                                menuState = menuState,
                                                coroutineScope = coroutineScope,
                                                playlist = item,
                                                modifier = Modifier.animateItem()
                                            )
                                        }

                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }
}
