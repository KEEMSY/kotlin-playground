package com.playground.infra.resilience

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import io.github.resilience4j.kotlin.ratelimiter.executeSuspendFunction
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.kotlin.timelimiter.executeSuspendFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.timelimiter.TimeLimiter
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

/**
 * Resilience4j + Kotlin Coroutines 확장 함수
 *
 * Resilience4j의 resilience4j-kotlin 모듈이 제공하는 확장 함수를 사용합니다.
 * 이 파일은 추가적인 유틸리티 함수를 제공합니다.
 */

/**
 * Circuit Breaker와 Retry를 조합하여 실행합니다.
 *
 * 실행 순서: Retry → Circuit Breaker → 비즈니스 로직
 * - Retry는 Circuit Breaker 외부에서 재시도 (Circuit Breaker OPEN 시에도 재시도)
 * - Circuit Breaker는 실제 호출 실패를 추적
 */
suspend fun <T> executeWithRetryAndCircuitBreaker(
    retry: Retry,
    circuitBreaker: CircuitBreaker,
    block: suspend () -> T
): T {
    return retry.executeSuspendFunction {
        circuitBreaker.executeSuspendFunction {
            block()
        }
    }
}

/**
 * Circuit Breaker, Retry, Rate Limiter를 모두 조합하여 실행합니다.
 *
 * 실행 순서: Rate Limiter → Retry → Circuit Breaker → 비즈니스 로직
 */
suspend fun <T> executeWithAllProtections(
    rateLimiter: RateLimiter,
    retry: Retry,
    circuitBreaker: CircuitBreaker,
    block: suspend () -> T
): T {
    return rateLimiter.executeSuspendFunction {
        retry.executeSuspendFunction {
            circuitBreaker.executeSuspendFunction {
                block()
            }
        }
    }
}
