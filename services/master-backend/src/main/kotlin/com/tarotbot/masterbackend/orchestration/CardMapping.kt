package com.tarotbot.masterbackend.orchestration

import com.tarotbot.domain.tarot.DrawnCard
import com.tarotbot.masterbackend.statemachine.DrawnCardDto
import com.tarotbot.proto.common.Card
import com.tarotbot.proto.common.SpreadCard
import com.tarotbot.proto.common.card
import com.tarotbot.proto.common.spreadCard

/** Conversions between the domain's [DrawnCard], the wire's [SpreadCard], and the
 * persisted [DrawnCardDto] used inside [com.tarotbot.masterbackend.statemachine.ConversationState.AwaitingRetry]. */
object CardMapping {

    fun toSpreadCard(positionIndex: Int, positionLabel: String, drawn: DrawnCard): SpreadCard {
        val idx = positionIndex
        val label = positionLabel
        val name = drawn.card.name
        val arcana = drawn.card.arcana
        val suit = drawn.card.suit ?: ""
        val reversed = drawn.reversed
        val meaning = drawn.meaning
        return spreadCard {
            this.positionIndex = idx
            this.positionLabel = label
            this.card = card {
                this.name = name
                this.arcana = arcana
                this.suit = suit
                this.reversed = reversed
                this.meaning = meaning
            }
        }
    }

    fun toDrawnCardDto(positionIndex: Int, positionLabel: String, drawn: DrawnCard): DrawnCardDto = DrawnCardDto(
        positionIndex = positionIndex,
        positionLabel = positionLabel,
        name = drawn.card.name,
        arcana = drawn.card.arcana,
        suit = drawn.card.suit,
        reversed = drawn.reversed,
        meaning = drawn.meaning,
    )

    fun DrawnCardDto.toSpreadCard(): SpreadCard {
        val dto = this
        return spreadCard {
            this.positionIndex = dto.positionIndex
            this.positionLabel = dto.positionLabel
            this.card = card {
                name = dto.name
                arcana = dto.arcana
                suit = dto.suit ?: ""
                reversed = dto.reversed
                meaning = dto.meaning
            }
        }
    }

    fun Card.toDto(positionIndex: Int, positionLabel: String): DrawnCardDto = DrawnCardDto(
        positionIndex = positionIndex,
        positionLabel = positionLabel,
        name = name,
        arcana = arcana,
        suit = suit.ifBlank { null },
        reversed = reversed,
        meaning = meaning,
    )
}
