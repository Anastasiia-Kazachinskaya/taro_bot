package com.tarotbot.gateway.mapping

import com.google.protobuf.ByteString
import com.tarotbot.gateway.telegram.TelegramClient
import com.tarotbot.proto.common.InlineKeyboard
import com.tarotbot.proto.gateway.answerCallbackAction
import com.tarotbot.proto.gateway.editMessageAction
import com.tarotbot.proto.gateway.outgoingResponse
import com.tarotbot.proto.gateway.photoAction
import com.tarotbot.proto.gateway.responseAction
import com.tarotbot.proto.gateway.textAction
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class ResponseRelayTest : FunSpec({

    test("dispatches each action type to the matching Telegram call") {
        val telegram = mockk<TelegramClient>(relaxed = true)
        coEvery { telegram.sendMessage(any(), any(), any(), any()) } returns Unit
        coEvery { telegram.sendPhoto(any(), any(), any(), any(), any()) } returns Unit
        coEvery { telegram.editMessageText(any(), any(), any(), any()) } returns Unit
        coEvery { telegram.answerCallbackQuery(any(), any(), any()) } returns Unit

        val jpegBytes = byteArrayOf(1, 2, 3)
        val response = outgoingResponse {
            actions.add(responseAction { text = textAction { text = "hello"; html = true } })
            actions.add(responseAction { photo = photoAction { jpeg = ByteString.copyFrom(jpegBytes); caption = "cap" } })
            actions.add(responseAction { edit = editMessageAction { messageId = 5; text = "edited" } })
            actions.add(responseAction { answerCallback = answerCallbackAction { text = "ok"; showAlert = false } })
        }

        ResponseRelay.relay(chatId = 100, callbackQueryId = "cb-1", response = response, telegram = telegram)

        coVerify(exactly = 1) { telegram.sendMessage(100, "hello", null, true) }
        coVerify(exactly = 1) { telegram.sendPhoto(100, jpegBytes, "cap", null, false) }
        coVerify(exactly = 1) { telegram.editMessageText(100, 5, "edited", null) }
        coVerify(exactly = 1) { telegram.answerCallbackQuery("cb-1", "ok", false) }
    }

    test("skips AnswerCallbackAction when there is no callback_query_id") {
        val telegram = mockk<TelegramClient>(relaxed = true)

        val response = outgoingResponse {
            actions.add(responseAction { answerCallback = answerCallbackAction { text = "ok" } })
        }

        ResponseRelay.relay(chatId = 100, callbackQueryId = "", response = response, telegram = telegram)

        coVerify(exactly = 0) { telegram.answerCallbackQuery(any(), any(), any()) }
    }

    test("passes a non-empty keyboard through unchanged") {
        val telegram = mockk<TelegramClient>(relaxed = true)
        val keyboard = InlineKeyboard.newBuilder()
            .addRows(
                InlineKeyboard.Row.newBuilder().addButtons(
                    InlineKeyboard.Button.newBuilder().setText("A").setCallbackData("a"),
                ),
            )
            .build()

        val response = outgoingResponse {
            actions.add(responseAction { text = textAction { text = "hi"; this.keyboard = keyboard } })
        }

        ResponseRelay.relay(chatId = 1, callbackQueryId = "", response = response, telegram = telegram)

        coVerify(exactly = 1) { telegram.sendMessage(1, "hi", keyboard, false) }
    }
})
