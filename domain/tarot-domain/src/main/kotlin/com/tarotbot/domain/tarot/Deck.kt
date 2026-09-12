package com.tarotbot.domain.tarot

import kotlinx.serialization.json.Json
import kotlin.random.Random

/** The full 78-card deck, loaded once from the classpath. */
object Deck {

    private val json = Json { ignoreUnknownKeys = true }

    val cards: List<DeckCard> by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/tarot/cards.json")) {
            "tarot/cards.json not found on classpath"
        }
        stream.use { json.decodeFromString<List<DeckCard>>(it.readBytes().decodeToString()) }
    }

    private fun resolveOrientation(card: DeckCard, allowReversed: Boolean, rng: Random): DrawnCard {
        val reversed = allowReversed && rng.nextBoolean()
        return DrawnCard(card, reversed)
    }

    /** Draws [count] unique cards, each independently reversed when [allowReversed] is true. */
    fun drawCards(count: Int, allowReversed: Boolean, rng: Random = Random.Default): List<DrawnCard> {
        require(count <= cards.size) { "Cannot draw more cards ($count) than the deck has (${cards.size})" }
        return cards.shuffled(rng).take(count).map { resolveOrientation(it, allowReversed, rng) }
    }

    /** Draws a single card not present in [excludeNames] — used for the clarifying-card feature. */
    fun drawSingleCard(
        excludeNames: Set<String> = emptySet(),
        allowReversed: Boolean,
        rng: Random = Random.Default,
    ): DrawnCard {
        val pool = cards.filter { it.name !in excludeNames }
        require(pool.isNotEmpty()) { "No cards left to draw for a clarifying card" }
        return resolveOrientation(pool.random(rng), allowReversed, rng)
    }
}
