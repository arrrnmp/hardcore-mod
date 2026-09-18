package com.hardcode.common.redis

import kotlinx.serialization.json.Json
import kotlinx.serialization.KSerializer
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub

/**
 * Thin wrapper around Jedis pub/sub used by every process (proxy, run-launcher, game-server,
 * limbo-server) to publish/subscribe to [RedisSchema.Channels] without each one re-implementing
 * connection handling and JSON (de)serialization.
 *
 * Subscribing blocks the calling thread (Jedis pub/sub semantics), so callers should run
 * [subscribe] on a dedicated thread/coroutine.
 */
class RedisEventBus(private val pool: JedisPool) : AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true }
    private val logger = LoggerFactory.getLogger(RedisEventBus::class.java)

    fun <T> publish(channel: String, serializer: KSerializer<T>, event: T) {
        pool.resource.use { jedis ->
            jedis.publish(channel, json.encodeToString(serializer, event))
        }
    }

    fun <T> subscribe(channel: String, serializer: KSerializer<T>, onEvent: (T) -> Unit) {
        pool.resource.use { jedis ->
            jedis.subscribe(
                object : JedisPubSub() {
                    override fun onMessage(subscribedChannel: String, message: String) {
                        runCatching { json.decodeFromString(serializer, message) }
                            .onSuccess(onEvent)
                            .onFailure { logger.warn("Failed to decode event on {}", subscribedChannel, it) }
                    }
                },
                channel,
            )
        }
    }

    override fun close() = pool.close()
}
