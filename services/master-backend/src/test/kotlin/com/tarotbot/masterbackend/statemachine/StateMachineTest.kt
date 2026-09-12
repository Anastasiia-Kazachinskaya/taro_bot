package com.tarotbot.masterbackend.statemachine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class StateMachineTest : FunSpec({

    context("global commands interrupt every state") {
        val states = listOf(
            ConversationState.Idle,
            ConversationState.ChoosingSpread,
            ConversationState.ChoosingReversed("one_card"),
            ConversationState.WaitingForQuestion("one_card", true),
            ConversationState.AwaitingRetry("one_card", "q", emptyList()),
            ConversationState.InReading(42),
            ConversationState.WaitingForNote(42),
        )

        states.forEach { state ->
            test("/start resets '$state' to Idle + ShowMainMenu") {
                val result = StateMachine.transition(state, Event.StartCommand)
                result.newState shouldBe ConversationState.Idle
                result.effects shouldBe listOf(Effect.ShowMainMenu)
            }

            test("/new interrupts '$state' into ChoosingSpread") {
                val result = StateMachine.transition(state, Event.NewCommand)
                result.newState shouldBe ConversationState.ChoosingSpread
                result.effects shouldBe listOf(Effect.ShowSpreadPicker)
            }

            test("/reset interrupts '$state' to Idle") {
                val result = StateMachine.transition(state, Event.ResetCommand)
                result.newState shouldBe ConversationState.Idle
            }

            test("/card works from '$state' without changing state") {
                val result = StateMachine.transition(state, Event.CardLookupCommand("отшельник"))
                result.newState shouldBe state
                result.effects shouldBe listOf(Effect.ShowCardLookup("отшельник"))
            }
        }
    }

    context("spread selection flow") {
        test("ChoosingSpread + SpreadChosen -> ChoosingReversed") {
            val result = StateMachine.transition(ConversationState.ChoosingSpread, Event.SpreadChosen("celtic_cross"))
            result.newState shouldBe ConversationState.ChoosingReversed("celtic_cross")
            result.effects shouldBe listOf(Effect.AskReversedPreference("celtic_cross"))
        }

        test("ChoosingReversed + ReversedChoiceMade -> WaitingForQuestion") {
            val result = StateMachine.transition(
                ConversationState.ChoosingReversed("celtic_cross"),
                Event.ReversedChoiceMade(allowReversed = true),
            )
            result.newState shouldBe ConversationState.WaitingForQuestion("celtic_cross", true)
            result.effects shouldBe listOf(Effect.AskQuestion("celtic_cross", true))
        }

        test("WaitingForQuestion + QuestionAsked -> PerformReading effect") {
            val state = ConversationState.WaitingForQuestion("celtic_cross", true)
            val result = StateMachine.transition(state, Event.QuestionAsked("Что меня ждёт?"))
            result.effects shouldBe listOf(Effect.PerformReading("celtic_cross", true, "Что меня ждёт?"))
        }
    }

    context("retry flow") {
        test("AwaitingRetry + RetryRequested preserves the original question and cards") {
            val cards = listOf(
                DrawnCardDto(1, "Суть", "Шут", "major", null, false, "Начало"),
            )
            val state = ConversationState.AwaitingRetry("one_card", "Как дела?", cards)
            val result = StateMachine.transition(state, Event.RetryRequested)
            result.effects shouldBe listOf(Effect.RetryInterpretation("one_card", "Как дела?", cards))
        }

        test("AwaitingRetry ignores unrelated events") {
            val state = ConversationState.AwaitingRetry("one_card", "q", emptyList())
            val result = StateMachine.transition(state, Event.FollowUpAsked("hi"))
            result.newState shouldBe state
            result.effects.single().shouldBeInstanceOf<Effect.ReplyError>()
        }
    }

    context("in-reading flow") {
        test("InReading + plain follow-up -> PerformFollowUp") {
            val result = StateMachine.transition(ConversationState.InReading(7), Event.FollowUpAsked("а если перевёрнутая?"))
            result.newState shouldBe ConversationState.InReading(7)
            result.effects shouldBe listOf(Effect.PerformFollowUp(7, "а если перевёрнутая?"))
        }

        test("InReading + clarifying card request -> PerformClarifyingCard") {
            val result = StateMachine.transition(ConversationState.InReading(7), Event.ClarifyingCardRequested)
            result.effects shouldBe listOf(Effect.PerformClarifyingCard(7))
        }
    }

    context("note flow") {
        test("note:<id> callback from any state -> WaitingForNote") {
            val result = StateMachine.transition(ConversationState.InReading(9), Event.NoteRequested(9))
            result.newState shouldBe ConversationState.WaitingForNote(9)
            result.effects shouldBe listOf(Effect.ShowNotePrompt(9))
        }

        test("WaitingForNote + text -> SaveNote, then back to InReading") {
            val result = StateMachine.transition(ConversationState.WaitingForNote(9), Event.NoteTextEntered("сбылось"))
            result.newState shouldBe ConversationState.InReading(9)
            result.effects shouldBe listOf(Effect.SaveNote(9, "сбылось"))
        }
    }

    context("resonance and settings need no state change") {
        test("resonance callback keeps the current state") {
            val state = ConversationState.InReading(3)
            val result = StateMachine.transition(state, Event.ResonanceSet(3, "yes"))
            result.newState shouldBe state
            result.effects shouldBe listOf(Effect.SetResonance(3, "yes"))
        }

        test("settings:reversed callback resets to Idle") {
            val result = StateMachine.transition(ConversationState.ChoosingSpread, Event.SettingsReversedChosen("ask"))
            result.newState shouldBe ConversationState.Idle
            result.effects shouldBe listOf(Effect.SetReversedPreference("ask"))
        }
    }

    context("history browsing") {
        test("HistoryReadingOpened puts the user back into that reading's context") {
            val result = StateMachine.transition(ConversationState.Idle, Event.HistoryReadingOpened(11))
            result.newState shouldBe ConversationState.InReading(11)
            result.effects shouldBe listOf(Effect.ShowReadingDetail(11))
        }
    }
})
