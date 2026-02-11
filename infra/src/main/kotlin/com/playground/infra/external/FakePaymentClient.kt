package com.playground.infra.external

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * 외부 결제 API 시뮬레이션 클라이언트
 *
 * Circuit Breaker 학습을 위해 다양한 실패 시나리오를 시뮬레이션합니다:
 * - 30% 확률로 실패 (PaymentException)
 * - 20% 확률로 지연 (500ms ~ 3000ms)
 * - 50% 확률로 정상 응답 (50ms ~ 200ms)
 *
 * 실패 모드를 수동으로 전환할 수 있어 테스트에 유용합니다.
 */
class FakePaymentClient(
    private val failureRate: Int = 30,       // 실패 확률 (%)
    private val slowCallRate: Int = 20,      // 지연 응답 확률 (%)
    private val normalLatencyMs: LongRange = 50L..200L,
    private val slowLatencyMs: LongRange = 500L..3000L
) : PaymentClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val callCount = AtomicInteger(0)
    private val failureCount = AtomicInteger(0)

    @Volatile
    var forceFailure: Boolean = false

    @Volatile
    var forceSuccess: Boolean = false

    override suspend fun processPayment(userId: Long, amount: Long): PaymentResult {
        val currentCall = callCount.incrementAndGet()
        log.debug("Processing payment #{}: userId={}, amount={}", currentCall, userId, amount)

        // 강제 실패 모드
        if (forceFailure) {
            failureCount.incrementAndGet()
            log.warn("Payment #{} forced to fail", currentCall)
            throw PaymentException("Forced failure mode enabled")
        }

        // 강제 성공 모드
        if (forceSuccess) {
            delay(normalLatencyMs.random())
            return createSuccessResult(userId, amount)
        }

        // 확률적 실패/지연 시뮬레이션
        val random = Random().nextInt(100)

        return when {
            random < failureRate -> {
                // 실패 시나리오
                failureCount.incrementAndGet()
                val errorType = listOf(
                    "Connection timeout",
                    "Service unavailable",
                    "Internal server error",
                    "Rate limit exceeded"
                ).random()
                log.warn("Payment #{} failed: {}", currentCall, errorType)
                throw PaymentException("Payment failed: $errorType")
            }

            random < failureRate + slowCallRate -> {
                // 지연 응답 시나리오
                val latency = slowLatencyMs.random()
                log.debug("Payment #{} slow response: {}ms", currentCall, latency)
                delay(latency)
                createSuccessResult(userId, amount)
            }

            else -> {
                // 정상 응답
                val latency = normalLatencyMs.random()
                delay(latency)
                log.debug("Payment #{} succeeded in {}ms", currentCall, latency)
                createSuccessResult(userId, amount)
            }
        }
    }

    override suspend fun cancelPayment(transactionId: String): PaymentResult {
        log.debug("Cancelling payment: {}", transactionId)
        delay(normalLatencyMs.random())

        if (forceFailure) {
            throw PaymentException("Cancel failed: Forced failure mode")
        }

        return PaymentResult(
            transactionId = transactionId,
            status = PaymentStatus.FAILED
        )
    }

    private fun createSuccessResult(userId: Long, amount: Long): PaymentResult {
        return PaymentResult(
            transactionId = UUID.randomUUID().toString(),
            status = PaymentStatus.COMPLETED,
            userId = userId,
            amount = amount
        )
    }

    fun getStats(): PaymentClientStats {
        return PaymentClientStats(
            totalCalls = callCount.get(),
            failedCalls = failureCount.get(),
            successRate = if (callCount.get() > 0) {
                ((callCount.get() - failureCount.get()) * 100.0 / callCount.get())
            } else 0.0
        )
    }

    fun resetStats() {
        callCount.set(0)
        failureCount.set(0)
    }
}

data class PaymentClientStats(
    val totalCalls: Int,
    val failedCalls: Int,
    val successRate: Double
)

private fun LongRange.random(): Long = Random().nextLong(this.first, this.last + 1)
