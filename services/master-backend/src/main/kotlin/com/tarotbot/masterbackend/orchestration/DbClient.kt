package com.tarotbot.masterbackend.orchestration

import com.tarotbot.proto.common.SpreadCard
import com.tarotbot.proto.db.BotStats
import com.tarotbot.proto.db.ConsumeQuotaResponse
import com.tarotbot.proto.db.ConversationState as ProtoConversationState
import com.tarotbot.proto.db.DbServiceGrpcKt
import com.tarotbot.proto.db.DiaryStats
import com.tarotbot.proto.db.ListReadingsResponse
import com.tarotbot.proto.db.Reading
import com.tarotbot.proto.db.ReversedCardsPreference
import com.tarotbot.proto.db.UserProfile
import com.tarotbot.proto.db.UsageSummary
import com.tarotbot.proto.db.appendFollowupRequest
import com.tarotbot.proto.db.consumeQuotaRequest
import com.tarotbot.proto.db.getBotStatsRequest
import com.tarotbot.proto.db.getReadingRequest
import com.tarotbot.proto.db.listReadingsRequest
import com.tarotbot.proto.db.saveConversationStateRequest
import com.tarotbot.proto.db.saveReadingRequest
import com.tarotbot.proto.db.setReadingNoteRequest
import com.tarotbot.proto.db.setReadingResonanceRequest
import com.tarotbot.proto.db.setReversedCardsPreferenceRequest
import com.tarotbot.proto.db.userRef

/**
 * Thin coroutine wrapper around [DbServiceGrpcKt.DbServiceCoroutineStub]. Every method
 * here does exactly one RPC — no retry/deadline logic (that lives in the channel built
 * via `ResilientChannelFactory`, see [com.tarotbot.masterbackend.Main]).
 *
 * Local vals are used before entering each proto DSL builder block on purpose: a
 * builder parameter named e.g. `userId` would otherwise shadow this function's own
 * `userId` parameter inside the block, so every value is captured under a distinct
 * name first.
 */
class DbClient(private val stub: DbServiceGrpcKt.DbServiceCoroutineStub) {

    suspend fun getOrCreateUser(userId: Long, username: String, firstName: String): UserProfile {
        val uid = userId
        val uname = username
        val fname = firstName
        return stub.getOrCreateUser(userRef { this.userId = uid; this.username = uname; this.firstName = fname })
    }

    suspend fun getConversationState(userId: Long): ProtoConversationState {
        val uid = userId
        return stub.getConversationState(userRef { this.userId = uid })
    }

    suspend fun saveConversationState(userId: Long, state: String, contextJson: String, expectedVersion: Int) {
        val uid = userId
        val st = state
        val ctx = contextJson
        val ver = expectedVersion
        stub.saveConversationState(
            saveConversationStateRequest {
                this.userId = uid
                this.state = st
                this.contextJson = ctx
                this.expectedVersion = ver
            },
        )
    }

    suspend fun checkAndConsumeQuota(
        userId: Long,
        readingsLimit: Int,
        llmLimit: Int,
        isReading: Boolean,
        unlimited: Boolean,
    ): ConsumeQuotaResponse {
        val uid = userId
        val rLimit = readingsLimit
        val lLimit = llmLimit
        val reading = isReading
        val unl = unlimited
        return stub.checkAndConsumeQuota(
            consumeQuotaRequest {
                this.userId = uid
                this.readingsLimit = rLimit
                this.llmLimit = lLimit
                this.isReading = reading
                this.unlimited = unl
            },
        )
    }

    suspend fun getUsage(userId: Long): UsageSummary {
        val uid = userId
        return stub.getUsage(userRef { this.userId = uid })
    }

    suspend fun saveReading(
        userId: Long,
        question: String,
        spreadName: String,
        cards: List<SpreadCard>,
        interpretation: String,
    ): Long {
        val uid = userId
        val q = question
        val sn = spreadName
        val cs = cards
        val interp = interpretation
        return stub.saveReading(
            saveReadingRequest {
                this.userId = uid
                this.question = q
                this.spreadName = sn
                this.spread.addAll(cs)
                this.interpretation = interp
            },
        ).readingId
    }

    suspend fun appendFollowup(readingId: Long, userId: Long, question: String, answer: String) {
        val rid = readingId
        val uid = userId
        val q = question
        val a = answer
        stub.appendFollowup(
            appendFollowupRequest {
                this.readingId = rid
                this.userId = uid
                this.question = q
                this.answer = a
            },
        )
    }

    suspend fun getReading(readingId: Long, userId: Long): Reading {
        val rid = readingId
        val uid = userId
        return stub.getReading(getReadingRequest { this.readingId = rid; this.userId = uid })
    }

    suspend fun listReadings(userId: Long, page: Int, pageSize: Int): ListReadingsResponse {
        val uid = userId
        val p = page
        val ps = pageSize
        return stub.listReadings(listReadingsRequest { this.userId = uid; this.page = p; this.pageSize = ps })
    }

    suspend fun setReadingNote(readingId: Long, userId: Long, note: String) {
        val rid = readingId
        val uid = userId
        val n = note
        stub.setReadingNote(setReadingNoteRequest { this.readingId = rid; this.userId = uid; this.note = n })
    }

    suspend fun setReadingResonance(readingId: Long, userId: Long, resonance: String) {
        val rid = readingId
        val uid = userId
        val res = resonance
        stub.setReadingResonance(
            setReadingResonanceRequest { this.readingId = rid; this.userId = uid; this.resonance = res },
        )
    }

    suspend fun getDiaryStats(userId: Long): DiaryStats {
        val uid = userId
        return stub.getDiaryStats(userRef { this.userId = uid })
    }

    suspend fun getReversedCardsPreference(userId: Long): ReversedCardsPreference {
        val uid = userId
        return stub.getReversedCardsPreference(userRef { this.userId = uid })
    }

    suspend fun setReversedCardsPreference(userId: Long, hasPreference: Boolean, reversedEnabled: Boolean) {
        val uid = userId
        val hasPref = hasPreference
        val revEnabled = reversedEnabled
        stub.setReversedCardsPreference(
            setReversedCardsPreferenceRequest {
                this.userId = uid
                this.hasPreference = hasPref
                this.reversedEnabled = revEnabled
            },
        )
    }

    suspend fun getBotStats(requestingUserId: Long): BotStats {
        val rid = requestingUserId
        return stub.getBotStats(getBotStatsRequest { this.requestingUserId = rid })
    }
}
