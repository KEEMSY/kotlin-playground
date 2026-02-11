package com.playground.infra.resilience

import com.playground.infra.external.PaymentClient
import com.playground.infra.external.PaymentException
import com.playground.infra.external.PaymentResult
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import io.github.resilience4j.kotlin.ratelimiter.executeSuspendFunction
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryRegistry
import org.slf4j.LoggerFactory

/**
 * Resilience4j가 적용된 결제 서비스
 *
 * ## 적용된 패턴
 * 1. **Circuit Breaker**: 연속 실패 시 빠른 실패 (Fast Fail)
 * 2. **Retry**: 일시적 오류 시 자동 재시도
 * 3. **Rate Limiter**: API 호출 빈도 제한
 * 4. **Fallback**: 장애 시 대체 응답
 *
 * ## 실행 순서
 * Rate Limiter → Retry → Circuit Breaker → PaymentClient
 *
 * ## Circuit Breaker 상태
 * - CLOSED: 정상 상태, 모든 요청 허용
 * - OPEN: 장애 상태, 모든 요청 즉시 거부
 * - HALF_OPEN: 복구 시도 상태, 일부 요청만 허용
 */
class ResilientPaymentService(
    private val paymentClient: PaymentClient,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
    private val rateLimiterRegistry: RateLimiterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val circuitBreaker: CircuitBreaker = circuitBreakerRegistry.circuitBreaker("payment")
    private val retry: Retry = retryRegistry.retry("payment")
    private val rateLimiter: RateLimiter = rateLimiterRegistry.rateLimiter("payment")

    init {
        // Circuit Breaker 이벤트 로깅
        circuitBreaker.eventPublisher
            .onStateTransition { event ->
                log.info(
                    "Circuit Breaker state transition: {} -> {}",
                    event.stateTransition.fromState,
                    event.stateTransition.toState
                )
            }
            .onError { event ->
                log.debug("Circuit Breaker recorded error: {}", event.throwable.message)
            }
            .onSuccess { event ->
                log.debug("Circuit Breaker recorded success, duration: {}ms", event.elapsedDuration.toMillis())
            }

        // Retry 이벤트 로깅
        retry.eventPublisher
            .onRetry { event ->
                log.info(
                    "Retry attempt #{} for payment, waiting {}ms",
                    event.numberOfRetryAttempts,
                    event.waitInterval.toMillis()
                )
            }
    }

    /**
     * 결제를 처리합니다 (Circuit Breaker + Retry 적용).
     *
     * @throws CallNotPermittedException Circuit Breaker가 OPEN 상태일 때
     * @throws PaymentException 결제 실패 시 (재시도 후에도 실패)
     */
    suspend fun processPayment(userId: Long, amount: Long): PaymentResult {
        return retry.executeSuspendFunction {
            circuitBreaker.executeSuspendFunction {
                paymentClient.processPayment(userId, amount)
            }
        }
    }

    /**
     * 결제를 처리합니다 (Fallback 포함).
     *
     * Circuit Breaker가 OPEN 상태이거나 모든 재시도가 실패하면
     * Fallback으로 PENDING 상태를 반환합니다.
     */
    suspend fun processPaymentWithFallback(userId: Long, amount: Long): PaymentResult {
        return try {
            processPayment(userId, amount)
        } catch (e: CallNotPermittedException) {
            log.warn("Circuit breaker is OPEN, using fallback for userId={}", userId)
            PaymentResult.pending(userId, amount)
        } catch (e: PaymentException) {
            log.warn("Payment failed after retries, using fallback for userId={}", userId)
            PaymentResult.pending(userId, amount)
        }
    }

    /**
     * 결제를 처리합니다 (Rate Limiter + Circuit Breaker + Retry 적용).
     *
     * API 호출 빈도를 제한하여 외부 서비스를 보호합니다.
     */
    suspend fun processPaymentWithRateLimit(userId: Long, amount: Long): PaymentResult {
        return rateLimiter.executeSuspendFunction {
            processPayment(userId, amount)
        }
    }

    /**
     * Circuit Breaker 상태를 반환합니다.
     */
    fun getCircuitBreakerState(): CircuitBreaker.State {
        return circuitBreaker.state
    }

    /**
     * Circuit Breaker 메트릭을 반환합니다.
     */
    fun getCircuitBreakerMetrics(): CircuitBreakerMetrics {
        val metrics = circuitBreaker.metrics
        return CircuitBreakerMetrics(
            state = circuitBreaker.state.name,
            failureRate = metrics.failureRate,
            slowCallRate = metrics.slowCallRate,
            numberOfSuccessfulCalls = metrics.numberOfSuccessfulCalls,
            numberOfFailedCalls = metrics.numberOfFailedCalls,
            numberOfSlowCalls = metrics.numberOfSlowCalls,
            numberOfNotPermittedCalls = metrics.numberOfNotPermittedCalls
        )
    }

    /**
     * Circuit Breaker를 수동으로 OPEN 상태로 전환합니다.
     * 테스트 또는 운영 시 강제 차단에 사용합니다.
     */
    fun forceOpen() {
        circuitBreaker.transitionToOpenState()
        log.warn("Circuit breaker forced to OPEN state")
    }

    /**
     * Circuit Breaker를 수동으로 CLOSED 상태로 전환합니다.
     */
    fun forceClosed() {
        circuitBreaker.transitionToClosedState()
        log.info("Circuit breaker forced to CLOSED state")
    }

    /**
     * Circuit Breaker를 리셋합니다.
     */
    fun reset() {
        circuitBreaker.reset()
        log.info("Circuit breaker reset")
    }
}

data class CircuitBreakerMetrics(
    val state: String,
    val failureRate: Float,
    val slowCallRate: Float,
    val numberOfSuccessfulCalls: Int,
    val numberOfFailedCalls: Int,
    val numberOfSlowCalls: Int,
    val numberOfNotPermittedCalls: Long
)
