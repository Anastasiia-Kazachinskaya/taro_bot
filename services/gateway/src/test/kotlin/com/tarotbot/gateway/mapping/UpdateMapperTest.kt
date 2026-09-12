package com.tarotbot.gateway.mapping

import com.tarotbot.gateway.telegram.TelegramCallbackQuery
import com.tarotbot.gateway.telegram.TelegramChat
import com.tarotbot.gateway.telegram.TelegramMessage
import com.tarotbot.gateway.telegram.TelegramUpdate
import com.tarotbot.gateway.telegram.TelegramUser
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class UpdateMapperTest : FunSpec({

    test("maps a text message to an IncomingUpdate") {
        val update = TelegramUpdate(
            updateId = 42,
            message = TelegramMessage(
                messageId = 7,
                chat = TelegramChat(id = 100),
                from = TelegramUser(id = 200, username = "kate", firstName = "Kate"),
                text = "/start",
            ),
        )

        val result = UpdateMapper.toIncomingUpdate(update)

        result.shouldNotBeNull()
        result!!.updateId shouldBe 42
        result.chatId shouldBe 100
        result.userId shouldBe 200
        result.username shouldBe "kate"
        result.firstName shouldBe "Kate"
        result.messageId shouldBe 7
        result.callbackQueryId shouldBe ""
        result.textMessage shouldBe "/start"
    }

    test("maps a callback query to an IncomingUpdate with callback_query_id set") {
        val update = TelegramUpdate(
            updateId = 43,
            callbackQuery = TelegramCallbackQuery(
                id = "cb-1",
                from = TelegramUser(id = 300, username = "anya", firstName = "Anya"),
                message = TelegramMessage(messageId = 9, chat = TelegramChat(id = 101), text = null),
                data = "spread:one_card",
            ),
        )

        val result = UpdateMapper.toIncomingUpdate(update)

        result.shouldNotBeNull()
        result!!.chatId shouldBe 101
        result.userId shouldBe 300
        result.messageId shouldBe 9
        result.callbackQueryId shouldBe "cb-1"
        result.callbackData shouldBe "spread:one_card"
    }

    test("returns null for an update with neither text nor callback data") {
        val update = TelegramUpdate(
            updateId = 44,
            message = TelegramMessage(messageId = 1, chat = TelegramChat(id = 1), text = null),
        )

        UpdateMapper.toIncomingUpdate(update).shouldBeNull()
    }
})
