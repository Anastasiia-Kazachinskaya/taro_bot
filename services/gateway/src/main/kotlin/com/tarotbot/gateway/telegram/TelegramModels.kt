package com.tarotbot.gateway.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Telegram wraps every Bot API response in this envelope. */
@Serializable
data class TelegramApiResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    val description: String? = null,
)

@Serializable
data class TelegramUpdate(
    @SerialName("update_id") val updateId: Long,
    val message: TelegramMessage? = null,
    @SerialName("callback_query") val callbackQuery: TelegramCallbackQuery? = null,
)

@Serializable
data class TelegramMessage(
    @SerialName("message_id") val messageId: Long,
    val chat: TelegramChat,
    val from: TelegramUser? = null,
    val text: String? = null,
)

@Serializable
data class TelegramChat(
    val id: Long,
)

@Serializable
data class TelegramUser(
    val id: Long,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null,
)

@Serializable
data class TelegramCallbackQuery(
    val id: String,
    val from: TelegramUser,
    val message: TelegramMessage? = null,
    val data: String? = null,
)

@Serializable
data class TelegramReplyMarkup(
    @SerialName("inline_keyboard") val inlineKeyboard: List<List<TelegramInlineButton>>,
)

@Serializable
data class TelegramInlineButton(
    val text: String,
    @SerialName("callback_data") val callbackData: String,
)
