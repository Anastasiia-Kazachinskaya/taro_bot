package com.tarotbot.domain.tarot

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class DeckTest : FunSpec({

    test("loads exactly 78 unique cards") {
        Deck.cards shouldHaveSize 78
        Deck.cards.map { it.name }.toSet() shouldHaveSize 78
    }

    test("drawCards returns the requested number of unique cards") {
        val drawn = Deck.drawCards(5, allowReversed = true)
        drawn shouldHaveSize 5
        drawn.map { it.card.name }.toSet() shouldHaveSize 5
    }

    test("drawCards never reverses cards when allowReversed is false") {
        val drawn = Deck.drawCards(78, allowReversed = false)
        drawn.all { !it.reversed } shouldBe true
    }

    test("drawCards throws when asked for more cards than the deck has") {
        shouldThrow<IllegalArgumentException> {
            Deck.drawCards(79, allowReversed = false)
        }
    }

    test("drawSingleCard excludes already-drawn names") {
        val exclude = Deck.cards.take(77).map { it.name }.toSet()
        val card = Deck.drawSingleCard(exclude, allowReversed = false)
        card.card.name shouldBe Deck.cards.last().name
    }

    test("drawSingleCard throws when no cards are left") {
        shouldThrow<IllegalArgumentException> {
            Deck.drawSingleCard(Deck.cards.map { it.name }.toSet(), allowReversed = false)
        }
    }
})
