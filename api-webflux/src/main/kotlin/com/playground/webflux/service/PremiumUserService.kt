package com.playground.webflux.service

import com.playground.core.exception.NotFoundException
import com.playground.core.exception.ServiceUnavailableException
import com.playground.core.util.logger
import com.playground.infra.external.PaymentException
import com.playground.infra.external.PaymentStatus
import com.playground.infra.resilience.CircuitBreakerMetrics
import com.playground.infra.resilience.ResilientPaymentService
import com.playground.webflux.dto.CreatePremiumUserRequest
import com.playground.webflux.dto.PremiumUserResponse
import com.playground.webflux.repository.UserRepository
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.springframework.stereotype.Service

/**
 * 프리미엄 사용자 서비스 (Coroutines 버전)
 *
 * api-mvc와 동일한 로직이지만 suspend 함수로 구현됩니다.
 * runBlocking 없이 자연스럽게 비동기 처리됩니다.
 */
@Service
class PremiumUserService(
    private val userRepository: UserRepository,
    private val resilientPaymentService: ResilientPaymentService
) {
    private val log = logger()

    /**
     * 프리미엄 사용자로 업그레이드합니다.
     */
    suspend fun upgradeToPremium(request: CreatePremiumUserRequest): PremiumUserResponse {
        log.info("Upgrading user {} to {} plan", request.userId, request.plan)

        if (!userRepository.existsById(request.userId)) {
            throw NotFoundException("User not found with id: ${request.userId}")
        }

        return try {
            val result = resilientPaymentService.processPayment(request.userId, request.amount)

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
     */
    suspend fun upgradeToPremiumWithFallback(request: CreatePremiumUserRequest): PremiumUserResponse {
        log.info("Upgrading user {} to {} plan (with fallback)", request.userId, request.plan)

        if (!userRepository.existsById(request.userId)) {
            throw NotFoundException("User not found with id: ${request.userId}")
        }

        val result = resilientPaymentService.processPaymentWithFallback(request.userId, request.amount)

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

    fun getCircuitBreakerMetrics(): CircuitBreakerMetrics {
        return resilientPaymentService.getCircuitBreakerMetrics()
    }

    fun resetCircuitBreaker() {
        resilientPaymentService.reset()
        log.info("Circuit breaker has been reset")
    }
}
