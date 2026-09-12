package com.tarotbot.gateway

import com.tarotbot.gateway.mapping.ResponseRelay
import com.tarotbot.gateway.mapping.UpdateMapper
import com.tarotbot.gateway.telegram.TelegramClient
import com.tarotbot.gateway.telegram.TelegramUpdate
import com.tarotbot.proto.gateway.MasterServiceGrpcKt
import kotlinx.coroutines.delay

/**
 * Long-polls Telegram's `getUpdates`, maps each update to a [com.tarotbot.proto.gateway.IncomingUpdate],
 * calls Master, and relays the response back. There is exactly one instance of this loop for the whole
 * gateway process — Telegram's `getUpdates` offset is a single exclusive cursor, so running more than
 * one poller would race over it.
 */
class PollingLoop(
    private val telegram: TelegramClient,
    private val masterStub: MasterServiceGrpcKt.MasterServiceCoroutineStub,
) {
    private var offset: Long = 0

    suspend fun run() {
        while (true) {
            val updates = try {
                telegram.getUpdates(offset, timeoutSeconds = 30)
            } catch (e: Exception) {
                System.err.println("getUpdates failed: ${e.message}")
                delay(1000)
                emptyList()
            }
            offset = processBatch(updates)
        }
    }

    /** Dispatches every update in [updates] and returns the offset the next poll should use. */
    suspend fun processBatch(updates: List<TelegramUpdate>): Long {
        var newOffset = offset
        for (update in updates) {
            newOffset = maxOf(newOffset, update.updateId + 1)

            val incoming = UpdateMapper.toIncomingUpdate(update) ?: continue
            try {
                val response = masterStub.handleUpdate(incoming)
                ResponseRelay.relay(incoming.chatId, incoming.callbackQueryId, response, telegram)
            } catch (e: Exception) {
                System.err.println("Failed to handle update ${update.updateId}: ${e.message}")
            }
        }
        return newOffset
    }
}
