package com.tarotbot.gateway.mapping

import com.tarotbot.gateway.telegram.TelegramUpdate
import com.tarotbot.proto.gateway.IncomingUpdate
import com.tarotbot.proto.gateway.incomingUpdate

/** Pure mapping from a raw Telegram update to our proto [IncomingUpdate]. */
object UpdateMapper {

    fun toIncomingUpdate(update: TelegramUpdate): IncomingUpdate? {
        val message = update.message
        val callback = update.callbackQuery

        return when {
            message?.text != null -> {
                val chat = message.chat.id
                val user = message.from
                val msgId = message.messageId
                val text = message.text
                val uid = update.updateId
                incomingUpdate {
                    updateId = uid
                    chatId = chat
                    userId = user?.id ?: 0
                    username = user?.username ?: ""
                    firstName = user?.firstName ?: ""
                    messageId = msgId
                    callbackQueryId = ""
                    textMessage = text
                }
            }

            callback?.data != null -> {
                val chat = callback.message?.chat?.id ?: 0
                val msgId = callback.message?.messageId ?: 0
                val user = callback.from
                val data = callback.data
                val queryId = callback.id
                val uid = update.updateId
                incomingUpdate {
                    updateId = uid
                    chatId = chat
                    userId = user.id
                    username = user.username ?: ""
                    firstName = user.firstName ?: ""
                    messageId = msgId
                    callbackQueryId = queryId
                    callbackData = data
                }
            }

            else -> null
        }
    }
}
