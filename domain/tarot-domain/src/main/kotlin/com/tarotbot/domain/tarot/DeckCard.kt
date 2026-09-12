package com.tarotbot.domain.tarot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One of the 78 cards in the deck, as loaded from `tarot/cards.json`. */
@Serializable
data class DeckCard(
    val name: String,
    val arcana: String,
    val suit: String? = null,
    @SerialName("upright_meaning") val uprightMeaning: String,
    @SerialName("reversed_meaning") val reversedMeaning: String,
)

/** A card drawn into a spread (or as a standalone clarifying card), with its orientation resolved. */
data class DrawnCard(
    val card: DeckCard,
    val reversed: Boolean,
) {
    val meaning: String get() = if (reversed) card.reversedMeaning else card.uprightMeaning
}
