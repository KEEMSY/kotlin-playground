package com.playground.webflux.config

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

@Configuration
class ResilienceConfig {

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
