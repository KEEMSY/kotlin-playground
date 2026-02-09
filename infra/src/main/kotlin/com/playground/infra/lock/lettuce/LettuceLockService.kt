package com.playground.infra.lock.lettuce

import com.playground.infra.lock.DistributedLockService
import com.playground.infra.lock.LockAcquisitionException
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import java.time.Duration
import java.util.*

/**
 * Lettuce 기반 동기 분산 락 구현체
 *
 * ## 구현 원리
 * 1. 락 획득: SET NX EX (Not eXists + EXpire) 원자적 명령어 사용
 * 2. 락 해제: Lua 스크립트로 "본인 락인지 확인 후 삭제" 원자적 수행
 *
 * ## 왜 Lua 스크립트가 필요한가?
 * GET + DEL을 별도로 실행하면 Race Condition 발생 가능:
 * - Thread A: GET lock -> "value-A" (본인 락 확인)
 * - Thread B: (A의 락 만료로) SET lock -> "value-B"
 * - Thread A: DEL lock (B의 락을 잘못 삭제!)
 *
 * Lua 스크립트는 Redis에서 원자적으로 실행되어 이 문제를 방지합니다.
 */
class LettuceLockService(
    private val redisTemplate: StringRedisTemplate
) : DistributedLockService {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val LOCK_PREFIX = "lock:"
        private const val RETRY_INTERVAL_MS = 50L

        /**
         * 락 해제 Lua 스크립트
         * KEYS[1]: 락 키
         * ARGV[1]: 락 값 (본인 확인용)
         * 반환: 1 (성공), 0 (실패 - 본인 락이 아님)
         */
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

    override fun <T> executeWithLock(
        lockKey: String,
        waitTime: Duration,
        leaseTime: Duration,
        action: () -> T
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

    override fun tryLock(lockKey: String, leaseTime: Duration): String? {
        val fullKey = LOCK_PREFIX + lockKey
        val lockValue = generateLockValue()

        val acquired = redisTemplate.opsForValue().setIfAbsent(
            fullKey,
            lockValue,
            leaseTime
        ) ?: false

        return if (acquired) lockValue else null
    }

    override fun unlock(lockKey: String, lockValue: String): Boolean {
        val fullKey = LOCK_PREFIX + lockKey

        val result = redisTemplate.execute(
            UNLOCK_SCRIPT,
            listOf(fullKey),
            lockValue
        )

        return result == 1L
    }

    private fun tryAcquireWithRetry(
        fullKey: String,
        waitTime: Duration,
        leaseTime: Duration
    ): String? {
        val deadline = System.currentTimeMillis() + waitTime.toMillis()
        val lockValue = generateLockValue()

        while (System.currentTimeMillis() < deadline) {
            val acquired = redisTemplate.opsForValue().setIfAbsent(
                fullKey,
                lockValue,
                leaseTime
            ) ?: false

            if (acquired) {
                return lockValue
            }

            Thread.sleep(RETRY_INTERVAL_MS)
        }

        return null
    }

    private fun generateLockValue(): String {
        return UUID.randomUUID().toString()
    }
}
