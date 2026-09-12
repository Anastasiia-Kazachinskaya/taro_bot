package com.tarotbot.gateway.telegram

import com.tarotbot.proto.common.InlineKeyboard
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class TelegramKeyboardMapperTest : FunSpec({

    fun button(text: String, callbackData: String): InlineKeyboard.Button =
        InlineKeyboard.Button.newBuilder().setText(text).setCallbackData(callbackData).build()

    fun row(vararg buttons: InlineKeyboard.Button): InlineKeyboard.Row =
        InlineKeyboard.Row.newBuilder().addAllButtons(buttons.toList()).build()

    fun keyboard(vararg rows: InlineKeyboard.Row): InlineKeyboard =
        InlineKeyboard.newBuilder().addAllRows(rows.toList()).build()

    test("maps rows and buttons in order") {
        val proto = keyboard(
            row(button("Одна карта", "spread:one_card"), button("Три карты", "spread:three_cards")),
        )

        val markup = TelegramKeyboardMapper.toReplyMarkup(proto)

        markup.shouldNotBeNull()
        markup!!.inlineKeyboard shouldBe listOf(
            listOf(
                TelegramInlineButton("Одна карта", "spread:one_card"),
                TelegramInlineButton("Три карты", "spread:three_cards"),
            ),
        )
    }

    test("returns null for an empty keyboard") {
        TelegramKeyboardMapper.toReplyMarkup(InlineKeyboard.getDefaultInstance()).shouldBeNull()
    }

    test("returns null for a null keyboard") {
        TelegramKeyboardMapper.toReplyMarkup(null).shouldBeNull()
    }
})
