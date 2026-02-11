package com.playground.mvc.service

import com.playground.core.exception.NotFoundException
import com.playground.core.exception.ServiceUnavailableException
import com.playground.core.util.logger
import com.playground.infra.external.PaymentException
import com.playground.infra.external.PaymentStatus
import com.playground.infra.resilience.CircuitBreakerMetrics
import com.playground.infra.resilience.ResilientPaymentService
import com.playground.mvc.dto.CreatePremiumUserRequest
import com.playground.mvc.dto.PremiumUserResponse
import com.playground.mvc.repository.UserRepository
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service

/**
 * 프리미엄 사용자 서비스
 *
 * 외부 결제 API 호출 시 Resilience4j를 적용하여
 * Circuit Breaker, Retry, Fallback 패턴을 구현합니다.
 */
@Service
class PremiumUserService(
    private val userRepository: UserRepository,
    private val resilientPaymentService: ResilientPaymentService
) {
    private val log = logger()

    /**
     * 프리미엄 사용자로 업그레이드합니다.
     *
     * 결제 실패 시:
     * - Circuit Breaker OPEN: ServiceUnavailableException (503)
     * - 결제 오류: 재시도 후 실패하면 ServiceUnavailableException
     */
    fun upgradeToPremium(request: CreatePremiumUserRequest): PremiumUserResponse {
        log.info("Upgrading user {} to {} plan", request.userId, request.plan)

        // 사용자 존재 확인
        if (!userRepository.existsById(request.userId)) {
            throw NotFoundException("User not found with id: ${request.userId}")
        }

        return try {
            // 결제 처리 (Circuit Breaker + Retry 적용)
            val result = runBlocking {
                resilientPaymentService.processPayment(request.userId, request.amount)
            }

            log.info("Payment successful: transactionId={}", result.transactionId)

            PremiumUserResponse(
                userId = request.userId,
                plan = request.plan,
                transactionId = result.transactionId,
                paymentStatus = result.status,
                message = "Successfully upgraded to ${request.plan} plan"
            )
        } catch (e: CallNotPermittedException) {
            log.error("Circuit breaker is OPEN, payment service unavailable")
            throw ServiceUnavailableException(
                message = "Payment service is temporarily unavailable. Please try again later.",
                cause = e
            )
        } catch (e: PaymentException) {
            log.error("Payment failed after retries: {}", e.message)
            throw ServiceUnavailableException(
                message = "Payment processing failed: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * 프리미엄 사용자로 업그레이드합니다 (Fallback 적용).
     *
     * 결제 실패 시 PENDING 상태로 응답합니다.
     * 실제 결제는 백그라운드에서 재처리됩니다.
     */
    fun upgradeToPremiumWithFallback(request: CreatePremiumUserRequest): PremiumUserResponse {
        log.info("Upgrading user {} to {} plan (with fallback)", request.userId, request.plan)

        if (!userRepository.existsById(request.userId)) {
            throw NotFoundException("User not found with id: ${request.userId}")
        }

        val result = runBlocking {
            resilientPaymentService.processPaymentWithFallback(request.userId, request.amount)
        }

        val message = when (result.status) {
            PaymentStatus.COMPLETED -> "Successfully upgraded to ${request.plan} plan"
            PaymentStatus.PENDING -> "Payment is being processed. You will be notified when complete."
            PaymentStatus.FAILED -> "Payment failed. Please try again."
        }

        return PremiumUserResponse(
            userId = request.userId,
            plan = request.plan,
            transactionId = result.transactionId,
            paymentStatus = result.status,
            message = message
        )
    }

    /**
     * Circuit Breaker 상태를 반환합니다.
     */
    fun getCircuitBreakerMetrics(): CircuitBreakerMetrics {
        return resilientPaymentService.getCircuitBreakerMetrics()
    }

    /**
     * Circuit Breaker를 수동으로 리셋합니다.
     */
    fun resetCircuitBreaker() {
        resilientPaymentService.reset()
        log.info("Circuit breaker has been reset")
    }
}
