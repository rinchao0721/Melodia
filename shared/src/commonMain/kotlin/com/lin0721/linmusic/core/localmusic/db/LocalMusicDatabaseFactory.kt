package com.lin0721.linmusic.core.localmusic.db

import androidx.room.RoomDatabase

expect class LocalMusicDatabaseFactory {
    fun createBuilder(): RoomDatabase.Builder<LocalMusicDatabase>
}
