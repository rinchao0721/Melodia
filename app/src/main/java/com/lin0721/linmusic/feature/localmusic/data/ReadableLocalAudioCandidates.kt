package com.lin0721.linmusic.feature.localmusic.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import com.lin0721.linmusic.feature.localmusic.domain.LocalAudioCandidate
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only descriptor identity; no file or permission mutation. */
internal fun localAudioIdentity(context: Context, uri: String): String? = try {
    context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { asset ->
        if (asset.length == 0L) return@use null
        val stat = runCatching { Os.fstat(asset.parcelFileDescriptor.fileDescriptor) }.getOrNull()
        if (stat != null && OsConstants.S_ISREG(stat.st_mode)) {
            val length = asset.declaredLength.takeIf { it >= 0L }
                ?: (stat.st_size - asset.startOffset)
            if (length <= 0L) null
            else "file:${stat.st_dev}:${stat.st_ino}:${asset.startOffset}:$length"
        } else {
            // Providers backed by pipes cannot prove physical identity; retain URI uniqueness.
            "uri:$uri"
        }
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

internal suspend fun readableLocalAudioCandidates(
    context: Context,
    tracks: List<LocalTrack>
): List<LocalAudioCandidate> = withContext(Dispatchers.IO) {
    tracks.mapNotNull { track ->
        val uri = track.uri.toString()
        val identity = localAudioIdentity(context, uri) ?: return@mapNotNull null
        val fileName = track.path?.substringAfterLast('/') ?: runCatching {
            context.contentResolver.query(track.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        LocalAudioCandidate(uri, track.title, track.artist, track.durationMs, identity, track.album, fileName)
    }
}
