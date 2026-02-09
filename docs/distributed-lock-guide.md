# Redis 분산 락 (Distributed Lock) 학습 가이드

## 목차
1. [분산 락이란?](#분산-락이란)
2. [왜 필요한가?](#왜-필요한가)
3. [구현 원리](#구현-원리)
4. [프로젝트 구조](#프로젝트-구조)
5. [개발 환경 설정](#개발-환경-설정)
6. [사용 방법](#사용-방법)
7. [운영 환경 가이드](#운영-환경-가이드)
8. [의사결정 기록](#의사결정-기록)

---

## 분산 락이란?

분산 락(Distributed Lock)은 여러 서버(또는 프로세스)가 공유 자원에 동시에 접근하는 것을 방지하는 동기화 메커니즘입니다.

### 단일 서버 vs 분산 환경

```
[단일 서버]
Thread A ─┐
Thread B ─┼─→ synchronized/Lock ─→ 공유 자원
Thread C ─┘

[분산 환경 - 락 없음]
Server 1 ─→ 공유 자원 ←─ Server 2   ❌ Race Condition!

[분산 환경 - 분산 락]
Server 1 ─┐
          ├─→ Redis Lock ─→ 공유 자원
Server 2 ─┘
```

---

## 왜 필요한가?

### 문제 상황: Race Condition

`UserService.createUser()`의 기존 코드:

```kotlin
@Transactional
fun createUser(request: CreateUserRequest): UserResponse {
    // 1. 이메일 중복 확인
    if (userRepository.existsByEmail(request.email)) {
        throw ConflictException("User already exists")
    }
    // 2. 사용자 저장
    return userRepository.save(request.toEntity())
}
```

**동시 요청 시 발생하는 문제:**

```
시간 →
Request 1: existsByEmail("a@test.com") → false ─────────────── save() → OK
Request 2: ──────── existsByEmail("a@test.com") → false ────── save() → UNIQUE CONSTRAINT ERROR!
```

- 두 요청이 거의 동시에 도착하면, 둘 다 `existsByEmail`이 false를 반환
- 둘 다 저장을 시도하지만, 하나는 DB Unique Constraint 에러 발생
- 예상치 못한 500 에러가 사용자에게 노출됨

### 해결: 분산 락 적용 후

```
시간 →
Request 1: [락 획득] existsByEmail → false → save() → OK [락 해제]
Request 2: ─────────[대기]────────────────────────────────[락 획득] existsByEmail → true → ConflictException
```

---

## 구현 원리

### 1. 락 획득: SET NX EX

Redis의 `SET` 명령어에 옵션을 조합하여 원자적 락 획득:

```
SET lock:user:email:test@example.com <uuid> NX EX 10
```

- `NX` (Not eXists): 키가 없을 때만 설정
- `EX 10`: 10초 후 자동 만료 (leaseTime)
- `<uuid>`: 락 소유자 식별값 (다른 프로세스의 락을 잘못 해제하지 않도록)

### 2. 락 해제: Lua 스크립트

**왜 Lua 스크립트가 필요한가?**

단순히 GET + DEL을 별도로 실행하면:

```
Thread A: GET lock → "value-A" (내 락 확인)
Thread B: (A의 락 만료로) SET lock → "value-B" (새 락 획득)
Thread A: DEL lock (B의 락을 잘못 삭제!)  ❌
```

**Lua 스크립트로 원자적 처리:**

```lua
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
else
    return 0
end
```

Redis에서 Lua 스크립트는 원자적으로 실행되므로, "확인 후 삭제"가 중간에 끊기지 않습니다.

### 3. 재시도 로직

락이 이미 점유 중이면, 일정 시간(`waitTime`) 동안 재시도:

```kotlin
while (System.currentTimeMillis() < deadline) {
    if (tryAcquire()) return lockValue
    Thread.sleep(50)  // 또는 delay() for Coroutines
}
throw LockAcquisitionException(...)
```

---

## 프로젝트 구조

```
kotlin-playground/
├── infra/
│   └── src/main/kotlin/com/playground/infra/lock/
│       ├── DistributedLockService.kt         # 동기 인터페이스
│       ├── ReactiveDistributedLockService.kt # Reactive 인터페이스
│       ├── LockAcquisitionException.kt       # 예외 클래스
│       └── lettuce/
│           ├── LettuceLockService.kt         # 동기 구현 (api-mvc용)
│           └── ReactiveLettuceLockService.kt # Reactive 구현 (api-webflux용)
├── api-mvc/
│   └── src/main/kotlin/.../config/RedisConfig.kt
│   └── src/main/kotlin/.../service/UserService.kt
├── api-webflux/
│   └── src/main/kotlin/.../config/RedisConfig.kt
│   └── src/main/kotlin/.../service/UserService.kt
└── docker/
    ├── docker-compose.yml
    └── redis/redis.conf
```

### 동기 vs Reactive 구현 차이

| 항목 | LettuceLockService | ReactiveLettuceLockService |
|------|-------------------|---------------------------|
| 대기 | `Thread.sleep()` | `delay()` |
| Template | `StringRedisTemplate` | `ReactiveStringRedisTemplate` |
| 함수 | 일반 함수 | `suspend fun` |
| 사용처 | api-mvc | api-webflux |

---

## 개발 환경 설정

### 1. Redis 시작

```bash
docker-compose -f docker/docker-compose.yml up -d redis
```

### 2. 애플리케이션 실행

```bash
# api-mvc (포트 8080)
./gradlew :api-mvc:bootRun --args='--spring.profiles.active=local'

# api-webflux (포트 8081)
./gradlew :api-webflux:bootRun --args='--spring.profiles.active=local'
```

### 3. 테스트

```bash
# 분산 락 테스트
./gradlew :infra:test --tests "*LettuceLockServiceTest*"

# 동시성 테스트
./gradlew :api-mvc:test --tests "*UserServiceConcurrencyTest*"
./gradlew :api-webflux:test --tests "*UserServiceConcurrencyTest*"
```

### 4. 수동 검증

```bash
# 동시 요청 시뮬레이션
for i in {1..10}; do
  curl -X POST localhost:8080/api/v1/users \
    -H "Content-Type: application/json" \
    -d '{"email":"race-test@example.com","name":"User"}' &
done
wait

# 결과 확인
# - 락 없음: 일부 500 에러 또는 중복 생성
# - 락 있음: 1개 201 Created, 나머지 409 Conflict
```

---

## 사용 방법

### api-mvc (동기)

```kotlin
@Service
class UserService(
    private val userRepository: UserRepository,
    private val lockService: DistributedLockService  // 주입
) {
    @Transactional
    fun createUser(request: CreateUserRequest): UserResponse {
        return lockService.executeWithLock("user:email:${request.email}") {
            if (userRepository.existsByEmail(request.email)) {
                throw ConflictException("User already exists")
            }
            userRepository.save(request.toEntity())
        }
    }
}
```

### api-webflux (Reactive)

```kotlin
@Service
class UserService(
    private val userRepository: UserRepository,
    private val lockService: ReactiveDistributedLockService  // 주입
) {
    @Transactional
    suspend fun createUser(request: CreateUserRequest): UserResponse {
        return lockService.executeWithLock("user:email:${request.email}") {
            if (userRepository.existsByEmail(request.email)) {
                throw ConflictException("User already exists")
            }
            userRepository.save(request.toEntity())
        }
    }
}
```

### 파라미터 조정

```kotlin
lockService.executeWithLock(
    lockKey = "payment:order:${orderId}",
    waitTime = Duration.ofSeconds(10),   // 락 대기 최대 시간
    leaseTime = Duration.ofSeconds(60)   // 락 보유 최대 시간
) {
    // 비즈니스 로직
}
```

---

## 운영 환경 가이드

### TTL 전략

| 상황 | waitTime | leaseTime | 이유 |
|------|----------|-----------|------|
| 사용자 생성 | 3초 | 10초 | 빠른 작업, 실패 시 즉시 재시도 유도 |
| 결제 처리 | 10초 | 60초 | 외부 API 호출 포함, 여유 필요 |
| 배치 작업 | 30초 | 5분 | 대량 데이터 처리 |

### Redis 고가용성 (HA)

#### Sentinel 구성

```yaml
# application-prod.yml
spring:
  data:
    redis:
      sentinel:
        master: mymaster
        nodes:
          - sentinel-1:26379
          - sentinel-2:26379
          - sentinel-3:26379
```

#### Cluster 구성

```yaml
spring:
  data:
    redis:
      cluster:
        nodes:
          - redis-1:6379
          - redis-2:6379
          - redis-3:6379
```

### 모니터링 포인트

1. **락 획득 성공/실패율**
   - 실패율이 높으면 waitTime 조정 또는 Redis 확장 검토

2. **락 보유 시간**
   - 평균 보유 시간이 leaseTime에 근접하면 비즈니스 로직 최적화 필요

3. **Redis 연결 상태**
   - 연결 풀 사용률, 연결 에러 모니터링

### 주의사항

1. **트랜잭션과 락의 순서**
   - 락 획득 → 트랜잭션 시작 → 비즈니스 로직 → 트랜잭션 커밋 → 락 해제
   - 트랜잭션 커밋 전에 락이 해제되면 Race Condition 발생 가능

2. **leaseTime 설정**
   - 너무 짧으면: 작업 중 락 만료로 다른 프로세스가 진입
   - 너무 길면: 장애 시 오래 대기

3. **Redis 장애 대응**
   - 단일 Redis 장애 시 락이 무력화됨
   - 운영 환경에서는 Sentinel 또는 Cluster 필수

---

## 의사결정 기록

### Q1: Redisson 대신 Lettuce를 선택한 이유

**결정**: Lettuce + Lua 스크립트로 직접 구현

**Why**:
- 학습 목적 프로젝트이므로 분산 락의 원리를 직접 이해하는 것이 중요
- Spring Boot에 기본 포함된 Lettuce로 추가 의존성 없이 구현 가능

**Alternatives**:
- Redisson: 더 쉬운 API, Watchdog(자동 연장) 내장, 다양한 락 타입 지원
- 직접 구현: 학습 가치 높음, 커스터마이징 자유도

**Trade-offs**:
| Lettuce 직접 구현 | Redisson |
|------------------|----------|
| 구현 복잡도 높음 | 추상화되어 쉬움 |
| 깊은 원리 이해 | 블랙박스 |
| 추가 의존성 없음 | 10MB+ 의존성 |
| Watchdog 없음 | 자동 락 연장 |

**Context**:
- 초기 스타트업 환경에서 Lettuce로 충분히 시작 가능
- 트래픽 증가로 복잡한 요구사항 발생 시 Redisson 전환 용이
- 원리를 이해한 상태에서 추상화 도구로 전환하는 것이 바람직

### Q2: AOP 대신 프로그래매틱 방식 선택

**결정**: `executeWithLock()` 직접 호출

**Why**:
- 락 적용 지점이 명시적으로 보여 디버깅이 쉬움
- 학습 단계에서 코드 흐름을 파악하기 용이
- 현재 락이 필요한 곳이 2-3개로 적음

**Alternatives**:
```kotlin
// AOP 방식 (선택하지 않음)
@DistributedLock(key = "#request.email", prefix = "user:email")
fun createUser(request: CreateUserRequest): UserResponse { ... }
```

**Trade-offs**:
| 프로그래매틱 | AOP |
|-------------|-----|
| 명시적, 추적 용이 | 깔끔한 비즈니스 코드 |
| 보일러플레이트 | 마법 같은 동작 |
| 테스트 쉬움 | 프록시 이슈 주의 |

**Context**:
- 락 적용 지점이 많아지면 AOP 도입 고려
- 현재 규모에서는 프로그래매틱 방식이 적절

### Q3: 락 키 설계

**결정**: `lock:user:email:{email}` 형식

**Why**:
- 동일 이메일에 대한 동시 생성만 방지
- 다른 이메일의 사용자 생성은 병렬 처리 가능

**Alternatives**:
- `lock:user:create`: 모든 사용자 생성을 직렬화 (너무 보수적)
- `lock:user:{userId}`: ID는 생성 전에 알 수 없음

**Trade-offs**:
- 세분화된 키: 병렬성 높음, 키 관리 복잡
- 범용 키: 단순, 병목 가능성

---

## 다음 단계 (추후 학습)

1. **Redisson 적용**: 복잡한 요구사항 발생 시
2. **Redlock 알고리즘**: 다중 Redis 노드 환경에서 안전한 락
3. **모니터링 대시보드**: Prometheus + Grafana로 락 메트릭 시각화
4. **Circuit Breaker**: Redis 장애 시 Fallback 전략
