package com.tarotbot.aibackend

import com.tarotbot.aibackend.config.ProviderRegistry
import com.tarotbot.proto.ai.AiServiceGrpcKt
import com.tarotbot.proto.ai.interpretSpreadRequest
import com.tarotbot.proto.common.card
import com.tarotbot.proto.common.spreadCard
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * Drives [AiServiceImpl] end to end (real prompt building, real provider selection
 * and fallback logic) over an in-process gRPC server, with the actual DeepSeek/GigaChat
 * HTTP calls replaced by a [MockEngine] — no real network access.
 */
class AiServiceFallbackIntegrationTest {

    private fun successBody(text: String, finishReason: String = "stop") =
        """{"choices":[{"message":{"role":"assistant","content":"$text"},"finish_reason":"$finishReason"}]}"""

    private fun writeConfig(): java.nio.file.Path {
        val dir = Files.createTempDirectory("ai-backend-fallback-test")
        val path = dir.resolve("providers.yaml")
        Files.writeString(
            path,
            """
            primary: deepseek
            fallback: gigachat
            providers:
              deepseek:
                baseUrl: "https://api.deepseek.com/v1"
                model: "deepseek-chat"
                apiKeyEnv: "TEST_DEEPSEEK_KEY"
              gigachat:
                baseUrl: "https://foundation-models.api.cloud.ru/v1"
                model: "ai-sage/GigaChat3.5-432B-A28B"
                apiKeyEnv: "TEST_GIGACHAT_KEY"
            """.trimIndent(),
        )
        return path
    }

    private fun requestFixture() = interpretSpreadRequest {
        userId = 1
        question = "Что меня ждёт?"
        spreadName = "one_card"
        cards += spreadCard {
            positionIndex = 1
            positionLabel = "Главная энергия ситуации"
            card = card {
                name = "Шут"
                arcana = "major"
                suit = ""
                reversed = false
                meaning = "Начало нового цикла"
            }
        }
    }

    private fun startServer(engine: MockEngine): Triple<Server, ManagedChannel, AiServiceGrpcKt.AiServiceCoroutineStub> {
        val registry = ProviderRegistry(writeConfig(), engine = engine, env = emptyMap(), startWatcher = false)
        val serverName = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder.forName(serverName).directExecutor()
            .addService(AiServiceImpl(registry)).build().start()
        val channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        return Triple(server, channel, AiServiceGrpcKt.AiServiceCoroutineStub(channel))
    }

    @Test
    fun `primary succeeds`() = runBlocking {
        val engine = MockEngine {
            respond(successBody("primary response"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val (server, channel, stub) = startServer(engine)
        try {
            val response = stub.interpretSpread(requestFixture())
            assertEquals("deepseek", response.providerUsed)
            assertEquals("primary response", response.text)
        } finally {
            channel.shutdownNow()
            server.shutdownNow()
        }
    }

    @Test
    fun `primary fails, fallback succeeds`() = runBlocking {
        val engine = MockEngine { request ->
            if (request.url.host.contains("deepseek")) {
                respond("server error", HttpStatusCode.InternalServerError)
            } else {
                respond(
                    successBody("fallback response"),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val (server, channel, stub) = startServer(engine)
        try {
            val response = stub.interpretSpread(requestFixture())
            assertEquals("gigachat", response.providerUsed)
            assertEquals("fallback response", response.text)
        } finally {
            channel.shutdownNow()
            server.shutdownNow()
        }
    }

    @Test
    fun `both providers fail`() = runBlocking {
        val engine = MockEngine { respond("server error", HttpStatusCode.InternalServerError) }
        val (server, channel, stub) = startServer(engine)
        try {
            val exception = assertThrows(StatusException::class.java) {
                runBlocking { stub.interpretSpread(requestFixture()) }
            }
            assertEquals(Status.Code.UNAVAILABLE, exception.status.code)
        } finally {
            channel.shutdownNow()
            server.shutdownNow()
        }
    }
}
