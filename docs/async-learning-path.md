# Kotlin 비동기 프로그래밍 학습 가이드

이 문서는 동기 프로그래밍에 익숙한 개발자가 Kotlin Coroutines와 Spring WebFlux를 단계별로 학습할 수 있도록 설계되었습니다.

---

## 학습 로드맵

```
┌─────────────────────────────────────────────────────────────────┐
│  1단계: 기초 개념                                                │
│  - 동기 vs 비동기 이해                                           │
│  - 블로킹 vs 논블로킹 차이                                        │
│  - 스레드 모델 비교                                              │
└─────────────────────┬───────────────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│  2단계: Coroutines 기초                                          │
│  - suspend 함수                                                  │
│  - 코루틴 빌더 (launch, async)                                   │
│  - 코루틴 스코프                                                 │
└─────────────────────┬───────────────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│  3단계: 실전 비동기 패턴                                          │
│  - 순차 vs 병렬 실행                                             │
│  - 에러 처리                                                     │
│  - 취소와 타임아웃                                               │
└─────────────────────┬───────────────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│  4단계: Flow와 스트리밍                                           │
│  - Flow 기초                                                     │
│  - Flow 연산자                                                   │
│  - 백프레셔                                                      │
└─────────────────────┬───────────────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│  5단계: Spring WebFlux 통합                                       │
│  - R2DBC                                                         │
│  - WebClient                                                     │
│  - 트랜잭션 관리                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 1단계: 기초 개념 이해

### 학습 목표
- 동기/비동기, 블로킹/논블로킹의 차이를 명확히 이해한다
- 스레드 모델의 차이와 각각의 장단점을 파악한다

### 1.1 용어 정리

| 용어 | 의미 | 예시 |
|------|------|------|
| **동기 (Synchronous)** | 작업 완료를 기다림 | 함수 호출 후 리턴까지 대기 |
| **비동기 (Asynchronous)** | 작업 완료를 기다리지 않음 | 콜백, Future, Coroutine |
| **블로킹 (Blocking)** | 스레드가 대기 중 점유됨 | `Thread.sleep()`, JDBC |
| **논블로킹 (Non-blocking)** | 스레드가 대기 중 해제됨 | `delay()`, R2DBC |

### 1.2 핵심 이해: 4가지 조합

```
                    │ 블로킹              │ 논블로킹
────────────────────┼────────────────────┼────────────────────
동기                │ 전통적인 코드       │ (드묾)
                    │ Thread.sleep()     │
────────────────────┼────────────────────┼────────────────────
비동기              │ 콜백 지옥           │ Coroutines, Reactor
                    │ Future + blocking  │ 우리가 원하는 것!
```

**우리의 목표**: 비동기 + 논블로킹

### 1.3 스레드 모델 비교

#### Thread-per-Request (MVC)

```
요청 1 ──▶ [Thread-1] ████████████░░░░░░░░ (I/O 대기 중에도 점유)
요청 2 ──▶ [Thread-2] ████████████░░░░░░░░
요청 3 ──▶ [Thread-3] ████████████░░░░░░░░
...
요청 200 ─▶ [Thread-200] ████████████░░░░░░
요청 201 ─▶ [대기열] ░░░░░░░░░░░░░░░░░░░░░ (스레드 풀 고갈!)
```

- 장점: 직관적, 디버깅 쉬움
- 단점: 스레드 수에 의해 동시 처리량 제한

#### Event Loop (WebFlux)

```
요청 1 ──▶ [reactor-1] ██░░░░██  (I/O 대기 중 스레드 해제)
요청 2 ──▶ [reactor-1] ██░░░░██  (같은 스레드, 다른 시간)
요청 3 ──▶ [reactor-2] ██░░░░██
...
요청 10000+ ─▶ 소수의 스레드로 모두 처리 가능
```

- 장점: 높은 동시성, 리소스 효율적
- 단점: 복잡한 코드, 디버깅 어려움

### 1.4 실습: 성능 차이 확인

프로젝트의 성능 테스트를 실행하여 차이를 직접 확인합니다.

```bash
# 서비스 실행
./gradlew :api-mvc:bootRun --args='--spring.profiles.active=local' &
./gradlew :api-webflux:bootRun --args='--spring.profiles.active=local' &

# 테스트 실행 (k6 필요)
cd tests/k6
k6 run high-load-test.js --env BASE_URL=http://localhost:8080
k6 run high-load-test.js --env BASE_URL=http://localhost:8081
k6 run high-load-test.js --env BASE_URL=http://localhost:8081 --env ENDPOINT=blocking
```

**관찰 포인트**:
- MVC와 WebFlux의 정상 사용 시 성능 비교
- WebFlux에서 블로킹 코드 사용 시 성능 저하 확인

### 1.5 체크포인트

- [ ] 동기/비동기와 블로킹/논블로킹의 차이를 설명할 수 있다
- [ ] Thread-per-Request와 Event Loop 모델을 비교할 수 있다
- [ ] 왜 비동기 + 논블로킹이 필요한지 이해했다

---

## 2단계: Coroutines 기초

### 학습 목표
- suspend 함수의 의미와 동작 원리를 이해한다
- 기본적인 코루틴 빌더를 사용할 수 있다
- 코루틴 스코프의 개념을 이해한다

### 2.1 suspend 함수란?

`suspend`는 **"이 함수가 일시 중단될 수 있다"** 는 표시입니다.

```kotlin
// 일반 함수 - 중단 불가
fun normalFunction(): String {
    return "Hello"
}

// suspend 함수 - 중단 가능
suspend fun suspendFunction(): String {
    delay(1000)  // 여기서 중단 (스레드 해제)
    return "Hello"
}
```

**핵심**: suspend 키워드 자체는 코드를 비동기로 만들지 않습니다!

```kotlin
// ❌ suspend가 있어도 블로킹
suspend fun stillBlocking(): String {
    Thread.sleep(1000)  // 스레드 점유!
    return "Hello"
}

// ✅ 진정한 논블로킹
suspend fun trulyNonBlocking(): String {
    delay(1000)  // 스레드 해제!
    return "Hello"
}
```

### 2.2 코루틴 빌더

#### launch - 결과가 필요 없을 때

```kotlin
fun main() = runBlocking {
    launch {
        delay(1000)
        println("World!")
    }
    println("Hello,")
}
// 출력:
// Hello,
// World!
```

#### async - 결과가 필요할 때

```kotlin
fun main() = runBlocking {
    val deferred: Deferred<Int> = async {
        delay(1000)
        42
    }
    println("계산 중...")
    val result = deferred.await()  // 결과 대기
    println("결과: $result")
}
```

#### runBlocking - 블로킹 세계와 연결

```kotlin
// 테스트나 main 함수에서 사용
fun main() = runBlocking {
    // 여기서 suspend 함수 호출 가능
    val result = mySuspendFunction()
}
```

### 2.3 코루틴 스코프

모든 코루틴은 스코프 내에서 실행되어야 합니다.

```kotlin
// ❌ 잘못된 사용 - 스코프 없음
suspend fun wrong() {
    launch { }  // 컴파일 에러!
}

// ✅ 올바른 사용 - 스코프 제공
suspend fun correct() = coroutineScope {
    launch { }  // OK
}
```

### 2.4 실습: 첫 번째 코루틴

```kotlin
// 파일: src/test/kotlin/CoroutineBasicTest.kt
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test

class CoroutineBasicTest {

    @Test
    fun `첫 번째 코루틴`() = runBlocking {
        println("시작: ${Thread.currentThread().name}")

        launch {
            delay(1000)
            println("launch 완료: ${Thread.currentThread().name}")
        }

        val result = async {
            delay(500)
            println("async 실행: ${Thread.currentThread().name}")
            42
        }

        println("결과 대기 중...")
        println("결과: ${result.await()}")
        println("종료")
    }
}
```

### 2.5 체크포인트

- [ ] suspend 함수가 왜 일반 함수에서 호출 불가능한지 설명할 수 있다
- [ ] launch와 async의 차이를 설명할 수 있다
- [ ] coroutineScope의 역할을 이해했다

---

## 3단계: 실전 비동기 패턴

### 학습 목표
- 순차 실행과 병렬 실행을 구분하여 사용할 수 있다
- 비동기 코드에서 에러를 올바르게 처리할 수 있다
- 취소와 타임아웃을 적용할 수 있다

### 3.1 순차 vs 병렬 실행

#### 순차 실행 (기본)

```kotlin
suspend fun sequential(): Int {
    val a = taskA()  // 1초
    val b = taskB()  // 1초
    return a + b     // 총 2초
}
```

#### 병렬 실행 (async)

```kotlin
suspend fun parallel(): Int = coroutineScope {
    val a = async { taskA() }  // 동시 시작
    val b = async { taskB() }  // 동시 시작
    a.await() + b.await()      // 총 1초
}
```

#### 언제 무엇을 사용하는가?

```kotlin
// 순차: B가 A의 결과에 의존
suspend fun dependent() {
    val user = getUser(id)           // 먼저 실행
    val orders = getOrders(user.id)  // user 필요
}

// 병렬: 서로 독립적
suspend fun independent() = coroutineScope {
    val user = async { getUser(id) }
    val products = async { getProducts() }  // user 불필요
    Pair(user.await(), products.await())
}
```

### 3.2 에러 처리

#### try-catch

```kotlin
suspend fun withErrorHandling(): Result {
    return try {
        riskyOperation()
    } catch (e: NetworkException) {
        fallbackResult()
    }
}
```

#### supervisorScope - 독립적 에러 처리

```kotlin
suspend fun independentTasks() = supervisorScope {
    val job1 = launch {
        throw Exception("실패!")  // 이것이 실패해도
    }
    val job2 = launch {
        delay(1000)
        println("성공!")  // 이것은 실행됨
    }
}
```

#### 구조화된 동시성

```kotlin
// ❌ GlobalScope - 에러가 전파되지 않음
GlobalScope.launch {
    throw Exception()  // 어디로도 전파되지 않음
}

// ✅ coroutineScope - 에러가 부모로 전파
coroutineScope {
    launch {
        throw Exception()  // 부모로 전파, 다른 자식도 취소
    }
}
```

### 3.3 취소와 타임아웃

#### 취소

```kotlin
val job = launch {
    repeat(1000) { i ->
        println("작업 중 $i...")
        delay(500)
    }
}

delay(1300)
job.cancel()  // 취소 요청
job.join()    // 취소 완료 대기
```

#### 타임아웃

```kotlin
// 타임아웃 시 예외 발생
val result = withTimeout(3000) {
    slowOperation()
}

// 타임아웃 시 null 반환
val result = withTimeoutOrNull(3000) {
    slowOperation()
} ?: defaultValue
```

### 3.4 실습: 병렬 API 호출

```kotlin
// 파일: src/test/kotlin/ParallelApiTest.kt
class ParallelApiTest {

    // 가상의 API 호출
    private suspend fun fetchUser(id: Long): User {
        delay(1000)  // 네트워크 지연 시뮬레이션
        return User(id, "User$id")
    }

    private suspend fun fetchOrders(userId: Long): List<Order> {
        delay(1000)
        return listOf(Order(1, userId), Order(2, userId))
    }

    private suspend fun fetchRecommendations(): List<Product> {
        delay(1000)
        return listOf(Product(1, "Product1"))
    }

    @Test
    fun `순차 실행 - 3초 소요`() = runBlocking {
        val start = System.currentTimeMillis()

        val user = fetchUser(1)
        val orders = fetchOrders(user.id)
        val recommendations = fetchRecommendations()

        val elapsed = System.currentTimeMillis() - start
        println("순차 실행: ${elapsed}ms")  // ~3000ms
    }

    @Test
    fun `병렬 실행 - 1초 소요`() = runBlocking {
        val start = System.currentTimeMillis()

        val result = coroutineScope {
            val user = async { fetchUser(1) }
            val orders = async { fetchOrders(1) }  // user.id 대신 1 사용
            val recommendations = async { fetchRecommendations() }

            Triple(user.await(), orders.await(), recommendations.await())
        }

        val elapsed = System.currentTimeMillis() - start
        println("병렬 실행: ${elapsed}ms")  // ~1000ms
    }
}
```

### 3.5 체크포인트

- [ ] async와 await를 사용하여 병렬 처리를 구현할 수 있다
- [ ] supervisorScope와 coroutineScope의 차이를 설명할 수 있다
- [ ] 타임아웃을 적용할 수 있다

---

## 4단계: Flow와 스트리밍

### 학습 목표
- Flow의 개념과 List와의 차이를 이해한다
- Flow 연산자를 활용할 수 있다
- 백프레셔의 개념을 이해한다

### 4.1 Flow란?

Flow는 **비동기 데이터 스트림**입니다.

```kotlin
// List: 모든 데이터를 메모리에 로드
fun getUsers(): List<User> = listOf(user1, user2, user3)

// Flow: 데이터를 하나씩 방출
fun getUsers(): Flow<User> = flow {
    emit(user1)
    emit(user2)
    emit(user3)
}
```

### 4.2 Flow vs List

| 특성 | List | Flow |
|------|------|------|
| 데이터 로딩 | 즉시 전체 로드 | 필요할 때 하나씩 |
| 메모리 | 전체 데이터 크기 | 현재 처리 중인 것만 |
| 무한 데이터 | 불가능 | 가능 |
| 취소 | 불가능 | 가능 |

### 4.3 Flow 생성

```kotlin
// flow 빌더
fun numbers(): Flow<Int> = flow {
    for (i in 1..3) {
        delay(100)
        emit(i)
    }
}

// flowOf
val flow1 = flowOf(1, 2, 3)

// asFlow
val flow2 = listOf(1, 2, 3).asFlow()

// 채널에서 Flow로
val flow3 = channelFlow {
    send(1)
    send(2)
}
```

### 4.4 Flow 연산자

```kotlin
flowOf(1, 2, 3, 4, 5)
    .filter { it % 2 == 0 }      // 짝수만
    .map { it * 2 }              // 2배
    .take(2)                     // 처음 2개만
    .onEach { println(it) }      // 부수 효과
    .collect { result.add(it) }  // 최종 수집
```

#### 주요 연산자

| 연산자 | 설명 |
|--------|------|
| `filter` | 조건에 맞는 요소만 통과 |
| `map` | 각 요소 변환 |
| `take(n)` | 처음 n개만 |
| `drop(n)` | 처음 n개 스킵 |
| `distinctUntilChanged` | 연속 중복 제거 |
| `flatMapConcat` | 순차적으로 펼치기 |
| `flatMapMerge` | 병렬로 펼치기 |
| `catch` | 에러 처리 |
| `onCompletion` | 완료 시 실행 |

### 4.5 Flow 소비

```kotlin
// collect - 기본
flow.collect { value ->
    println(value)
}

// toList - List로 변환
val list = flow.toList()

// first - 첫 번째 요소
val first = flow.first()

// reduce - 축소
val sum = flow.reduce { acc, value -> acc + value }
```

### 4.6 백프레셔 (Backpressure)

생산자가 소비자보다 빠를 때의 처리 전략입니다.

```kotlin
flow {
    repeat(100) {
        emit(it)  // 빠른 생산
    }
}
.buffer()  // 버퍼에 저장
.collect {
    delay(100)  // 느린 소비
    println(it)
}
```

#### 전략

| 전략 | 설명 |
|------|------|
| `buffer()` | 버퍼에 저장 |
| `conflate()` | 최신 값만 유지 |
| `collectLatest` | 새 값이 오면 이전 처리 취소 |

### 4.7 실습: 실시간 데이터 처리

```kotlin
class FlowPracticeTest {

    // 실시간 가격 스트림 시뮬레이션
    private fun priceStream(): Flow<Double> = flow {
        var price = 100.0
        while (true) {
            price += (Math.random() - 0.5) * 10
            emit(price)
            delay(100)
        }
    }

    @Test
    fun `가격 스트림 처리`() = runBlocking {
        priceStream()
            .take(20)  // 20개만
            .filter { it > 100 }  // 100 이상만
            .map { "%.2f".format(it) }
            .onEach { println("가격: $it") }
            .collect()
    }

    @Test
    fun `이동 평균 계산`() = runBlocking {
        val window = mutableListOf<Double>()

        priceStream()
            .take(50)
            .collect { price ->
                window.add(price)
                if (window.size > 5) window.removeAt(0)
                val avg = window.average()
                println("현재: %.2f, 이동평균(5): %.2f".format(price, avg))
            }
    }
}
```

### 4.8 체크포인트

- [ ] Flow와 List의 차이를 설명할 수 있다
- [ ] map, filter, take 등 기본 연산자를 사용할 수 있다
- [ ] 백프레셔가 왜 필요한지 이해했다

---

## 5단계: Spring WebFlux 통합

### 학습 목표
- R2DBC를 사용하여 논블로킹 DB 접근을 구현할 수 있다
- WebClient를 사용하여 논블로킹 HTTP 호출을 구현할 수 있다
- 리액티브 트랜잭션을 관리할 수 있다

### 5.1 R2DBC

#### Repository 정의

```kotlin
interface UserRepository : CoroutineCrudRepository<User, Long> {
    fun findByEmail(email: String): Flow<User>
    suspend fun findByName(name: String): User?
}
```

#### Service 구현

```kotlin
@Service
class UserService(private val userRepository: UserRepository) {

    // 단건 조회
    suspend fun findById(id: Long): User? {
        return userRepository.findById(id)
    }

    // 전체 조회 (Flow)
    fun findAll(): Flow<User> {
        return userRepository.findAll()
    }

    // 저장
    suspend fun save(user: User): User {
        return userRepository.save(user)
    }
}
```

### 5.2 WebClient

#### 설정

```kotlin
@Configuration
class WebClientConfig {
    @Bean
    fun webClient(): WebClient {
        return WebClient.builder()
            .baseUrl("https://api.example.com")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}
```

#### 사용

```kotlin
@Service
class ExternalApiService(private val webClient: WebClient) {

    suspend fun getUser(id: Long): ExternalUser {
        return webClient.get()
            .uri("/users/{id}", id)
            .retrieve()
            .awaitBody()
    }

    fun getUsers(): Flow<ExternalUser> {
        return webClient.get()
            .uri("/users")
            .retrieve()
            .bodyToFlow()
    }

    suspend fun createUser(user: CreateUserRequest): ExternalUser {
        return webClient.post()
            .uri("/users")
            .bodyValue(user)
            .retrieve()
            .awaitBody()
    }
}
```

### 5.3 Handler와 Router

#### Handler

```kotlin
@Component
class UserHandler(private val userService: UserService) {

    suspend fun getUser(request: ServerRequest): ServerResponse {
        val id = request.pathVariable("id").toLong()
        val user = userService.findById(id)
            ?: return ServerResponse.notFound().buildAndAwait()
        return ServerResponse.ok().bodyValueAndAwait(user)
    }

    suspend fun getAllUsers(request: ServerRequest): ServerResponse {
        val users = userService.findAll()
        return ServerResponse.ok().bodyAndAwait(users)
    }

    suspend fun createUser(request: ServerRequest): ServerResponse {
        val createRequest = request.awaitBody<CreateUserRequest>()
        val user = userService.save(createRequest.toEntity())
        return ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(user)
    }
}
```

#### Router

```kotlin
@Configuration
class UserRouter(private val handler: UserHandler) {

    @Bean
    fun userRoutes() = coRouter {
        "/api/v1/users".nest {
            GET("", handler::getAllUsers)
            GET("/{id}", handler::getUser)
            POST("", handler::createUser)
        }
    }
}
```

### 5.4 트랜잭션 관리

#### TransactionalOperator 사용

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val inventoryRepository: InventoryRepository,
    private val operator: TransactionalOperator
) {

    suspend fun placeOrder(order: Order): Order {
        return operator.executeAndAwait {
            // 재고 확인 및 차감
            val inventory = inventoryRepository.findByProductId(order.productId)
                ?: throw NotFoundException("Product not found")

            if (inventory.quantity < order.quantity) {
                throw InsufficientStockException()
            }

            inventory.quantity -= order.quantity
            inventoryRepository.save(inventory)

            // 주문 저장
            orderRepository.save(order)
        }
    }
}
```

### 5.5 실습: 완전한 비동기 서비스

이 프로젝트의 `api-webflux` 모듈을 참고하여:

1. **UserRepository** 분석 (`repository/UserRepository.kt`)
2. **UserService** 분석 (`service/UserService.kt`)
3. **UserHandler** 분석 (`handler/UserHandler.kt`)
4. **UserRouter** 분석 (`router/UserRouter.kt`)

```bash
# 코드 위치
api-webflux/src/main/kotlin/com/playground/webflux/
├── repository/UserRepository.kt
├── service/UserService.kt
├── handler/UserHandler.kt
└── router/UserRouter.kt
```

### 5.6 체크포인트

- [ ] CoroutineCrudRepository를 사용할 수 있다
- [ ] WebClient로 외부 API를 호출할 수 있다
- [ ] Handler + Router 패턴으로 API를 구현할 수 있다
- [ ] 트랜잭션을 적용할 수 있다

---

## 추가 학습 자료

### 공식 문서
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Spring WebFlux Reference](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Spring Data R2DBC](https://spring.io/projects/spring-data-r2dbc)

### 추천 학습 순서

1. Kotlin 공식 코루틴 튜토리얼 완료
2. 이 프로젝트의 `api-mvc` vs `api-webflux` 코드 비교
3. 성능 테스트로 차이 확인
4. 새로운 도메인 추가 실습 (예: Post, Comment)

### 실습 과제

1. **기초**: `api-webflux`에 새로운 엔티티 추가
2. **중급**: 외부 API 연동 (WebClient 사용)
3. **고급**: 여러 서비스 병렬 호출 및 결과 조합
4. **심화**: 실시간 데이터 스트리밍 (SSE)

---

## 마무리

비동기 프로그래밍은 처음에는 어렵지만, 핵심 원칙을 이해하면 강력한 도구가 됩니다:

1. **블로킹 코드를 피하라** - 논블로킹 라이브러리 사용
2. **독립적인 작업은 병렬화하라** - async 활용
3. **구조화된 동시성을 유지하라** - GlobalScope 금지
4. **대용량 데이터는 Flow로** - 메모리 효율성
5. **에러와 취소를 항상 고려하라** - 안정적인 시스템

꾸준한 실습이 가장 중요합니다. 이 프로젝트의 코드를 수정하고 테스트하면서 감을 익히세요!
