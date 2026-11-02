package com.lin0721.linmusic.desktop.data.cloud

import com.lin0721.linmusic.desktop.data.local.LocalTagIo
import com.lin0721.linmusic.desktop.data.local.UNKNOWN_ARTIST
import com.lin0721.linmusic.desktop.data.local.isAudioFile
import com.lin0721.linmusic.desktop.data.local.normalizePath
import com.lin0721.linmusic.feature.cloud.upload.LocalAudioMetadata
import com.lin0721.linmusic.feature.cloud.upload.UploadSource
import java.io.File
import java.io.InputStream

private val MIME_TYPES = mapOf(
    "mp3" to "audio/mpeg",
    "flac" to "audio/flac",
    "m4a" to "audio/mp4",
    "aac" to "audio/aac",
    "ogg" to "audio/ogg",
    "opus" to "audio/ogg",
    "wav" to "audio/wav",
    "aif" to "audio/aiff",
    "aiff" to "audio/aiff",
    "wma" to "audio/x-ms-wma",
    "ape" to "audio/ape",
    "wv" to "audio/wavpack"
)

class FileUploadSource(private val file: File) : UploadSource {

    override val key: String = normalizePath(file.path)

    override val fileName: String = file.name

    override val mimeType: String = MIME_TYPES[file.extension.lowercase()] ?: "application/octet-stream"

    override fun size(): Long = file.length()

    override fun openStream(): InputStream = file.inputStream()

    override fun readMetadata(): LocalAudioMetadata {
        val tags = runCatching { LocalTagIo.read(file) }.getOrNull()
            ?: return LocalAudioMetadata(title = null, artist = null, album = null)
        return LocalAudioMetadata(
            title = tags.title.ifBlank { null },
            artist = tags.artist.takeIf { it.isNotBlank() && it != UNKNOWN_ARTIST },
            album = tags.album.ifBlank { null }
        )
    }
}

internal fun uploadSourcesOf(files: Collection<File>): List<UploadSource> =
    files.filter { it.isFile && isAudioFile(it) }.map(::FileUploadSource)
