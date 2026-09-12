package com.tarotbot.gateway.telegram

import com.tarotbot.proto.common.InlineKeyboard
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** What the gateway needs from the Telegram Bot API — kept small and mockable for tests. */
interface TelegramClient {
    suspend fun getUpdates(offset: Long, timeoutSeconds: Int): List<TelegramUpdate>
    suspend fun sendMessage(chatId: Long, text: String, keyboard: InlineKeyboard?, html: Boolean)
    suspend fun sendPhoto(chatId: Long, jpeg: ByteArray, caption: String, keyboard: InlineKeyboard?, html: Boolean)
    suspend fun editMessageText(chatId: Long, messageId: Long, text: String, keyboard: InlineKeyboard?)
    suspend fun answerCallbackQuery(callbackQueryId: String, text: String, showAlert: Boolean)
}

@Serializable
private data class SendMessageRequest(
    @SerialName("chat_id") val chatId: Long,
    val text: String,
    @SerialName("reply_markup") val replyMarkup: TelegramReplyMarkup? = null,
    @SerialName("parse_mode") val parseMode: String? = null,
)

@Serializable
private data class EditMessageTextRequest(
    @SerialName("chat_id") val chatId: Long,
    @SerialName("message_id") val messageId: Long,
    val text: String,
    @SerialName("reply_markup") val replyMarkup: TelegramReplyMarkup? = null,
)

@Serializable
private data class AnswerCallbackQueryRequest(
    @SerialName("callback_query_id") val callbackQueryId: String,
    val text: String,
    @SerialName("show_alert") val showAlert: Boolean,
)

/** Real Bot API client — talks directly to `api.telegram.org`, no Telegram SDK. */
class TelegramHttpClient(
    private val botToken: String,
    private val httpClient: HttpClient = defaultHttpClient(),
) : TelegramClient {

    private val baseUrl = "https://api.telegram.org/bot$botToken"
    private val jsonCodec = Json { ignoreUnknownKeys = true }

    override suspend fun getUpdates(offset: Long, timeoutSeconds: Int): List<TelegramUpdate> {
        val response: TelegramApiResponse<List<TelegramUpdate>> =
            httpClient.get("$baseUrl/getUpdates") {
                parameter("offset", offset)
                parameter("timeout", timeoutSeconds)
                timeout { requestTimeoutMillis = (timeoutSeconds + 10) * 1000L }
            }.body()
        return response.result.orEmpty()
    }

    override suspend fun sendMessage(chatId: Long, text: String, keyboard: InlineKeyboard?, html: Boolean) {
        val body = SendMessageRequest(
            chatId = chatId,
            text = text,
            replyMarkup = TelegramKeyboardMapper.toReplyMarkup(keyboard),
            parseMode = if (html) "HTML" else null,
        )
        httpClient.post("$baseUrl/sendMessage") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    override suspend fun sendPhoto(chatId: Long, jpeg: ByteArray, caption: String, keyboard: InlineKeyboard?, html: Boolean) {
        val replyMarkup = TelegramKeyboardMapper.toReplyMarkup(keyboard)
        val photoBytes = jpeg
        httpClient.submitFormWithBinaryData(
            url = "$baseUrl/sendPhoto",
            formData = formData {
                append("chat_id", chatId.toString())
                append("caption", caption)
                if (html) append("parse_mode", "HTML")
                replyMarkup?.let { append("reply_markup", jsonCodec.encodeToString(it)) }
                append(
                    "photo",
                    photoBytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=spread.jpg")
                    },
                )
            },
        )
    }

    override suspend fun editMessageText(chatId: Long, messageId: Long, text: String, keyboard: InlineKeyboard?) {
        val body = EditMessageTextRequest(
            chatId = chatId,
            messageId = messageId,
            text = text,
            replyMarkup = TelegramKeyboardMapper.toReplyMarkup(keyboard),
        )
        httpClient.post("$baseUrl/editMessageText") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    override suspend fun answerCallbackQuery(callbackQueryId: String, text: String, showAlert: Boolean) {
        val body = AnswerCallbackQueryRequest(callbackQueryId, text, showAlert)
        httpClient.post("$baseUrl/answerCallbackQuery") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    companion object {
        fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) { requestTimeoutMillis = 40_000 }
            install(HttpRequestRetry) {
                maxRetries = 3
                retryOnServerErrors()
                retryOnExceptionIf { _, cause -> cause is java.io.IOException }
                exponentialDelay()
            }
        }
    }
}
