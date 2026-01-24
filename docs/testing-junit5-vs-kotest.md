# 테스트 코드 분석: JUnit5 vs Kotest (MockK 포함)

이 문서는 `README.md`의 “3단계: 테스트 코드 분석” 섹션을 확장하여, 이 저장소에서 사용하는 **JUnit5**와 **Kotest** 스타일의 차이를 정리합니다.
예시는 실제 테스트 코드(`api-mvc`, `api-webflux`)의 패턴을 그대로 따릅니다.

---

## 목차

1. [이 저장소의 테스트 구성](#1-이-저장소의-테스트-구성)
2. [공통 패턴: Given-When-Then + MockK](#2-공통-패턴-given-when-then--mockk)
3. [JUnit5 스타일: @Test, @Nested, @DisplayName](#3-junit5-스타일-test-nested-displayname)
4. [Kotest 스타일: DescribeSpec, describe/context/it](#4-kotest-스타일-describespec-describecontextit)
5. [동기(MVC) vs 비동기(WebFlux) 테스트 차이](#5-동기mvc-vs-비동기webflux-테스트-차이)
6. [코루틴 테스트(runTest)와 MockK(coEvery/coVerify)](#6-코루틴-테스트runtest와-mockkcoeverycoverify)
7. [테스트 실행 명령어](#7-테스트-실행-명령어)
8. [선택 가이드: 언제 JUnit5 / Kotest?](#8-선택-가이드-언제-junit5--kotest)

---

## 1. 이 저장소의 테스트 구성

- **테스트 프레임워크**
  - **JUnit5**: 표준 단위/통합 테스트
  - **Kotest**: BDD 스타일(설명 중심) 테스트
- **Mocking**
  - **MockK** 사용
  - 코루틴(suspend) 또는 코루틴 리포지토리 호출은 `coEvery` / `coVerify` 사용
- **모듈별 테스트 파일 예시**
  - `api-mvc/src/test/kotlin/.../UserServiceJUnit5Test.kt`
  - `api-mvc/src/test/kotlin/.../UserServiceKotestTest.kt`
  - `api-webflux/src/test/kotlin/.../UserServiceJUnit5Test.kt`
  - `api-webflux/src/test/kotlin/.../UserServiceKotestTest.kt`

---

## 2. 공통 패턴: Given-When-Then + MockK

### Given-When-Then

이 저장소의 서비스 단위 테스트는 다음 흐름이 기본입니다.

- **Given**: 의존성(Repository 등)을 Mock으로 세팅
- **When**: 테스트 대상 메서드 호출
- **Then**: 결과 검증 + 호출 검증(verify)

### MockK 기본 문법

```kotlin
// given: 스텁(stub) 설정
every { userRepository.findAll() } returns listOf(testUser)

// when: 실행
val result = userService.getAllUsers()

// then: 값 검증
assertEquals(1, result.size)

// then: 호출 검증
verify(exactly = 1) { userRepository.findAll() }
```

---

## 3. JUnit5 스타일: @Test, @Nested, @DisplayName

### 특징

- 테스트를 **클래스/메서드 단위로 명확히 분리**할 수 있습니다.
- `@Nested`로 “기능 단위 그룹화”가 가능합니다.
- `@DisplayName`으로 테스트 출력 이름을 사람이 읽기 좋게 만들 수 있습니다.

### 예시: 단건 조회(getUserById)

```kotlin
@Nested
@DisplayName("getUserById")
inner class GetUserByIdTest {

    @Test
    @DisplayName("should return user when found")
    fun `should return user when found`() {
        // given
        every { userRepository.findById(1L) } returns Optional.of(testUser)

        // when
        val result = userService.getUserById(1L)

        // then
        assertEquals(testUser.id, result.id)
    }
}
```

### MockK 초기화 패턴 (JUnit5)

이 저장소의 JUnit5 테스트는 어노테이션 기반 MockK 초기화를 씁니다.

```kotlin
@MockK
private lateinit var userRepository: UserRepository

@InjectMockKs
private lateinit var userService: UserService

@BeforeEach
fun setUp() {
    MockKAnnotations.init(this)
}

@AfterEach
fun tearDown() {
    clearAllMocks()
}
```

---

## 4. Kotest 스타일: DescribeSpec, describe/context/it

### 특징

- 테스트 구조가 “설명 문장”에 가깝습니다.
- `describe → context → it`로 **시나리오 중심(BDD)** 분해가 자연스럽습니다.
- assertion이 `shouldBe`, `shouldHaveSize` 등으로 읽기 좋습니다.

### 예시: 단건 조회(getUserById)

```kotlin
describe("getUserById") {
    context("when user exists") {
        it("should return user") {
            every { userRepository.findById(1L) } returns Optional.of(testUser)

            val result = userService.getUserById(1L)

            result.id shouldBe testUser.id
        }
    }
}
```

### MockK 구성 패턴 (Kotest)

Kotest 예제는 어노테이션 초기화 대신, 명시적으로 mock 객체를 생성합니다.

```kotlin
val userRepository = mockk<UserRepository>()
val userService = UserService(userRepository)

beforeEach {
    clearAllMocks()
}
```

### 예외 검증

Kotest는 예외 검증이 자연스럽습니다.

```kotlin
shouldThrow<NotFoundException> {
    userService.getUserById(1L)
}
```

---

## 5. 동기(MVC) vs 비동기(WebFlux) 테스트 차이

### MVC (동기)

- Repository 반환이 보통 **즉시값** (예: `List<T>`, `Optional<T>`)
- 서비스 메서드가 일반 함수인 경우가 많습니다.

예: `findById`가 `Optional<User>`를 반환

```kotlin
every { userRepository.findById(1L) } returns Optional.of(testUser)
val result = userService.getUserById(1L)
```

### WebFlux + Coroutines (비동기)

- Repository가 `CoroutineCrudRepository` 기반이면,
  - 단건 조회는 **nullable** (예: `User?`)
  - 전체 조회는 `Flow<User>` 형태가 일반적입니다.
- 테스트에서도 코루틴을 고려해야 합니다.

예: `findById`가 `User?`를 반환

```kotlin
coEvery { userRepository.findById(1L) } returns testUser
val result = userService.getUserById(1L) // suspend 함수일 수 있음
```

예: `findAll`이 `Flow<User>`를 반환

```kotlin
coEvery { userRepository.findAll() } returns flowOf(testUser)
val result = userService.getAllUsers().toList()
```

---

## 6. 코루틴 테스트(runTest)와 MockK(coEvery/coVerify)

### runTest를 쓰는 이유

- 코루틴 테스트를 **안전하게** 실행하기 위해 `kotlinx-coroutines-test`의 `runTest { ... }`를 사용합니다.
- 이 저장소의 가이드(`AGENTS.md`)도 suspend 테스트에 `runTest` 사용을 권장합니다.

### JUnit5 + runTest 예시

```kotlin
@Test
fun `should return all users`() = runTest {
    // given
    coEvery { userRepository.findAll() } returns flowOf(testUser)

    // when
    val result = userService.getAllUsers().toList()

    // then
    assertEquals(1, result.size)
    coVerify(exactly = 1) { userRepository.findAll() }
}
```

### MockK에서 언제 coEvery/coVerify를 쓰나?

- 호출 대상이 **suspend 함수**이거나,
- 코루틴 기반 리포지토리/서비스 호출이면 `coEvery` / `coVerify`를 사용합니다.

```kotlin
coEvery { userRepository.findById(1L) } returns null

shouldThrow<NotFoundException> {
    userService.getUserById(1L)
}
```

---

## 7. 테스트 실행 명령어

### 전체 테스트

```bash
./gradlew test
```

### 모듈별 테스트

```bash
./gradlew :api-mvc:test
./gradlew :api-webflux:test
```

### 단일 테스트 클래스 실행

```bash
./gradlew :api-mvc:test --tests "com.playground.mvc.UserServiceJUnit5Test"
./gradlew :api-webflux:test --tests "com.playground.webflux.UserServiceKotestTest"
```

### 단일 테스트 메서드 실행 (JUnit5)

```bash
./gradlew :api-mvc:test --tests "com.playground.mvc.UserServiceJUnit5Test.getUserById"
```

---

## 8. 선택 가이드: 언제 JUnit5 / Kotest?

### JUnit5가 유리한 경우

- 팀 표준이 JUnit5인 경우
- `@Nested`, `@DisplayName` 기반으로 **구조적인 테스트 분류**가 중요한 경우
- IDE/리포팅/도구 체인이 JUnit 중심으로 맞춰져 있는 경우

### Kotest가 유리한 경우

- `describe/context/it`로 **시나리오를 문장처럼** 표현하고 싶은 경우
- assertion을 “읽기 좋게” 작성하고 싶은 경우(`shouldBe`, `shouldThrow` 등)
- 테스트를 문서처럼 유지하고 싶은 경우(BDD 스타일)

### 결론

- 두 스타일 모두 **정답**이며, 팀/프로젝트 특성에 따라 선택하면 됩니다.
- 이 저장소는 “학습/비교” 목적이므로, 동일 기능을 두 스타일로 구현해 차이를 체감하는 것을 권장합니다.

