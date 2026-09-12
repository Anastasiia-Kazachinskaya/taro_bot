package com.tarotbot.resilience

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class RetryPolicyTest : FunSpec({

    test("default policy retries on transient failures") {
        val policy = RetryPolicy()
        policy.retryableStatusCodes shouldContain "UNAVAILABLE"
        policy.retryableStatusCodes shouldContain "DEADLINE_EXCEEDED"
    }

    test("default policy never retries caller errors") {
        val policy = RetryPolicy()
        policy.retryableStatusCodes shouldNotContain "INVALID_ARGUMENT"
        policy.retryableStatusCodes shouldNotContain "FAILED_PRECONDITION"
        policy.retryableStatusCodes shouldNotContain "NOT_FOUND"
    }

    test("named channel policy presets set expected deadlines") {
        ChannelPolicy.forDbBackend().deadline.seconds shouldBe 10L
        ChannelPolicy.forAiBackend().deadline.seconds shouldBe 65L
        ChannelPolicy.forMasterBackend().deadline.seconds shouldBe 15L
    }
})
