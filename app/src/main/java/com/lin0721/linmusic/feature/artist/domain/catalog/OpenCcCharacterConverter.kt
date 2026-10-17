package com.lin0721.linmusic.feature.artist.domain.catalog

import java.io.Reader

/** Character-level normalization from the complete, pinned OpenCC TSCharacters dictionary.
 * It is intentionally not advertised as OpenCC's phrase-aware t2s conversion pipeline.
 */
class OpenCcCharacterConverter private constructor(private val characters: Map<Int, String>) {
    val mappingCount: Int get() = characters.size

    fun convert(value: String): String = buildString(value.length) {
        var offset = 0
        while (offset < value.length) {
            val codePoint = value.codePointAt(offset)
            append(characters[codePoint] ?: String(Character.toChars(codePoint)))
            offset += Character.charCount(codePoint)
        }
    }

    companion object {
        val bundled: OpenCcCharacterConverter by lazy {
            val stream = checkNotNull(OpenCcCharacterConverter::class.java.getResourceAsStream("/catalog/opencc/TSCharacters.txt")) {
                "Bundled OpenCC character dictionary is missing"
            }
            stream.bufferedReader(Charsets.UTF_8).use(::fromDictionary)
        }

        fun fromDictionary(reader: Reader): OpenCcCharacterConverter {
            val mappings = linkedMapOf<Int, String>()
            reader.buffered().lineSequence().forEach { line ->
                if (line.isBlank() || line.startsWith('#')) return@forEach
                val fields = line.split('\t', limit = 2)
                require(fields.size == 2 && fields[0].codePointCount(0, fields[0].length) == 1) {
                    "Invalid character dictionary row"
                }
                val target = fields[1].substringBefore(' ').trim()
                require(target.isNotEmpty()) { "Empty character dictionary target" }
                mappings[fields[0].codePointAt(0)] = target
            }
            return OpenCcCharacterConverter(mappings)
        }
    }
}
