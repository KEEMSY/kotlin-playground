# Resilience4j 학습 가이드

## 목차
1. [Resilience4j란?](#resilience4j란)
2. [핵심 패턴](#핵심-패턴)
3. [프로젝트 구조](#프로젝트-구조)
4. [Circuit Breaker 상세](#circuit-breaker-상세)
5. [Retry 상세](#retry-상세)
6. [Rate Limiter 상세](#rate-limiter-상세)
7. [Kotlin Coroutines 통합](#kotlin-coroutines-통합)
8. [테스트 방법](#테스트-방법)
9. [모니터링](#모니터링)
10. [의사결정 기록](#의사결정-기록)

---

## Resilience4j란?

Resilience4j는 함수형 프로그래밍 기반의 경량 장애 허용(fault tolerance) 라이브러리입니다.

### 왜 필요한가?

분산 시스템에서 외부 서비스 호출은 언제든 실패할 수 있습니다:
- 네트워크 지연
- 서비스 과부하
- 일시적 장애

이런 상황에서 **장애 전파**를 막고 **시스템 복원력**을 높이는 것이 목표입니다.

```
[장애 전파 없음]
Service A → Circuit Breaker → 외부 API (장애)
                 ↓
           빠른 실패 (503)

[장애 전파 발생]
Service A → 외부 API (장애)
                 ↓
          타임아웃 대기 → 스레드 고갈 → Service A 장애 → 연쇄 장애
```

### Netflix Hystrix와의 비교

| 항목 | Resilience4j | Hystrix |
|------|-------------|---------|
| 상태 | 활발히 유지보수 | 유지보수 종료 |
| 아키텍처 | 함수형, 경량 | 복잡, 무거움 |
| Spring Boot 3 | 지원 | 미지원 |
| Kotlin Coroutines | 지원 | 미지원 |

---

## 핵심 패턴

### 1. Circuit Breaker (회로 차단기)
연속 실패 시 요청을 차단하여 장애 전파 방지

### 2. Retry (재시도)
일시적 오류 시 자동으로 재시도

### 3. Rate Limiter (호출 빈도 제한)
초당 요청 수를 제한하여 외부 서비스 보호

### 4. Time Limiter (시간 제한)
응답 지연 시 빠른 실패

### 5. Bulkhead (격벽)
동시 호출 수를 제한하여 리소스 격리

### 패턴 조합 순서
```
요청 → Rate Limiter → Retry → Circuit Breaker → Time Limiter → 외부 서비스
```

---

## 프로젝트 구조

```
kotlin-playground/
├── infra/
│   └── src/main/kotlin/com/playground/infra/
│       ├── external/
│       │   ├── PaymentClient.kt           # 외부 API 인터페이스
│       │   ├── FakePaymentClient.kt       # 실패 시뮬레이션 구현체
│       │   ├── PaymentResult.kt           # 결제 결과 DTO
│       │   └── PaymentException.kt        # 결제 예외
│       └── resilience/
│           ├── ResilientPaymentService.kt # Circuit Breaker 적용 서비스
│           └── CircuitBreakerExtensions.kt # Kotlin 확장 함수
├── api-mvc/
│   └── src/main/kotlin/.../
│       ├── config/ResilienceConfig.kt     # 설정 빈 등록
│       ├── service/PremiumUserService.kt  # 비즈니스 로직
│       └── controller/PremiumUserController.kt # API 엔드포인트
└── api-webflux/
    └── src/main/kotlin/.../
        ├── config/ResilienceConfig.kt     # 설정 빈 등록
        ├── service/PremiumUserService.kt  # Coroutines 버전
        └── controller/PremiumUserController.kt # API 엔드포인트
```

---

## Circuit Breaker 상세

### 상태 다이어그램

```
         실패율 < 임계값
    ┌──────────────────────────────┐
    │                              │
    ▼                              │
┌────────┐   실패율 >= 임계값   ┌────────┐
│ CLOSED │ ─────────────────────▶ │  OPEN  │
└────────┘                       └────────┘
    ▲                              │
    │                              │ 대기 시간 경과
    │      일부 요청 허용          ▼
    │   ┌─────────────────────────────┐
    │   │       HALF_OPEN             │
    │   └─────────────────────────────┘
    │              │
    │              │ 성공률 >= 임계값
    └──────────────┘
```

### 상태 설명

| 상태 | 설명 | 요청 허용 |
|------|------|----------|
| **CLOSED** | 정상 상태 | 모든 요청 허용 |
| **OPEN** | 장애 상태 | 모든 요청 즉시 거부 |
| **HALF_OPEN** | 복구 시도 | 일부 요청만 허용 |

### 설정 옵션

```kotlin
CircuitBreakerConfig.custom()
    // Sliding Window 설정
    .slidingWindowType(SlidingWindowType.COUNT_BASED)  // 횟수 기반 (또는 TIME_BASED)
    .slidingWindowSize(10)                              // 최근 10개 요청 기준
    .minimumNumberOfCalls(5)                            // 최소 5회 호출 후 계산 시작

    // 실패율 임계값
    .failureRateThreshold(50f)                          // 50% 이상 실패 시 OPEN

    // OPEN → HALF_OPEN 전이
    .waitDurationInOpenState(Duration.ofSeconds(10))    // 10초 대기 후 HALF_OPEN
    .permittedNumberOfCallsInHalfOpenState(3)           // HALF_OPEN에서 3개 요청 허용

    // Slow Call 설정
    .slowCallDurationThreshold(Duration.ofSeconds(2))   // 2초 이상 → slow call
    .slowCallRateThreshold(100f)                        // slow call은 실패로 미포함

    // 예외 설정
    .recordExceptions(PaymentException::class.java)     // 실패로 기록할 예외
    .ignoreExceptions(IllegalArgumentException::class.java) // 무시할 예외
    .build()
```

### 코드 예시

```kotlin
// 기본 사용
suspend fun processPayment(userId: Long, amount: Long): PaymentResult {
    return circuitBreaker.executeSuspendFunction {
        paymentClient.processPayment(userId, amount)
    }
}

// Fallback 포함
suspend fun processPaymentWithFallback(userId: Long, amount: Long): PaymentResult {
    return try {
        processPayment(userId, amount)
    } catch (e: CallNotPermittedException) {
        // Circuit Breaker OPEN 상태
        PaymentResult.pending(userId, amount)
    }
}
```

---

## Retry 상세

### 설정 옵션

```kotlin
RetryConfig.custom<Any>()
    .maxAttempts(3)                                  // 최대 3회 시도
    .waitDuration(Duration.ofMillis(500))            // 재시도 간격 500ms
    .retryExceptions(                                // 재시도할 예외
        PaymentException::class.java,
        IOException::class.java
    )
    .ignoreExceptions(IllegalArgumentException::class.java)
    .build()
```

### 지수 백오프 (Exponential Backoff)

```kotlin
RetryConfig.custom<Any>()
    .maxAttempts(5)
    .intervalFunction(IntervalFunction.ofExponentialBackoff(
        Duration.ofMillis(100),  // 초기 간격
        2.0                       // 배수
    ))
    // 100ms → 200ms → 400ms → 800ms → 1600ms
    .build()
```

### Retry + Circuit Breaker 조합

```kotlin
// 실행 순서: Retry → Circuit Breaker
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
```

**왜 Retry가 바깥에 있는가?**
- Circuit Breaker OPEN 시 Retry가 CallNotPermittedException을 받아 재시도하지 않음
- 실제 외부 호출 실패만 재시도

---

## Rate Limiter 상세

### 설정 옵션

```kotlin
RateLimiterConfig.custom()
    .limitForPeriod(10)                     // 기간당 허용 요청 수
    .limitRefreshPeriod(Duration.ofSeconds(1))  // 리밋 리셋 주기
    .timeoutDuration(Duration.ZERO)         // 대기 시간 (0 = 즉시 거부)
    .build()
```

### 사용 시나리오

| 상황 | limitForPeriod | limitRefreshPeriod |
|------|---------------|-------------------|
| 외부 API 제한 | 10 | 1초 |
| 배치 작업 | 100 | 1분 |
| 사용자별 제한 | 5 | 1초 |

### Rate Limiter vs Bulkhead

| 항목 | Rate Limiter | Bulkhead |
|------|-------------|----------|
| 제한 대상 | 호출 빈도 (시간당) | 동시 호출 수 |
| 사용 사례 | 외부 API 제한 | 리소스 격리 |

---

## Kotlin Coroutines 통합

### resilience4j-kotlin 모듈

```kotlin
// Circuit Breaker
circuitBreaker.executeSuspendFunction {
    suspendFun()
}

// Retry
retry.executeSuspendFunction {
    suspendFun()
}

// Rate Limiter
rateLimiter.executeSuspendFunction {
    suspendFun()
}
```

### MVC vs WebFlux

| 항목 | api-mvc | api-webflux |
|------|---------|-------------|
| 호출 방식 | `runBlocking { }` | `suspend fun` 직접 |
| 스레드 | 블로킹 허용 | 이벤트 루프 보호 |

```kotlin
// api-mvc (동기)
fun upgradeToPremium(request: Request): Response {
    val result = runBlocking {
        resilientPaymentService.processPayment(...)
    }
    return Response.from(result)
}

// api-webflux (비동기)
suspend fun upgradeToPremium(request: Request): Response {
    val result = resilientPaymentService.processPayment(...)
    return Response.from(result)
}
```

---

## 테스트 방법

### 1. 애플리케이션 실행

```bash
# Docker Redis 시작
docker-compose -f docker/docker-compose.yml up -d redis postgres

# api-mvc 실행
./gradlew :api-mvc:bootRun --args='--spring.profiles.active=local'

# 또는 api-webflux 실행
./gradlew :api-webflux:bootRun --args='--spring.profiles.active=local'
```

### 2. API 호출 테스트

```bash
# 프리미엄 업그레이드 (30% 확률로 실패)
curl -X POST localhost:8080/api/v1/premium-users \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"plan":"PREMIUM","amount":29900}'

# Fallback 버전 (실패해도 PENDING 반환)
curl -X POST localhost:8080/api/v1/premium-users/with-fallback \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"plan":"PREMIUM","amount":29900}'
```

### 3. Circuit Breaker 테스트

```bash
# 연속 요청으로 Circuit Breaker 트리거
for i in {1..20}; do
  curl -s -X POST localhost:8080/api/v1/premium-users \
    -H "Content-Type: application/json" \
    -d '{"userId":1,"plan":"PREMIUM"}' &
done
wait

# Circuit Breaker 상태 확인
curl localhost:8080/api/v1/premium-users/circuit-breaker/metrics
# 예상 결과: state: "OPEN" (50% 이상 실패 시)

# Circuit Breaker 리셋
curl -X POST localhost:8080/api/v1/premium-users/circuit-breaker/reset
```

### 4. 단위 테스트

```bash
# Resilience 테스트
./gradlew :infra:test --tests "*ResilientPaymentServiceTest*"

# 전체 테스트
./gradlew test
```

---

## 모니터링

### Actuator 엔드포인트

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,circuitbreakers,retries,ratelimiters
  health:
    circuitbreakers:
      enabled: true
```

### 엔드포인트 사용

```bash
# Circuit Breaker 전체 상태
curl localhost:8080/actuator/circuitbreakers

# 특정 Circuit Breaker 상태
curl localhost:8080/actuator/circuitbreakers/payment

# Retry 상태
curl localhost:8080/actuator/retries

# Rate Limiter 상태
curl localhost:8080/actuator/ratelimiters
```

### 커스텀 메트릭 API

```bash
# 이 프로젝트에서 제공하는 API
curl localhost:8080/api/v1/premium-users/circuit-breaker/metrics

# 응답 예시
{
  "state": "CLOSED",
  "failureRate": 20.0,
  "slowCallRate": 0.0,
  "numberOfSuccessfulCalls": 8,
  "numberOfFailedCalls": 2,
  "numberOfSlowCalls": 0,
  "numberOfNotPermittedCalls": 0
}
```

---

## 의사결정 기록

### Q1: Resilience4j 버전 선택

**결정**: resilience4j 2.2.0

**Why**:
- Spring Boot 3.2.5 호환
- Kotlin Coroutines 완벽 지원
- 최신 안정 버전

**Alternatives**:
- resilience4j 1.x: Spring Boot 2 대상
- Polly (.NET): 다른 생태계
- Hystrix: 유지보수 종료

### Q2: AOP vs 프로그래매틱 방식

**결정**: 프로그래매틱 방식 (직접 호출)

**Why**:
- 학습 목적으로 코드 흐름을 명시적으로 이해
- 디버깅이 쉬움
- IDE에서 추적 가능

**Alternatives**:
```kotlin
// AOP 방식 (선택하지 않음)
@CircuitBreaker(name = "payment")
@Retry(name = "payment")
suspend fun processPayment(userId: Long, amount: Long): PaymentResult
```

**Trade-offs**:
| 프로그래매틱 | AOP |
|-------------|-----|
| 명시적, 추적 용이 | 깔끔한 코드 |
| 보일러플레이트 | 숨겨진 동작 |
| 조건부 적용 가능 | 선언적 |

### Q3: Fallback 전략

**결정**: PENDING 상태 반환

**Why**:
- 결제 실패 시 사용자에게 명확한 피드백
- 백그라운드 재처리 가능
- UX 저하 최소화

**Alternatives**:
- 즉시 에러: 사용자 경험 나쁨
- 캐시된 데이터: 결제에는 부적합
- 기본값: 결제에는 위험

### Q4: 외부 API 시뮬레이션 방식

**결정**: FakePaymentClient (확률적 실패)

**Why**:
- 실제 외부 API 없이 학습 가능
- 테스트 재현성
- 실패 시나리오 제어 가능

**Alternatives**:
- WireMock: 더 현실적이지만 복잡
- 실제 Sandbox API: 네트워크 의존성

---

## 다음 학습 단계

1. **Prometheus + Grafana**: 메트릭 시각화 대시보드
2. **Testcontainers**: Redis 포함 통합 테스트
3. **Distributed Tracing**: 분산 추적 (Zipkin, Jaeger)
4. **Spring Cloud Gateway**: API Gateway에서 Circuit Breaker 적용
