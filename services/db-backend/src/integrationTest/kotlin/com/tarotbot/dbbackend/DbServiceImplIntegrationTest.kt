package com.tarotbot.dbbackend

import com.tarotbot.proto.common.card
import com.tarotbot.proto.common.spreadCard
import com.tarotbot.proto.db.DbServiceGrpcKt
import com.tarotbot.proto.db.appendFollowupRequest
import com.tarotbot.proto.db.consumeQuotaRequest
import com.tarotbot.proto.db.getBotStatsRequest
import com.tarotbot.proto.db.getReadingRequest
import com.tarotbot.proto.db.listReadingsRequest
import com.tarotbot.proto.db.saveConversationStateRequest
import com.tarotbot.proto.db.saveReadingRequest
import com.tarotbot.proto.db.setReadingNoteRequest
import com.tarotbot.proto.db.setReadingResonanceRequest
import com.tarotbot.proto.db.userRef
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.random.Random

@Testcontainers
class DbServiceImplIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        private lateinit var server: Server
        private lateinit var channel: ManagedChannel
        private lateinit var stub: DbServiceGrpcKt.DbServiceCoroutineStub

        @BeforeAll
        @JvmStatic
        fun setup() {
            FlywayMigrator.migrate(postgres.jdbcUrl, postgres.username, postgres.password)
            val db = Database.connect(
                url = postgres.jdbcUrl,
                driver = "org.postgresql.Driver",
                user = postgres.username,
                password = postgres.password,
            )
            val databases = Databases(db, db)

            val serverName = InProcessServerBuilder.generateName()
            server = InProcessServerBuilder.forName(serverName).directExecutor()
                .addService(DbServiceImpl(databases)).build().start()
            channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
            stub = DbServiceGrpcKt.DbServiceCoroutineStub(channel)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            channel.shutdownNow()
            server.shutdownNow()
        }
    }

    private fun newUserId(): Long = Random.nextLong(1, Long.MAX_VALUE / 2)

    private suspend fun ensureUser(userId: Long) {
        stub.getOrCreateUser(userRef { this.userId = userId; username = "user$userId"; firstName = "Test" })
    }

    private fun sampleSpread(cardName: String = "Шут") = listOf(
        spreadCard {
            positionIndex = 1
            positionLabel = "Суть ситуации"
            card = card {
                name = cardName
                arcana = "major"
                suit = ""
                reversed = false
                meaning = "meaning"
            }
        },
    )

    @Test
    fun `GetOrCreateUser upserts without duplicating`() = runBlocking {
        val userId = newUserId()
        val ref = userRef { this.userId = userId; username = "alice"; firstName = "Alice" }
        val first = stub.getOrCreateUser(ref)
        Thread.sleep(10)
        val second = stub.getOrCreateUser(userRef { this.userId = userId; username = "alice2"; firstName = "Alice" })

        assertEquals(userId, second.userId)
        assertEquals("alice2", second.username)
        assertTrue(second.lastSeen.seconds >= first.lastSeen.seconds)

        val stats = stub.getBotStats(getBotStatsRequest { requestingUserId = userId })
        assertEquals(1, stats.usersList.count { it.userId == userId })
    }

    @Test
    fun `conversation state round-trips and rejects a stale version`() = runBlocking {
        val userId = newUserId()
        ensureUser(userId)

        val initial = stub.getConversationState(userRef { this.userId = userId })
        assertEquals("Idle", initial.state)
        assertEquals(0, initial.version)

        stub.saveConversationState(
            saveConversationStateRequest {
                this.userId = userId
                state = "WaitingForQuestion"
                contextJson = """{"spread":"one_card"}"""
                expectedVersion = 0
            },
        )
        val updated = stub.getConversationState(userRef { this.userId = userId })
        assertEquals("WaitingForQuestion", updated.state)
        assertEquals(1, updated.version)

        val ex = assertThrows(StatusException::class.java) {
            runBlocking {
                stub.saveConversationState(
                    saveConversationStateRequest {
                        this.userId = userId
                        state = "Idle"
                        contextJson = "{}"
                        expectedVersion = 0 // stale — current version is 1
                    },
                )
            }
        }
        assertEquals(Status.Code.ABORTED, Status.fromThrowable(ex).code)
    }

    @Test
    fun `quota blocks after the limit and unlimited bypasses it`() = runBlocking {
        val userId = newUserId()
        ensureUser(userId)

        repeat(2) {
            val response = stub.checkAndConsumeQuota(
                consumeQuotaRequest {
                    this.userId = userId
                    readingsLimit = 2
                    llmLimit = 0
                    isReading = true
                    unlimited = false
                },
            )
            assertTrue(response.allowed)
        }

        val blocked = stub.checkAndConsumeQuota(
            consumeQuotaRequest {
                this.userId = userId
                readingsLimit = 2
                llmLimit = 0
                isReading = true
                unlimited = false
            },
        )
        assertTrue(!blocked.allowed)
        assertEquals("readings", blocked.blockedReason)

        val bypassed = stub.checkAndConsumeQuota(
            consumeQuotaRequest {
                this.userId = userId
                readingsLimit = 2
                llmLimit = 0
                isReading = true
                unlimited = true
            },
        )
        assertTrue(bypassed.allowed)
    }

    @Test
    fun `followups cap at 3 turns`() = runBlocking {
        val userId = newUserId()
        ensureUser(userId)
        val saved = stub.saveReading(
            saveReadingRequest {
                this.userId = userId
                question = "q0"
                spreadName = "one_card"
                spread.addAll(sampleSpread())
                interpretation = "interpretation"
            },
        )

        repeat(5) { i ->
            stub.appendFollowup(
                appendFollowupRequest {
                    readingId = saved.readingId
                    this.userId = userId
                    question = "q$i"
                    answer = "a$i"
                },
            )
        }

        val reading = stub.getReading(getReadingRequest { readingId = saved.readingId; this.userId = userId })
        assertEquals(3, reading.followupsCount)
        assertEquals("q2", reading.followupsList[0].question)
        assertEquals("q4", reading.followupsList[2].question)
    }

    @Test
    fun `note and resonance updates reject a different user`() = runBlocking {
        val owner = newUserId()
        val intruder = newUserId()
        ensureUser(owner)
        ensureUser(intruder)

        val saved = stub.saveReading(
            saveReadingRequest {
                this.userId = owner
                question = "q"
                spreadName = "one_card"
                spread.addAll(sampleSpread())
                interpretation = "interpretation"
            },
        )

        val noteEx = assertThrows(StatusException::class.java) {
            runBlocking {
                stub.setReadingNote(setReadingNoteRequest { readingId = saved.readingId; userId = intruder; note = "hijacked" })
            }
        }
        assertEquals(Status.Code.PERMISSION_DENIED, Status.fromThrowable(noteEx).code)

        val resonanceEx = assertThrows(StatusException::class.java) {
            runBlocking {
                stub.setReadingResonance(setReadingResonanceRequest { readingId = saved.readingId; userId = intruder; resonance = "yes" })
            }
        }
        assertEquals(Status.Code.PERMISSION_DENIED, Status.fromThrowable(resonanceEx).code)

        stub.setReadingNote(setReadingNoteRequest { readingId = saved.readingId; userId = owner; note = "my note" })
        stub.setReadingResonance(setReadingResonanceRequest { readingId = saved.readingId; userId = owner; resonance = "yes" })

        val reading = stub.getReading(getReadingRequest { readingId = saved.readingId; userId = owner })
        assertEquals("my note", reading.note)
        assertEquals("yes", reading.resonance)
    }

    @Test
    fun `listReadings paginates in descending order`() = runBlocking {
        val userId = newUserId()
        ensureUser(userId)

        val ids = (1..5).map { i ->
            stub.saveReading(
                saveReadingRequest {
                    this.userId = userId
                    question = "q$i"
                    spreadName = "one_card"
                    spread.addAll(sampleSpread())
                    interpretation = "interpretation $i"
                },
            ).readingId.also { Thread.sleep(5) }
        }

        val page1 = stub.listReadings(listReadingsRequest { this.userId = userId; page = 1; pageSize = 2 })
        assertEquals(5, page1.totalCount)
        assertEquals(2, page1.readingsCount)
        assertEquals(ids.last(), page1.readingsList[0].id)

        val page3 = stub.listReadings(listReadingsRequest { this.userId = userId; page = 3; pageSize = 2 })
        assertEquals(1, page3.readingsCount)
        assertEquals(ids.first(), page3.readingsList[0].id)
    }

    @Test
    fun `diary stats count top cards across readings`() = runBlocking {
        val userId = newUserId()
        ensureUser(userId)

        stub.saveReading(
            saveReadingRequest {
                this.userId = userId
                question = "q1"
                spreadName = "one_card"
                spread.addAll(sampleSpread("Шут"))
                interpretation = "i1"
            },
        )
        val second = stub.saveReading(
            saveReadingRequest {
                this.userId = userId
                question = "q2"
                spreadName = "one_card"
                spread.addAll(sampleSpread("Шут"))
                interpretation = "i2"
            },
        )
        stub.setReadingResonance(setReadingResonanceRequest { readingId = second.readingId; this.userId = userId; resonance = "no" })
        stub.setReadingNote(setReadingNoteRequest { readingId = second.readingId; this.userId = userId; note = "note" })

        val stats = stub.getDiaryStats(userRef { this.userId = userId })
        assertEquals(2, stats.totalReadings)
        assertEquals(1, stats.notesCount)
        assertEquals(1, stats.resonanceNo)
        assertEquals(0, stats.resonanceYes)
        assertEquals("Шут", stats.topCardsList.first().cardName)
        assertEquals(2, stats.topCardsList.first().count)
    }

    @Test
    fun `bot stats reflect lifetime counts`() = runBlocking {
        val userId = newUserId()
        ensureUser(userId)

        repeat(3) {
            stub.checkAndConsumeQuota(
                consumeQuotaRequest {
                    this.userId = userId
                    readingsLimit = 0
                    llmLimit = 0
                    isReading = true
                    unlimited = false
                },
            )
        }
        stub.saveReading(
            saveReadingRequest {
                this.userId = userId
                question = "q"
                spreadName = "one_card"
                spread.addAll(sampleSpread())
                interpretation = "i"
            },
        )

        val stats = stub.getBotStats(getBotStatsRequest { requestingUserId = userId })
        val activity = stats.usersList.single { it.userId == userId }
        assertEquals(1, activity.readingsCount)
        assertEquals(3, activity.llmRequestsCount)
        assertNotEquals(0, activity.lastSeen.seconds)
    }
}
