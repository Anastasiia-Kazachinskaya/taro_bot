package com.tarotbot.dbbackend

import com.google.protobuf.Timestamp
import com.tarotbot.proto.db.BotStats
import com.tarotbot.proto.db.ConsumeQuotaRequest
import com.tarotbot.proto.db.ConsumeQuotaResponse
import com.tarotbot.proto.db.ConversationState
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
import com.tarotbot.proto.db.botStats
import com.tarotbot.proto.db.cardFrequency
import com.tarotbot.proto.db.consumeQuotaResponse
import com.tarotbot.proto.db.conversationState
import com.tarotbot.proto.db.diaryStats
import com.tarotbot.proto.db.empty
import com.tarotbot.proto.db.listReadingsResponse
import com.tarotbot.proto.db.reading
import com.tarotbot.proto.db.reversedCardsPreference
import com.tarotbot.proto.db.saveReadingResponse
import com.tarotbot.proto.db.usageSummary
import com.tarotbot.proto.db.userActivity
import com.tarotbot.proto.db.userProfile
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.sql.Date
import java.time.Instant
import java.time.LocalDate

private fun Instant.toProtoTimestamp(): Timestamp =
    Timestamp.newBuilder().setSeconds(epochSecond).setNanos(nano).build()

private const val DEFAULT_PAGE_SIZE = 10
private const val TOP_CARDS_LIMIT = 5

class DbServiceImpl(private val databases: Databases) : DbServiceGrpcKt.DbServiceCoroutineImplBase() {

    private suspend fun <T> primary(block: suspend org.jetbrains.exposed.sql.Transaction.() -> T): T =
        wrapErrors { newSuspendedTransaction(Dispatchers.IO, db = databases.primary, statement = block) }

    private suspend fun <T> replica(block: suspend org.jetbrains.exposed.sql.Transaction.() -> T): T =
        wrapErrors { newSuspendedTransaction(Dispatchers.IO, db = databases.replica, statement = block) }

    private suspend fun <T> wrapErrors(block: suspend () -> T): T = try {
        block()
    } catch (e: StatusException) {
        throw e
    } catch (e: io.grpc.StatusRuntimeException) {
        throw e
    } catch (e: Exception) {
        throw StatusException(Status.INTERNAL.withDescription(e.message).withCause(e))
    }

    override suspend fun getOrCreateUser(request: UserRef): UserProfile = primary {
        val now = Instant.now()
        val existing = Users.selectAll().where { Users.userId eq request.userId }.singleOrNull()

        if (existing == null) {
            Users.insert {
                it[userId] = request.userId
                it[username] = request.username
                it[firstName] = request.firstName
                it[firstSeen] = now
                it[lastSeen] = now
            }
            UserSettings.insert {
                it[userId] = request.userId
                it[reversedCards] = null
            }
        } else {
            Users.update({ Users.userId eq request.userId }) {
                it[username] = request.username
                it[firstName] = request.firstName
                it[lastSeen] = now
            }
        }

        val row = Users.selectAll().where { Users.userId eq request.userId }.single()
        userProfile {
            userId = row[Users.userId]
            username = row[Users.username] ?: ""
            firstName = row[Users.firstName] ?: ""
            firstSeen = row[Users.firstSeen].toProtoTimestamp()
            lastSeen = row[Users.lastSeen].toProtoTimestamp()
        }
    }

    override suspend fun getConversationState(request: UserRef): ConversationState = primary {
        val row = ConversationStates.selectAll().where { ConversationStates.userId eq request.userId }.singleOrNull()
        conversationState {
            userId = request.userId
            state = row?.get(ConversationStates.state) ?: "Idle"
            contextJson = row?.get(ConversationStates.context) ?: "{}"
            version = row?.get(ConversationStates.version) ?: 0
        }
    }

    override suspend fun saveConversationState(request: SaveConversationStateRequest): Empty = primary {
        val existing = ConversationStates.selectAll().where { ConversationStates.userId eq request.userId }.singleOrNull()
        val currentVersion = existing?.get(ConversationStates.version) ?: 0

        if (request.expectedVersion != -1 && request.expectedVersion != currentVersion) {
            throw StatusException(
                Status.ABORTED.withDescription(
                    "Conversation state version mismatch: expected ${request.expectedVersion}, was $currentVersion",
                ),
            )
        }

        val now = Instant.now()
        if (existing == null) {
            ConversationStates.insert {
                it[userId] = request.userId
                it[state] = request.state
                it[context] = request.contextJson
                it[version] = currentVersion + 1
                it[updatedAt] = now
            }
        } else {
            ConversationStates.update({ ConversationStates.userId eq request.userId }) {
                it[state] = request.state
                it[context] = request.contextJson
                it[version] = currentVersion + 1
                it[updatedAt] = now
            }
        }
        empty {}
    }

    override suspend fun checkAndConsumeQuota(request: ConsumeQuotaRequest): ConsumeQuotaResponse = primary {
        if (request.unlimited) {
            val today = LocalDate.now()
            val row = Usage.selectAll()
                .where { (Usage.userId eq request.userId) and (Usage.day eq today) }
                .singleOrNull()
            return@primary consumeQuotaResponse {
                allowed = true
                blockedReason = ""
                readingsUsed = row?.get(Usage.readings) ?: 0
                llmUsed = row?.get(Usage.llmRequests) ?: 0
            }
        }

        val jdbcConnection = connection.connection as java.sql.Connection
        jdbcConnection.prepareStatement("SELECT * FROM consume_quota(?, ?, ?, ?, ?)").use { ps ->
            ps.setLong(1, request.userId)
            ps.setDate(2, Date.valueOf(LocalDate.now()))
            ps.setInt(3, request.readingsLimit)
            ps.setInt(4, request.llmLimit)
            ps.setBoolean(5, request.isReading)
            ps.executeQuery().use { rs ->
                rs.next()
                consumeQuotaResponse {
                    allowed = rs.getBoolean("allowed")
                    blockedReason = rs.getString("reason") ?: ""
                    readingsUsed = rs.getInt("readings_used")
                    llmUsed = rs.getInt("llm_used")
                }
            }
        }
    }

    override suspend fun getUsage(request: UserRef): UsageSummary = primary {
        val today = LocalDate.now()
        val row = Usage.selectAll()
            .where { (Usage.userId eq request.userId) and (Usage.day eq today) }
            .singleOrNull()
        usageSummary {
            readingsUsed = row?.get(Usage.readings) ?: 0
            llmUsed = row?.get(Usage.llmRequests) ?: 0
            readingsLimit = 0
            llmLimit = 0
        }
    }

    override suspend fun saveReading(request: SaveReadingRequest): SaveReadingResponse = primary {
        val newId = Readings.insert {
            it[userId] = request.userId
            it[createdAt] = Instant.now()
            it[question] = request.question
            it[spreadName] = request.spreadName
            it[spread] = SpreadJson.encode(request.spreadList)
            it[interpretation] = request.interpretation
            it[note] = null
            it[resonance] = null
            it[followups] = FollowupsJson.EMPTY
        } get Readings.id
        saveReadingResponse { readingId = newId }
    }

    override suspend fun appendFollowup(request: com.tarotbot.proto.db.AppendFollowupRequest): Empty = primary {
        val row = Readings.selectAll().where { Readings.id eq request.readingId }.singleOrNull()
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Reading ${request.readingId} not found"))
        if (row[Readings.userId] != request.userId) {
            throw StatusException(Status.PERMISSION_DENIED.withDescription("Reading does not belong to this user"))
        }
        val updated = FollowupsJson.appendCapped(row[Readings.followups], request.question, request.answer)
        Readings.update({ Readings.id eq request.readingId }) { it[followups] = updated }
        empty {}
    }

    private fun rowToReading(row: org.jetbrains.exposed.sql.ResultRow): Reading = reading {
        id = row[Readings.id]
        userId = row[Readings.userId]
        createdAt = row[Readings.createdAt].toProtoTimestamp()
        question = row[Readings.question]
        spreadName = row[Readings.spreadName]
        spread.addAll(SpreadJson.decode(row[Readings.spread]))
        interpretation = row[Readings.interpretation]
        note = row[Readings.note] ?: ""
        resonance = row[Readings.resonance] ?: ""
        followups.addAll(FollowupsJson.decode(row[Readings.followups]))
    }

    override suspend fun getReading(request: GetReadingRequest): Reading = replica {
        val row = Readings.selectAll().where { Readings.id eq request.readingId }.singleOrNull()
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Reading ${request.readingId} not found"))
        if (row[Readings.userId] != request.userId) {
            throw StatusException(Status.NOT_FOUND.withDescription("Reading ${request.readingId} not found"))
        }
        rowToReading(row)
    }

    override suspend fun listReadings(request: ListReadingsRequest): ListReadingsResponse = replica {
        val pageSize = if (request.pageSize > 0) request.pageSize else DEFAULT_PAGE_SIZE
        val page = if (request.page > 0) request.page else 1

        val totalCountValue = Readings.selectAll().where { Readings.userId eq request.userId }.count()
        val rows = Readings.selectAll()
            .where { Readings.userId eq request.userId }
            .orderBy(Readings.createdAt, SortOrder.DESC)
            .limit(pageSize).offset(((page - 1).toLong()) * pageSize)
            .toList()

        listReadingsResponse {
            readings.addAll(rows.map(::rowToReading))
            totalCount = totalCountValue.toInt()
        }
    }

    override suspend fun setReadingNote(request: SetReadingNoteRequest): Empty = primary {
        val row = Readings.selectAll().where { Readings.id eq request.readingId }.singleOrNull()
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Reading ${request.readingId} not found"))
        if (row[Readings.userId] != request.userId) {
            throw StatusException(Status.PERMISSION_DENIED.withDescription("Reading does not belong to this user"))
        }
        Readings.update({ Readings.id eq request.readingId }) {
            it[note] = request.note.ifBlank { null }
        }
        empty {}
    }

    override suspend fun setReadingResonance(request: SetReadingResonanceRequest): Empty = primary {
        val row = Readings.selectAll().where { Readings.id eq request.readingId }.singleOrNull()
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Reading ${request.readingId} not found"))
        if (row[Readings.userId] != request.userId) {
            throw StatusException(Status.PERMISSION_DENIED.withDescription("Reading does not belong to this user"))
        }
        val value = when (request.resonance) {
            "yes", "no" -> request.resonance
            "" -> null
            else -> throw StatusException(Status.INVALID_ARGUMENT.withDescription("resonance must be 'yes', 'no' or ''"))
        }
        Readings.update({ Readings.id eq request.readingId }) { it[resonance] = value }
        empty {}
    }

    override suspend fun getDiaryStats(request: UserRef): DiaryStats = replica {
        val rows = Readings.selectAll().where { Readings.userId eq request.userId }.toList()
        val notesCountValue = rows.count { !it[Readings.note].isNullOrBlank() }
        val resonanceYesCount = rows.count { it[Readings.resonance] == "yes" }
        val resonanceNoCount = rows.count { it[Readings.resonance] == "no" }
        val topCardEntries = SpreadJson.countCardNames(rows.map { it[Readings.spread] })
            .entries.sortedByDescending { it.value }
            .take(TOP_CARDS_LIMIT)

        diaryStats {
            totalReadings = rows.size
            this.notesCount = notesCountValue
            resonanceYes = resonanceYesCount
            resonanceNo = resonanceNoCount
            topCardEntries.forEach { (name, count) ->
                topCards.add(cardFrequency {
                    cardName = name
                    this.count = count
                })
            }
        }
    }

    override suspend fun getReversedCardsPreference(request: UserRef): ReversedCardsPreference = primary {
        val row = UserSettings.selectAll().where { UserSettings.userId eq request.userId }.singleOrNull()
        val value = row?.get(UserSettings.reversedCards)
        reversedCardsPreference {
            hasPreference = value != null
            reversedEnabled = value ?: false
        }
    }

    override suspend fun setReversedCardsPreference(request: SetReversedCardsPreferenceRequest): Empty = primary {
        val value = if (request.hasPreference) request.reversedEnabled else null
        val existing = UserSettings.selectAll().where { UserSettings.userId eq request.userId }.singleOrNull()
        if (existing == null) {
            UserSettings.insert {
                it[userId] = request.userId
                it[reversedCards] = value
            }
        } else {
            UserSettings.update({ UserSettings.userId eq request.userId }) { it[reversedCards] = value }
        }
        empty {}
    }

    override suspend fun getBotStats(request: GetBotStatsRequest): BotStats = replica {
        val users = Users.selectAll().toList()
        val activities = users.map { userRow ->
            val userId = userRow[Users.userId]
            val readingsCount = Readings.selectAll().where { Readings.userId eq userId }.count()
            val llmRequestsCount = Usage.selectAll().where { Usage.userId eq userId }
                .sumOf { it[Usage.llmRequests] }
            userActivity {
                this.userId = userId
                username = userRow[Users.username] ?: ""
                this.readingsCount = readingsCount.toInt()
                this.llmRequestsCount = llmRequestsCount
                lastSeen = userRow[Users.lastSeen].toProtoTimestamp()
            }
        }
        botStats {
            totalUsers = users.size
            this.users.addAll(activities)
        }
    }
}
