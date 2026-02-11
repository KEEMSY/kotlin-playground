package com.playground.mvc.config

import com.playground.infra.external.FakePaymentClient
import com.playground.infra.external.PaymentClient
import com.playground.infra.external.PaymentException
import com.playground.infra.resilience.ResilientPaymentService
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeoutException

/**
 * Resilience4j 설정
 *
 * ## 프로그래매틱 설정 vs YAML 설정
 * - 프로그래매틱: 타입 안전, IDE 자동완성, 조건부 설정 가능
 * - YAML: 재시작 없이 변경 가능 (ConfigMap), 설정 분리
 *
 * 이 프로젝트는 학습 목적으로 프로그래매틱 설정을 사용합니다.
 */
@Configuration
class ResilienceConfig {

    /**
     * Circuit Breaker 설정
     *
     * - slidingWindowSize: 최근 10개 요청 기준으로 실패율 계산
     * - failureRateThreshold: 50% 이상 실패 시 OPEN
     * - waitDurationInOpenState: OPEN 상태에서 10초 대기 후 HALF_OPEN
     * - permittedNumberOfCallsInHalfOpenState: HALF_OPEN에서 3개 요청 허용
     * - slowCallDurationThreshold: 2초 이상 걸리면 slow call
     * - slowCallRateThreshold: 100% (slow call은 실패로 미포함)
     */
    @Bean
    fun circuitBreakerRegistry(): CircuitBreakerRegistry {
        val config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slowCallDurationThreshold(Duration.ofSeconds(2))
            .slowCallRateThreshold(100f)
            .recordExceptions(
                PaymentException::class.java,
                IOException::class.java,
                TimeoutException::class.java
            )
            .ignoreExceptions(
                IllegalArgumentException::class.java
            )
            .build()

        return CircuitBreakerRegistry.of(config)
    }

    /**
     * Retry 설정
     *
     * - maxAttempts: 최대 3회 시도 (첫 시도 + 2회 재시도)
     * - waitDuration: 500ms 간격으로 재시도
     * - retryExceptions: 재시도할 예외 지정
     */
    @Bean
    fun retryRegistry(): RetryRegistry {
        val config = RetryConfig.custom<Any>()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(
                PaymentException::class.java,
                IOException::class.java,
                TimeoutException::class.java
            )
            .ignoreExceptions(
                IllegalArgumentException::class.java
            )
            .build()

        return RetryRegistry.of(config)
    }

    /**
     * Rate Limiter 설정
     *
     * - limitForPeriod: 1초당 10개 요청 허용
     * - limitRefreshPeriod: 1초마다 리밋 리셋
     * - timeoutDuration: 허용량 초과 시 즉시 거부 (0)
     */
    @Bean
    fun rateLimiterRegistry(): RateLimiterRegistry {
        val config = RateLimiterConfig.custom()
            .limitForPeriod(10)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ZERO)
            .build()

        return RateLimiterRegistry.of(config)
    }

    @Bean
    fun paymentClient(): PaymentClient {
        return FakePaymentClient(
            failureRate = 30,
            slowCallRate = 20
        )
    }

    @Bean
    fun resilientPaymentService(
        paymentClient: PaymentClient,
        circuitBreakerRegistry: CircuitBreakerRegistry,
        retryRegistry: RetryRegistry,
        rateLimiterRegistry: RateLimiterRegistry
    ): ResilientPaymentService {
        return ResilientPaymentService(
            paymentClient,
            circuitBreakerRegistry,
            retryRegistry,
            rateLimiterRegistry
        )
    }
}
