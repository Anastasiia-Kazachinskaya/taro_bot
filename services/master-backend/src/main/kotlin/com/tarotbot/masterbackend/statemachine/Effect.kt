package com.tarotbot.masterbackend.statemachine

/**
 * A pure description of "what should happen" — no I/O. [com.tarotbot.masterbackend.orchestration.MasterOrchestrator]
 * is the only place these are actually executed (gRPC calls, rendering, formatting).
 */
sealed interface Effect {
    data object ShowMainMenu : Effect
    data object ShowSpreadPicker : Effect
    data class AskReversedPreference(val spreadId: String) : Effect
    data class AskQuestion(val spreadId: String, val allowReversed: Boolean) : Effect
    data class PerformReading(val spreadId: String, val allowReversed: Boolean, val question: String) : Effect
    data class RetryInterpretation(val spreadId: String, val question: String, val cards: List<DrawnCardDto>) : Effect
    data class PerformFollowUp(val readingId: Long, val text: String) : Effect
    data class PerformClarifyingCard(val readingId: Long) : Effect
    data class ShowNotePrompt(val readingId: Long) : Effect
    data class SaveNote(val readingId: Long, val text: String) : Effect
    data class SetResonance(val readingId: Long, val value: String) : Effect
    data class ShowHistory(val page: Int) : Effect
    data class ShowReadingDetail(val readingId: Long) : Effect
    data object ShowStats : Effect
    data object ShowAdminStats : Effect
    data object ShowAbout : Effect
    data object ShowHelp : Effect
    data object ShowSettings : Effect
    data class SetReversedPreference(val value: String) : Effect
    data class ShowCardLookup(val query: String) : Effect
    data class ReplyError(val message: String) : Effect
}

data class TransitionResult(val newState: ConversationState, val effects: List<Effect>) {
    companion object {
        fun of(newState: ConversationState, effect: Effect) = TransitionResult(newState, listOf(effect))
    }
}
