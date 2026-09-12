package com.tarotbot.aibackend.prompt

import com.tarotbot.proto.ai.AnswerFollowupRequest
import com.tarotbot.proto.ai.InterpretClarifyingCardRequest
import com.tarotbot.proto.ai.InterpretSpreadRequest

private const val OUTPUT_INSTRUCTION_SINGLE = """
Для одной карты дай 3 содержательных абзаца.
Не используй заголовок и отдельное заключение.
"""

private const val OUTPUT_INSTRUCTION_MULTI = """
Для нескольких карт:
1. интерпретируй позиции;
2. покажи связи и противоречия между картами;
3. сформулируй общую динамику;
4. вернись к исходному вопросу и дай главный вывод.

Не повторяй одну мысль несколько раз.
"""

private const val FOLLOWUP_MAX_TOKENS = 700
private const val CLARIFYING_CARD_MAX_TOKENS = 700
private const val MAX_HISTORY_TURNS = 3

/** Assembles the system+user prompt and token budget for each of the 3 AiService RPCs. */
object PromptBuilder {

    data class Spec(val systemPrompt: String, val userPrompt: String, val maxTokens: Int)

    fun forInterpretSpread(request: InterpretSpreadRequest): Spec {
        val cardCount = request.cardsList.size
        val outputInstruction = if (cardCount <= 1) OUTPUT_INSTRUCTION_SINGLE else OUTPUT_INSTRUCTION_MULTI
        val prompt = """
Вопрос пользователя:
${request.question}

Название расклада:
${spreadTitle(request.spreadName)}

Специальные правила этого расклада:
${spreadInstructions(request.spreadName)}

Карты расклада:
${formatCardsText(request.cardsList)}

Прочитай этот расклад именно как ответ
на вопрос пользователя.

$outputInstruction

Не добавляй ничего кроме готовой интерпретации.
"""
        return Spec(SYSTEM_PROMPT, prompt, maxTokensForSpread(cardCount))
    }

    fun forAnswerFollowup(request: AnswerFollowupRequest): Spec {
        val recentHistory = request.historyList.takeLast(MAX_HISTORY_TURNS)
        val historyText = recentHistory.joinToString("\n") { turn ->
            "Уточняющий вопрос: ${turn.question}\nОтвет: ${turn.answer}\n"
        }
        val historyBlock = if (recentHistory.isNotEmpty()) {
            "\nПредыдущие уточняющие вопросы и ответы:\n$historyText"
        } else {
            ""
        }
        val prompt = """
Изначальный вопрос пользователя:
${request.originalQuestion}

Название расклада:
${spreadTitle(request.spreadName)}

Карты расклада:
${formatCardsText(request.cardsList)}

Первая интерпретация расклада:
${request.originalInterpretation}
$historyBlock

Новый уточняющий вопрос пользователя:
${request.newQuestion}

Ответь именно на этот уточняющий вопрос, используя карты и позиции
этого расклада. Не повторяй весь расклад заново.
"""
        return Spec(FOLLOWUP_SYSTEM_PROMPT, prompt, FOLLOWUP_MAX_TOKENS)
    }

    fun forClarifyingCard(request: InterpretClarifyingCardRequest): Spec {
        val newCard = request.clarifyingCard.card
        val prompt = """
Изначальный вопрос пользователя:
${request.originalQuestion}

Название расклада:
${spreadTitle(request.spreadName)}

Карты исходного расклада:
${formatCardsText(request.originalCardsList)}

Первая интерпретация расклада:
${request.originalInterpretation}

Дополнительно вытянутая уточняющая карта:
Название: ${newCard.name}
Аркан: ${newCard.arcana}
Масть: ${newCard.suit.ifBlank { "нет" }}
Положение: ${if (newCard.reversed) "перевёрнутая" else "прямая"}
Значение: ${newCard.meaning}

Объясни, как эта уточняющая карта дополняет или уточняет
интерпретацию расклада по отношению к изначальному вопросу.
"""
        return Spec(CLARIFYING_CARD_SYSTEM_PROMPT, prompt, CLARIFYING_CARD_MAX_TOKENS)
    }
}
