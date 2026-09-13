# Tarot Bot

A Telegram tarot-reading bot, built as a distributed Kotlin/gRPC microservices
system: a Telegram-facing gateway, a stateless clustered master backend
driven by an explicit state machine, a clustered Postgres-backed DB backend,
and a clustered AI backend with a runtime-swappable LLM provider
(DeepSeek primary, GigaChat fallback). See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
for the full design — diagram, component responsibilities, proto contracts,
testing strategy.

## Features

- Six spread types (one card, three cards, relationship, choice, seven
  cards, Celtic cross), drawn from a 78-card Rider-Waite deck with optional
  reversed cards.
- AI-generated interpretations for a fresh spread, follow-up questions, and
  a clarifying card drawn mid-conversation.
- `/card <query>` — fuzzy Russian-language card lookup, no AI call.
- Reading history, per-reading notes, and a resonance ("did it come true?")
  marker.
- Per-user daily quotas (readings and AI calls, independently configurable,
  with an unlimited-user allowlist) and an admin usage summary.
- Every service is stateless and horizontally replicated (3 instances each
  for the master/DB/AI tiers), load-balanced through Envoy, and observable
  via Prometheus + Grafana.

## Stack

Kotlin 2.0 / JDK 21, gRPC (`grpc-kotlin`, coroutine stubs), Gradle
multi-module build, PostgreSQL with streaming replication (Flyway
migrations, JetBrains Exposed), Ktor (HTTP client for Telegram/LLM calls,
embedded metrics server), Envoy, Prometheus, Grafana, Docker Compose.
Tests: kotest + mockk (unit), Testcontainers + in-process gRPC
(integration).

## Project layout

```
proto/                  .proto contracts (gateway<->master<->db/ai)
libs/
  resilience/            shared gRPC channel factory: deadlines, retry, circuit breaker
  observability/          Prometheus metrics + health/reflection, used by every service
  config/                 env-var and YAML config helpers
domain/
  tarot-domain/           deck, spreads, fuzzy card search (pure logic)
  tarot-render/           spread image compositing (Java2D)
services/
  gateway/                Telegram long-polling client
  master-backend/         state machine + orchestration
  db-backend/             Postgres access, quotas, diary, history
  ai-backend/             DeepSeek/GigaChat providers, prompts
deploy/                  Envoy, Prometheus, Grafana config
docker-compose.yml       brings up the full system
```

## Running it

Copy `.env.example` to `.env` and fill in the required values (a Telegram
bot token at minimum; `DEEPSEEK_API_KEY`/`CLOUD_API_KEY` for AI
interpretations — the AI backend falls back from the primary to the
secondary provider automatically if one is unavailable).

```
docker compose up -d --build
```

This starts Postgres (primary + streaming replica), three replicas each of
the DB, AI, and master backends behind Envoy, a single gateway instance,
Prometheus, and Grafana. Grafana is on `localhost:3000`, Prometheus on
`localhost:9099`, Envoy's admin interface on `localhost:9901`.

## Development

```
./gradlew build test integrationTest
```

`integrationTest` is a separate Gradle source set (Testcontainers-backed for
db-backend, in-process gRPC for master-backend/ai-backend) — it needs a
running Docker daemon. CI runs the same command on every push/PR to `main`;
`main` is protected and only accepts changes through a PR with a green
`build-test` check.
