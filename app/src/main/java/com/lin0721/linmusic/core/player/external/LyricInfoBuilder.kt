package com.lin0721.linmusic.core.player.external

import com.lin0721.linmusic.core.player.domain.LyricLine
import org.json.JSONObject
import java.util.Locale

object LyricInfoBuilder {

    fun formatLrcTag(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val m = safeMs / 60000
        val s = (safeMs % 60000) / 1000
        val msPart = safeMs % 1000
        return String.format(Locale.US, "[%02d:%02d.%03d]", m, s, msPart)
    }

    fun formatWordTag(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val m = safeMs / 60000
        val s = (safeMs % 60000) / 1000
        val msPart = safeMs % 1000
        return String.format(Locale.US, "<%02d:%02d.%03d>", m, s, msPart)
    }

    fun buildPlainLrc(lines: List<LyricLine>): String {
        val buf = StringBuilder()
        for (line in lines) {
            if (line.text.isBlank()) continue
            buf.append(formatLrcTag(line.timeMs))
                .append(line.text)
                .append('\n')
        }
        return buf.toString().trimEnd()
    }

    fun buildRawLyric(lines: List<LyricLine>): String {
        val buf = StringBuilder()
        for (line in lines) {
            if (line.text.isBlank()) continue
            buf.append(formatLrcTag(line.timeMs))
            if (line.words.isNotEmpty()) {
                for (w in line.words) {
                    val wordStart = line.timeMs + w.startOffsetMs
                    buf.append(formatWordTag(wordStart))
                        .append(w.text)
                }
                val last = line.words.last()
                val wordEnd = line.timeMs + last.startOffsetMs + last.durationMs
                if (last.durationMs > 0) {
                    buf.append(formatWordTag(wordEnd))
                }
            } else {
                buf.append(formatWordTag(line.timeMs))
                    .append(line.text)
                if (line.durationMs > 0) {
                    buf.append(formatWordTag(line.timeMs + line.durationMs))
                }
            }
            buf.append('\n')
        }
        return buf.toString().trimEnd()
    }

    fun buildTranslationLrc(lines: List<LyricLine>): String {
        val buf = StringBuilder()
        for (line in lines) {
            val trans = line.translation
            if (!trans.isNullOrBlank()) {
                buf.append(formatLrcTag(line.timeMs))
                    .append(trans)
                    .append('\n')
            }
        }
        return buf.toString().trimEnd()
    }

    fun buildLyricInfoJson(
        songName: String,
        artist: String,
        songId: String,
        album: String? = null,
        lines: List<LyricLine>,
        showTranslation: Boolean = true
    ): String {
        val isPureMusic = lines.size == 1 && lines[0].text == "纯音乐"
        val hasNoLyric = lines.isEmpty() || isPureMusic

        val json = JSONObject()
        json.put("songName", songName)
        json.put("artist", artist)
        json.put("songId", songId)
        json.put("lyricType", 0)
        json.put("provider", "com.lin0721.linmusic")
        json.put("source", "com.lin0721.linmusic")
        json.put("noLyric", hasNoLyric)

        if (!album.isNullOrBlank()) {
            json.put("album", album)
        }

        if (!hasNoLyric) {
            val lyric = buildPlainLrc(lines)
            json.put("lyric", lyric)

            val rawLyric = buildRawLyric(lines)
            if (rawLyric.isNotBlank()) {
                json.put("rawLyric", rawLyric)
            }

            if (showTranslation) {
                val translation = buildTranslationLrc(lines)
                if (translation.isNotBlank()) {
                    json.put("translationLyric", translation)
                }
            }
        } else {
            json.put("lyric", "")
        }

        return json.toString()
    }
}
