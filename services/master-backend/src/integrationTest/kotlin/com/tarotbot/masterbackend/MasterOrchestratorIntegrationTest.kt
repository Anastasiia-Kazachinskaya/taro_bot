package com.tarotbot.masterbackend

import com.tarotbot.masterbackend.orchestration.AiClient
import com.tarotbot.masterbackend.orchestration.DbClient
import com.tarotbot.masterbackend.orchestration.MasterConfig
import com.tarotbot.masterbackend.orchestration.MasterOrchestrator
import com.tarotbot.proto.ai.AiServiceGrpcKt
import com.tarotbot.proto.db.DbServiceGrpcKt
import com.tarotbot.proto.gateway.IncomingUpdate
import com.tarotbot.proto.gateway.OutgoingResponse
import com.tarotbot.proto.gateway.ResponseAction
import com.tarotbot.proto.gateway.incomingUpdate
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Drives [MasterOrchestrator] through full conversations against hand-written in-memory
 * stand-ins for db-backend/ai-backend. A **fresh** [MasterOrchestrator] (and fresh
 * [DbClient]/[AiClient] wrapping it) is built before every single call — never reused
 * across turns — which is the concrete proof that any of Master's 3 real replicas
 * could equally have handled the next message: all that matters is what's in the fakes'
 * storage (standing in for real Postgres), never anything held by the orchestrator itself.
 */
class MasterOrchestratorIntegrationTest {

    private lateinit var dbServer: Server
    private lateinit var aiServer: Server
    private lateinit var dbChannel: ManagedChannel
    private lateinit var aiChannel: ManagedChannel
    private lateinit var fakeDb: FakeDbService
    private lateinit var fakeAi: FakeAiService

    @BeforeEach
    fun setup() {
        fakeDb = FakeDbService()
        fakeAi = FakeAiService()

        val dbServerName = InProcessServerBuilder.generateName()
        dbServer = InProcessServerBuilder.forName(dbServerName).directExecutor().addService(fakeDb).build().start()
        dbChannel = InProcessChannelBuilder.forName(dbServerName).directExecutor().build()

        val aiServerName = InProcessServerBuilder.generateName()
        aiServer = InProcessServerBuilder.forName(aiServerName).directExecutor().addService(fakeAi).build().start()
        aiChannel = InProcessChannelBuilder.forName(aiServerName).directExecutor().build()
    }

    @AfterEach
    fun tearDown() {
        dbChannel.shutdownNow()
        aiChannel.shutdownNow()
        dbServer.shutdownNow()
        aiServer.shutdownNow()
    }

    /** Builds a brand-new orchestrator — see the class doc: never reused across calls in this test. */
    private fun freshOrchestrator(): MasterOrchestrator = MasterOrchestrator(
        DbClient(DbServiceGrpcKt.DbServiceCoroutineStub(dbChannel)),
        AiClient(AiServiceGrpcKt.AiServiceCoroutineStub(aiChannel)),
        MasterConfig(readingsLimit = 10, llmLimit = 30, unlimitedUserIds = emptySet(), adminUserIds = emptySet()),
    )

    private fun send(userId: Long, text: String? = null, callbackData: String? = null): OutgoingResponse = runBlocking {
        val update = incomingUpdate {
            this.userId = userId
            chatId = userId
            username = "tester"
            firstName = "Test"
            text?.let { this.textMessage = it }
            callbackData?.let { this.callbackData = it }
        }
        freshOrchestrator().handle(update)
    }

    private fun OutgoingResponse.allText(): String =
        actionsList.filter { it.actionCase == ResponseAction.ActionCase.TEXT }.joinToString("\n") { it.text.text }

    private fun OutgoingResponse.hasPhoto(): Boolean = actionsList.any { it.actionCase == ResponseAction.ActionCase.PHOTO }

    @Test
    fun `full multi-turn conversation produces a saved reading and an appended follow-up`() {
        val userId = 1001L

        val menu = send(userId, text = "/start")
        assertTrue(menu.allText().isNotBlank())

        send(userId, text = "/new")
        send(userId, callbackData = "spread:one_card")
        send(userId, callbackData = "reversed:yes")

        val readingResponse = send(userId, text = "Как пройдёт этот день?")
        assertTrue(readingResponse.hasPhoto(), "a fresh reading should include the rendered spread image")
        assertEquals(1, fakeDb.saveReadingCallCount)
        assertEquals(1, fakeAi.interpretSpreadCallCount)

        val readingId = fakeDb.readings.keys.single()

        val followUpResponse = send(userId, text = "А что если карта перевёрнута?")
        assertTrue(followUpResponse.allText().contains("Ответ на уточняющий вопрос"))
        assertEquals(1, fakeDb.readings[readingId]!!.followups.size)
        assertEquals(1, fakeDb.saveReadingCallCount, "a follow-up must not create a second reading")
    }

    @Test
    fun `AI failure offers a retry that reuses the same drawn cards without a new draw`() {
        val userId = 1002L
        fakeAi.failNextInterpretSpreadCalls = 1

        send(userId, text = "/new")
        send(userId, callbackData = "spread:three_cards")
        send(userId, callbackData = "reversed:no")
        val failedResponse = send(userId, text = "Что меня ждёт?")

        assertEquals(0, fakeDb.saveReadingCallCount, "nothing is saved while the AI call is failing")
        assertEquals(1, fakeAi.interpretSpreadCallCount)
        assertTrue(failedResponse.allText().isNotBlank())

        val retryResponse = send(userId, callbackData = "retry_interpretation")

        assertEquals(2, fakeAi.interpretSpreadCallCount)
        assertEquals(1, fakeDb.saveReadingCallCount, "exactly one reading is saved for the whole retry sequence")
        assertEquals(
            fakeAi.interpretSpreadCardsSeen[0].map { it.card.name },
            fakeAi.interpretSpreadCardsSeen[1].map { it.card.name },
            "retry must reuse the exact same drawn cards, not redraw",
        )
        assertTrue(retryResponse.allText().isNotBlank())
    }

    @Test
    fun `a blocked quota stops the reading before any AI call is made`() {
        val userId = 1003L
        fakeDb.quotaAllowed = false
        fakeDb.quotaBlockedReason = "readings"

        send(userId, text = "/new")
        send(userId, callbackData = "spread:one_card")
        send(userId, callbackData = "reversed:yes")
        val blockedResponse = send(userId, text = "Стоит ли ждать перемен?")

        assertEquals(0, fakeAi.interpretSpreadCallCount, "a blocked quota must never reach the AI backend")
        assertEquals(0, fakeDb.saveReadingCallCount)
        assertFalse(blockedResponse.allText().isBlank())
    }
}
