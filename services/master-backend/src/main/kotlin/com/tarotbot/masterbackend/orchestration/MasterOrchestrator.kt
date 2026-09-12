package com.tarotbot.masterbackend.orchestration

import com.tarotbot.domain.render.SpreadRenderer
import com.tarotbot.domain.tarot.CardFinder
import com.tarotbot.domain.tarot.Deck
import com.tarotbot.domain.tarot.DrawnCard
import com.tarotbot.domain.tarot.Spread
import com.tarotbot.masterbackend.orchestration.CardMapping.toSpreadCard
import com.tarotbot.masterbackend.statemachine.ConversationState
import com.tarotbot.masterbackend.statemachine.DrawnCardDto
import com.tarotbot.masterbackend.statemachine.Effect
import com.tarotbot.masterbackend.statemachine.StateCodec
import com.tarotbot.masterbackend.statemachine.StateMachine
import com.tarotbot.proto.common.SpreadCard
import com.tarotbot.proto.gateway.IncomingUpdate
import com.tarotbot.proto.gateway.OutgoingResponse
import com.tarotbot.proto.gateway.ResponseAction
import com.tarotbot.proto.gateway.outgoingResponse
import io.grpc.Status
import io.grpc.StatusException

data class MasterConfig(
    val readingsLimit: Int,
    val llmLimit: Int,
    val unlimitedUserIds: Set<Long>,
    val adminUserIds: Set<Long>,
)

private data class EffectOutcome(val actions: List<ResponseAction>, val stateOverride: ConversationState? = null) {
    companion object {
        fun text(body: String) = EffectOutcome(listOf(ActionBuilder.text(body)))
    }
}

/**
 * The only impure layer: reads/writes state via [DbClient], calls [AiClient], renders
 * images via [SpreadRenderer]. Every call to [handle] always ends by persisting the
 * conversation's final state to `db-backend` — that (not anything held in memory) is
 * what lets any of Master's replicas serve any user's next message.
 */
class MasterOrchestrator(
    private val db: DbClient,
    private val ai: AiClient,
    private val config: MasterConfig,
) {

    suspend fun handle(update: IncomingUpdate): OutgoingResponse {
        val userId = update.userId
        db.getOrCreateUser(userId, update.username, update.firstName)

        val stateRow = db.getConversationState(userId)
        val currentState = StateCodec.decode(stateRow.contextJson)
        val version = stateRow.version

        val event = EventMapper.map(update, currentState)
        val result = StateMachine.transition(currentState, event)

        val actions = mutableListOf<ResponseAction>()
        var finalState = result.newState

        for (effect in result.effects) {
            val outcome = executeEffect(effect, userId, currentState)
            actions += outcome.actions
            outcome.stateOverride?.let { finalState = it }
        }

        if (update.callbackQueryId.isNotBlank()) {
            actions += ActionBuilder.answerCallback()
        }

        saveState(userId, finalState, version)

        return outgoingResponse { this.actions.addAll(actions) }
    }

    private suspend fun saveState(userId: Long, state: ConversationState, expectedVersion: Int) {
        try {
            db.saveConversationState(userId, StateCodec.tag(state), StateCodec.encode(state), expectedVersion)
        } catch (e: StatusException) {
            if (e.status.code == Status.Code.ABORTED) {
                // Another replica raced us — last write wins rather than losing this reply.
                db.saveConversationState(userId, StateCodec.tag(state), StateCodec.encode(state), -1)
            } else {
                throw e
            }
        }
    }

    private suspend fun executeEffect(effect: Effect, userId: Long, previousState: ConversationState): EffectOutcome =
        when (effect) {
            Effect.ShowMainMenu -> EffectOutcome(listOf(ActionBuilder.text(ResponseFormatter.mainMenu(), KeyboardBuilder.mainMenu())))
            Effect.ShowSpreadPicker -> EffectOutcome(listOf(ActionBuilder.text(ResponseFormatter.spreadPicker(), KeyboardBuilder.spreadPicker())))
            is Effect.AskReversedPreference -> askReversedPreference(userId, effect.spreadId)
            is Effect.AskQuestion -> askQuestion(effect.spreadId, effect.allowReversed)
            is Effect.PerformReading -> performReading(userId, effect.spreadId, effect.allowReversed, effect.question)
            is Effect.RetryInterpretation -> retryInterpretation(userId, effect.spreadId, effect.question, effect.cards)
            is Effect.PerformFollowUp -> performFollowUp(userId, effect.readingId, effect.text)
            is Effect.PerformClarifyingCard -> performClarifyingCard(userId, effect.readingId)
            is Effect.ShowNotePrompt -> EffectOutcome.text(ResponseFormatter.notePrompt())
            is Effect.SaveNote -> saveNote(userId, effect.readingId, effect.text)
            is Effect.SetResonance -> setResonance(userId, effect.readingId, effect.value)
            is Effect.ShowHistory -> showHistory(userId, effect.page)
            is Effect.ShowReadingDetail -> showReadingDetail(userId, effect.readingId)
            Effect.ShowStats -> showStats(userId)
            Effect.ShowAdminStats -> showAdminStats(userId)
            Effect.ShowAbout -> EffectOutcome.text(ResponseFormatter.about())
            Effect.ShowHelp -> EffectOutcome.text(ResponseFormatter.help())
            Effect.ShowSettings -> EffectOutcome(listOf(ActionBuilder.text(ResponseFormatter.settingsMenu(), KeyboardBuilder.settingsMenu())))
            is Effect.SetReversedPreference -> setReversedPreference(userId, effect.value)
            is Effect.ShowCardLookup -> showCardLookup(effect.query)
            is Effect.ReplyError -> EffectOutcome.text(ResponseFormatter.genericError(effect.message))
        }

    private suspend fun askReversedPreference(userId: Long, spreadId: String): EffectOutcome {
        val pref = db.getReversedCardsPreference(userId)
        if (!pref.hasPreference) {
            return EffectOutcome(listOf(ActionBuilder.text(ResponseFormatter.reversedPrompt(Spread.fromId(spreadId)), KeyboardBuilder.reversedChoice())))
        }
        // A saved preference skips straight to the question — no reversed-cards prompt needed.
        val allowReversed = pref.reversedEnabled
        return EffectOutcome(
            listOf(ActionBuilder.text(ResponseFormatter.questionPrompt(Spread.fromId(spreadId)))),
            stateOverride = ConversationState.WaitingForQuestion(spreadId, allowReversed),
        )
    }

    private fun askQuestion(spreadId: String, allowReversed: Boolean): EffectOutcome =
        EffectOutcome.text(ResponseFormatter.questionPrompt(Spread.fromId(spreadId)))

    private suspend fun performReading(userId: Long, spreadId: String, allowReversed: Boolean, question: String): EffectOutcome {
        val spread = Spread.fromId(spreadId)
        val drawn = Deck.drawCards(spread.positions.size, allowReversed)
        val spreadCards = buildSpreadCards(spread, drawn)

        val quota = db.checkAndConsumeQuota(userId, config.readingsLimit, config.llmLimit, isReading = true, unlimited = userId in config.unlimitedUserIds)
        if (!quota.allowed) {
            return EffectOutcome(listOf(ActionBuilder.text(ResponseFormatter.quotaExceeded(quota.blockedReason))), stateOverride = ConversationState.Idle)
        }

        val imageBytes = SpreadRenderer.renderSpread(spreadId, drawn)
        val photoAction = ActionBuilder.photo(imageBytes, spread.displayName)

        val interpretation = runCatching { ai.interpretSpread(userId, question, spread.displayName, spreadCards) }.getOrNull()
        if (interpretation == null) {
            val cardDtos = spread.positions.mapIndexed { i, label -> CardMapping.toDrawnCardDto(i + 1, label, drawn[i]) }
            return EffectOutcome(
                listOf(photoAction, ActionBuilder.text(ResponseFormatter.retryOffer(), KeyboardBuilder.retry())),
                stateOverride = ConversationState.AwaitingRetry(spreadId, question, cardDtos),
            )
        }

        val readingId = db.saveReading(userId, question, spread.displayName, spreadCards, interpretation.text)
        return EffectOutcome(
            listOf(photoAction, ActionBuilder.text(interpretation.text, KeyboardBuilder.postReading(readingId))),
            stateOverride = ConversationState.InReading(readingId),
        )
    }

    private suspend fun retryInterpretation(userId: Long, spreadId: String, question: String, cards: List<DrawnCardDto>): EffectOutcome {
        val spread = Spread.fromId(spreadId)
        val spreadCards = cards.map { it.toSpreadCard() }

        val quota = db.checkAndConsumeQuota(userId, config.readingsLimit, config.llmLimit, isReading = false, unlimited = userId in config.unlimitedUserIds)
        if (!quota.allowed) {
            return EffectOutcome.text(ResponseFormatter.quotaExceeded(quota.blockedReason))
        }

        val interpretation = runCatching { ai.interpretSpread(userId, question, spread.displayName, spreadCards) }.getOrNull()
        if (interpretation == null) {
            return EffectOutcome(
                listOf(ActionBuilder.text(ResponseFormatter.retryOffer(), KeyboardBuilder.retry())),
                stateOverride = ConversationState.AwaitingRetry(spreadId, question, cards),
            )
        }

        val readingId = db.saveReading(userId, question, spread.displayName, spreadCards, interpretation.text)
        return EffectOutcome(
            listOf(ActionBuilder.text(interpretation.text, KeyboardBuilder.postReading(readingId))),
            stateOverride = ConversationState.InReading(readingId),
        )
    }

    private suspend fun performFollowUp(userId: Long, readingId: Long, text: String): EffectOutcome {
        val quota = db.checkAndConsumeQuota(userId, config.readingsLimit, config.llmLimit, isReading = false, unlimited = userId in config.unlimitedUserIds)
        if (!quota.allowed) {
            return EffectOutcome.text(ResponseFormatter.quotaExceeded(quota.blockedReason))
        }

        val reading = runCatching { db.getReading(readingId, userId) }.getOrElse {
            return EffectOutcome.text(ResponseFormatter.genericError("Не нашла этот расклад."))
        }

        val history = reading.followupsList.map { it.question to it.answer }
        val response = runCatching {
            ai.answerFollowup(userId, reading.spreadName, reading.spreadList, reading.question, reading.interpretation, history, text)
        }.getOrNull() ?: return EffectOutcome.text(ResponseFormatter.genericError("Не получилось получить ответ, попробуй ещё раз."))

        db.appendFollowup(readingId, userId, text, response.text)
        return EffectOutcome.text(ResponseFormatter.followUpAnswer(response.text))
    }

    private suspend fun performClarifyingCard(userId: Long, readingId: Long): EffectOutcome {
        val quota = db.checkAndConsumeQuota(userId, config.readingsLimit, config.llmLimit, isReading = false, unlimited = userId in config.unlimitedUserIds)
        if (!quota.allowed) {
            return EffectOutcome.text(ResponseFormatter.quotaExceeded(quota.blockedReason))
        }

        val reading = runCatching { db.getReading(readingId, userId) }.getOrElse {
            return EffectOutcome.text(ResponseFormatter.genericError("Не нашла этот расклад."))
        }

        val excludeNames = reading.spreadList.map { it.card.name }.toSet()
        val drawn = Deck.drawSingleCard(excludeNames, allowReversed = true)
        val clarifyingCard = CardMapping.toSpreadCard(reading.spreadCount + 1, "Уточняющая карта", drawn)
        val imageBytes = SpreadRenderer.renderSingleCard(drawn)
        val photoAction = ActionBuilder.photo(imageBytes, drawn.card.name)

        val response = runCatching {
            ai.interpretClarifyingCard(userId, reading.spreadName, reading.spreadList, reading.question, reading.interpretation, clarifyingCard)
        }.getOrNull() ?: return EffectOutcome(listOf(photoAction, ActionBuilder.text(ResponseFormatter.genericError("Не получилось получить уточнение, попробуй ещё раз."))))

        db.appendFollowup(readingId, userId, "[Уточняющая карта: ${drawn.card.name}]", response.text)
        return EffectOutcome(listOf(photoAction, ActionBuilder.text(ResponseFormatter.clarifyingCardAnswer(drawn.card.name, drawn.reversed, response.text))))
    }

    private suspend fun saveNote(userId: Long, readingId: Long, text: String): EffectOutcome {
        runCatching { db.setReadingNote(readingId, userId, text) }
            .onFailure { return EffectOutcome.text(ResponseFormatter.genericError("Не удалось сохранить заметку.")) }
        return EffectOutcome.text(ResponseFormatter.noteSaved())
    }

    private suspend fun setResonance(userId: Long, readingId: Long, value: String): EffectOutcome {
        runCatching { db.setReadingResonance(readingId, userId, value) }
            .onFailure { return EffectOutcome.text(ResponseFormatter.genericError("Не удалось сохранить отметку.")) }
        return EffectOutcome.text(ResponseFormatter.resonanceSaved(value))
    }

    private suspend fun showHistory(userId: Long, page: Int): EffectOutcome {
        val pageSize = 10
        val response = db.listReadings(userId, page, pageSize)
        if (response.readingsList.isEmpty() && page <= 1) {
            return EffectOutcome.text(ResponseFormatter.historyEmpty())
        }
        val totalPages = maxOf(1, (response.totalCount + pageSize - 1) / pageSize)
        val header = ResponseFormatter.historyHeader(page, totalPages, response.totalCount)
        val entries = response.readingsList.map { it.id to ResponseFormatter.historyEntryLabel(it) }
        return EffectOutcome(listOf(ActionBuilder.text(header, KeyboardBuilder.historyPage(entries, page, totalPages))))
    }

    private suspend fun showReadingDetail(userId: Long, readingId: Long): EffectOutcome {
        val reading = runCatching { db.getReading(readingId, userId) }.getOrElse {
            return EffectOutcome.text(ResponseFormatter.genericError("Не нашла этот расклад."))
        }
        return EffectOutcome(listOf(ActionBuilder.text(ResponseFormatter.readingDetail(reading), KeyboardBuilder.postReading(readingId))))
    }

    private suspend fun showStats(userId: Long): EffectOutcome {
        val diary = db.getDiaryStats(userId)
        val usage = db.getUsage(userId)
        return EffectOutcome.text(ResponseFormatter.statsSummary(diary, usage, config.readingsLimit, config.llmLimit))
    }

    private suspend fun showAdminStats(userId: Long): EffectOutcome {
        if (userId !in config.adminUserIds) {
            return EffectOutcome.text(ResponseFormatter.adminAccessDenied())
        }
        val stats = db.getBotStats(userId)
        return EffectOutcome.text(ResponseFormatter.adminStats(stats))
    }

    private suspend fun setReversedPreference(userId: Long, value: String): EffectOutcome {
        when (value) {
            "yes" -> db.setReversedCardsPreference(userId, hasPreference = true, reversedEnabled = true)
            "no" -> db.setReversedCardsPreference(userId, hasPreference = true, reversedEnabled = false)
            else -> db.setReversedCardsPreference(userId, hasPreference = false, reversedEnabled = false)
        }
        return EffectOutcome.text(ResponseFormatter.reversedPreferenceSaved(value))
    }

    private fun showCardLookup(query: String): EffectOutcome {
        val matches = CardFinder.findCards(query)
        return when {
            matches.isEmpty() -> EffectOutcome.text(ResponseFormatter.cardLookupNoMatch(query))
            matches.size == 1 -> EffectOutcome.text(ResponseFormatter.cardLookupResult(matches.single()))
            else -> EffectOutcome.text(ResponseFormatter.cardLookupAmbiguous(query, matches))
        }
    }

    private fun buildSpreadCards(spread: Spread, drawn: List<DrawnCard>): List<SpreadCard> =
        spread.positions.mapIndexed { i, label -> toSpreadCard(i + 1, label, drawn[i]) }
}
