package com.playground.infra.resilience

import com.playground.infra.external.FakePaymentClient
import com.playground.infra.external.PaymentException
import com.playground.infra.external.PaymentStatus
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.Duration

@DisplayName("ResilientPaymentService Tests")
class ResilientPaymentServiceTest {

    private lateinit var fakePaymentClient: FakePaymentClient
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry
    private lateinit var retryRegistry: RetryRegistry
    private lateinit var rateLimiterRegistry: RateLimiterRegistry
    private lateinit var service: ResilientPaymentService

    @BeforeEach
    fun setUp() {
        fakePaymentClient = FakePaymentClient(
            failureRate = 0,  // 테스트에서 직접 제어
            slowCallRate = 0
        )

        circuitBreakerRegistry = CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .slidingWindowSize(5)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build()
        )

        retryRegistry = RetryRegistry.of(
            RetryConfig.custom<Any>()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(100))
                .retryExceptions(PaymentException::class.java)
                .build()
        )

        rateLimiterRegistry = RateLimiterRegistry.of(
            RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ZERO)
                .build()
        )

        service = ResilientPaymentService(
            fakePaymentClient,
            circuitBreakerRegistry,
            retryRegistry,
            rateLimiterRegistry
        )
    }

    @Nested
    @DisplayName("Circuit Breaker 상태 전이")
    inner class CircuitBreakerStateTransitionTest {

        @Test
        @DisplayName("정상 상태에서는 CLOSED")
        fun `should be CLOSED when all calls succeed`() = runTest {
            // given
            fakePaymentClient.forceSuccess = true

            // when
            repeat(5) {
                service.processPayment(1L, 10000L)
            }

            // then
            assertEquals(CircuitBreaker.State.CLOSED, service.getCircuitBreakerState())
        }

        @Test
        @DisplayName("실패율 50% 초과 시 OPEN")
        fun `should transition to OPEN when failure rate exceeds threshold`() = runTest {
            // given - 5회 중 3회 실패 (60% 실패율)
            fakePaymentClient.forceSuccess = true

            // 2회 성공
            repeat(2) {
                service.processPayment(1L, 10000L)
            }

            // 3회 실패 유도
            fakePaymentClient.forceSuccess = false
            fakePaymentClient.forceFailure = true

            repeat(3) {
                runCatching {
                    service.processPayment(1L, 10000L)
                }
            }

            // then
            assertEquals(CircuitBreaker.State.OPEN, service.getCircuitBreakerState())
        }

        @Test
        @DisplayName("OPEN 상태에서 요청 시 CallNotPermittedException")
        fun `should throw CallNotPermittedException when circuit breaker is OPEN`() = runTest {
            // given - Circuit Breaker를 OPEN 상태로 강제 전환
            service.forceOpen()

            // when & then
            assertThrows<CallNotPermittedException> {
                kotlinx.coroutines.runBlocking {
                    service.processPayment(1L, 10000L)
                }
            }
        }

        @Test
        @DisplayName("OPEN 상태에서 대기 후 HALF_OPEN으로 전이")
        fun `should transition to HALF_OPEN after wait duration`() = runTest {
            // given
            service.forceOpen()
            assertEquals(CircuitBreaker.State.OPEN, service.getCircuitBreakerState())

            // when - 대기 시간 경과 (1초)
            Thread.sleep(1100)

            // 다음 요청 시 HALF_OPEN으로 전이
            fakePaymentClient.forceSuccess = true
            fakePaymentClient.forceFailure = false
            runCatching { service.processPayment(1L, 10000L) }

            // then
            val state = service.getCircuitBreakerState()
            assertTrue(
                state == CircuitBreaker.State.HALF_OPEN || state == CircuitBreaker.State.CLOSED,
                "State should be HALF_OPEN or CLOSED, but was $state"
            )
        }
    }

    @Nested
    @DisplayName("Fallback 동작")
    inner class FallbackTest {

        @Test
        @DisplayName("Circuit Breaker OPEN 시 Fallback으로 PENDING 반환")
        fun `should return PENDING when circuit breaker is OPEN`() = runTest {
            // given
            service.forceOpen()

            // when
            val result = service.processPaymentWithFallback(1L, 10000L)

            // then
            assertEquals(PaymentStatus.PENDING, result.status)
            assertTrue(result.transactionId.startsWith("PENDING-"))
        }

        @Test
        @DisplayName("결제 실패 시 Fallback으로 PENDING 반환")
        fun `should return PENDING when payment fails`() = runTest {
            // given
            fakePaymentClient.forceFailure = true

            // when
            val result = service.processPaymentWithFallback(1L, 10000L)

            // then
            assertEquals(PaymentStatus.PENDING, result.status)
        }

        @Test
        @DisplayName("결제 성공 시 COMPLETED 반환")
        fun `should return COMPLETED when payment succeeds`() = runTest {
            // given
            fakePaymentClient.forceSuccess = true

            // when
            val result = service.processPaymentWithFallback(1L, 10000L)

            // then
            assertEquals(PaymentStatus.COMPLETED, result.status)
        }
    }

    @Nested
    @DisplayName("메트릭 조회")
    inner class MetricsTest {

        @Test
        @DisplayName("성공 호출 카운트 추적")
        fun `should track success calls`() = runTest {
            // given
            fakePaymentClient.forceSuccess = true

            // when - 3회 성공
            repeat(3) {
                service.processPayment(1L, 10000L)
            }

            // then
            val metrics = service.getCircuitBreakerMetrics()
            assertTrue(metrics.numberOfSuccessfulCalls >= 3, "Should have at least 3 successful calls")
            assertEquals(CircuitBreaker.State.CLOSED.name, metrics.state)
        }

        @Test
        @DisplayName("실패 호출 카운트 추적")
        fun `should track failed calls`() = runTest {
            // given
            fakePaymentClient.forceFailure = true

            // when - 2회 실패 시도
            repeat(2) {
                runCatching { service.processPayment(1L, 10000L) }
            }

            // then
            val metrics = service.getCircuitBreakerMetrics()
            assertTrue(metrics.numberOfFailedCalls >= 2, "Should have at least 2 failed calls")
        }
    }

    @Nested
    @DisplayName("수동 제어")
    inner class ManualControlTest {

        @Test
        @DisplayName("forceOpen()으로 OPEN 상태 전환")
        fun `should force circuit breaker to OPEN`() {
            // when
            service.forceOpen()

            // then
            assertEquals(CircuitBreaker.State.OPEN, service.getCircuitBreakerState())
        }

        @Test
        @DisplayName("forceClosed()로 CLOSED 상태 전환")
        fun `should force circuit breaker to CLOSED`() {
            // given
            service.forceOpen()

            // when
            service.forceClosed()

            // then
            assertEquals(CircuitBreaker.State.CLOSED, service.getCircuitBreakerState())
        }

        @Test
        @DisplayName("reset()으로 메트릭 초기화")
        fun `should reset circuit breaker metrics`() = runTest {
            // given
            fakePaymentClient.forceSuccess = true
            repeat(3) { service.processPayment(1L, 10000L) }

            // when
            service.reset()

            // then
            val metrics = service.getCircuitBreakerMetrics()
            assertEquals(CircuitBreaker.State.CLOSED.name, metrics.state)
            assertEquals(0, metrics.numberOfSuccessfulCalls)
        }
    }
}
