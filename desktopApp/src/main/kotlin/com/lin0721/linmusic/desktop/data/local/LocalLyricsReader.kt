package com.lin0721.linmusic.desktop.data.local

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

private const val MAX_LYRICS_BYTES = 2L * 1024 * 1024

// 同名 .lrc 优先，其次内嵌歌词
object LocalLyricsReader {

    private val gb18030: Charset = Charset.forName("GB18030")

    fun read(audioPath: String): String? {
        val audio = File(audioPath)
        if (!audio.isFile) return null
        sidecarFile(audio)?.let { lrc ->
            runCatching { decode(lrc.readBytes()) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return LocalTagIo.readEmbeddedLyrics(audio)
    }

    internal fun sidecarFile(audio: File): File? {
        val dir = audio.parentFile ?: return null
        val base = audio.nameWithoutExtension
        val candidates = dir.listFiles { file ->
            file.isFile && file.extension.equals("lrc", ignoreCase = true) && file.nameWithoutExtension.equals(base, ignoreCase = true)
        } ?: return null
        val lrc = candidates.firstOrNull { it.nameWithoutExtension == base } ?: candidates.firstOrNull()
        return lrc?.takeIf { it.length() in 1..MAX_LYRICS_BYTES }
    }

    // 有 BOM 按 BOM，否则先严格按 UTF-8 解码，失败再按 GB18030
    internal fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, gb18030)
        }
    }
}
