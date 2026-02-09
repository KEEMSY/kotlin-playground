package com.playground.mvc

import com.playground.core.exception.ConflictException
import com.playground.infra.lock.DistributedLockService
import com.playground.infra.lock.LockAcquisitionException
import com.playground.mvc.dto.CreateUserRequest
import com.playground.mvc.entity.User
import com.playground.mvc.repository.UserRepository
import com.playground.mvc.service.UserService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("UserService Concurrency Tests")
class UserServiceConcurrencyTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var lockService: DistributedLockService

    @InjectMockKs
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
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
        fun `should prevent duplicate user creation with distributed lock`() {
            // given
            val request = CreateUserRequest("test@example.com", "Test User")
            val createdUser = User(
                id = 1L,
                email = request.email,
                name = request.name,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            // First call acquires lock and creates user
            // Second call acquires lock but finds user already exists
            var callCount = 0
            every {
                lockService.executeWithLock<Any>(
                    lockKey = "user:email:${request.email}",
                    waitTime = any<Duration>(),
                    leaseTime = any<Duration>(),
                    action = any()
                )
            } answers {
                val action = arg<() -> Any>(3)
                action()
            }

            every { userRepository.existsByEmail(request.email) } answers {
                callCount++ > 0 // First call returns false, subsequent calls return true
            }
            every { userRepository.save(any()) } returns createdUser

            // when - first call succeeds
            val result = userService.createUser(request)

            // then
            assertEquals(request.email, result.email)

            // when - second call throws ConflictException
            assertThrows<ConflictException> {
                userService.createUser(request)
            }

            // verify lock was used
            verify(exactly = 2) {
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
        fun `should throw LockAcquisitionException when lock cannot be acquired`() {
            // given
            val request = CreateUserRequest("test@example.com", "Test User")

            every {
                lockService.executeWithLock<Any>(
                    lockKey = "user:email:${request.email}",
                    waitTime = any<Duration>(),
                    leaseTime = any<Duration>(),
                    action = any()
                )
            } throws LockAcquisitionException("user:email:${request.email}", 3000)

            // when & then
            assertThrows<LockAcquisitionException> {
                userService.createUser(request)
            }
        }
    }

    @Nested
    @DisplayName("Lock Behavior Simulation")
    inner class LockBehaviorTest {

        @Test
        @DisplayName("should serialize concurrent requests with lock")
        fun `should serialize concurrent requests with lock`() {
            // given
            val request = CreateUserRequest("test@example.com", "Test User")
            val createdUser = User(
                id = 1L,
                email = request.email,
                name = request.name,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            val executionOrder = mutableListOf<Int>()
            val lock = Object()

            // Simulate lock behavior - serialize execution
            every {
                lockService.executeWithLock<Any>(
                    lockKey = any<String>(),
                    waitTime = any<Duration>(),
                    leaseTime = any<Duration>(),
                    action = any()
                )
            } answers {
                val action = arg<() -> Any>(3)
                synchronized(lock) {
                    action()
                }
            }

            val successCount = AtomicInteger(0)
            val conflictCount = AtomicInteger(0)
            var userCreated = false

            every { userRepository.existsByEmail(request.email) } answers {
                userCreated
            }
            every { userRepository.save(any()) } answers {
                userCreated = true
                createdUser
            }

            // when - simulate concurrent requests
            val threadCount = 5
            val latch = CountDownLatch(threadCount)
            val executor = Executors.newFixedThreadPool(threadCount)

            repeat(threadCount) { i ->
                executor.submit {
                    try {
                        userService.createUser(request)
                        successCount.incrementAndGet()
                        synchronized(executionOrder) {
                            executionOrder.add(i)
                        }
                    } catch (e: ConflictException) {
                        conflictCount.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // then - only one should succeed
            assertEquals(1, successCount.get(), "Only one request should succeed")
            assertEquals(threadCount - 1, conflictCount.get(), "Others should get ConflictException")
        }
    }
}
