package com.tarotbot.masterbackend.orchestration

import com.tarotbot.domain.tarot.DeckCard
import com.tarotbot.domain.tarot.Spread
import com.tarotbot.proto.db.BotStats
import com.tarotbot.proto.db.DiaryStats
import com.tarotbot.proto.db.Reading
import com.tarotbot.proto.db.UsageSummary

/** Builds the Russian-language reply text for every effect. Pure formatting, no I/O. */
object ResponseFormatter {

    fun mainMenu(): String = "Привет! Я персональный AI-таролог. Что хочешь сделать?"

    fun spreadPicker(): String = "Выбери расклад:"

    fun reversedPrompt(spread: Spread): String =
        "Расклад «${spread.displayName}». Учитывать перевёрнутые карты в этом раскладе?"

    fun questionPrompt(spread: Spread): String =
        "Расклад «${spread.displayName}». Сформулируй свой вопрос — на что хочешь получить ответ?"

    fun quotaExceeded(reason: String): String = when (reason) {
        "readings" -> "На сегодня лимит новых расклада исчерпан. Загляни завтра — счётчик обновится."
        "llm" -> "На сегодня лимит обращений к ИИ исчерпан. Загляни завтра — счётчик обновится."
        else -> "На сегодня лимит исчерпан. Загляни завтра — счётчик обновится."
    }

    fun retryOffer(): String = "Не получилось получить интерпретацию — сервис ИИ временно недоступен. Попробовать ещё раз?"

    fun followUpAnswer(text: String): String = text

    fun clarifyingCardAnswer(cardName: String, reversed: Boolean, text: String): String {
        val orientation = if (reversed) "перевёрнутая" else "прямая"
        return "Уточняющая карта: $cardName ($orientation)\n\n$text"
    }

    fun notePrompt(): String = "Напиши текст заметки к этому раскладу следующим сообщением."

    fun noteSaved(): String = "Заметка сохранена."

    fun resonanceSaved(value: String): String = when (value) {
        "yes" -> "Отмечено: расклад сбылся."
        "no" -> "Отмечено: расклад не сбылся."
        else -> "Отметка снята."
    }

    fun historyEmpty(): String = "Пока нет сохранённых раскладов. Начни новый через /new."

    fun historyHeader(page: Int, totalPages: Int, totalCount: Int): String =
        "История раскладов (страница $page из $totalPages, всего $totalCount):"

    fun historyEntryLabel(reading: Reading): String {
        val date = reading.createdAt.seconds.let { java.time.Instant.ofEpochSecond(it) }
        val shortQuestion = reading.question.take(30).let { if (reading.question.length > 30) "$it…" else it }
        return "${reading.spreadName}: $shortQuestion ($date)"
    }

    fun readingDetail(reading: Reading): String = buildString {
        appendLine("Расклад: ${reading.spreadName}")
        appendLine("Вопрос: ${reading.question}")
        appendLine()
        appendLine(reading.interpretation)
        if (reading.note.isNotBlank()) {
            appendLine()
            appendLine("Заметка: ${reading.note}")
        }
        if (reading.resonance.isNotBlank()) {
            appendLine()
            appendLine(if (reading.resonance == "yes") "Отметка: сбылось" else "Отметка: не сбылось")
        }
    }.trimEnd()

    fun statsSummary(diary: DiaryStats, usage: UsageSummary, readingsLimit: Int, llmLimit: Int): String = buildString {
        appendLine("Дневник:")
        appendLine("Всего раскладов: ${diary.totalReadings}")
        appendLine("С заметками: ${diary.notesCount}")
        appendLine("Сбылось: ${diary.resonanceYes}, не сбылось: ${diary.resonanceNo}")
        if (diary.topCardsCount > 0) {
            val top = diary.topCardsList.joinToString(", ") { "${it.cardName} (${it.count})" }
            appendLine("Часто выпадающие карты: $top")
        }
        appendLine()
        appendLine("Сегодня:")
        val readingsLimitText = if (readingsLimit > 0) "${usage.readingsUsed}/$readingsLimit" else "${usage.readingsUsed} (без лимита)"
        val llmLimitText = if (llmLimit > 0) "${usage.llmUsed}/$llmLimit" else "${usage.llmUsed} (без лимита)"
        appendLine("Раскладов: $readingsLimitText")
        append("Обращений к ИИ: $llmLimitText")
    }

    fun adminStats(stats: BotStats): String = buildString {
        appendLine("Всего пользователей: ${stats.totalUsers}")
        appendLine()
        stats.usersList.sortedByDescending { it.readingsCount }.forEach { u ->
            val name = u.username.ifBlank { u.userId.toString() }
            appendLine("$name — раскладов: ${u.readingsCount}, обращений к ИИ: ${u.llmRequestsCount}")
        }
    }.trimEnd()

    fun adminAccessDenied(): String = "Команда доступна только администраторам."

    fun about(): String = "Персональный AI-таролог на картах Уэйта-Смит. Расклады, дневник, история и уточняющие вопросы."

    fun help(): String = "/new — новый расклад\n/card <название> — значение карты\n/history — история\n/stats — дневник\n/settings — настройки\n/reset — сначала"

    fun settingsMenu(): String = "Настройки: как поступать с перевёрнутыми картами?"

    fun reversedPreferenceSaved(value: String): String = when (value) {
        "yes" -> "Готово: буду учитывать перевёрнутые карты всегда."
        "no" -> "Готово: буду использовать только прямые карты."
        else -> "Готово: буду спрашивать об этом при каждом новом раскладе."
    }

    fun cardLookupNoMatch(query: String): String = "Не нашла карту по запросу «$query». Попробуй другое название."

    fun cardLookupAmbiguous(query: String, matches: List<DeckCard>): String =
        "Запрос «$query» подходит нескольким картам: ${matches.joinToString(", ") { it.name }}. Уточни название."

    fun cardLookupResult(card: DeckCard): String = buildString {
        appendLine(card.name)
        appendLine()
        appendLine("Прямое значение: ${card.uprightMeaning}")
        appendLine()
        append("Перевёрнутое значение: ${card.reversedMeaning}")
    }

    fun genericError(message: String): String = message
}
