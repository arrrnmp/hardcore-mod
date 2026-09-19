package com.aaronmompie.common.redis

import redis.clients.jedis.JedisPool

/** Shared env-var convention for the Redis connection, used by game-server and run-launcher. */
object RedisConnection {
    fun fromEnv(): JedisPool {
        val host = System.getenv("HARDCODE_REDIS_HOST") ?: "localhost"
        val port = System.getenv("HARDCODE_REDIS_PORT")?.toIntOrNull() ?: 6379
        return JedisPool(host, port)
    }
}
