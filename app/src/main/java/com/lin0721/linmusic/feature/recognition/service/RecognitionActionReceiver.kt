package com.lin0721.linmusic.feature.recognition.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.recognition.data.RecognitionPlayback
import com.lin0721.linmusic.feature.recognition.domain.RecognitionCandidate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "RecognitionActionReceiver"
private const val ACTION_PLAY = "com.lin0721.linmusic.action.RECOGNITION_PLAY"
private const val EXTRA_SONG_ID = "extra_song_id"
private const val EXTRA_TITLE = "extra_title"
private const val EXTRA_ARTISTS = "extra_artists"
private const val EXTRA_COVER = "extra_cover"
private const val EXTRA_START_MS = "extra_start_ms"

// goAsync 最多给约 10 秒，冷启动连播放服务要留足余量
private const val READY_TIMEOUT_MS = 8_000L

// 结果通知的「播放」按钮：不打开界面，直接插到下一首并从命中位置播放
class RecognitionActionReceiver : BroadcastReceiver(), KoinComponent {

    companion object {
        fun playIntent(context: Context, candidate: RecognitionCandidate): Intent =
            Intent(context, RecognitionActionReceiver::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_SONG_ID, candidate.songId)
                .putExtra(EXTRA_TITLE, candidate.title)
                .putExtra(EXTRA_ARTISTS, candidate.artists)
                .putExtra(EXTRA_COVER, candidate.coverUrl)
                .putExtra(EXTRA_START_MS, candidate.startTimeMs)
    }

    private val playback: RecognitionPlayback by inject()
    private val notifications: RecognitionNotifications by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PLAY) return
        val songId = intent.getLongExtra(EXTRA_SONG_ID, 0L)
        if (songId <= 0L) return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val artists = intent.getStringExtra(EXTRA_ARTISTS).orEmpty()
        val cover = intent.getStringExtra(EXTRA_COVER).orEmpty()
        val startMs = intent.getLongExtra(EXTRA_START_MS, 0L)

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            try {
                withTimeoutOrNull(READY_TIMEOUT_MS) { playback.ensureReady() }
                    ?: AppLogger.w(TAG, "播放器准备超时，仍尝试直接播放")
                playback.playFrom(songId, title, artists, cover, startMs)
                notifications.cancelResult()
            } catch (e: Exception) {
                AppLogger.e(TAG, "通知播放失败", e)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }
}
