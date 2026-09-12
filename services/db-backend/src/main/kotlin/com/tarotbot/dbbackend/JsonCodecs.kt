package com.tarotbot.dbbackend

import com.tarotbot.proto.common.Card
import com.tarotbot.proto.common.SpreadCard
import com.tarotbot.proto.common.card
import com.tarotbot.proto.common.spreadCard
import com.tarotbot.proto.db.Followup
import com.tarotbot.proto.db.followup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class JsonCard(
    val name: String,
    val arcana: String,
    val suit: String,
    val reversed: Boolean,
    val meaning: String,
)

@Serializable
private data class JsonSpreadCard(
    @SerialName("position_index") val positionIndex: Int,
    @SerialName("position_label") val positionLabel: String,
    val card: JsonCard,
)

@Serializable
private data class JsonFollowup(val question: String, val answer: String)

/** Stores a [SpreadCard] list as JSON text for the `readings.spread` jsonb column. */
object SpreadJson {

    fun encode(cards: List<SpreadCard>): String {
        val dto = cards.map {
            JsonSpreadCard(
                positionIndex = it.positionIndex,
                positionLabel = it.positionLabel,
                card = JsonCard(it.card.name, it.card.arcana, it.card.suit, it.card.reversed, it.card.meaning),
            )
        }
        return json.encodeToString(dto)
    }

    fun decode(raw: String): List<SpreadCard> =
        json.decodeFromString<List<JsonSpreadCard>>(raw).map { sc ->
            spreadCard {
                positionIndex = sc.positionIndex
                positionLabel = sc.positionLabel
                card = card {
                    name = sc.card.name
                    arcana = sc.card.arcana
                    suit = sc.card.suit
                    reversed = sc.card.reversed
                    meaning = sc.card.meaning
                }
            }
        }

    /** Counts card-name occurrences across the raw jsonb of several readings, for diary stats. */
    fun countCardNames(rawSpreads: List<String>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        rawSpreads.forEach { raw ->
            json.decodeFromString<List<JsonSpreadCard>>(raw).forEach { sc ->
                counts[sc.card.name] = (counts[sc.card.name] ?: 0) + 1
            }
        }
        return counts
    }
}

/** Stores a [Followup] list as JSON text for the `readings.followups` jsonb column. */
object FollowupsJson {

    /** Matches the old bot's MAX_HISTORY_TURNS — only the most recent turns are kept. */
    const val MAX_TURNS = 3

    val EMPTY: String = json.encodeToString(emptyList<JsonFollowup>())

    fun decode(raw: String): List<Followup> =
        json.decodeFromString<List<JsonFollowup>>(raw).map {
            followup {
                question = it.question
                answer = it.answer
            }
        }

    /** Appends one turn to the decoded list, capping at the last [MAX_TURNS] entries. */
    fun appendCapped(raw: String, question: String, answer: String): String {
        val existing = json.decodeFromString<List<JsonFollowup>>(raw)
        val updated = (existing + JsonFollowup(question, answer)).takeLast(MAX_TURNS)
        return json.encodeToString(updated)
    }
}
