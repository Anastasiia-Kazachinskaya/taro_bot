package com.tarotbot.masterbackend

import com.tarotbot.proto.ai.AiServiceGrpcKt
import com.tarotbot.proto.ai.AnswerFollowupRequest
import com.tarotbot.proto.ai.InterpretClarifyingCardRequest
import com.tarotbot.proto.ai.InterpretSpreadRequest
import com.tarotbot.proto.ai.InterpretationResponse
import com.tarotbot.proto.ai.interpretationResponse
import com.tarotbot.proto.common.SpreadCard
import com.tarotbot.proto.db.BotStats
import com.tarotbot.proto.db.ConsumeQuotaRequest
import com.tarotbot.proto.db.ConsumeQuotaResponse
import com.tarotbot.proto.db.DbServiceGrpcKt
import com.tarotbot.proto.db.DiaryStats
import com.tarotbot.proto.db.Empty
import com.tarotbot.proto.db.GetBotStatsRequest
import com.tarotbot.proto.db.GetReadingRequest
import com.tarotbot.proto.db.ListReadingsRequest
import com.tarotbot.proto.db.ListReadingsResponse
import com.tarotbot.proto.db.Reading
import com.tarotbot.proto.db.ReversedCardsPreference
import com.tarotbot.proto.db.SaveConversationStateRequest
import com.tarotbot.proto.db.SaveReadingRequest
import com.tarotbot.proto.db.SaveReadingResponse
import com.tarotbot.proto.db.SetReadingNoteRequest
import com.tarotbot.proto.db.SetReadingResonanceRequest
import com.tarotbot.proto.db.SetReversedCardsPreferenceRequest
import com.tarotbot.proto.db.UsageSummary
import com.tarotbot.proto.db.UserProfile
import com.tarotbot.proto.db.UserRef
import com.tarotbot.proto.db.appendFollowupRequest
import com.tarotbot.proto.db.botStats
import com.tarotbot.proto.db.conversationState
import com.tarotbot.proto.db.diaryStats
import com.tarotbot.proto.db.empty
import com.tarotbot.proto.db.listReadingsResponse
import com.tarotbot.proto.db.reading
import com.tarotbot.proto.db.reversedCardsPreference
import com.tarotbot.proto.db.saveReadingResponse
import com.tarotbot.proto.db.usageSummary
import com.tarotbot.proto.db.userProfile
import io.grpc.Status
import io.grpc.StatusException

/** In-memory stand-in for db-backend — db-backend's own contract is proven by its own integration tests. */
class FakeDbService : DbServiceGrpcKt.DbServiceCoroutineImplBase() {

    data class StoredState(val state: String, val contextJson: String, var version: Int)
    data class StoredReading(
        val id: Long,
        val userId: Long,
        val question: String,
        val spreadName: String,
        val spread: List<SpreadCard>,
        var interpretation: String,
        var note: String = "",
        var resonance: String = "",
        val followups: MutableList<Pair<String, String>> = mutableListOf(),
    )

    val states = mutableMapOf<Long, StoredState>()
    val readings = mutableMapOf<Long, StoredReading>()
    private var nextReadingId = 1L
    var saveReadingCallCount = 0
    var quotaAllowed = true
    var quotaBlockedReason = "readings"

    override suspend fun getOrCreateUser(request: UserRef): UserProfile = userProfile {
        userId = request.userId
        username = request.username
        firstName = request.firstName
    }

    override suspend fun getConversationState(request: UserRef): com.tarotbot.proto.db.ConversationState {
        val stored = states[request.userId]
        return conversationState {
            userId = request.userId
            state = stored?.state ?: "Idle"
            contextJson = stored?.contextJson ?: "{}"
            version = stored?.version ?: 0
        }
    }

    override suspend fun saveConversationState(request: SaveConversationStateRequest): Empty {
        val existing = states[request.userId]
        val currentVersion = existing?.version ?: 0
        if (request.expectedVersion != -1 && request.expectedVersion != currentVersion) {
            throw StatusException(Status.ABORTED.withDescription("version mismatch"))
        }
        states[request.userId] = StoredState(request.state, request.contextJson, currentVersion + 1)
        return empty {}
    }

    override suspend fun checkAndConsumeQuota(request: ConsumeQuotaRequest): ConsumeQuotaResponse = com.tarotbot.proto.db.consumeQuotaResponse {
        allowed = request.unlimited || quotaAllowed
        blockedReason = if (allowed) "" else quotaBlockedReason
        readingsUsed = 0
        llmUsed = 0
    }

    override suspend fun getUsage(request: UserRef): UsageSummary = usageSummary {
        readingsUsed = 0
        llmUsed = 0
        readingsLimit = 0
        llmLimit = 0
    }

    override suspend fun saveReading(request: SaveReadingRequest): SaveReadingResponse {
        saveReadingCallCount++
        val id = nextReadingId++
        readings[id] = StoredReading(id, request.userId, request.question, request.spreadName, request.spreadList, request.interpretation)
        return saveReadingResponse { readingId = id }
    }

    override suspend fun appendFollowup(request: com.tarotbot.proto.db.AppendFollowupRequest): Empty {
        val stored = readings[request.readingId] ?: throw StatusException(Status.NOT_FOUND)
        if (stored.userId != request.userId) throw StatusException(Status.PERMISSION_DENIED)
        stored.followups += request.question to request.answer
        while (stored.followups.size > 3) stored.followups.removeAt(0)
        return empty {}
    }

    override suspend fun getReading(request: GetReadingRequest): Reading {
        val stored = readings[request.readingId] ?: throw StatusException(Status.NOT_FOUND)
        if (stored.userId != request.userId) throw StatusException(Status.NOT_FOUND)
        return toProto(stored)
    }

    override suspend fun listReadings(request: ListReadingsRequest): ListReadingsResponse {
        val mine = readings.values.filter { it.userId == request.userId }
        return listReadingsResponse {
            readings.addAll(mine.map(::toProto))
            totalCount = mine.size
        }
    }

    override suspend fun setReadingNote(request: SetReadingNoteRequest): Empty {
        val stored = readings[request.readingId] ?: throw StatusException(Status.NOT_FOUND)
        stored.note = request.note
        return empty {}
    }

    override suspend fun setReadingResonance(request: SetReadingResonanceRequest): Empty {
        val stored = readings[request.readingId] ?: throw StatusException(Status.NOT_FOUND)
        stored.resonance = request.resonance
        return empty {}
    }

    override suspend fun getDiaryStats(request: UserRef): DiaryStats = diaryStats {
        totalReadings = readings.values.count { it.userId == request.userId }
    }

    override suspend fun getReversedCardsPreference(request: UserRef): ReversedCardsPreference = reversedCardsPreference {
        hasPreference = false
    }

    override suspend fun setReversedCardsPreference(request: SetReversedCardsPreferenceRequest): Empty = empty {}

    override suspend fun getBotStats(request: GetBotStatsRequest): BotStats = botStats { totalUsers = 0 }

    private fun toProto(stored: StoredReading): Reading = reading {
        id = stored.id
        userId = stored.userId
        question = stored.question
        spreadName = stored.spreadName
        spread.addAll(stored.spread)
        interpretation = stored.interpretation
        note = stored.note
        resonance = stored.resonance
        followups.addAll(stored.followups.map { (q, a) -> com.tarotbot.proto.db.followup { this.question = q; this.answer = a } })
    }
}

/** In-memory stand-in for ai-backend, with controllable failures for testing the retry path. */
class FakeAiService : AiServiceGrpcKt.AiServiceCoroutineImplBase() {

    var failNextInterpretSpreadCalls = 0
    var interpretSpreadCallCount = 0
    val interpretSpreadCardsSeen = mutableListOf<List<SpreadCard>>()

    override suspend fun interpretSpread(request: InterpretSpreadRequest): InterpretationResponse {
        interpretSpreadCallCount++
        interpretSpreadCardsSeen += request.cardsList
        if (failNextInterpretSpreadCalls > 0) {
            failNextInterpretSpreadCalls--
            throw StatusException(Status.UNAVAILABLE.withDescription("simulated AI outage"))
        }
        return interpretationResponse {
            text = "Интерпретация расклада."
            truncated = false
            providerUsed = "fake"
        }
    }

    override suspend fun answerFollowup(request: AnswerFollowupRequest): InterpretationResponse = interpretationResponse {
        text = "Ответ на уточняющий вопрос."
        truncated = false
        providerUsed = "fake"
    }

    override suspend fun interpretClarifyingCard(request: InterpretClarifyingCardRequest): InterpretationResponse = interpretationResponse {
        text = "Пояснение по уточняющей карте."
        truncated = false
        providerUsed = "fake"
    }
}
