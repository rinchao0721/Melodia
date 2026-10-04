package com.lin0721.linmusic.core.player

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricsKind
import com.lin0721.linmusic.core.player.domain.shouldShowFloatingLyrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private const val TAG = "FloatingLyricService"

class FloatingLyricService : Service() {

    private val playerManager: PlayerManager by inject()
    private val settingsPreferences: SettingsPreferences by inject()
    private val lyricsResolver: LyricsResolver by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lyricJob: Job? = null
    private var positionJob: Job? = null
    private var pureMusicPreviewJob: Job? = null

    private var windowManager: WindowManager? = null
    private var floatingView: TextView? = null

    private val lyricLines = mutableListOf<LyricLine>()
    private var currentSongId = -1L
    private var isActuallyPlaying = false
    private var lyricsKind: LyricsKind? = null
    private var showPureMusicPreview = false
    private var pureMusicPreviewConsumed = false
    private var isFloatingViewVisible = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupFloatingWindow()
        observePlayback()
        observeStyle()
    }

    // 监听悬浮歌词样式变化
    private fun observeStyle() {
        serviceScope.launch {
            settingsPreferences.lyricTextSize.collectLatest { size ->
                floatingView?.textSize = size.toFloat()
            }
        }
        serviceScope.launch {
            settingsPreferences.lyricTextColor.collectLatest { colorHex ->
                try {
                    floatingView?.setTextColor(Color.parseColor(colorHex))
                } catch (e: Exception) {
                    AppLogger.w(TAG, "悬浮歌词颜色值非法: $colorHex，回退白色", e)
                    floatingView?.setTextColor(Color.WHITE)
                }
            }
        }
    }

    private fun setupFloatingWindow() {
        val view = TextView(this).apply {
            text = "Melodia 悬浮歌词"
            alpha = 0f
            visibility = View.INVISIBLE
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(32, 16, 32, 16)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#B3000000")) // 70% 透明黑
                cornerRadius = 30f
            }
        }
        floatingView = view

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120
        }

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                serviceScope.launch {
                    settingsPreferences.saveShowDesktopLrc(false)
                }
                stopSelf()
                return true
            }
        })

        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                gestureDetector.onTouchEvent(event)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(view, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(view, params)
    }

    private fun observePlayback() {
        serviceScope.launch {
            playerManager.isPlaying.collectLatest { isPlaying ->
                isActuallyPlaying = isPlaying
                if (isPlaying) {
                    startPureMusicPreviewIfNeeded()
                } else {
                    pureMusicPreviewJob?.cancel()
                    showPureMusicPreview = false
                }
                updateFloatingViewVisibility()
            }
        }

        serviceScope.launch {
            playerManager.currentTrack.collectLatest { mediaItem ->
                val songId = mediaItem?.mediaId?.toLongOrNull() ?: -1L
                if (songId != currentSongId) {
                    currentSongId = songId
                    lyricLines.clear()
                    lyricsKind = null
                    showPureMusicPreview = false
                    pureMusicPreviewConsumed = false
                    pureMusicPreviewJob?.cancel()
                    lyricJob?.cancel()
                    updateFloatingViewVisibility()
                    if (songId != -1L) {
                        lyricJob = launch {
                            lyricsResolver.lyricsWithSourceFor(songId).collect { result ->
                                result.onSuccess { resolved ->
                                    lyricLines.clear()
                                    lyricLines.addAll(resolved.lines)
                                    lyricsKind = resolved.kind
                                    if (resolved.kind == LyricsKind.PURE_MUSIC) {
                                        floatingView?.text = "纯音乐，请欣赏"
                                        startPureMusicPreviewIfNeeded()
                                    } else {
                                        showPureMusicPreview = false
                                        pureMusicPreviewJob?.cancel()
                                    }
                                    updateFloatingViewVisibility()
                                }.onFailure {
                                    AppLogger.w(TAG, "悬浮歌词拉取失败 songId=$songId", it)
                                    lyricsKind = null
                                    updateFloatingViewVisibility()
                                }
                            }
                        }
                    } else {
                        floatingView?.text = "Melodia 播放器"
                        updateFloatingViewVisibility()
                    }
                }
            }
        }

        positionJob = serviceScope.launch {
            playerManager.currentPosition.collectLatest { positionMs ->
                if (lyricLines.isNotEmpty()) {
                    if (lyricsKind == LyricsKind.PURE_MUSIC) {
                        floatingView?.text = "纯音乐，请欣赏"
                        return@collectLatest
                    }
                    val index = findLyricIndex(lyricLines, positionMs)
                    if (index in lyricLines.indices) {
                        floatingView?.text = lyricLines[index].text
                    }
                } else {
                    val curTrack = playerManager.currentTrack.value
                    if (curTrack != null) {
                        floatingView?.text = curTrack.mediaMetadata.title?.toString() ?: "Melodia 播放器"
                    } else {
                        floatingView?.text = "Melodia 播放器"
                    }
                }
            }
        }
    }

    private fun startPureMusicPreviewIfNeeded() {
        if (!isActuallyPlaying || lyricsKind != LyricsKind.PURE_MUSIC || pureMusicPreviewConsumed || showPureMusicPreview) {
            return
        }
        showPureMusicPreview = true
        pureMusicPreviewJob?.cancel()
        pureMusicPreviewJob = serviceScope.launch {
            delay(3_000L)
            showPureMusicPreview = false
            pureMusicPreviewConsumed = true
            updateFloatingViewVisibility()
        }
    }

    private fun updateFloatingViewVisibility() {
        val view = floatingView ?: return
        val shouldShow = shouldShowFloatingLyrics(
            isPlaying = isActuallyPlaying,
            hasTrack = currentSongId != -1L,
            lyricsKind = lyricsKind,
            showPureMusicPreview = showPureMusicPreview
        )
        if (shouldShow == isFloatingViewVisible) return
        isFloatingViewVisible = shouldShow

        view.animate().cancel()
        if (shouldShow) {
            view.visibility = View.VISIBLE
            view.translationY = -8f * resources.displayMetrics.density
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .start()
        } else {
            view.animate()
                .alpha(0f)
                .translationY(-8f * resources.displayMetrics.density)
                .setDuration(150L)
                .withEndAction {
                    if (!isFloatingViewVisible) view.visibility = View.INVISIBLE
                }
                .start()
        }
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

    override fun onDestroy() {
        lyricJob?.cancel()
        positionJob?.cancel()
        pureMusicPreviewJob?.cancel()
        serviceScope.cancel()
        floatingView?.let {
            windowManager?.removeView(it)
            floatingView = null
        }
        super.onDestroy()
    }
}
