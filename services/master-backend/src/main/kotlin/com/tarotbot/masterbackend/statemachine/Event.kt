package com.tarotbot.masterbackend.statemachine

/** What an incoming Telegram update means, once mapped out of raw text/callback_data. */
sealed interface Event {

    // Global commands — always interrupt whatever state the user is in.
    data object StartCommand : Event
    data object NewCommand : Event
    data class CardLookupCommand(val query: String) : Event
    data object HistoryCommand : Event
    data object StatsCommand : Event
    data object AdminCommand : Event
    data object AboutCommand : Event
    data object HelpCommand : Event
    data object ResetCommand : Event
    data object SettingsOpened : Event
    data object MainMenuOpened : Event

    // In-flow events.
    data class SpreadChosen(val spreadId: String) : Event
    data class ReversedChoiceMade(val allowReversed: Boolean) : Event
    data class QuestionAsked(val text: String) : Event
    data object RetryRequested : Event
    data class FollowUpAsked(val text: String) : Event
    data object ClarifyingCardRequested : Event
    data class NoteRequested(val readingId: Long) : Event
    data class NoteTextEntered(val text: String) : Event
    data class ResonanceSet(val readingId: Long, val value: String) : Event
    data class SettingsReversedChosen(val value: String) : Event
    data class HistoryPageRequested(val page: Int) : Event
    data class HistoryReadingOpened(val readingId: Long) : Event

    /** Anything we couldn't map — e.g. stray text with no active flow, or unknown callback_data. */
    data class Unrecognized(val raw: String) : Event
}
