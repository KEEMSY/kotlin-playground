package com.playground.webflux.controller

import com.playground.infra.resilience.CircuitBreakerMetrics
import com.playground.webflux.dto.CreatePremiumUserRequest
import com.playground.webflux.dto.PremiumUserResponse
import com.playground.webflux.service.PremiumUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Premium Users", description = "프리미엄 사용자 관리 API (Resilience4j 학습용)")
@RestController
@RequestMapping("/api/v1/premium-users")
class PremiumUserController(
    private val premiumUserService: PremiumUserService
) {

    @Operation(
        summary = "프리미엄 업그레이드",
        description = """
            사용자를 프리미엄으로 업그레이드합니다.

            ## Resilience4j 적용
            - Circuit Breaker: 연속 실패 시 503 Service Unavailable
            - Retry: 일시적 오류 시 최대 3회 재시도

            ## 테스트 방법
            1. 여러 번 호출하여 일부 실패 확인
            2. /circuit-breaker/metrics로 상태 확인
            3. 50% 이상 실패 시 Circuit Breaker OPEN
        """
    )
    @PostMapping
    suspend fun upgradeToPremium(
        @Valid @RequestBody request: CreatePremiumUserRequest
    ): ResponseEntity<PremiumUserResponse> {
        val response = premiumUserService.upgradeToPremium(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(
        summary = "프리미엄 업그레이드 (Fallback 적용)",
        description = """
            결제 실패 시에도 PENDING 상태로 응답합니다.
            실제 결제는 백그라운드에서 재처리됩니다.
        """
    )
    @PostMapping("/with-fallback")
    suspend fun upgradeToPremiumWithFallback(
        @Valid @RequestBody request: CreatePremiumUserRequest
    ): ResponseEntity<PremiumUserResponse> {
        val response = premiumUserService.upgradeToPremiumWithFallback(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(
        summary = "Circuit Breaker 상태 조회",
        description = """
            현재 Circuit Breaker의 상태와 메트릭을 반환합니다.

            ## 상태
            - CLOSED: 정상 상태
            - OPEN: 장애 상태 (모든 요청 거부)
            - HALF_OPEN: 복구 시도 상태
        """
    )
    @GetMapping("/circuit-breaker/metrics")
    fun getCircuitBreakerMetrics(): CircuitBreakerMetrics {
        return premiumUserService.getCircuitBreakerMetrics()
    }

    @Operation(
        summary = "Circuit Breaker 리셋",
        description = "Circuit Breaker를 CLOSED 상태로 리셋합니다."
    )
    @PostMapping("/circuit-breaker/reset")
    fun resetCircuitBreaker(): ResponseEntity<Map<String, String>> {
        premiumUserService.resetCircuitBreaker()
        return ResponseEntity.ok(mapOf("message" to "Circuit breaker has been reset"))
    }
}
