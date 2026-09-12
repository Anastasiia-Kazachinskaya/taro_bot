package com.tarotbot.masterbackend.orchestration

import com.tarotbot.proto.ai.AiServiceGrpcKt
import com.tarotbot.proto.ai.InterpretationResponse
import com.tarotbot.proto.ai.answerFollowupRequest
import com.tarotbot.proto.ai.followupTurn
import com.tarotbot.proto.ai.interpretClarifyingCardRequest
import com.tarotbot.proto.ai.interpretSpreadRequest
import com.tarotbot.proto.common.SpreadCard

/** Thin coroutine wrapper around [AiServiceGrpcKt.AiServiceCoroutineStub]. See [DbClient] for why local vals precede every DSL block. */
class AiClient(private val stub: AiServiceGrpcKt.AiServiceCoroutineStub) {

    suspend fun interpretSpread(
        userId: Long,
        question: String,
        spreadName: String,
        cards: List<SpreadCard>,
    ): InterpretationResponse {
        val uid = userId
        val q = question
        val sn = spreadName
        val cs = cards
        return stub.interpretSpread(
            interpretSpreadRequest {
                this.userId = uid
                this.question = q
                this.spreadName = sn
                this.cards.addAll(cs)
            },
        )
    }

    suspend fun answerFollowup(
        userId: Long,
        spreadName: String,
        cards: List<SpreadCard>,
        originalQuestion: String,
        originalInterpretation: String,
        history: List<Pair<String, String>>,
        newQuestion: String,
    ): InterpretationResponse {
        val uid = userId
        val sn = spreadName
        val cs = cards
        val origQ = originalQuestion
        val origI = originalInterpretation
        val hist = history
        val newQ = newQuestion
        return stub.answerFollowup(
            answerFollowupRequest {
                this.userId = uid
                this.spreadName = sn
                this.cards.addAll(cs)
                this.originalQuestion = origQ
                this.originalInterpretation = origI
                this.history.addAll(hist.map { (q, a) -> followupTurn { this.question = q; this.answer = a } })
                this.newQuestion = newQ
            },
        )
    }

    suspend fun interpretClarifyingCard(
        userId: Long,
        spreadName: String,
        originalCards: List<SpreadCard>,
        originalQuestion: String,
        originalInterpretation: String,
        clarifyingCard: SpreadCard,
    ): InterpretationResponse {
        val uid = userId
        val sn = spreadName
        val origCards = originalCards
        val origQ = originalQuestion
        val origI = originalInterpretation
        val cc = clarifyingCard
        return stub.interpretClarifyingCard(
            interpretClarifyingCardRequest {
                this.userId = uid
                this.spreadName = sn
                this.originalCards.addAll(origCards)
                this.originalQuestion = origQ
                this.originalInterpretation = origI
                this.clarifyingCard = cc
            },
        )
    }
}
