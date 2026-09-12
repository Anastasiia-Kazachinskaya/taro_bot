package com.tarotbot.domain.render

import com.tarotbot.domain.tarot.DrawnCard
import com.tarotbot.domain.tarot.Spread
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Composites drawn cards into one spread image, porting the hand-tuned per-layout
 * positioning of the old Python renderer (see docs/ARCHITECTURE.md) to Java2D.
 */
object SpreadRenderer {

    private const val CARD_WIDTH = 240
    private const val CARD_HEIGHT = 400
    private const val CARD_GAP = 30
    private const val PADDING = 40

    private data class NumberBadge(val index: Int, val x: Int, val y: Int)

    private fun cardImage(drawn: DrawnCard): BufferedImage {
        val image = ImageOps.fit(CardImages.load(drawn.card), CARD_WIDTH, CARD_HEIGHT)
        return if (drawn.reversed) ImageOps.rotateExpand(image, 180.0) else image
    }

    /** Renders a full spread; [cards] must be in the same order as [Spread.positions]. */
    fun renderSpread(spreadId: String, cards: List<DrawnCard>): ByteArray {
        val spread = Spread.fromId(spreadId)
        require(cards.size == spread.positions.size) {
            "Expected ${spread.positions.size} cards for '$spreadId', got ${cards.size}"
        }
        val images = cards.map(::cardImage)
        val (canvas, badges) = when (spread) {
            Spread.ONE_CARD -> layoutOneCard(images)
            Spread.THREE_CARDS -> layoutThreeCards(images)
            Spread.RELATIONSHIP -> layoutRelationship(images)
            Spread.CHOICE -> layoutChoice(images)
            Spread.SEVEN_CARDS -> layoutSevenCards(images)
            Spread.CELTIC_CROSS -> layoutCelticCross(images)
        }
        val g = canvas.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        badges.forEach { drawCardNumber(g, it.index, it.x, it.y) }
        g.dispose()
        return encodeJpeg(canvas)
    }

    /** Renders a single standalone card — used for the clarifying-card feature. */
    fun renderSingleCard(card: DrawnCard): ByteArray {
        val image = cardImage(card)
        val width = PADDING * 2 + CARD_WIDTH
        val height = PADDING * 2 + CARD_HEIGHT
        val canvas = ImageOps.newCanvas(width, height)
        val g = canvas.createGraphics()
        g.drawImage(image, PADDING, PADDING, null)
        g.dispose()
        return encodeJpeg(canvas)
    }

    // ---------------------------------------------------------------------
    // Layouts
    // ---------------------------------------------------------------------

    private fun layoutOneCard(images: List<BufferedImage>): Pair<BufferedImage, List<NumberBadge>> {
        val width = PADDING * 2 + CARD_WIDTH
        val height = PADDING * 2 + CARD_HEIGHT
        val canvas = ImageOps.newCanvas(width, height)
        canvas.createGraphics().apply {
            drawImage(images[0], PADDING, PADDING, null)
            dispose()
        }
        return canvas to listOf(NumberBadge(1, PADDING, PADDING))
    }

    private fun layoutThreeCards(images: List<BufferedImage>): Pair<BufferedImage, List<NumberBadge>> {
        val width = PADDING * 2 + CARD_WIDTH * 3 + CARD_GAP * 2
        val height = PADDING * 2 + CARD_HEIGHT
        val canvas = ImageOps.newCanvas(width, height)
        val g = canvas.createGraphics()
        val badges = mutableListOf<NumberBadge>()
        var x = PADDING
        images.forEachIndexed { i, image ->
            g.drawImage(image, x, PADDING, null)
            badges += NumberBadge(i + 1, x, PADDING)
            x += CARD_WIDTH + CARD_GAP
        }
        g.dispose()
        return canvas to badges
    }

    private fun layoutRelationship(images: List<BufferedImage>): Pair<BufferedImage, List<NumberBadge>> {
        val width = PADDING * 2 + CARD_WIDTH * 3 + CARD_GAP * 2
        val height = PADDING * 2 + CARD_HEIGHT * 2 + CARD_GAP
        val canvas = ImageOps.newCanvas(width, height)
        val g = canvas.createGraphics()
        val badges = mutableListOf<NumberBadge>()

        var x = PADDING
        images.subList(0, 3).forEachIndexed { i, image ->
            g.drawImage(image, x, PADDING, null)
            badges += NumberBadge(i + 1, x, PADDING)
            x += CARD_WIDTH + CARD_GAP
        }

        val bottomWidth = CARD_WIDTH * 2 + CARD_GAP
        val bottomStartX = (width - bottomWidth) / 2
        val bottomY = PADDING + CARD_HEIGHT + CARD_GAP
        x = bottomStartX
        images.subList(3, 5).forEachIndexed { i, image ->
            g.drawImage(image, x, bottomY, null)
            badges += NumberBadge(i + 4, x, bottomY)
            x += CARD_WIDTH + CARD_GAP
        }

        g.dispose()
        return canvas to badges
    }

    private fun layoutChoice(images: List<BufferedImage>): Pair<BufferedImage, List<NumberBadge>> {
        val optionGap = 70
        val situationGap = 45
        val rowGap = 28

        val optionsWidth = CARD_WIDTH * 2 + optionGap
        val width = PADDING * 2 + optionsWidth
        val height = PADDING + 32 + 15 + CARD_HEIGHT + situationGap + 30 + 15 + CARD_HEIGHT + rowGap + CARD_HEIGHT + 45

        val canvas = ImageOps.newCanvas(width, height)
        val g = canvas.createGraphics()
        val titleFont = Font("SansSerif", Font.BOLD, 25)
        val smallFont = Font("SansSerif", Font.BOLD, 16)

        fun drawCentered(text: String, centerX: Int, y: Int, font: Font) {
            g.font = font
            val textWidth = g.fontMetrics.stringWidth(text)
            g.color = Color.BLACK
            g.drawString(text, centerX - textWidth / 2, y + g.fontMetrics.ascent)
        }

        val situationCenter = width / 2
        drawCentered("СИТУАЦИЯ", situationCenter, PADDING, titleFont)

        val situationY = PADDING + 32 + 15
        val situationX = (width - CARD_WIDTH) / 2
        g.drawImage(images[0], situationX, situationY, null)

        val optionsLabelY = situationY + CARD_HEIGHT + situationGap
        val leftX = PADDING
        val rightX = PADDING + CARD_WIDTH + optionGap
        val leftCenter = leftX + CARD_WIDTH / 2
        val rightCenter = rightX + CARD_WIDTH / 2

        drawCentered("ВАРИАНТ А", leftCenter, optionsLabelY, titleFont)
        drawCentered("ВАРИАНТ Б", rightCenter, optionsLabelY, titleFont)

        val potentialY = optionsLabelY + 30 + 15
        g.drawImage(images[1], leftX, potentialY, null)
        g.drawImage(images[3], rightX, potentialY, null)
        drawCentered("потенциал", leftCenter, potentialY + CARD_HEIGHT + 5, smallFont)
        drawCentered("потенциал", rightCenter, potentialY + CARD_HEIGHT + 5, smallFont)

        val riskY = potentialY + CARD_HEIGHT + rowGap + 20
        g.drawImage(images[2], leftX, riskY, null)
        g.drawImage(images[4], rightX, riskY, null)
        drawCentered("риск", leftCenter, riskY + CARD_HEIGHT + 5, smallFont)
        drawCentered("риск", rightCenter, riskY + CARD_HEIGHT + 5, smallFont)

        g.dispose()

        val badges = listOf(
            NumberBadge(1, situationX, situationY),
            NumberBadge(2, leftX, potentialY),
            NumberBadge(3, leftX, riskY),
            NumberBadge(4, rightX, potentialY),
            NumberBadge(5, rightX, riskY),
        )
        return canvas to badges
    }

    private fun layoutSevenCards(images: List<BufferedImage>): Pair<BufferedImage, List<NumberBadge>> {
        val topCount = 4
        val bottomCount = 3
        val width = PADDING * 2 + CARD_WIDTH * topCount + CARD_GAP * (topCount - 1)
        val height = PADDING * 2 + CARD_HEIGHT * 2 + CARD_GAP
        val canvas = ImageOps.newCanvas(width, height)
        val g = canvas.createGraphics()
        val badges = mutableListOf<NumberBadge>()

        var x = PADDING
        images.subList(0, 4).forEachIndexed { i, image ->
            g.drawImage(image, x, PADDING, null)
            badges += NumberBadge(i + 1, x, PADDING)
            x += CARD_WIDTH + CARD_GAP
        }

        val bottomWidth = CARD_WIDTH * bottomCount + CARD_GAP * (bottomCount - 1)
        val bottomStartX = (width - bottomWidth) / 2
        val bottomY = PADDING + CARD_HEIGHT + CARD_GAP
        x = bottomStartX
        images.subList(4, 7).forEachIndexed { i, image ->
            g.drawImage(image, x, bottomY, null)
            badges += NumberBadge(i + 5, x, bottomY)
            x += CARD_WIDTH + CARD_GAP
        }

        g.dispose()
        return canvas to badges
    }

    private fun layoutCelticCross(images: List<BufferedImage>): Pair<BufferedImage, List<NumberBadge>> {
        val sideGap = maxOf(CARD_GAP, (CARD_HEIGHT - CARD_WIDTH) / 2 + 15)
        val crossWidth = CARD_WIDTH * 3 + sideGap * 2
        val crossHeight = CARD_HEIGHT * 3 + CARD_GAP * 2

        val columnCardWidth = (CARD_WIDTH * 0.65).toInt()
        val columnCardHeight = (CARD_HEIGHT * 0.65).toInt()
        val columnGap = 20
        val columnHeight = columnCardHeight * 4 + columnGap * 3

        val rightColumnGap = 80
        val width = PADDING * 2 + crossWidth + rightColumnGap + columnCardWidth
        val height = PADDING * 2 + crossHeight

        val canvas = ImageOps.newCanvas(width, height)
        val g = canvas.createGraphics()
        val badges = mutableListOf<NumberBadge>()

        val centerX = PADDING + CARD_WIDTH + sideGap
        val centerY = PADDING + CARD_HEIGHT + CARD_GAP

        // #1 — суть ситуации
        g.drawImage(images[0], centerX, centerY, null)

        // #3 — осознанное, полностью над #1
        g.drawImage(images[2], centerX, PADDING, null)

        // #4 — прошлое, полностью слева от #1
        g.drawImage(images[3], PADDING, centerY, null)

        // #5 — возможное развитие, под #1
        val card5Y = centerY + CARD_HEIGHT + CARD_GAP
        g.drawImage(images[4], centerX, card5Y, null)

        // #6 — ближайшее будущее, полностью справа от #1
        val card6X = centerX + CARD_WIDTH + sideGap
        g.drawImage(images[5], card6X, centerY, null)

        // #2 — пересекает ситуацию; рисуется после #4/#6, чтобы лежать поверх них
        val crossCard = ImageOps.rotateExpand(images[1], 90.0)
        val crossX = centerX + (CARD_WIDTH - crossCard.width) / 2
        val crossY = centerY + (CARD_HEIGHT - crossCard.height) / 2
        g.drawImage(crossCard, crossX, crossY, null)

        // Правая колонка: #7..#10
        val columnX = PADDING + crossWidth + rightColumnGap
        val columnStartY = PADDING + (crossHeight - columnHeight) / 2
        images.subList(6, 10).forEachIndexed { i, image ->
            val resized = ImageOps.fit(image, columnCardWidth, columnCardHeight)
            val x = columnX + (columnCardWidth - resized.width) / 2
            val y = columnStartY + i * (columnCardHeight + columnGap)
            g.drawImage(resized, x, y, null)
            badges += NumberBadge(i + 7, x, y)
        }

        g.dispose()

        badges += NumberBadge(1, centerX, centerY)
        badges += NumberBadge(2, crossX, crossY)
        badges += NumberBadge(3, centerX, PADDING)
        badges += NumberBadge(4, PADDING, centerY)
        badges += NumberBadge(5, centerX, card5Y)
        badges += NumberBadge(6, card6X, centerY)

        return canvas to badges
    }

    // ---------------------------------------------------------------------
    // Shared drawing / encoding
    // ---------------------------------------------------------------------

    private fun drawCardNumber(g: Graphics2D, number: Int, x: Int, y: Int, size: Int = 36) {
        val centerX = x + size / 2
        val centerY = y + size / 2
        val radius = size / 2

        g.color = Color.WHITE
        g.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2)
        g.color = Color.BLACK
        g.stroke = java.awt.BasicStroke(2f)
        g.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2)

        g.font = Font("SansSerif", Font.BOLD, 20)
        val text = number.toString()
        val metrics = g.fontMetrics
        val textWidth = metrics.stringWidth(text)
        g.drawString(text, centerX - textWidth / 2, centerY + metrics.ascent / 2 - 2)
    }

    private fun encodeJpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = 0.95f
        }
        val output = ByteArrayOutputStream()
        javax.imageio.stream.MemoryCacheImageOutputStream(output).use { ios ->
            writer.output = ios
            writer.write(null, IIOImage(image, null, null), params)
        }
        writer.dispose()
        return output.toByteArray()
    }
}
