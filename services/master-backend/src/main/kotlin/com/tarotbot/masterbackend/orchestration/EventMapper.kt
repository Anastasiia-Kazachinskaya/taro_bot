package com.tarotbot.masterbackend.orchestration

import com.tarotbot.masterbackend.statemachine.ConversationState
import com.tarotbot.masterbackend.statemachine.Event
import com.tarotbot.proto.gateway.IncomingUpdate

/** Turns a raw [IncomingUpdate] into an [Event], using [currentState] only to interpret plain text. */
object EventMapper {

    fun map(update: IncomingUpdate, currentState: ConversationState): Event = when (update.payloadCase) {
        IncomingUpdate.PayloadCase.CALLBACK_DATA -> mapCallback(update.callbackData)
        IncomingUpdate.PayloadCase.TEXT_MESSAGE -> mapText(update.textMessage, currentState)
        else -> Event.Unrecognized("")
    }

    private fun mapCallback(data: String): Event {
        val parts = data.split(":")
        return when {
            data == "main_menu" -> Event.MainMenuOpened
            data == "new_reading" -> Event.NewCommand
            data == "about" -> Event.AboutCommand
            data == "history" -> Event.HistoryCommand
            data == "settings" -> Event.SettingsOpened
            data == "retry_interpretation" -> Event.RetryRequested
            data == "draw_clarifying_card" -> Event.ClarifyingCardRequested
            data == "reversed:yes" -> Event.ReversedChoiceMade(true)
            data == "reversed:no" -> Event.ReversedChoiceMade(false)
            parts.size == 2 && parts[0] == "spread" -> Event.SpreadChosen(parts[1])
            parts.size == 2 && parts[0] == "note" -> parts[1].toLongOrNull()?.let(Event::NoteRequested) ?: Event.Unrecognized(data)
            parts.size == 2 && parts[0] == "history_page" -> parts[1].toIntOrNull()?.let(Event::HistoryPageRequested) ?: Event.Unrecognized(data)
            parts.size == 2 && parts[0] == "history_reading" -> parts[1].toLongOrNull()?.let(Event::HistoryReadingOpened) ?: Event.Unrecognized(data)
            parts.size == 3 && parts[0] == "resonance" -> {
                val readingId = parts[1].toLongOrNull()
                val value = when (parts[2]) {
                    "yes" -> "yes"
                    "no" -> "no"
                    "clear" -> ""
                    else -> null
                }
                if (readingId != null && value != null) Event.ResonanceSet(readingId, value) else Event.Unrecognized(data)
            }
            parts.size == 3 && parts[0] == "settings" && parts[1] == "reversed" -> Event.SettingsReversedChosen(parts[2])
            else -> Event.Unrecognized(data)
        }
    }

    private fun mapText(text: String, currentState: ConversationState): Event {
        val trimmed = text.trim()
        if (trimmed.startsWith("/")) {
            val firstSpace = trimmed.indexOf(' ')
            val command = if (firstSpace == -1) trimmed else trimmed.substring(0, firstSpace)
            val rest = if (firstSpace == -1) "" else trimmed.substring(firstSpace + 1).trim()
            return when (command.substringBefore('@')) {
                "/start" -> Event.StartCommand
                "/new" -> Event.NewCommand
                "/card" -> Event.CardLookupCommand(rest)
                "/history" -> Event.HistoryCommand
                "/stats" -> Event.StatsCommand
                "/admin" -> Event.AdminCommand
                "/about" -> Event.AboutCommand
                "/help" -> Event.HelpCommand
                "/reset" -> Event.ResetCommand
                else -> Event.Unrecognized(trimmed)
            }
        }

        return when (currentState) {
            is ConversationState.WaitingForQuestion -> Event.QuestionAsked(trimmed)
            is ConversationState.InReading -> Event.FollowUpAsked(trimmed)
            is ConversationState.WaitingForNote -> Event.NoteTextEntered(trimmed)
            else -> Event.Unrecognized(trimmed)
        }
    }
}
