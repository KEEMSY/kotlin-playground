package com.playground.infra.lock.lettuce

import com.playground.infra.lock.LockAcquisitionException
import com.playground.infra.lock.ReactiveDistributedLockService
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import java.time.Duration
import java.util.*

/**
 * Lettuce 기반 Reactive 분산 락 구현체
 *
 * ## 동기 버전(LettuceLockService)과의 차이점
 * - Thread.sleep() 대신 delay() 사용 (이벤트 루프 블로킹 방지)
 * - ReactiveStringRedisTemplate 사용
 * - suspend 함수로 구현
 *
 * ## 구현 원리는 동일
 * 1. 락 획득: SET NX EX 원자적 명령어
 * 2. 락 해제: Lua 스크립트로 원자적 검증 + 삭제
 */
class ReactiveLettuceLockService(
    private val redisTemplate: ReactiveStringRedisTemplate
) : ReactiveDistributedLockService {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val LOCK_PREFIX = "lock:"
        private const val RETRY_INTERVAL_MS = 50L

        private val UNLOCK_SCRIPT = RedisScript.of<Long>(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            else
                return 0
            end
            """.trimIndent(),
            Long::class.java
        )
    }

    override suspend fun <T> executeWithLock(
        lockKey: String,
        waitTime: Duration,
        leaseTime: Duration,
        action: suspend () -> T
    ): T {
        val fullKey = LOCK_PREFIX + lockKey
        val lockValue = tryAcquireWithRetry(fullKey, waitTime, leaseTime)
            ?: throw LockAcquisitionException(lockKey, waitTime.toMillis())

        return try {
            log.debug("Lock acquired: key={}, value={}", fullKey, lockValue)
            action()
        } finally {
            val released = unlock(lockKey, lockValue)
            if (released) {
                log.debug("Lock released: key={}", fullKey)
            } else {
                log.warn("Lock already expired or released by another process: key={}", fullKey)
            }
        }
    }

    override suspend fun tryLock(lockKey: String, leaseTime: Duration): String? {
        val fullKey = LOCK_PREFIX + lockKey
        val lockValue = generateLockValue()

        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(fullKey, lockValue, leaseTime)
            .awaitFirstOrNull() ?: false

        return if (acquired) lockValue else null
    }

    override suspend fun unlock(lockKey: String, lockValue: String): Boolean {
        val fullKey = LOCK_PREFIX + lockKey

        val result = redisTemplate.execute(
            UNLOCK_SCRIPT,
            listOf(fullKey),
            listOf(lockValue)
        ).awaitFirstOrNull()

        return result == 1L
    }

    private suspend fun tryAcquireWithRetry(
        fullKey: String,
        waitTime: Duration,
        leaseTime: Duration
    ): String? {
        val deadline = System.currentTimeMillis() + waitTime.toMillis()
        val lockValue = generateLockValue()

        while (System.currentTimeMillis() < deadline) {
            val acquired = redisTemplate.opsForValue()
                .setIfAbsent(fullKey, lockValue, leaseTime)
                .awaitSingle()

            if (acquired) {
                return lockValue
            }

            delay(RETRY_INTERVAL_MS)
        }

        return null
    }

    private fun generateLockValue(): String {
        return UUID.randomUUID().toString()
    }
}
