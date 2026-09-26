package com.lin0721.linmusic.core.player.external

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import com.hchen.superlyricapi.SuperLyricLine
import com.hchen.superlyricapi.SuperLyricWord
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ExternalLyricCoordinator"

class ExternalLyricCoordinator(
    private val context: Context,
    private val playerManager: PlayerManager,
    private val playbackRepository: PlaybackRepository,
    private val settingsPreferences: SettingsPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lyriconAdapter = LyriconAdapter(context)
    private val fluidCloudNotifier = FluidCloudLyricNotifier(context)

    private var lyricFetchJob: Job? = null

    private var currentSongId: Long = -1L
    private var currentLines: List<LyricLine> = emptyList()
    private var currentLyricIndex: Int = -1

    private var originalTitle: String = ""
    private var originalArtist: String = ""
    private var originalAlbum: String = ""
    private var originalDurationMs: Long = 0L

    private var displayedTitle: String = ""
    private var currentLyricInfoJson: String = ""

    private var isSuperLyricEnabled = false
    private var isLyricInfoEnabled = false
    private var isBluetoothLyricEnabled = false
    private var isLyriconEnabled = false
    private var isFluidCloudLyricEnabled = false
    private var isShowTranslation = true

    var onMetadataChanged: (() -> Unit)? = null

    init {
        observeSettings()
        observePlayback()
    }

    private fun observeSettings() {
        scope.launch {
            settingsPreferences.superLyricEnabled.collectLatest { enabled ->
                isSuperLyricEnabled = enabled
                if (enabled) {
                    SuperLyricHelper.registerPublisher()
                    dispatchSuperLyricCurrentLine()
                } else {
                    SuperLyricHelper.unregisterPublisher()
                }
            }
        }

        scope.launch {
            settingsPreferences.lyricInfoEnabled.collectLatest { enabled ->
                isLyricInfoEnabled = enabled
                rebuildLyricInfo()
                onMetadataChanged?.invoke()
            }
        }

        scope.launch {
            settingsPreferences.bluetoothLyricEnabled.collectLatest { enabled ->
                isBluetoothLyricEnabled = enabled
                if (!enabled) {
                    displayedTitle = originalTitle
                } else {
                    updateBluetoothTitleForIndex(currentLyricIndex)
                }
                onMetadataChanged?.invoke()
            }
        }

        scope.launch {
            settingsPreferences.lyriconEnabled.collectLatest { enabled ->
                isLyriconEnabled = enabled
                lyriconAdapter.setEnabled(enabled)
                if (enabled && currentSongId != -1L) {
                    lyriconAdapter.updateSong(
                        songId = currentSongId.toString(),
                        title = originalTitle,
                        artist = originalArtist,
                        durationMs = originalDurationMs,
                        lines = currentLines
                    )
                }
            }
        }

        scope.launch {
            settingsPreferences.fluidCloudLyricEnabled.collectLatest { enabled ->
                isFluidCloudLyricEnabled = enabled
                refreshFluidCloudLyric()
            }
        }

        scope.launch {
            settingsPreferences.fullScreenLyricShowTranslation.collectLatest { show ->
                isShowTranslation = show
                rebuildLyricInfo()
                if (isBluetoothLyricEnabled) {
                    updateBluetoothTitleForIndex(currentLyricIndex)
                }
                onMetadataChanged?.invoke()
                dispatchSuperLyricCurrentLine()
                refreshFluidCloudLyric()
            }
        }
    }

    private fun observePlayback() {
        scope.launch {
            playerManager.currentTrack.collectLatest { mediaItem ->
                handleTrackChanged(mediaItem)
            }
        }

        scope.launch {
            playerManager.isPlaying.collectLatest { isPlaying ->
                handlePlayStateChanged(isPlaying)
            }
        }

        scope.launch {
            playerManager.currentPosition.collectLatest { positionMs ->
                handlePositionChanged(positionMs)
            }
        }
    }

    private fun handleTrackChanged(mediaItem: MediaItem?) {
        val songId = mediaItem?.mediaId?.toLongOrNull() ?: -1L
        val meta = mediaItem?.mediaMetadata
        originalTitle = meta?.title?.toString() ?: ""
        originalArtist = meta?.artist?.toString() ?: ""
        originalAlbum = meta?.albumTitle?.toString() ?: ""
        displayedTitle = originalTitle

        if (songId != currentSongId) {
            currentSongId = songId
            currentLines = emptyList()
            currentLyricIndex = -1
            currentLyricInfoJson = ""
            lyricFetchJob?.cancel()

            if (songId != -1L) {
                lyricFetchJob = scope.launch {
                    playbackRepository.getLyrics(songId).collect { result ->
                        result.onSuccess { lines ->
                            currentLines = lines
                            rebuildLyricInfo()
                            onMetadataChanged?.invoke()

                            if (isLyriconEnabled) {
                                lyriconAdapter.updateSong(
                                    songId = songId.toString(),
                                    title = originalTitle,
                                    artist = originalArtist,
                                    durationMs = originalDurationMs,
                                    lines = currentLines
                                )
                            }
                            dispatchSuperLyricCurrentLine()
                            refreshFluidCloudLyric()
                        }.onFailure {
                            AppLogger.w(TAG, "外部歌词加载失败: songId=$songId", it)
                        }
                    }
                }
            } else {
                onMetadataChanged?.invoke()
            }
        } else {
            onMetadataChanged?.invoke()
        }
        refreshFluidCloudLyric()
    }

    private fun handlePlayStateChanged(isPlaying: Boolean) {
        if (!isPlaying) {
            if (isSuperLyricEnabled) {
                val data = SuperLyricData()
                    .setTitle(originalTitle)
                    .setArtist(originalArtist)
                SuperLyricHelper.sendStop(data)
            }
            if (isBluetoothLyricEnabled && displayedTitle != originalTitle) {
                displayedTitle = originalTitle
                onMetadataChanged?.invoke()
            }
        } else {
            dispatchSuperLyricCurrentLine()
            if (isBluetoothLyricEnabled) {
                updateBluetoothTitleForIndex(currentLyricIndex)
                onMetadataChanged?.invoke()
            }
        }
        if (isLyriconEnabled) {
            lyriconAdapter.updatePlaybackState(isPlaying, playerManager.currentPosition.value)
        }
        refreshFluidCloudLyric()
    }

    private fun handlePositionChanged(positionMs: Long) {
        if (currentLines.isNotEmpty()) {
            val newIndex = findLyricIndex(currentLines, positionMs)
            if (newIndex != currentLyricIndex) {
                currentLyricIndex = newIndex
                if (playerManager.isPlaying.value) {
                    dispatchSuperLyricCurrentLine()
                    if (isBluetoothLyricEnabled) {
                        updateBluetoothTitleForIndex(newIndex)
                        onMetadataChanged?.invoke()
                    }
                    refreshFluidCloudLyric()
                }
            }
        }
        if (isLyriconEnabled) {
            lyriconAdapter.updatePlaybackState(playerManager.isPlaying.value, positionMs)
        }
    }

    private fun updateBluetoothTitleForIndex(index: Int) {
        if (!isBluetoothLyricEnabled) {
            displayedTitle = originalTitle
            return
        }
        val line = currentLines.getOrNull(index)
        if (line != null && line.text.isNotBlank()) {
            val trans = line.translation
            displayedTitle = if (isShowTranslation && !trans.isNullOrBlank()) {
                "${line.text} (${trans})"
            } else {
                line.text
            }
        } else {
            displayedTitle = originalTitle
        }
    }

    private fun dispatchSuperLyricCurrentLine() {
        if (!isSuperLyricEnabled || currentSongId == -1L || currentLines.isEmpty()) return
        val index = currentLyricIndex
        val line = currentLines.getOrNull(index) ?: return
        if (line.text.isBlank()) return

        val wordsArray = if (line.words.isNotEmpty()) {
            line.words.map { w ->
                val start = line.timeMs + w.startOffsetMs
                val end = start + w.durationMs
                SuperLyricWord(w.text, start, end)
            }.toTypedArray()
        } else null

        val nextLineStartTime = currentLines.getOrNull(index + 1)?.timeMs
        val lineEndTime = if (line.durationMs > 0) {
            line.timeMs + line.durationMs
        } else {
            nextLineStartTime ?: (line.timeMs + 4000L)
        }

        val lyricLine = SuperLyricLine(line.text, wordsArray, line.timeMs, lineEndTime)
        val data = SuperLyricData()
            .setTitle(originalTitle)
            .setArtist(originalArtist)
            .setAlbum(originalAlbum)
            .setLyric(lyricLine)

        val translation = line.translation
        if (isShowTranslation && !translation.isNullOrBlank()) {
            data.setTranslation(SuperLyricLine(translation, line.timeMs, lineEndTime))
        }
        val roma = line.roma
        if (!roma.isNullOrBlank()) {
            data.setSecondary(SuperLyricLine(roma, line.timeMs, lineEndTime))
        }

        SuperLyricHelper.sendLyric(data)
    }

    private fun refreshFluidCloudLyric() {
        if (!isFluidCloudLyricEnabled || currentSongId == -1L || !playerManager.isPlaying.value) {
            fluidCloudNotifier.dismiss()
            return
        }
        val line = currentLines.getOrNull(currentLyricIndex)
        fluidCloudNotifier.show(
            title = originalTitle,
            artist = originalArtist,
            lyric = line?.text,
            translation = if (isShowTranslation) line?.translation else null
        )
    }

    private fun rebuildLyricInfo() {
        if (!isLyricInfoEnabled || currentSongId == -1L || currentLines.isEmpty()) {
            currentLyricInfoJson = ""
            return
        }
        currentLyricInfoJson = LyricInfoBuilder.buildLyricInfoJson(
            songName = originalTitle,
            artist = originalArtist,
            songId = currentSongId.toString(),
            album = originalAlbum,
            lines = currentLines,
            showTranslation = isShowTranslation
        )
    }

    fun applyToMediaMetadata(builder: MediaMetadata.Builder, original: MediaMetadata): MediaMetadata {
        if (isBluetoothLyricEnabled && displayedTitle.isNotBlank()) {
            builder.setTitle(displayedTitle)
        } else {
            builder.setTitle(originalTitle.ifBlank { original.title })
        }

        val existingExtras = original.extras
        if (isLyricInfoEnabled && currentLyricInfoJson.isNotBlank()) {
            val bundle = if (existingExtras != null) Bundle(existingExtras) else Bundle()
            bundle.putString("lyricInfo", currentLyricInfoJson)
            builder.setExtras(bundle)
        } else if (existingExtras != null && existingExtras.containsKey("lyricInfo")) {
            val bundle = Bundle(existingExtras)
            bundle.remove("lyricInfo")
            builder.setExtras(bundle)
        }

        return builder.build()
    }

    private fun findLyricIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    fun release() {
        if (isSuperLyricEnabled) {
            SuperLyricHelper.unregisterPublisher()
        }
        lyriconAdapter.release()
        fluidCloudNotifier.dismiss()
        lyricFetchJob?.cancel()
        scope.cancel()
        onMetadataChanged = null
    }
}
