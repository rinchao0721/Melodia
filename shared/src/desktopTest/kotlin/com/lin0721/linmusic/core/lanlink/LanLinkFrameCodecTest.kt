package com.lin0721.linmusic.core.lanlink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

class LanLinkFrameCodecTest {

    @Test
    fun writeAndReadRoundtripWithChunkedReadsAndStickyFrames() {
        val out = ByteArrayOutputStream()
        val first = "hello-lanlink".encodeToByteArray()
        val second = ByteArray(1024) { (it % 251).toByte() }

        LanLinkFrameCodec.writeFrame(out, first)
        LanLinkFrameCodec.writeFrame(out, second)

        // 每次只返回 1 字节，验证分片/粘包场景下的完整读取
        val raw = out.toByteArray()
        val oneByteInput = object : InputStream() {
            private var index = 0
            override fun read(): Int = if (index < raw.size) raw[index++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (index >= raw.size) return -1
                b[off] = raw[index++]
                return 1
            }
        }

        assertArrayEquals(first, LanLinkFrameCodec.readFrame(oneByteInput))
        assertArrayEquals(second, LanLinkFrameCodec.readFrame(oneByteInput))
        assertNull(LanLinkFrameCodec.readFrame(oneByteInput))
    }

    @Test
    fun rejectsEmptyOrOversizedFramesAndThrowsOnTruncatedEof() {
        assertThrows(IOException::class.java) {
            LanLinkFrameCodec.writeFrame(ByteArrayOutputStream(), ByteArray(0))
        }

        // 长度声明为 0 或超过上限均应拒绝
        val zeroHeader = ByteArrayInputStream(byteArrayOf(0, 0, 0, 0))
        assertThrows(IOException::class.java) {
            LanLinkFrameCodec.readFrame(zeroHeader)
        }

        val oversizedHeader = ByteArrayInputStream(byteArrayOf(0x01, 0x00, 0x00, 0x00))
        assertThrows(IOException::class.java) {
            LanLinkFrameCodec.readFrame(oversizedHeader, maxFrameBytes = 1024)
        }

        // 声明 10 字节但只有 3 字节负载，应抛出 EOFException
        val truncated = ByteArrayInputStream(byteArrayOf(0, 0, 0, 10, 1, 2, 3))
        assertThrows(EOFException::class.java) {
            LanLinkFrameCodec.readFrame(truncated)
        }
    }
}
