package com.tarotbot.masterbackend.orchestration

import com.google.protobuf.ByteString
import com.tarotbot.proto.common.InlineKeyboard
import com.tarotbot.proto.gateway.ResponseAction
import com.tarotbot.proto.gateway.answerCallbackAction
import com.tarotbot.proto.gateway.photoAction
import com.tarotbot.proto.gateway.responseAction
import com.tarotbot.proto.gateway.textAction

/** Small builders for [ResponseAction] — kept separate from [ResponseFormatter] (text only) and [KeyboardBuilder]. */
object ActionBuilder {

    fun text(body: String, keyboard: InlineKeyboard? = null): ResponseAction {
        val t = body
        val kb = keyboard
        return responseAction {
            text = textAction {
                this.text = t
                kb?.let { this.keyboard = it }
            }
        }
    }

    fun photo(jpeg: ByteArray, caption: String, keyboard: InlineKeyboard? = null): ResponseAction {
        val bytes = jpeg
        val cap = caption
        val kb = keyboard
        return responseAction {
            photo = photoAction {
                this.jpeg = ByteString.copyFrom(bytes)
                this.caption = cap
                kb?.let { this.keyboard = it }
            }
        }
    }

    fun answerCallback(text: String = "", showAlert: Boolean = false): ResponseAction {
        val t = text
        val alert = showAlert
        return responseAction {
            answerCallback = answerCallbackAction {
                this.text = t
                this.showAlert = alert
            }
        }
    }
}
