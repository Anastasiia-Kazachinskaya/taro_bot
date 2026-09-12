package com.tarotbot.domain.tarot

/** Fuzzy Russian-language `/card` lookup, ported from the old bot's `find_cards`. */
object CardFinder {

    private val suitNames = mapOf(
        "wands" to "Жезлов",
        "cups" to "Кубков",
        "swords" to "Мечей",
        "pentacles" to "Пентаклей",
    )

    /** How a suit might be typed in a query. */
    private val suitAliases = mapOf(
        "жезл" to "wands", "жезлы" to "wands", "жезлов" to "wands",
        "посох" to "wands", "посохи" to "wands", "посохов" to "wands",
        "кубок" to "cups", "кубки" to "cups", "кубков" to "cups",
        "чаша" to "cups", "чаши" to "cups", "чаш" to "cups",
        "меч" to "swords", "мечи" to "swords", "мечей" to "swords",
        "пентакль" to "pentacles", "пентакли" to "pentacles", "пентаклей" to "pentacles",
        "монета" to "pentacles", "монеты" to "pentacles", "монет" to "pentacles",
        "диск" to "pentacles", "диски" to "pentacles", "дисков" to "pentacles",
    )

    /** How a rank might be typed: as a digit or as another word form. */
    private val rankAliases = mapOf(
        "1" to "Туз", "туза" to "Туз",
        "2" to "Двойка", "двойки" to "Двойка", "двойку" to "Двойка", "две" to "Двойка",
        "3" to "Тройка", "тройки" to "Тройка", "тройку" to "Тройка", "три" to "Тройка",
        "4" to "Четвёрка", "четверка" to "Четвёрка", "четыре" to "Четвёрка",
        "5" to "Пятёрка", "пятерка" to "Пятёрка", "пять" to "Пятёрка",
        "6" to "Шестёрка", "шестерка" to "Шестёрка", "шесть" to "Шестёрка",
        "7" to "Семёрка", "семерка" to "Семёрка", "семь" to "Семёрка",
        "8" to "Восьмёрка", "восьмерка" to "Восьмёрка", "восемь" to "Восьмёрка",
        "9" to "Девятка", "девятки" to "Девятка", "девять" to "Девятка",
        "10" to "Десятка", "десятки" to "Десятка", "десять" to "Десятка",
        "11" to "Паж", "пажа" to "Паж", "валет" to "Паж",
        "12" to "Рыцарь", "рыцаря" to "Рыцарь", "конь" to "Рыцарь",
        "13" to "Королева", "королевы" to "Королева", "дама" to "Королева",
        "14" to "Король", "короля" to "Король",
    )

    /** Lowercase, ё→е, punctuation stripped to spaces, whitespace collapsed. */
    fun normalize(text: String): String {
        val lowered = text.lowercase().replace('ё', 'е')
        val alnumOnly = lowered.map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
        return alnumOnly.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** Turns "10 мечей" / "туз чаш" / "дама монет" into the deck's normalized name form. */
    private fun normalizedQuery(query: String): String {
        val words = normalize(query).split(" ").filter { it.isNotEmpty() }
        val converted = words.map { word ->
            rankAliases[word]?.let { return@map normalize(it) }
            suitAliases[word]?.let { suit -> return@map normalize(suitNames.getValue(suit)) }
            word
        }
        return converted.joinToString(" ")
    }

    /**
     * Finds cards matching [query]: empty = no match, one = exact hit, several =
     * the query is too broad (e.g. a bare suit name) and the caller should show options.
     */
    fun findCards(query: String, deck: List<DeckCard> = Deck.cards): List<DeckCard> {
        val normalized = normalizedQuery(query)
        if (normalized.isBlank()) return emptyList()

        val byNormalizedName = deck.associateBy { normalize(it.name) }

        byNormalizedName[normalized]?.let { return listOf(it) }

        val words = normalized.split(" ").filter { it.isNotEmpty() }
        val wordMatches = byNormalizedName.filterKeys { name -> words.all { it in name } }
        if (wordMatches.isNotEmpty()) return wordMatches.values.toList()

        return byNormalizedName.filterKeys { it.startsWith(normalized) }.values.toList()
    }
}
