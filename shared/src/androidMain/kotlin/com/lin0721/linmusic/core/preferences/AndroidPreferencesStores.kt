package com.lin0721.linmusic.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File

// 与 preferencesDataStore 委托相同的落盘位置，升级后沿用原有数据
fun PreferencesStores.get(context: Context, name: String): DataStore<Preferences> =
    get(File(context.applicationContext.filesDir, "datastore/$name.preferences_pb"))
