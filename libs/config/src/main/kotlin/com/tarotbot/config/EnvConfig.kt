package com.tarotbot.config

/**
 * Env-var helpers shared by every service's `main()`. Every function takes an
 * explicit [env] map (defaulting to the real process environment) so callers
 * can unit-test config parsing without mutating actual environment variables.
 */
object EnvConfig {

    /** Returns the value of [name], or throws if it's missing or blank. */
    fun requiredEnv(name: String, env: Map<String, String> = System.getenv()): String {
        val value = env[name]
        check(!value.isNullOrBlank()) { "Required environment variable '$name' is not set" }
        return value
    }

    /** Returns the value of [name], or [default] if it's missing or blank. */
    fun envOrDefault(name: String, default: String, env: Map<String, String> = System.getenv()): String {
        val value = env[name]
        return if (value.isNullOrBlank()) default else value
    }

    /**
     * Returns [name] parsed as an [Int], or [default] if the variable is missing/blank.
     * A *present but unparseable* value throws rather than silently falling back to
     * [default] — a malformed config value is a deploy-time bug worth failing loudly on.
     */
    fun intEnv(name: String, default: Int, env: Map<String, String> = System.getenv()): Int {
        val value = env[name]
        if (value.isNullOrBlank()) return default
        return value.trim().toIntOrNull()
            ?: throw IllegalStateException("Environment variable '$name' = '$value' is not a valid integer")
    }

    /**
     * Parses a comma-separated list of Telegram user ids (e.g. "111,222, 333") into a
     * [Set] of [Long]. Missing/blank input returns an empty set. Blank entries between
     * commas are ignored.
     */
    fun idSetEnv(name: String, env: Map<String, String> = System.getenv()): Set<Long> {
        val value = env[name] ?: return emptySet()
        return value.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map {
                it.toLongOrNull()
                    ?: throw IllegalStateException("Environment variable '$name' contains a non-numeric id: '$it'")
            }
            .toSet()
    }
}
