from llm.cloudru import interpret_tarot


spread = [
    {
        "position": "Главная энергия ситуации",
        "card": {
            "name": "Тройка Жезлов",
            "arcana": "minor",
            "suit": "wands",
            "reversed": True,
            "meaning": (
                "Пересмотр планов, задержка, ограниченный горизонт, "
                "сомнения в дальнейшем направлении, ожидание результата, "
                "который пока не приходит."
            )
        }
    }
]


questions = [
    "Что меня ждёт сегодня?",
    "Стоит ли мне сегодня соглашаться на новое предложение?",
    "Почему я сейчас чувствую, что не двигаюсь вперёд?"
]


for question in questions:
    print("\n" + "=" * 60)
    print(f"ВОПРОС: {question}")
    print("=" * 60)

    result = interpret_tarot(
        question=question,
        spread_name="Одна карта",
        spread=spread
    )

    print(result)