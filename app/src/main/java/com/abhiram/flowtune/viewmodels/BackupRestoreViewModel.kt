package com.abhiram.flowtune.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import com.abhiram.flowtune.MainActivity
import com.abhiram.flowtune.R
import com.abhiram.flowtune.constants.ScannerMatchCriteria
import com.abhiram.flowtune.db.InternalDatabase
import com.abhiram.flowtune.db.MusicDatabase
import com.abhiram.flowtune.db.entities.ArtistEntity
import com.abhiram.flowtune.db.entities.Song
import com.abhiram.flowtune.db.entities.SongEntity
import com.abhiram.flowtune.extensions.div
import com.abhiram.flowtune.extensions.zipInputStream
import com.abhiram.flowtune.extensions.zipOutputStream
import com.abhiram.flowtune.playback.MusicService
import com.abhiram.flowtune.utils.reportException
import com.abhiram.flowtune.utils.scanners.LocalMediaScanner.Companion.compareSong
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    val database: MusicDatabase,
) : ViewModel() {
    fun backup(context: Context, uri: Uri) {
        runCatching {
            context.applicationContext.contentResolver.openOutputStream(uri)?.use {
                it.buffered().zipOutputStream().use { outputStream ->
                    outputStream.setLevel(Deflater.BEST_COMPRESSION)
                    (context.filesDir / "datastore" / SETTINGS_FILENAME).inputStream().buffered().use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                        inputStream.copyTo(outputStream)
                    }
                    runBlocking(Dispatchers.IO) {
                        database.checkpoint()
                    }
                    FileInputStream(database.openHelper.writableDatabase.path).use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }.onSuccess {
            Toast.makeText(context, R.string.backup_create_success, Toast.LENGTH_SHORT).show()
        }.onFailure {
            reportException(it)
            Toast.makeText(context, R.string.backup_create_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun restore(context: Context, uri: Uri) {
        runCatching {
            context.applicationContext.contentResolver.openInputStream(uri)?.use {
                it.zipInputStream().use { inputStream ->
                    var entry = inputStream.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            SETTINGS_FILENAME -> {
                                (context.filesDir / "datastore" / SETTINGS_FILENAME).outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }

                            InternalDatabase.DB_NAME -> {
                                runBlocking(Dispatchers.IO) {
                                    database.checkpoint()
                                }
                                database.close()
                                FileOutputStream(database.openHelper.writableDatabase.path).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }
                        entry = inputStream.nextEntry
                    }
                }
            }
            context.stopService(Intent(context, MusicService::class.java))
            context.startActivity(Intent(context, MainActivity::class.java))
            exitProcess(0)
        }.onFailure {
            reportException(it)
            Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Parse m3u file and scans the database for matching songs
     */
    fun loadM3u(
        context: Context,
        uri: Uri,
        matchStrength: ScannerMatchCriteria = ScannerMatchCriteria.LEVEL_2
    ): Triple<ArrayList<Song>, ArrayList<String>, String> {
        val songs = ArrayList<Song>()
        val rejectedSongs = ArrayList<String>()

        runCatching {
            context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                val lines = stream.readLines()
                if (lines.first().startsWith("#EXTM3U")) {
                    lines.forEachIndexed { index, rawLine ->
                        if (rawLine.startsWith("#EXTINF:")) {
                            // maybe later write this to be more efficient
                            val artists =
                                rawLine.substringAfter("#EXTINF:").substringAfter(',').substringBefore(" - ").split(';')
                            val title = rawLine.substringAfter("#EXTINF:").substringAfter(',').substringAfter(" - ")

                            val mockSong = Song(
                                song = SongEntity(
                                    id = "",
                                    title = title,
                                    isLocal = true,
                                    localPath = if (index + 1 < lines.size) lines[index + 1] else ""
                                ),
                                artists = artists.map { ArtistEntity("", it) },
                            )

                            // now find the best match
                            val matches = database.searchSongs(title)
                            val oldSize = songs.size
                            runBlocking {
                                matches.first().forEach {
                                    if (compareSong(mockSong, it, matchStrength = matchStrength)) {
                                        songs.add(it)
                                    }
                                }
                            }

                            if (oldSize == songs.size) {
                                rejectedSongs.add(rawLine)
                            }
                        }
                    }
                }
            }
        }.onFailure {
            reportException(it)
            Toast.makeText(context, R.string.m3u_import_playlist_failed, Toast.LENGTH_SHORT).show()
        }

        if (songs.isEmpty()) {
            Toast.makeText(
                context,
                "No songs found. Invalid file, or perhaps no song matches were found.",
                Toast.LENGTH_SHORT
            ).show()
        }
        return Triple(songs, rejectedSongs, uri.path?.substringAfterLast('/')?.substringBeforeLast('.') ?: "")
    }

    /**
     * Read a file to a string
     */
    private fun InputStream.readLines(): List<String> {
        return this.bufferedReader().useLines { it.toList() }
    }


    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"
    }
}
