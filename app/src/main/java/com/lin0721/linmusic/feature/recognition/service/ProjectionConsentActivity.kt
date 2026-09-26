package com.lin0721.linmusic.feature.recognition.service

import android.Manifest
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.lin0721.linmusic.core.log.AppLogger
import org.koin.android.ext.android.inject

private const val TAG = "ProjectionConsentActivity"

// 透明跳板：依次申请录音、通知、录屏授权，随即启动内录服务并关闭，原 App 保持前台
class ProjectionConsentActivity : ComponentActivity() {

    private val launcher: PlaybackRecognitionLauncher by inject()

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestNotificationPermission() else finishWithToast("需要录音权限才能识曲，可在 Melodia 设置中开启")
    }

    // 通知权限只影响结果呈现方式（通知 / Toast），拒绝也继续
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestProjection()
    }

    private val projectionConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            launcher.start(result.resultCode, data)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!launcher.isSupported) {
            finishWithToast("内录识曲需要 Android 10 及以上")
            return
        }
        // 旋转等重建时授权流程已在进行中，不重复发起
        if (savedInstanceState != null) return
        if (isGranted(Manifest.permission.RECORD_AUDIO)) {
            requestNotificationPermission()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestProjection()
        }
    }

    private fun requestProjection() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            finishWithToast("系统不支持内录")
            return
        }
        runCatching { projectionConsent.launch(manager.createScreenCaptureIntent()) }
            .onFailure {
                AppLogger.e(TAG, "无法打开录屏授权", it)
                finishWithToast("无法打开录屏授权")
            }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun finishWithToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
