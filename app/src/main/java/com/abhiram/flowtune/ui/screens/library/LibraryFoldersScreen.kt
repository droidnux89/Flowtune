package com.abhiram.flowtune.ui.screens.library

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.abhiram.flowtune.LocalDatabase
import com.abhiram.flowtune.LocalPlayerAwareWindowInsets
import com.abhiram.flowtune.LocalPlayerConnection
import com.abhiram.flowtune.R
import com.abhiram.flowtune.constants.CONTENT_TYPE_FOLDER
import com.abhiram.flowtune.constants.CONTENT_TYPE_HEADER
import com.abhiram.flowtune.constants.CONTENT_TYPE_SONG
import com.abhiram.flowtune.constants.FlatSubfoldersKey
import com.abhiram.flowtune.constants.LastLocalScanKey
import com.abhiram.flowtune.constants.SongSortDescendingKey
import com.abhiram.flowtune.constants.SongSortType
import com.abhiram.flowtune.constants.SongSortTypeKey
import com.abhiram.flowtune.db.entities.Song
import com.abhiram.flowtune.models.DirectoryTree
import com.abhiram.flowtune.models.toMediaMetadata
import com.abhiram.flowtune.playback.queues.ListQueue
import com.abhiram.flowtune.ui.component.HideOnScrollFAB
import com.abhiram.flowtune.ui.component.LocalMenuState
import com.abhiram.flowtune.ui.component.SelectHeader
import com.abhiram.flowtune.ui.component.SongFolderItem
import com.abhiram.flowtune.ui.component.SongListItem
import com.abhiram.flowtune.ui.component.SortHeader
import com.abhiram.flowtune.utils.numberToAlpha
import com.abhiram.flowtune.utils.rememberEnumPreference
import com.abhiram.flowtune.utils.rememberPreference
import com.abhiram.flowtune.viewmodels.LibrarySongsViewModel
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Stack

@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryFoldersScreen(
    navController: NavController,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
    filterContent: @Composable (() -> Unit)? = null
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val snackbarHostState = remember { SnackbarHostState() }

    /**
     * The top of the stack is the folder that the page will render.
     * Clicking on a folder pushes, while the back button pops.
     */
    val folderStack = remember { Stack<DirectoryTree>() }
    val flatSubfolders by rememberPreference(FlatSubfoldersKey, defaultValue = true)
    val lastLocalScan by rememberPreference(LastLocalScanKey, LocalDateTime.now().atOffset(ZoneOffset.UTC).toEpochSecond())

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    val lazyListState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    // destroy old structure when pref changes
    LaunchedEffect(flatSubfolders, lastLocalScan) {
        folderStack.clear()
    }

    // initialize with first directory
    if (folderStack.isEmpty()) {
        viewModel.getLocalSongs(database)

        folderStack.push(
            if (flatSubfolders) viewModel.localSongDirectoryTree.value.toFlattenedTree()
            else viewModel.localSongDirectoryTree.value
        )
    }

    // content to load for this page
    var currDir by remember {
        // hello mikooo from the fture, this is mikooo from the past warning you to not touch this.
        // mikooo, you clearly are just going to waste time trying to put this in the in the viewmodel
        // If anyone else would like to try, be my guest
        mutableStateOf(folderStack.peek())
    }

    val mutableSongs = remember {
        mutableStateListOf<Song>()
    }

    // multiselect
    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    LaunchedEffect(inSelectMode) {
        backStackEntry?.savedStateHandle?.set("inSelectMode", inSelectMode)
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(sortType, sortDescending, currDir) {
        val tempList = currDir.files.map { it }.toMutableList()
        // sort songs
        tempList.sortBy {
            when (sortType) {
                SongSortType.CREATE_DATE -> numberToAlpha(it.song.inLibrary?.toEpochSecond(ZoneOffset.UTC)?: -1L)
                SongSortType.MODIFIED_DATE -> numberToAlpha(it.song.getDateModifiedLong()?: -1L)
                SongSortType.RELEASE_DATE -> numberToAlpha(it.song.getDateLong()?: -1L)
                SongSortType.NAME -> it.song.title.lowercase()
                SongSortType.ARTIST -> it.artists.joinToString { artist -> artist.name }.lowercase()
                SongSortType.PLAY_TIME -> numberToAlpha(it.song.totalPlayTime)
                SongSortType.PLAY_COUNT -> numberToAlpha((it.playCount?.fastSumBy { it.count })?.toLong() ?: 0L)
            }
        }
        // sort folders
        currDir.subdirs.sortBy { it.currentDir.lowercase() } // only sort by name

        if (sortDescending) {
            currDir.subdirs.reverse()
            tempList.reverse()
        }

        mutableSongs.clear()
        mutableSongs.addAll(tempList)
    }

    BackHandler(folderStack.size > 1) {
        folderStack.pop()
        currDir = folderStack.peek()
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            item(
                key = "header",
                contentType = CONTENT_TYPE_HEADER
            ) {
                Column(
                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                ) {
                    filterContent?.let {
                        it()
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        if (inSelectMode) {
                            SelectHeader(
                                selectedItems = selection.mapNotNull { songId ->
                                    mutableSongs.find { it.id == songId }
                                }.map { it.toMediaMetadata()},
                                totalItemCount = mutableSongs.size,
                                onSelectAll = {
                                    selection.clear()
                                    selection.addAll(mutableSongs.map { it.id })
                                },
                                onDeselectAll = { selection.clear() },
                                menuState = menuState,
                                onDismiss = onExitSelectionMode
                            )
                        } else {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = sortDescending,
                                onSortTypeChange = onSortTypeChange,
                                onSortDescendingChange = onSortDescendingChange,
                                sortTypeText = { sortType ->
                                    when (sortType) {
                                        SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                        SongSortType.MODIFIED_DATE -> R.string.sort_by_date_modified
                                        SongSortType.RELEASE_DATE -> R.string.sort_by_date_released
                                        SongSortType.NAME -> R.string.sort_by_name
                                        SongSortType.ARTIST -> R.string.sort_by_artist
                                        SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                        SongSortType.PLAY_COUNT -> R.string.sort_by_play_count
                                    }
                                }
                            )

                            Spacer(Modifier.weight(1f))

                            Text(
                                text = pluralStringResource(
                                    R.plurals.n_song, currDir.toList().size, currDir.toList().size
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            if (folderStack.size > 1)
                item(
                    key = "previous",
                    contentType = CONTENT_TYPE_FOLDER
                ) {
                    SongFolderItem(
                        folderTitle = "..",
                        subtitle = "Previous folder",
                        modifier = Modifier
                            .clickable {
                                if (folderStack.size > 1) {
                                    folderStack.pop()
                                    currDir = folderStack.peek()
                                }
                            }
                    )
                }

            // all subdirectories listed here
            itemsIndexed(
                items = currDir.subdirs,
                key = { _, item -> item.uid },
                contentType = { _, _ -> CONTENT_TYPE_FOLDER }
            ) { index, folder ->
                SongFolderItem(
                    folder = folder,
                    subtitle = "${folder.toList().size} Song${if (folder.toList().size > 1) "" else "s"}",
                    modifier = Modifier
                        .combinedClickable {
                            // navigate to next page
                            currDir = folderStack.push(folder)
                        }
                        .animateItem(),
                    menuState = menuState,
                    navController = navController
                )
            }

            // separator
            if (currDir.subdirs.isNotEmpty() && mutableSongs.isNotEmpty()) {
                item(
                    key = "folder_songs_divider",
                ) {
                    HorizontalDivider(
                        thickness = DividerDefaults.Thickness,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }

            // all songs get listed here
            itemsIndexed(
                items = mutableSongs,
                key = { _, item -> item.id },
                contentType = { _, _ -> CONTENT_TYPE_SONG }
            ) { index, song ->
                SongListItem(
                    song = song,
                    onPlay = {
                        playerConnection.playQueue(
                            ListQueue(
                                title = currDir.currentDir,
                                items = mutableSongs.map { it.toMediaMetadata() },
                                startIndex = mutableSongs.indexOf(song)
                            )
                        )
                    },
                    onSelectedChange = {
                        inSelectMode = true
                        if (it) {
                            selection.add(song.id)
                        } else {
                            selection.remove(song.id)
                        }
                    },
                    inSelectMode = inSelectMode,
                    isSelected = selection.contains(song.id),
                    navController = navController,
                    modifier = Modifier.fillMaxWidth().animateItem()
                )
            }
        }

        HideOnScrollFAB(
            visible = currDir.toList().isNotEmpty(),
            lazyListState = lazyListState,
            icon = Icons.Rounded.Shuffle,
            onClick = {
                playerConnection.playQueue(
                    ListQueue(
                        title = currDir.currentDir,
                        items = currDir.toSortedList(sortType, sortDescending).shuffled().map { it.toMediaMetadata() }
                    )
                )
            }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }
}
