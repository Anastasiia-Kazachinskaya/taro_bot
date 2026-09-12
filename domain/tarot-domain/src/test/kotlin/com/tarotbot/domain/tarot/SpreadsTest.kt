package com.tarotbot.domain.tarot

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class SpreadsTest : FunSpec({

    test("each spread has the expected number of positions") {
        Spread.ONE_CARD.positions shouldHaveSize 1
        Spread.THREE_CARDS.positions shouldHaveSize 3
        Spread.RELATIONSHIP.positions shouldHaveSize 5
        Spread.CHOICE.positions shouldHaveSize 5
        Spread.SEVEN_CARDS.positions shouldHaveSize 7
        Spread.CELTIC_CROSS.positions shouldHaveSize 10
    }

    test("makeSpread returns one drawn card per position, in order") {
        val result = makeSpread("celtic_cross", allowReversed = true)
        result shouldHaveSize 10
        result.map { it.first } shouldBe Spread.CELTIC_CROSS.positions
    }

    test("makeSpread throws on an unknown spread id") {
        shouldThrow<IllegalArgumentException> {
            makeSpread("not_a_real_spread", allowReversed = false)
        }
    }
})
