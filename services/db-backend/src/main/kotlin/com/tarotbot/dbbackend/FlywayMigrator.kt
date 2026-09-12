package com.tarotbot.dbbackend

import org.flywaydb.core.Flyway

object FlywayMigrator {
    fun migrate(url: String, user: String, password: String) {
        Flyway.configure()
            .dataSource(url, user, password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
