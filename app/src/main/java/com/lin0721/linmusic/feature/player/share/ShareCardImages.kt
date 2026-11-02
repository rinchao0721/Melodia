package com.lin0721.linmusic.feature.player.share

import android.Manifest
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ShareCardImages {
    private const val TAG = "ShareCardImages"
    private const val SHARE_DIR = "share_cards"
    private const val ALBUM_DIR = "Melodia"

    fun needsLegacyPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    suspend fun saveToGallery(context: Context, bitmap: Bitmap, baseName: String): Boolean = withContext(Dispatchers.IO) {
        val name = "${sanitize(baseName)}_${System.currentTimeMillis()}.png"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM_DIR")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("MediaStore 插入失败")
                try {
                    resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        ?: throw IOException("无法打开输出流")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM_DIR)
                if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建目录 ${dir.path}")
                val file = File(dir, name)
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf("image/png"), null)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存分享图片失败", e)
            false
        }
    }

    // 缓存目录只保留最近一张
    suspend fun writeShareFile(context: Context, bitmap: Bitmap, baseName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, SHARE_DIR)
            if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建目录 ${dir.path}")
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "${sanitize(baseName)}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "写入分享图片失败", e)
            null
        }
    }

    fun launchShare(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享图片"))
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(40).ifBlank { "Melodia" }
}
