package com.lin0721.linmusic.desktop.data

import com.lin0721.linmusic.desktop.platform.chooseFile
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

// 与移动端裁剪参数一致：1:1、JPEG 质量 90、长边不超过 1024
object CoverImage {

    const val MAX_EDGE = 1024
    private const val MAX_FILE_BYTES = 30L * 1024 * 1024
    private const val JPEG_QUALITY = 0.9f

    // 居中裁成正方形后缩放，透明背景铺白
    fun prepareJpeg(file: File): Result<ByteArray> = runCatching {
        require(file.isFile && file.length() in 1..MAX_FILE_BYTES) { "图片文件无效或超过 30MB" }
        val source = ImageIO.read(file) ?: throw IllegalArgumentException("无法读取该图片，请选择 JPG 或 PNG")
        encodeJpeg(squareFit(source, MAX_EDGE))
    }

    // 内嵌封面转缩略图
    fun thumbnailJpeg(bytes: ByteArray, maxEdge: Int): Result<ByteArray> = runCatching {
        require(bytes.isNotEmpty() && maxEdge > 0) { "封面数据为空" }
        val source = ImageIO.read(bytes.inputStream()) ?: throw IllegalArgumentException("无法解码封面")
        encodeJpeg(squareFit(source, maxEdge))
    }

    private fun squareFit(source: BufferedImage, maxEdge: Int): BufferedImage {
        val side = minOf(source.width, source.height)
        val target = minOf(side, maxEdge)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        val output = BufferedImage(target, target, BufferedImage.TYPE_INT_RGB)
        val graphics = output.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.color = java.awt.Color.WHITE
            graphics.fillRect(0, 0, target, target)
            graphics.drawImage(source, 0, 0, target, target, left, top, left + side, top + side, null)
        } finally {
            graphics.dispose()
        }
        return output
    }

    private fun encodeJpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = JPEG_QUALITY
        }
        val bytes = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(bytes).use { stream ->
                writer.output = stream
                writer.write(null, IIOImage(image, null, null), params)
            }
        } finally {
            writer.dispose()
        }
        return bytes.toByteArray()
    }
}

// 取消返回 null
suspend fun chooseImageFile(title: String): File? = chooseFile(title, "*.jpg;*.jpeg;*.png;*.bmp;*.gif")
