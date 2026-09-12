CREATE TABLE users (
    user_id BIGINT PRIMARY KEY,
    username TEXT,
    first_name TEXT,
    first_seen TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_settings (
    user_id BIGINT PRIMARY KEY REFERENCES users(user_id),
    reversed_cards BOOLEAN
);

CREATE TABLE readings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    question TEXT NOT NULL,
    spread_name TEXT NOT NULL,
    spread JSONB NOT NULL,
    interpretation TEXT NOT NULL,
    note TEXT,
    resonance TEXT CHECK (resonance IN ('yes', 'no') OR resonance IS NULL),
    followups JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX idx_readings_user ON readings (user_id, created_at DESC);

CREATE TABLE usage (
    user_id BIGINT NOT NULL,
    day DATE NOT NULL,
    readings INT NOT NULL DEFAULT 0,
    llm_requests INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day)
);

CREATE TABLE conversation_state (
    user_id BIGINT PRIMARY KEY REFERENCES users(user_id),
    state TEXT NOT NULL,
    context JSONB NOT NULL DEFAULT '{}'::jsonb,
    version INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
