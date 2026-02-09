package com.playground.webflux

import com.playground.core.exception.ConflictException
import com.playground.infra.lock.LockAcquisitionException
import com.playground.infra.lock.ReactiveDistributedLockService
import com.playground.webflux.dto.CreateUserRequest
import com.playground.webflux.entity.User
import com.playground.webflux.repository.UserRepository
import com.playground.webflux.service.UserService
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.data.redis.core.ReactiveRedisTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("UserService Concurrency Tests (Coroutines)")
class UserServiceConcurrencyTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var redisTemplate: ReactiveRedisTemplate<String, Any>

    @MockK
    private lateinit var lockService: ReactiveDistributedLockService

    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        userService = UserService(userRepository, redisTemplate, lockService)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    @DisplayName("Race Condition Prevention")
    inner class RaceConditionTest {

        @Test
        @DisplayName("should prevent duplicate user creation with distributed lock")
        fun `should prevent duplicate user creation with distributed lock`() = runTest {
            // given
            val request = CreateUserRequest("test@example.com", "Test User")
            val createdUser = User(
                id = 1L,
                email = request.email,
                name = request.name,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            var callCount = 0
            coEvery {
                lockService.executeWithLock<Any>(
                    lockKey = "user:email:${request.email}",
                    waitTime = any<Duration>(),
                    leaseTime = any<Duration>(),
                    action = any()
                )
            } coAnswers {
                val action = arg<suspend () -> Any>(3)
                action()
            }

            coEvery { userRepository.existsByEmail(request.email) } answers {
                callCount++ > 0
            }
            coEvery { userRepository.save(any()) } returns createdUser

            // when - first call succeeds
            val result = userService.createUser(request)

            // then
            assertEquals(request.email, result.email)

            // when - second call throws ConflictException
            assertThrows<ConflictException> {
                kotlinx.coroutines.runBlocking {
                    userService.createUser(request)
                }
            }

            // verify lock was used
            coVerify(exactly = 2) {
                lockService.executeWithLock<Any>(
                    lockKey = "user:email:${request.email}",
                    waitTime = any<Duration>(),
                    leaseTime = any<Duration>(),
                    action = any()
                )
            }
        }

        @Test
        @DisplayName("should throw LockAcquisitionException when lock cannot be acquired")
        fun `should throw LockAcquisitionException when lock cannot be acquired`() = runTest {
            // given
            val request = CreateUserRequest("test@example.com", "Test User")

            coEvery {
                lockService.executeWithLock<Any>(
                    lockKey = "user:email:${request.email}",
                    waitTime = any<Duration>(),
                    leaseTime = any<Duration>(),
                    action = any()
                )
            } throws LockAcquisitionException("user:email:${request.email}", 3000)

            // when & then
            assertThrows<LockAcquisitionException> {
                kotlinx.coroutines.runBlocking {
                    userService.createUser(request)
                }
            }
        }
    }

    @Nested
    @DisplayName("Lock Behavior Simulation")
    inner class LockBehaviorTest {

        @Test
        @DisplayName("should serialize concurrent coroutine requests with lock")
        fun `should serialize concurrent coroutine requests with lock`() = runTest {
            // given
            val request = CreateUserRequest("test@example.com", "Test User")
            val createdUser = User(
                id = 1L,
                email = request.email,
                name = request.name,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            val mutex = Mutex()

            // Simulate lock behavior - serialize execution using mutex
            coEvery {
                lockService.executeWithLock<Any>(
                    lockKey = any<String>(),
                    waitTime = any<Duration>(),
                    leaseTime = any<Duration>(),
                    action = any()
                )
            } coAnswers {
                val action = arg<suspend () -> Any>(3)
                mutex.withLock {
                    action()
                }
            }

            val successCount = AtomicInteger(0)
            val conflictCount = AtomicInteger(0)
            var userCreated = false

            coEvery { userRepository.existsByEmail(request.email) } answers {
                userCreated
            }
            coEvery { userRepository.save(any()) } answers {
                userCreated = true
                createdUser
            }

            // when - simulate concurrent coroutine requests
            val coroutineCount = 5
            val results = (1..coroutineCount).map {
                async {
                    try {
                        userService.createUser(request)
                        successCount.incrementAndGet()
                    } catch (e: ConflictException) {
                        conflictCount.incrementAndGet()
                    }
                }
            }

            results.awaitAll()

            // then - only one should succeed
            assertEquals(1, successCount.get(), "Only one request should succeed")
            assertEquals(coroutineCount - 1, conflictCount.get(), "Others should get ConflictException")
        }
    }
}
