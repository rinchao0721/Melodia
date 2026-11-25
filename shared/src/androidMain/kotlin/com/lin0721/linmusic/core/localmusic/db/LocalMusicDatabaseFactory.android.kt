package com.lin0721.linmusic.core.localmusic.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual class LocalMusicDatabaseFactory(private val context: Context) {
    actual fun createBuilder(): RoomDatabase.Builder<LocalMusicDatabase> {
        val dbFile = context.getDatabasePath(LocalMusicDatabase.NAME)
        return Room.databaseBuilder<LocalMusicDatabase>(
            context = context.applicationContext,
            name = dbFile.absolutePath
        )
    }
}
