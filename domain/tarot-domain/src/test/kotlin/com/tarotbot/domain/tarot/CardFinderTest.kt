package com.tarotbot.domain.tarot

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class CardFinderTest : FunSpec({

    test("finds a major arcana card by a partial name") {
        val result = CardFinder.findCards("отшель")
        result shouldHaveSize 1
        result.single().name shouldBe "Отшельник"
    }

    test("resolves a rank+suit alias query to the exact card") {
        val result = CardFinder.findCards("10 мечей")
        result shouldHaveSize 1
        result.single().name shouldBe "Десятка Мечей"
    }

    test("resolves a nominative rank word with a suit alias") {
        val result = CardFinder.findCards("туз жезлов")
        result shouldHaveSize 1
        result.single().name shouldBe "Туз Жезлов"
    }

    test("a bare suit query is ambiguous and returns all matching cards") {
        val result = CardFinder.findCards("мечей")
        result shouldHaveSize 14
        result.all { it.suit == "swords" } shouldBe true
    }

    test("an unrecognizable query returns no matches") {
        CardFinder.findCards("совершенно случайный текст запроса") shouldHaveSize 0
    }

    test("a blank query returns no matches") {
        CardFinder.findCards("   ") shouldHaveSize 0
    }
})
