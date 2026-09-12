package com.tarotbot.domain.render

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Small Java2D helpers mirroring the Pillow operations the old renderer relied on. */
internal object ImageOps {

    /** Equivalent of Pillow's `ImageOps.contain`: scales to fit within [maxW]x[maxH], preserving aspect ratio. */
    fun fit(src: BufferedImage, maxW: Int, maxH: Int): BufferedImage {
        val scale = minOf(maxW.toDouble() / src.width, maxH.toDouble() / src.height)
        val newW = (src.width * scale).roundToInt().coerceAtLeast(1)
        val newH = (src.height * scale).roundToInt().coerceAtLeast(1)
        val dst = BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB)
        val g = dst.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, newW, newH, null)
        g.dispose()
        return dst
    }

    /**
     * Rotates [src] by [degrees], expanding the canvas so nothing is clipped —
     * equivalent of Pillow's `Image.rotate(angle, expand=True)`.
     */
    fun rotateExpand(src: BufferedImage, degrees: Double): BufferedImage {
        val radians = Math.toRadians(degrees)
        val s = abs(sin(radians))
        val c = abs(cos(radians))
        val newWidth = floor(src.width * c + src.height * s).toInt().coerceAtLeast(1)
        val newHeight = floor(src.width * s + src.height * c).toInt().coerceAtLeast(1)
        val dst = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val g = dst.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.color = Color.WHITE
        g.fillRect(0, 0, newWidth, newHeight)
        g.translate((newWidth - src.width) / 2, (newHeight - src.height) / 2)
        g.rotate(radians, src.width / 2.0, src.height / 2.0)
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return dst
    }

    fun newCanvas(width: Int, height: Int): BufferedImage {
        val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = canvas.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.dispose()
        return canvas
    }
}
