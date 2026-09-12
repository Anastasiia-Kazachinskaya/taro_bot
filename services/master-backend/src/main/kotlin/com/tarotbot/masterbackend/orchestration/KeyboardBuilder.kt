package com.tarotbot.masterbackend.orchestration

import com.tarotbot.domain.tarot.Spread
import com.tarotbot.proto.common.InlineKeyboard

/**
 * Builds [InlineKeyboard] protos. Uses the plain Java-style builder API (not the
 * Kotlin proto DSL) since nested-message DSL entry points are easy to get wrong —
 * `newBuilder()` is unambiguous and this is called from few, simple call sites.
 */
object KeyboardBuilder {

    private fun button(text: String, callbackData: String): InlineKeyboard.Button =
        InlineKeyboard.Button.newBuilder().setText(text).setCallbackData(callbackData).build()

    private fun row(vararg buttons: InlineKeyboard.Button): InlineKeyboard.Row =
        InlineKeyboard.Row.newBuilder().addAllButtons(buttons.toList()).build()

    private fun keyboard(vararg rows: InlineKeyboard.Row): InlineKeyboard =
        InlineKeyboard.newBuilder().addAllRows(rows.toList()).build()

    fun mainMenu(): InlineKeyboard = keyboard(
        row(button("🔮 Новый расклад", "new_reading")),
        row(button("📖 История", "history"), button("⚙️ Настройки", "settings")),
        row(button("ℹ️ О боте", "about")),
    )

    fun spreadPicker(): InlineKeyboard = keyboard(
        *Spread.entries.map { row(button(it.displayName, "spread:${it.id}")) }.toTypedArray(),
    )

    fun reversedChoice(): InlineKeyboard = keyboard(
        row(button("Да, с перевёрнутыми", "reversed:yes"), button("Нет, только прямые", "reversed:no")),
    )

    fun retry(): InlineKeyboard = keyboard(row(button("🔁 Повторить", "retry_interpretation")))

    fun postReading(readingId: Long): InlineKeyboard = keyboard(
        row(button("🃏 Уточняющая карта", "draw_clarifying_card")),
        row(button("📝 Добавить заметку", "note:$readingId")),
        row(
            button("✅ Сбылось", "resonance:$readingId:yes"),
            button("❌ Не сбылось", "resonance:$readingId:no"),
        ),
        row(button("🏠 Главное меню", "main_menu")),
    )

    fun historyPage(readingIds: List<Pair<Long, String>>, page: Int, totalPages: Int): InlineKeyboard {
        val readingRows = readingIds.map { (id, label) -> row(button(label, "history_reading:$id")) }
        val navButtons = buildList {
            if (page > 1) add(button("« Назад", "history_page:${page - 1}"))
            if (page < totalPages) add(button("Вперёд »", "history_page:${page + 1}"))
        }
        val navRow = if (navButtons.isNotEmpty()) listOf(row(*navButtons.toTypedArray())) else emptyList()
        return keyboard(*(readingRows + navRow + listOf(row(button("🏠 Главное меню", "main_menu")))).toTypedArray())
    }

    fun settingsMenu(): InlineKeyboard = keyboard(
        row(
            button("Всегда с перевёрнутыми", "settings:reversed:yes"),
            button("Только прямые", "settings:reversed:no"),
        ),
        row(button("Спрашивать каждый раз", "settings:reversed:ask")),
    )
}
