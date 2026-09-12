package com.tarotbot.dbbackend

import com.tarotbot.proto.common.card
import com.tarotbot.proto.common.spreadCard
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class JsonCodecsTest : FunSpec({

    context("SpreadJson") {
        test("round-trips a list of spread cards") {
            val cards = listOf(
                spreadCard {
                    positionIndex = 1
                    positionLabel = "Суть ситуации"
                    card = card {
                        name = "Шут"
                        arcana = "major"
                        suit = ""
                        reversed = false
                        meaning = "Начало нового цикла"
                    }
                },
            )
            val decoded = SpreadJson.decode(SpreadJson.encode(cards))
            decoded shouldHaveSize 1
            decoded[0].card.name shouldBe "Шут"
            decoded[0].positionLabel shouldBe "Суть ситуации"
        }

        test("counts card-name occurrences across several raw spreads") {
            val spreadA = SpreadJson.encode(
                listOf(
                    spreadCard { positionIndex = 1; positionLabel = "A"; card = card { name = "Шут"; arcana = "major"; suit = ""; reversed = false; meaning = "m" } },
                    spreadCard { positionIndex = 2; positionLabel = "B"; card = card { name = "Маг"; arcana = "major"; suit = ""; reversed = false; meaning = "m" } },
                ),
            )
            val spreadB = SpreadJson.encode(
                listOf(
                    spreadCard { positionIndex = 1; positionLabel = "A"; card = card { name = "Шут"; arcana = "major"; suit = ""; reversed = false; meaning = "m" } },
                ),
            )
            val counts = SpreadJson.countCardNames(listOf(spreadA, spreadB))
            counts["Шут"] shouldBe 2
            counts["Маг"] shouldBe 1
        }
    }

    context("FollowupsJson") {
        test("appends a turn to an empty list") {
            val updated = FollowupsJson.appendCapped(FollowupsJson.EMPTY, "q1", "a1")
            val decoded = FollowupsJson.decode(updated)
            decoded shouldHaveSize 1
            decoded[0].question shouldBe "q1"
        }

        test("caps at the last 3 turns, dropping the oldest") {
            var raw = FollowupsJson.EMPTY
            repeat(5) { i -> raw = FollowupsJson.appendCapped(raw, "q$i", "a$i") }
            val decoded = FollowupsJson.decode(raw)
            decoded shouldHaveSize 3
            decoded.map { it.question } shouldBe listOf("q2", "q3", "q4")
        }
    }
})
