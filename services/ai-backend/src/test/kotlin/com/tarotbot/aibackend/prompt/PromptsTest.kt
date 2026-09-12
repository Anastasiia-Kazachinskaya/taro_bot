package com.tarotbot.aibackend.prompt

import com.tarotbot.proto.common.card
import com.tarotbot.proto.common.spreadCard
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith

class PromptsTest : FunSpec({

    test("trimTruncated cuts at the last paragraph break past 60% and appends the notice") {
        val head = "A".repeat(100)
        val text = "$head\n\nfinished paragraph. " + "B".repeat(5)
        val result = trimTruncated(text)
        result shouldEndWith "(Интерпретация получилась длиннее лимита и была сокращена.)"
        result shouldContain head
        result.shouldNotContainCut("finished paragraph")
    }

    test("trimTruncated leaves complete text's content intact aside from the notice") {
        val text = "Короткий, но законченный ответ."
        val result = trimTruncated(text)
        result shouldContain text
    }

    test("maxTokensForSpread returns 700 for a single card") {
        maxTokensForSpread(1) shouldBe 700
    }

    test("maxTokensForSpread scales with card count and caps at 2800") {
        maxTokensForSpread(3) shouldBe 950
        maxTokensForSpread(10) shouldBe 2000
        maxTokensForSpread(100) shouldBe 2800
    }

    test("spreadInstructions returns non-empty guidance for the 4 special spreads") {
        listOf("relationship", "choice", "celtic_cross", "seven_cards").forEach { id ->
            spreadInstructions(id).isBlank() shouldBe false
        }
    }

    test("spreadInstructions returns empty for one_card and three_cards") {
        spreadInstructions("one_card") shouldBe ""
        spreadInstructions("three_cards") shouldBe ""
    }

    test("formatCardsText includes each card's position, name and orientation") {
        val cards = listOf(
            spreadCard {
                positionIndex = 1
                positionLabel = "Главная энергия ситуации"
                card = card {
                    name = "Шут"
                    arcana = "major"
                    suit = ""
                    reversed = true
                    meaning = "Наивность, необдуманный риск"
                }
            },
        )
        val text = formatCardsText(cards)
        text shouldContain "Позиция: Главная энергия ситуации"
        text shouldContain "Название: Шут"
        text shouldContain "Масть: нет"
        text shouldContain "Положение: перевёрнутая"
    }
})

private fun String.shouldNotContainCut(marker: String) {
    // The paragraph break falls right after the 100-char head, so the sentence
    // that follows it must have been cut away.
    if (this.contains(marker)) error("Expected '$marker' to have been trimmed away")
}
