# Kotlin 비동기 코드 작성 가이드

Kotlin Coroutines와 Spring WebFlux를 사용한 비동기 코드 작성 시 주의사항과 베스트 프랙티스를 정리합니다.

---

## 핵심 개념: suspend 키워드의 의미

```kotlin
suspend fun getData(): Data
```

`suspend`는 **"이 함수가 일시 중단될 수 있다"**는 것만 표시합니다.

**중요**: `suspend` 키워드만으로는 코드가 비동기로 동작하지 않습니다!

```kotlin
// ❌ suspend가 있어도 블로킹
suspend fun badExample(): Data {
    Thread.sleep(1000)  // 스레드를 점유하고 대기
    return data
}

// ✅ 진정한 비동기
suspend fun goodExample(): Data {
    delay(1000)  // 스레드를 해제하고 대기
    return data
}
```

---

## 1. 블로킹 코드 사용 금지

### 문제점

WebFlux의 이벤트 루프 스레드는 **CPU 코어 수만큼만 존재**합니다 (보통 8~16개).
블로킹 코드를 사용하면 이 적은 스레드가 즉시 고갈됩니다.

### 블로킹 vs 논블로킹 라이브러리

| 용도 | 블로킹 (사용 금지) | 논블로킹 (사용) |
|------|-------------------|-----------------|
| 지연 | `Thread.sleep()` | `delay()` |
| DB 접근 | JDBC, JPA | R2DBC, Spring Data R2DBC |
| HTTP 클라이언트 | RestTemplate | WebClient |
| Redis | Jedis | Lettuce (reactive) |
| 파일 I/O | `FileInputStream` | `AsynchronousFileChannel` |

### 코드 예시

```kotlin
// ❌ 잘못된 예 - 모두 블로킹
suspend fun getUserData(userId: Long): UserData {
    Thread.sleep(100)  // 블로킹!

    val user = jdbcTemplate.queryForObject(  // 블로킹!
        "SELECT * FROM users WHERE id = ?",
        userId
    )

    val profile = restTemplate.getForObject(  // 블로킹!
        "http://api.example.com/profile/$userId",
        Profile::class.java
    )

    return UserData(user, profile)
}

// ✅ 올바른 예 - 모두 논블로킹
suspend fun getUserData(userId: Long): UserData {
    delay(100)  // 논블로킹

    val user = userRepository.findById(userId)  // R2DBC - 논블로킹
        .awaitSingleOrNull()

    val profile = webClient.get()  // WebClient - 논블로킹
        .uri("http://api.example.com/profile/$userId")
        .retrieve()
        .awaitBody<Profile>()

    return UserData(user, profile)
}
```

---

## 2. 블로킹이 불가피할 때 - Dispatcher 분리

레거시 라이브러리나 CPU 집약적 작업은 블로킹이 불가피합니다.
이 경우 **Dispatchers.IO**를 사용하여 이벤트 루프 스레드를 보호합니다.

```kotlin
suspend fun processWithLegacyLibrary(): Result {
    // 이벤트 루프 스레드에서 실행 (논블로킹 작업)
    val data = webClient.get().awaitBody<Data>()

    // IO 디스패처로 전환 (블로킹 작업)
    val processed = withContext(Dispatchers.IO) {
        legacyBlockingLibrary.process(data)  // 블로킹 OK
    }

    // 다시 이벤트 루프 스레드
    return repository.save(processed).awaitSingle()
}
```

### Dispatcher 종류

| Dispatcher | 용도 | 스레드 수 |
|------------|------|----------|
| `Dispatchers.Default` | CPU 집약적 작업 | CPU 코어 수 |
| `Dispatchers.IO` | 블로킹 I/O 작업 | 최대 64개 (확장 가능) |
| `Dispatchers.Main` | UI 작업 (Android) | 1개 |
| `Dispatchers.Unconfined` | 테스트용 | 호출 스레드 |

---

## 3. 동시성 활용 - 순차 vs 병렬

### 순차 실행 (기본)

독립적인 작업도 순차 실행되어 비효율적입니다.

```kotlin
// 총 3초 소요
suspend fun fetchSequential(): Combined {
    val user = userService.getUser(id)       // 1초
    val orders = orderService.getOrders(id)  // 1초
    val reviews = reviewService.getReviews(id) // 1초
    return Combined(user, orders, reviews)
}
```

### 병렬 실행 (async)

독립적인 작업은 `async`로 병렬 실행합니다.

```kotlin
// 총 1초 소요 (가장 긴 작업 시간)
suspend fun fetchParallel(): Combined = coroutineScope {
    val userDeferred = async { userService.getUser(id) }
    val ordersDeferred = async { orderService.getOrders(id) }
    val reviewsDeferred = async { reviewService.getReviews(id) }

    Combined(
        userDeferred.await(),
        ordersDeferred.await(),
        reviewsDeferred.await()
    )
}
```

### 언제 병렬화하는가?

```
작업 A의 결과가 작업 B에 필요한가?
├── YES → 순차 실행
└── NO  → 병렬 실행 (async)
```

---

## 4. 에러 처리와 구조화된 동시성

### GlobalScope 사용 금지

```kotlin
// ❌ 위험 - 부모와 무관하게 실행
suspend fun dangerous() {
    GlobalScope.launch {
        task1()  // 부모가 취소되어도 계속 실행
    }
    GlobalScope.launch {
        task2()  // 에러가 발생해도 다른 작업에 영향 없음
    }
}

// ✅ 안전 - 구조화된 동시성
suspend fun safe() = coroutineScope {
    launch { task1() }  // 부모 스코프에 바인딩
    launch { task2() }  // 하나 실패 시 모두 취소
}
```

### 구조화된 동시성의 장점

1. **자동 취소**: 부모가 취소되면 자식도 취소
2. **에러 전파**: 자식의 에러가 부모로 전파
3. **리소스 누수 방지**: 스코프 종료 시 모든 코루틴 정리

### 에러 처리 패턴

```kotlin
suspend fun fetchWithErrorHandling(): Result = coroutineScope {
    val primary = async {
        try {
            primaryService.fetch()
        } catch (e: Exception) {
            null  // 실패 시 null 반환
        }
    }

    val fallback = async {
        fallbackService.fetch()
    }

    primary.await() ?: fallback.await()
}
```

### supervisorScope - 독립적인 에러 처리

```kotlin
// 하나가 실패해도 다른 것은 계속 실행
suspend fun independentTasks() = supervisorScope {
    launch {
        task1()  // 실패해도
    }
    launch {
        task2()  // 계속 실행
    }
}
```

---

## 5. Flow vs List - 대용량 데이터 처리

### List의 문제점

```kotlin
// ❌ 메모리에 전체 로드
suspend fun getAllUsers(): List<User> {
    return repository.findAll().toList()
    // 100만 건이면 OutOfMemoryError!
}
```

### Flow로 스트리밍

```kotlin
// ✅ 하나씩 처리 - 메모리 효율적
fun getAllUsers(): Flow<User> {
    return repository.findAll()
}

// 사용 예시
suspend fun processAllUsers() {
    userService.getAllUsers()
        .filter { it.isActive }
        .map { transform(it) }
        .collect { save(it) }  // 하나씩 처리
}
```

### Flow 연산자

```kotlin
flow
    .filter { ... }      // 필터링
    .map { ... }         // 변환
    .take(10)            // 처음 N개만
    .drop(5)             // 처음 N개 스킵
    .distinctUntilChanged()  // 연속 중복 제거
    .debounce(300)       // 디바운스
    .flatMapMerge { }    // 병렬 처리
    .catch { }           // 에러 처리
    .onEach { }          // 부수 효과
    .collect { }         // 최종 소비
```

---

## 6. 트랜잭션 처리 주의사항

### 문제: 스레드 전환

JPA/JDBC 트랜잭션은 **ThreadLocal**에 바인딩됩니다.
`suspend` 함수는 중단점에서 스레드가 바뀔 수 있어 문제가 발생합니다.

```kotlin
// ❌ 위험 - 스레드 전환으로 트랜잭션 유실 가능
@Transactional
suspend fun transferMoney() {
    accountA.withdraw(100)
    delay(100)  // 여기서 스레드가 바뀔 수 있음!
    accountB.deposit(100)  // 다른 스레드 = 다른 트랜잭션 컨텍스트
}
```

### 해결책 1: R2DBC 트랜잭션

```kotlin
@Service
class TransferService(
    private val transactionalOperator: TransactionalOperator
) {
    suspend fun transferMoney(from: Long, to: Long, amount: BigDecimal) {
        transactionalOperator.executeAndAwait {
            val fromAccount = accountRepository.findById(from).awaitSingle()
            val toAccount = accountRepository.findById(to).awaitSingle()

            fromAccount.balance -= amount
            toAccount.balance += amount

            accountRepository.save(fromAccount).awaitSingle()
            accountRepository.save(toAccount).awaitSingle()
        }
    }
}
```

### 해결책 2: @Transactional with Reactor Context

```kotlin
@Transactional
suspend fun transferMoney() {
    // Spring 5.3+에서 코루틴 트랜잭션 지원
    // ReactorContext를 통해 트랜잭션 전파
    accountA.withdraw(100)
    accountB.deposit(100)
}
```

---

## 7. 테스트 작성

### runTest 사용

```kotlin
@Test
fun `should fetch user data`() = runTest {
    // given
    coEvery { userRepository.findById(1L) } returns flowOf(testUser)

    // when
    val result = userService.getUserById(1L)

    // then
    result shouldBe testUser
}
```

### 가상 시간 제어

```kotlin
@Test
fun `should timeout after 5 seconds`() = runTest {
    val result = withTimeoutOrNull(5000) {
        delay(10000)  // 가상 시간으로 즉시 실행
        "completed"
    }

    result shouldBe null
}
```

---

## 핵심 원칙 요약

| 원칙 | 설명 |
|------|------|
| **논블로킹 라이브러리 사용** | R2DBC, WebClient, Lettuce 등 |
| **블로킹 코드는 Dispatchers.IO** | 이벤트 루프 스레드 보호 |
| **독립 작업은 async로 병렬화** | 응답 시간 단축 |
| **GlobalScope 금지** | 구조화된 동시성 유지 |
| **대용량 데이터는 Flow** | 메모리 효율적 스트리밍 |
| **트랜잭션 경계 주의** | 스레드 전환 고려 |
| **runTest로 테스트** | 가상 시간 활용 |

---

## 참고 자료

- [Kotlin Coroutines 공식 가이드](https://kotlinlang.org/docs/coroutines-guide.html)
- [Spring WebFlux with Kotlin Coroutines](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html)
- [Structured Concurrency](https://kotlinlang.org/docs/coroutines-basics.html#structured-concurrency)
- [Flow 가이드](https://kotlinlang.org/docs/flow.html)
