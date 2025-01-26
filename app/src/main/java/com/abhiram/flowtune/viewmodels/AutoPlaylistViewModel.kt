package com.abhiram.flowtune.viewmodels

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abhiram.flowtune.constants.SongSortDescendingKey
import com.abhiram.flowtune.constants.SongSortType
import com.abhiram.flowtune.constants.SongSortTypeKey
import com.abhiram.flowtune.db.MusicDatabase
import com.abhiram.flowtune.extensions.toEnum
import com.abhiram.flowtune.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!

    val thumbnail: StateFlow<ImageVector> = MutableStateFlow(
        when (playlistId) {
            "liked" -> Icons.Rounded.Favorite
            "downloaded" -> Icons.Rounded.CloudDownload
            else -> Icons.AutoMirrored.Rounded.QueueMusic
        }
    ).asStateFlow()

    val songs = context.dataStore.data
        .map {
            it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE) to (it[SongSortDescendingKey] ?: true)
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            when (playlistId) {
                "liked" -> database.likedSongs(sortType, descending)
                "downloaded" -> database.downloadSongs(sortType, descending)
                else -> MutableStateFlow(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
