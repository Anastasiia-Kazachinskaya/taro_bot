package com.tarotbot.domain.render

import com.tarotbot.domain.tarot.DeckCard
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** Resolves a [DeckCard] to its Rider-Waite classpath image and loads it. */
object CardImages {

    private val majorArcanaImages = mapOf(
        "Шут" to "00_Fool.jpg",
        "Маг" to "01_Magician.jpg",
        "Верховная Жрица" to "02_High_Priestess.jpg",
        "Императрица" to "03_Empress.jpg",
        "Император" to "04_Emperor.jpg",
        "Иерофант" to "05_Hierophant.jpg",
        "Влюблённые" to "06_Lovers.jpg",
        "Колесница" to "07_Chariot.jpg",
        "Сила" to "08_Strength.jpg",
        "Отшельник" to "09_Hermit.jpg",
        "Колесо Фортуны" to "10_Wheel_of_Fortune.jpg",
        "Справедливость" to "11_Justice.jpg",
        "Повешенный" to "12_Hanged_Man.jpg",
        "Смерть" to "13_Death.jpg",
        "Умеренность" to "14_Temperance.jpg",
        "Дьявол" to "15_Devil.jpg",
        "Башня" to "16_Tower.jpg",
        "Звезда" to "17_Star.jpg",
        "Луна" to "18_Moon.jpg",
        "Солнце" to "19_Sun.jpg",
        "Суд" to "20_Judgement.jpg",
        "Мир" to "21_World.jpg",
    )

    private val suitPrefixes = mapOf(
        "wands" to "Wands",
        "cups" to "Cups",
        "swords" to "Swords",
        "pentacles" to "Pents",
    )

    private val ranks = mapOf(
        "Туз" to 1, "Двойка" to 2, "Тройка" to 3, "Четвёрка" to 4, "Пятёрка" to 5,
        "Шестёрка" to 6, "Семёрка" to 7, "Восьмёрка" to 8, "Девятка" to 9, "Десятка" to 10,
        "Паж" to 11, "Рыцарь" to 12, "Королева" to 13, "Король" to 14,
    )

    private fun imageFileName(card: DeckCard): String {
        if (card.arcana == "major") {
            return requireNotNull(majorArcanaImages[card.name]) { "No image mapped for major arcana card '${card.name}'" }
        }
        val suit = requireNotNull(card.suit) { "Minor arcana card '${card.name}' has no suit" }
        val prefix = requireNotNull(suitPrefixes[suit]) { "Unknown suit '$suit'" }
        val rankWord = card.name.split(" ").first()
        val rank = requireNotNull(ranks[rankWord]) { "Unknown rank word '$rankWord' in card '${card.name}'" }
        return "${prefix}${rank.toString().padStart(2, '0')}.jpg"
    }

    /** Loads the card's image from the classpath, throwing if it isn't bundled. */
    fun load(card: DeckCard): BufferedImage {
        val fileName = imageFileName(card)
        val path = "/images/rider_waite/$fileName"
        val stream = requireNotNull(javaClass.getResourceAsStream(path)) { "Card image not found: $path" }
        return stream.use { ImageIO.read(it) }
    }
}
