package com.tarotbot.dbbackend

import com.tarotbot.config.EnvConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import javax.sql.DataSource

/** The two Exposed [Database] handles the service reads/writes through. */
data class Databases(val primary: Database, val replica: Database)

object DbConnections {

    private fun dataSource(url: String, user: String, password: String): DataSource {
        val config = HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = 10
        }
        return HikariDataSource(config)
    }

    /**
     * Builds the primary + replica [Databases] from env config. `DB_REPLICA_URL` falls
     * back to the primary connection when unset (local/single-node runs and tests).
     */
    fun fromEnv(env: Map<String, String> = System.getenv()): Databases {
        val primaryUrl = EnvConfig.requiredEnv("DB_PRIMARY_URL", env)
        val primaryUser = EnvConfig.requiredEnv("DB_PRIMARY_USER", env)
        val primaryPassword = EnvConfig.requiredEnv("DB_PRIMARY_PASSWORD", env)

        val primary = Database.connect(dataSource(primaryUrl, primaryUser, primaryPassword))

        val replicaUrl = EnvConfig.envOrDefault("DB_REPLICA_URL", primaryUrl, env)
        val replicaUser = EnvConfig.envOrDefault("DB_REPLICA_USER", primaryUser, env)
        val replicaPassword = EnvConfig.envOrDefault("DB_REPLICA_PASSWORD", primaryPassword, env)
        val replica = if (replicaUrl == primaryUrl) primary
        else Database.connect(dataSource(replicaUrl, replicaUser, replicaPassword))

        return Databases(primary, replica)
    }
}
