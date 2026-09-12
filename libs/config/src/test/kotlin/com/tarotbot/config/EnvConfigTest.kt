package com.tarotbot.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EnvConfigTest : FunSpec({

    context("requiredEnv") {
        test("throws when the variable is unset") {
            shouldThrow<IllegalStateException> {
                EnvConfig.requiredEnv("MISSING", env = emptyMap())
            }
        }

        test("throws when the variable is blank") {
            shouldThrow<IllegalStateException> {
                EnvConfig.requiredEnv("BLANK", env = mapOf("BLANK" to "   "))
            }
        }

        test("returns the value when set") {
            EnvConfig.requiredEnv("BOT_TOKEN", env = mapOf("BOT_TOKEN" to "abc123")) shouldBe "abc123"
        }
    }

    context("envOrDefault") {
        test("returns the default when unset") {
            EnvConfig.envOrDefault("MISSING", "fallback", env = emptyMap()) shouldBe "fallback"
        }

        test("returns the actual value when set") {
            EnvConfig.envOrDefault("KEY", "fallback", env = mapOf("KEY" to "value")) shouldBe "value"
        }
    }

    context("intEnv") {
        test("parses a valid integer") {
            EnvConfig.intEnv("LIMIT", 10, env = mapOf("LIMIT" to "42")) shouldBe 42
        }

        test("falls back to the default when unset") {
            EnvConfig.intEnv("MISSING", 10, env = emptyMap()) shouldBe 10
        }

        test("falls back to the default when blank") {
            EnvConfig.intEnv("BLANK", 10, env = mapOf("BLANK" to "")) shouldBe 10
        }

        test("throws when present but not a valid integer") {
            shouldThrow<IllegalStateException> {
                EnvConfig.intEnv("LIMIT", 10, env = mapOf("LIMIT" to "not-a-number"))
            }
        }
    }

    context("idSetEnv") {
        test("parses a comma-separated list of ids") {
            EnvConfig.idSetEnv("IDS", env = mapOf("IDS" to "111,222, 333")) shouldBe setOf(111L, 222L, 333L)
        }

        test("returns an empty set when missing") {
            EnvConfig.idSetEnv("MISSING", env = emptyMap()) shouldBe emptySet()
        }

        test("returns an empty set when blank") {
            EnvConfig.idSetEnv("BLANK", env = mapOf("BLANK" to "")) shouldBe emptySet()
        }

        test("ignores blank entries between commas") {
            EnvConfig.idSetEnv("IDS", env = mapOf("IDS" to "111,,222,")) shouldBe setOf(111L, 222L)
        }
    }
})
