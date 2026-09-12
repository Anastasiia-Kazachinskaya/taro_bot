package com.tarotbot.masterbackend.statemachine

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * (De)serializes [ConversationState] to/from the JSON stored in `conversation_state.context`
 * via `DbService`. This — not any in-memory state — is what any Master replica reads to
 * resume a user's conversation.
 */
object StateCodec {

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    fun encode(state: ConversationState): String = json.encodeToString(state)

    /** A blank/empty context (a brand new user, or `db-backend`'s default) decodes to [ConversationState.Idle]. */
    fun decode(raw: String): ConversationState {
        if (raw.isBlank() || raw == "{}") return ConversationState.Idle
        return json.decodeFromString(raw)
    }

    /** Short tag stored in the proto's `state` field — informational only, not the source of truth. */
    fun tag(state: ConversationState): String = state::class.simpleName ?: "Unknown"
}
