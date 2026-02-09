package com.playground.infra.lock.lettuce

import com.playground.infra.lock.LockAcquisitionException
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.script.RedisScript
import java.time.Duration

@DisplayName("LettuceLockService Tests")
class LettuceLockServiceTest {

    @MockK
    private lateinit var redisTemplate: StringRedisTemplate

    @MockK
    private lateinit var valueOperations: ValueOperations<String, String>

    private lateinit var lockService: LettuceLockService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { redisTemplate.opsForValue() } returns valueOperations
        lockService = LettuceLockService(redisTemplate)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    @DisplayName("tryLock")
    inner class TryLockTest {

        @Test
        @DisplayName("should return lockValue when lock acquired successfully")
        fun `should return lockValue when lock acquired successfully`() {
            // given
            every {
                valueOperations.setIfAbsent(any<String>(), any<String>(), any<Duration>())
            } returns true

            // when
            val result = lockService.tryLock("test-key", Duration.ofSeconds(10))

            // then
            assertNotNull(result)
            verify {
                valueOperations.setIfAbsent(
                    eq("lock:test-key"),
                    any<String>(),
                    eq(Duration.ofSeconds(10))
                )
            }
        }

        @Test
        @DisplayName("should return null when lock already exists")
        fun `should return null when lock already exists`() {
            // given
            every {
                valueOperations.setIfAbsent(any<String>(), any<String>(), any<Duration>())
            } returns false

            // when
            val result = lockService.tryLock("test-key", Duration.ofSeconds(10))

            // then
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("unlock")
    inner class UnlockTest {

        @Test
        @DisplayName("should return true when unlock successful")
        fun `should return true when unlock successful`() {
            // given
            every {
                redisTemplate.execute(any<RedisScript<Long>>(), any<List<String>>(), any<String>())
            } returns 1L

            // when
            val result = lockService.unlock("test-key", "lock-value")

            // then
            assertTrue(result)
        }

        @Test
        @DisplayName("should return false when lock value does not match")
        fun `should return false when lock value does not match`() {
            // given
            every {
                redisTemplate.execute(any<RedisScript<Long>>(), any<List<String>>(), any<String>())
            } returns 0L

            // when
            val result = lockService.unlock("test-key", "wrong-value")

            // then
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("executeWithLock")
    inner class ExecuteWithLockTest {

        @Test
        @DisplayName("should execute action and return result when lock acquired")
        fun `should execute action and return result when lock acquired`() {
            // given
            every {
                valueOperations.setIfAbsent(any<String>(), any<String>(), any<Duration>())
            } returns true
            every {
                redisTemplate.execute(any<RedisScript<Long>>(), any<List<String>>(), any<String>())
            } returns 1L

            // when
            val result = lockService.executeWithLock("test-key") {
                "action-result"
            }

            // then
            assertEquals("action-result", result)
            verify(exactly = 1) {
                valueOperations.setIfAbsent(any<String>(), any<String>(), any<Duration>())
            }
            verify(exactly = 1) {
                redisTemplate.execute(any<RedisScript<Long>>(), any<List<String>>(), any<String>())
            }
        }

        @Test
        @DisplayName("should throw LockAcquisitionException when lock not acquired within waitTime")
        fun `should throw LockAcquisitionException when lock not acquired within waitTime`() {
            // given
            every {
                valueOperations.setIfAbsent(any<String>(), any<String>(), any<Duration>())
            } returns false

            // when & then
            assertThrows<LockAcquisitionException> {
                lockService.executeWithLock(
                    lockKey = "test-key",
                    waitTime = Duration.ofMillis(100),
                    leaseTime = Duration.ofSeconds(10)
                ) {
                    "should not reach here"
                }
            }
        }

        @Test
        @DisplayName("should release lock even when action throws exception")
        fun `should release lock even when action throws exception`() {
            // given
            every {
                valueOperations.setIfAbsent(any<String>(), any<String>(), any<Duration>())
            } returns true
            every {
                redisTemplate.execute(any<RedisScript<Long>>(), any<List<String>>(), any<String>())
            } returns 1L

            // when & then
            assertThrows<RuntimeException> {
                lockService.executeWithLock("test-key") {
                    throw RuntimeException("Action failed")
                }
            }

            // verify unlock was called
            verify(exactly = 1) {
                redisTemplate.execute(any<RedisScript<Long>>(), any<List<String>>(), any<String>())
            }
        }
    }
}
