package com.tarotbot.masterbackend.statemachine

/**
 * The pure core of Master: given the current state and an event, decides the next
 * state and what should happen — no I/O, no suspension, fully deterministic and
 * unit-testable. [com.tarotbot.masterbackend.orchestration.MasterOrchestrator] executes
 * the returned [Effect]s and may persist a *different* final state than [TransitionResult.newState]
 * for effects whose real outcome (e.g. a new reading id) can only be known after I/O —
 * see the doc on [Effect.PerformReading]/[Effect.RetryInterpretation] callers.
 */
object StateMachine {

    fun transition(state: ConversationState, event: Event): TransitionResult {
        if (event is Event.CardLookupCommand) {
            // Doesn't disrupt whatever flow the user is in.
            return TransitionResult.of(state, Effect.ShowCardLookup(event.query))
        }

        globalCommand(event)?.let { (newState, effect) -> return TransitionResult.of(newState, effect) }
        universalCallback(state, event)?.let { return it }

        return when (state) {
            ConversationState.Idle -> whenIdle(event)
            ConversationState.ChoosingSpread -> whenChoosingSpread(event)
            is ConversationState.ChoosingReversed -> whenChoosingReversed(state, event)
            is ConversationState.WaitingForQuestion -> whenWaitingForQuestion(state, event)
            is ConversationState.AwaitingRetry -> whenAwaitingRetry(state, event)
            is ConversationState.InReading -> whenInReading(state, event)
            is ConversationState.WaitingForNote -> whenWaitingForNote(state, event)
        }
    }

    /** Global commands always interrupt the current flow, landing on a fixed top-level state. */
    private fun globalCommand(event: Event): Pair<ConversationState, Effect>? = when (event) {
        Event.StartCommand, Event.ResetCommand, Event.MainMenuOpened ->
            ConversationState.Idle to Effect.ShowMainMenu
        Event.NewCommand ->
            ConversationState.ChoosingSpread to Effect.ShowSpreadPicker
        Event.HistoryCommand ->
            ConversationState.Idle to Effect.ShowHistory(page = 1)
        is Event.HistoryPageRequested ->
            ConversationState.Idle to Effect.ShowHistory(event.page)
        Event.StatsCommand ->
            ConversationState.Idle to Effect.ShowStats
        Event.AdminCommand ->
            ConversationState.Idle to Effect.ShowAdminStats
        Event.AboutCommand ->
            ConversationState.Idle to Effect.ShowAbout
        Event.HelpCommand ->
            ConversationState.Idle to Effect.ShowHelp
        Event.SettingsOpened ->
            ConversationState.Idle to Effect.ShowSettings
        else -> null
    }

    /**
     * Callback-driven events that carry their own target id/data and are safe to
     * handle identically no matter what state the user was previously in.
     */
    private fun universalCallback(state: ConversationState, event: Event): TransitionResult? = when (event) {
        is Event.SpreadChosen ->
            TransitionResult.of(ConversationState.ChoosingReversed(event.spreadId), Effect.AskReversedPreference(event.spreadId))
        is Event.ResonanceSet ->
            TransitionResult.of(state, Effect.SetResonance(event.readingId, event.value))
        is Event.NoteRequested ->
            TransitionResult.of(ConversationState.WaitingForNote(event.readingId), Effect.ShowNotePrompt(event.readingId))
        is Event.HistoryReadingOpened ->
            TransitionResult.of(ConversationState.InReading(event.readingId), Effect.ShowReadingDetail(event.readingId))
        is Event.SettingsReversedChosen ->
            TransitionResult.of(ConversationState.Idle, Effect.SetReversedPreference(event.value))
        else -> null
    }

    private fun whenIdle(event: Event): TransitionResult = when (event) {
        else -> TransitionResult.of(ConversationState.Idle, Effect.ShowMainMenu)
    }

    private fun whenChoosingSpread(event: Event): TransitionResult = when (event) {
        else -> TransitionResult.of(ConversationState.ChoosingSpread, Effect.ShowSpreadPicker)
    }

    private fun whenChoosingReversed(state: ConversationState.ChoosingReversed, event: Event): TransitionResult =
        when (event) {
            is Event.ReversedChoiceMade -> TransitionResult.of(
                ConversationState.WaitingForQuestion(state.spreadId, event.allowReversed),
                Effect.AskQuestion(state.spreadId, event.allowReversed),
            )
            else -> TransitionResult.of(state, Effect.AskReversedPreference(state.spreadId))
        }

    private fun whenWaitingForQuestion(state: ConversationState.WaitingForQuestion, event: Event): TransitionResult =
        when (event) {
            is Event.QuestionAsked -> TransitionResult.of(
                // Placeholder — the orchestrator persists InReading(readingId) on success or
                // AwaitingRetry(...) on AI failure, since the real outcome needs I/O to know.
                state,
                Effect.PerformReading(state.spreadId, state.allowReversed, event.text),
            )
            else -> TransitionResult.of(state, Effect.AskQuestion(state.spreadId, state.allowReversed))
        }

    private fun whenAwaitingRetry(state: ConversationState.AwaitingRetry, event: Event): TransitionResult =
        when (event) {
            is Event.RetryRequested -> TransitionResult.of(
                state, // placeholder — see whenWaitingForQuestion
                Effect.RetryInterpretation(state.spreadId, state.question, state.cards),
            )
            else -> TransitionResult.of(
                state,
                Effect.ReplyError("Нажмите «Повторить», чтобы попробовать получить интерпретацию снова."),
            )
        }

    private fun whenInReading(state: ConversationState.InReading, event: Event): TransitionResult = when (event) {
        is Event.FollowUpAsked -> TransitionResult.of(state, Effect.PerformFollowUp(state.readingId, event.text))
        Event.ClarifyingCardRequested -> TransitionResult.of(state, Effect.PerformClarifyingCard(state.readingId))
        else -> TransitionResult.of(
            state,
            Effect.ReplyError("Задайте уточняющий вопрос текстом или воспользуйтесь кнопками под раскладом."),
        )
    }

    private fun whenWaitingForNote(state: ConversationState.WaitingForNote, event: Event): TransitionResult =
        when (event) {
            is Event.NoteTextEntered -> TransitionResult.of(
                ConversationState.InReading(state.readingId),
                Effect.SaveNote(state.readingId, event.text),
            )
            else -> TransitionResult.of(state, Effect.ShowNotePrompt(state.readingId))
        }
}
