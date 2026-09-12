package com.tarotbot.dbbackend

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb

object Users : Table("users") {
    val userId = long("user_id")
    val username = text("username").nullable()
    val firstName = text("first_name").nullable()
    val firstSeen = timestamp("first_seen")
    val lastSeen = timestamp("last_seen")
    override val primaryKey = PrimaryKey(userId)
}

object UserSettings : Table("user_settings") {
    val userId = long("user_id").references(Users.userId)
    val reversedCards = bool("reversed_cards").nullable()
    override val primaryKey = PrimaryKey(userId)
}

object Readings : Table("readings") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.userId)
    val createdAt = timestamp("created_at")
    val question = text("question")
    val spreadName = text("spread_name")

    // Plain JSON text round-tripped by the caller; the Exposed `jsonb` column
    // type handles the Postgres jsonb binding correctly (a raw String bound
    // via setString would fail with a type-mismatch error at the JDBC level).
    val spread = jsonb<String>("spread", serialize = { it }, deserialize = { it })
    val interpretation = text("interpretation")
    val note = text("note").nullable()
    val resonance = text("resonance").nullable()
    val followups = jsonb<String>("followups", serialize = { it }, deserialize = { it })
    override val primaryKey = PrimaryKey(id)
}

object Usage : Table("usage") {
    val userId = long("user_id")
    val day = date("day")
    val readings = integer("readings")
    val llmRequests = integer("llm_requests")
    override val primaryKey = PrimaryKey(userId, day)
}

object ConversationStates : Table("conversation_state") {
    val userId = long("user_id").references(Users.userId)
    val state = text("state")
    val context = jsonb<String>("context", serialize = { it }, deserialize = { it })
    val version = integer("version")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(userId)
}
