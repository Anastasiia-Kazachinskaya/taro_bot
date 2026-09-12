package com.tarotbot.domain.tarot

import kotlin.random.Random

/** One of the 6 fixed spread layouts, identified by [id] (used across the wire as `spread_name`). */
enum class Spread(val id: String, val displayName: String, val positions: List<String>) {
    ONE_CARD(
        id = "one_card",
        displayName = "Одна карта",
        positions = listOf("Главная энергия ситуации"),
    ),
    THREE_CARDS(
        id = "three_cards",
        displayName = "Три карты",
        positions = listOf(
            "Что происходит сейчас",
            "Что скрыто или влияет",
            "К чему ситуация движется",
        ),
    ),
    RELATIONSHIP(
        id = "relationship",
        displayName = "Отношения",
        positions = listOf(
            "Я в этих отношениях",
            "Другой человек в этих отношениях",
            "Что происходит между нами",
            "Что скрыто",
            "К чему всё движется",
        ),
    ),
    CHOICE(
        id = "choice",
        displayName = "Выбор",
        positions = listOf(
            "Ситуация",
            "Вариант А: потенциал",
            "Вариант А: риск",
            "Вариант Б: потенциал",
            "Вариант Б: риск",
        ),
    ),
    SEVEN_CARDS(
        id = "seven_cards",
        displayName = "Семь карт",
        positions = listOf(
            "Суть ситуации",
            "Прошлое влияние",
            "Настоящее",
            "Ближайшее развитие",
            "Скрытый фактор",
            "Совет",
            "Итог",
        ),
    ),
    CELTIC_CROSS(
        id = "celtic_cross",
        displayName = "Кельтский крест",
        positions = listOf(
            "Суть ситуации",
            "Что препятствует или пересекает ситуацию",
            "Осознанное",
            "Прошлое",
            "Возможное развитие",
            "Ближайшее будущее",
            "Позиция вопрошающего",
            "Внешние обстоятельства",
            "Надежды и страхи",
            "Итог",
        ),
    ),
    ;

    companion object {
        fun fromId(id: String): Spread =
            entries.find { it.id == id } ?: throw IllegalArgumentException("Неизвестный расклад: $id")
    }
}

/** Draws a full spread: [Spread.positions] paired with a freshly drawn card, in order. */
fun makeSpread(spreadId: String, allowReversed: Boolean, rng: Random = Random.Default): List<Pair<String, DrawnCard>> {
    val spread = Spread.fromId(spreadId)
    val cards = Deck.drawCards(spread.positions.size, allowReversed, rng)
    return spread.positions.zip(cards)
}
