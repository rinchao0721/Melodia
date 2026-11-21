package com.lin0721.linmusic.core.lanlink

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

// TCP 帧编解码：4 字节大端无符号长度前缀 + 负载字节
object LanLinkFrameCodec {

    fun writeFrame(
        output: OutputStream,
        payload: ByteArray,
        maxFrameBytes: Int = LanLinkConstants.MAX_FRAME_BYTES
    ) {
        if (payload.isEmpty() || payload.size > maxFrameBytes) {
            throw IOException("帧长度超出允许范围: ${payload.size}")
        }
        val header = ByteArray(4)
        val len = payload.size
        header[0] = ((len ushr 24) and 0xFF).toByte()
        header[1] = ((len ushr 16) and 0xFF).toByte()
        header[2] = ((len ushr 8) and 0xFF).toByte()
        header[3] = (len and 0xFF).toByte()
        output.write(header)
        output.write(payload)
        output.flush()
    }

    // 正常流结束且未读到任何字节时返回 null；读到半帧截断或非法长度时抛异常
    fun readFrame(
        input: InputStream,
        maxFrameBytes: Int = LanLinkConstants.MAX_FRAME_BYTES
    ): ByteArray? {
        val header = ByteArray(4)
        val firstByte = input.read()
        if (firstByte == -1) return null
        header[0] = firstByte.toByte()
        readExactly(input, header, 1, 3)

        val length = ((header[0].toInt() and 0xFF) shl 24) or
            ((header[1].toInt() and 0xFF) shl 16) or
            ((header[2].toInt() and 0xFF) shl 8) or
            (header[3].toInt() and 0xFF)

        if (length <= 0 || length > maxFrameBytes) {
            throw IOException("非法帧长度: $length")
        }

        val payload = ByteArray(length)
        readExactly(input, payload, 0, length)
        return payload
    }

    private fun readExactly(input: InputStream, buffer: ByteArray, offset: Int, count: Int) {
        var readTotal = 0
        while (readTotal < count) {
            val read = input.read(buffer, offset + readTotal, count - readTotal)
            if (read == -1) {
                throw EOFException("读取帧数据途中连接已关闭")
            }
            readTotal += read
        }
    }
}
