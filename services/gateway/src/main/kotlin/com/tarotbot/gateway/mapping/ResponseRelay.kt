package com.tarotbot.gateway.mapping

import com.tarotbot.gateway.telegram.TelegramClient
import com.tarotbot.proto.common.InlineKeyboard
import com.tarotbot.proto.gateway.OutgoingResponse

/** Replays a HandleUpdate response back to Telegram, one action at a time. */
object ResponseRelay {

    suspend fun relay(chatId: Long, callbackQueryId: String, response: OutgoingResponse, telegram: TelegramClient) {
        for (action in response.actionsList) {
            when {
                action.hasText() -> {
                    val body = action.text
                    telegram.sendMessage(chatId, body.text, body.keyboard.orNullIfEmpty(), body.html)
                }

                action.hasPhoto() -> {
                    val body = action.photo
                    telegram.sendPhoto(chatId, body.jpeg.toByteArray(), body.caption, body.keyboard.orNullIfEmpty(), body.html)
                }

                action.hasEdit() -> {
                    val body = action.edit
                    telegram.editMessageText(chatId, body.messageId, body.text, body.keyboard.orNullIfEmpty())
                }

                action.hasAnswerCallback() -> {
                    if (callbackQueryId.isNotBlank()) {
                        val body = action.answerCallback
                        telegram.answerCallbackQuery(callbackQueryId, body.text, body.showAlert)
                    }
                }
            }
        }
    }

    private fun InlineKeyboard.orNullIfEmpty(): InlineKeyboard? = if (rowsList.isEmpty()) null else this
}
