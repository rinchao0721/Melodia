package com.lin0721.linmusic.feature.recognition.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.lin0721.linmusic.core.log.AppLogger

private const val TAG = "RecognitionTileService"

// 快捷设置「识曲」：不离开当前 App，点一下即后台内录识别
class RecognitionTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (isLocked) {
            unlockAndRun { launchConsent() }
        } else {
            launchConsent()
        }
    }

    private fun launchConsent() {
        val intent = Intent(this, ProjectionConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }.onFailure { AppLogger.e(TAG, "磁贴拉起授权页失败", it) }
    }
}
