package com.lin0721.linmusic.core.localmusic.db

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

actual class LocalMusicDatabaseFactory(private val dataDir: File) {
    actual fun createBuilder(): RoomDatabase.Builder<LocalMusicDatabase> {
        dataDir.mkdirs()
        val dbFile = File(dataDir, LocalMusicDatabase.NAME)
        return Room.databaseBuilder<LocalMusicDatabase>(
            name = dbFile.absolutePath
        )
    }
}
