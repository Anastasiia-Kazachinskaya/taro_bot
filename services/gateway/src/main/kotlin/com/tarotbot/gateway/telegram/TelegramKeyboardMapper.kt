package com.tarotbot.gateway.telegram

import com.tarotbot.proto.common.InlineKeyboard

/** Maps our proto [InlineKeyboard] to Telegram's `reply_markup` JSON shape. */
object TelegramKeyboardMapper {

    fun toReplyMarkup(keyboard: InlineKeyboard?): TelegramReplyMarkup? {
        if (keyboard == null || keyboard.rowsList.isEmpty()) return null
        return TelegramReplyMarkup(
            inlineKeyboard = keyboard.rowsList.map { row ->
                row.buttonsList.map { button -> TelegramInlineButton(button.text, button.callbackData) }
            },
        )
    }
}
