package com.tarotbot.observability

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

class ObservabilityRoutingTest : FunSpec({

    test("/healthz returns 200 OK") {
        testApplication {
            application { observabilityRouting(PrometheusMeterRegistry(PrometheusConfig.DEFAULT)) }
            val response = client.get("/healthz")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("/metrics returns a Prometheus scrape") {
        testApplication {
            application { observabilityRouting(PrometheusMeterRegistry(PrometheusConfig.DEFAULT)) }
            val response = client.get("/metrics")
            response.status shouldBe HttpStatusCode.OK
        }
    }
})
