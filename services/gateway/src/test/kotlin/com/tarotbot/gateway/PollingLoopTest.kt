package com.tarotbot.gateway

import com.tarotbot.gateway.telegram.TelegramCallbackQuery
import com.tarotbot.gateway.telegram.TelegramChat
import com.tarotbot.gateway.telegram.TelegramClient
import com.tarotbot.gateway.telegram.TelegramMessage
import com.tarotbot.gateway.telegram.TelegramUpdate
import com.tarotbot.gateway.telegram.TelegramUser
import com.tarotbot.proto.gateway.MasterServiceGrpcKt
import com.tarotbot.proto.gateway.outgoingResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class PollingLoopTest : FunSpec({

    test("dispatches every handleable update and skips one it can't map") {
        val telegram = mockk<TelegramClient>(relaxed = true)
        val masterStub = mockk<MasterServiceGrpcKt.MasterServiceCoroutineStub>()
        coEvery { masterStub.handleUpdate(any(), any()) } returns outgoingResponse { }

        val textUpdate = TelegramUpdate(
            updateId = 1,
            message = TelegramMessage(messageId = 1, chat = TelegramChat(1), from = TelegramUser(1), text = "/start"),
        )
        val callbackUpdate = TelegramUpdate(
            updateId = 2,
            callbackQuery = TelegramCallbackQuery(
                id = "cb",
                from = TelegramUser(2),
                message = TelegramMessage(messageId = 2, chat = TelegramChat(2)),
                data = "main_menu",
            ),
        )
        val unhandledUpdate = TelegramUpdate(
            updateId = 3,
            message = TelegramMessage(messageId = 3, chat = TelegramChat(3), text = null),
        )

        val loop = PollingLoop(telegram, masterStub)
        val newOffset = loop.processBatch(listOf(textUpdate, callbackUpdate, unhandledUpdate))

        newOffset shouldBe 4
        coVerify(exactly = 2) { masterStub.handleUpdate(any(), any()) }
    }

    test("advances the offset even for updates it can't handle") {
        val telegram = mockk<TelegramClient>(relaxed = true)
        val masterStub = mockk<MasterServiceGrpcKt.MasterServiceCoroutineStub>()

        val unhandledUpdate = TelegramUpdate(
            updateId = 9,
            message = TelegramMessage(messageId = 1, chat = TelegramChat(1), text = null),
        )

        val loop = PollingLoop(telegram, masterStub)
        val newOffset = loop.processBatch(listOf(unhandledUpdate))

        newOffset shouldBe 10
        coVerify(exactly = 0) { masterStub.handleUpdate(any(), any()) }
    }

    test("an empty batch leaves the offset unchanged") {
        val telegram = mockk<TelegramClient>(relaxed = true)
        val masterStub = mockk<MasterServiceGrpcKt.MasterServiceCoroutineStub>()

        val loop = PollingLoop(telegram, masterStub)
        loop.processBatch(emptyList()) shouldBe 0
    }
})
