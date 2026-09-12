package com.tarotbot.domain.render

import com.tarotbot.domain.tarot.Deck
import com.tarotbot.domain.tarot.Spread
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class SpreadRendererTest : FunSpec({

    fun decode(bytes: ByteArray) = ImageIO.read(ByteArrayInputStream(bytes))

    Spread.entries.forEach { spread ->
        test("renders a valid non-empty JPEG for '${spread.id}'") {
            val cards = Deck.drawCards(spread.positions.size, allowReversed = true)
            val bytes = SpreadRenderer.renderSpread(spread.id, cards)
            bytes.size shouldBeGreaterThan 0

            val image = decode(bytes)
            image.width shouldBeGreaterThan 0
            image.height shouldBeGreaterThan 0
        }
    }

    test("renders a single standalone card") {
        val card = Deck.drawSingleCard(allowReversed = true)
        val bytes = SpreadRenderer.renderSingleCard(card)
        bytes.size shouldBeGreaterThan 0

        val image = decode(bytes)
        image.width shouldBeGreaterThan 0
        image.height shouldBeGreaterThan 0
    }

    test("renderSpread rejects a card count that doesn't match the spread") {
        val cards = Deck.drawCards(2, allowReversed = false)
        runCatching { SpreadRenderer.renderSpread("three_cards", cards) }
            .isFailure shouldBe true
    }
})
