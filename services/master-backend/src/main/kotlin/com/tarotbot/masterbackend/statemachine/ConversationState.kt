package com.tarotbot.masterbackend.statemachine

import kotlinx.serialization.Serializable

/**
 * One drawn card as persisted inside [ConversationState.AwaitingRetry] — enough to
 * rebuild the exact [com.tarotbot.proto.common.SpreadCard] list for a retry without
 * redrawing (and without depending on proto-generated classes for JSON persistence).
 */
@Serializable
data class DrawnCardDto(
    val positionIndex: Int,
    val positionLabel: String,
    val name: String,
    val arcana: String,
    val suit: String? = null,
    val reversed: Boolean,
    val meaning: String,
)

/**
 * What the conversation is doing right now, from Master's point of view. Persisted
 * as JSON via `DbService.SaveConversationState`/`GetConversationState` — this (not
 * any in-memory state) is what lets any of Master's replicas handle a user's next
 * message.
 */
@Serializable
sealed interface ConversationState {

    @Serializable
    data object Idle : ConversationState

    @Serializable
    data object ChoosingSpread : ConversationState

    @Serializable
    data class ChoosingReversed(val spreadId: String) : ConversationState

    @Serializable
    data class WaitingForQuestion(val spreadId: String, val allowReversed: Boolean) : ConversationState

    /** AI interpretation failed for a freshly drawn spread; a retry reuses these exact cards. */
    @Serializable
    data class AwaitingRetry(
        val spreadId: String,
        val question: String,
        val cards: List<DrawnCardDto>,
    ) : ConversationState

    /** A reading was saved; further plain text is a follow-up, or a button triggers an action on it. */
    @Serializable
    data class InReading(val readingId: Long) : ConversationState

    @Serializable
    data class WaitingForNote(val readingId: Long) : ConversationState
}
