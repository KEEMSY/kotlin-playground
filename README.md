# Kotlin Playground

Spring Boot + Kotlin 기반의 멀티 모듈 학습용 프로젝트입니다.
동기(MVC)와 비동기(WebFlux + Coroutines) 방식을 비교하며 학습할 수 있도록 설계되었습니다.

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Kotlin 1.9.x |
| Framework | Spring Boot 3.2.x |
| Build | Gradle Kotlin DSL |
| DB (동기) | PostgreSQL + Spring Data JPA |
| DB (비동기) | PostgreSQL + R2DBC |
| Async | Kotlin Coroutines |
| API Docs | springdoc-openapi (Swagger UI) |
| Test | Kotest + JUnit5 + MockK |
| Container | Docker + Docker Compose |

---

## 프로젝트 구조

```
kotlin-playground/
├── build.gradle.kts          # 루트 빌드 설정
├── settings.gradle.kts       # 모듈 정의
├── gradle.properties         # 버전 관리
├── Dockerfile                # 애플리케이션 이미지
├── docker/
│   ├── docker-compose.yml        # local 환경
│   ├── docker-compose.qa.yml     # QA 환경
│   ├── docker-compose.prod.yml   # Production 환경
│   └── init-db.sql               # DB 초기화 스크립트
│
├── core/                     # 공통 모듈
│   └── src/main/kotlin/com/playground/core/
│       ├── domain/           # 공통 도메인 모델
│       ├── exception/        # 공통 예외
│       └── util/             # 유틸리티
│
├── api-mvc/                  # 동기 API (Spring MVC + JPA)
│   └── src/main/kotlin/com/playground/mvc/
│       ├── MvcApplication.kt
│       ├── config/           # 설정 (Swagger, Exception Handler)
│       ├── controller/       # REST Controller
│       ├── service/          # 비즈니스 로직
│       ├── repository/       # JPA Repository
│       ├── entity/           # JPA Entity
│       └── dto/              # Request/Response DTO
│
├── api-webflux/              # 비동기 API (WebFlux + R2DBC + Coroutines)
│   └── src/main/kotlin/com/playground/webflux/
│       ├── WebfluxApplication.kt
│       ├── config/           # 설정 (Swagger, R2DBC, Exception Handler)
│       ├── router/           # Functional Router
│       ├── handler/          # Request Handler
│       ├── service/          # 비즈니스 로직 (suspend 함수)
│       ├── repository/       # R2DBC Repository
│       ├── entity/           # R2DBC Entity
│       └── dto/              # Request/Response DTO
│
└── infra/                    # 인프라 연동 모듈 (확장 예정)
    ├── redis/                # Redis 연동 (예정)
    └── kafka/                # Kafka 연동 (예정)
```

---

## 동기 vs 비동기 비교

| 구분 | api-mvc | api-webflux |
|------|---------|-------------|
| 웹 프레임워크 | Spring MVC | Spring WebFlux |
| DB 접근 | JPA (blocking) | R2DBC (non-blocking) |
| 스레드 모델 | Thread per Request | Event Loop |
| 코드 스타일 | 일반 함수 | suspend 함수 (Coroutines) |
| Repository | `JpaRepository` | `CoroutineCrudRepository` |
| API 구조 | Controller | Handler + Router |

### 코드 비교 예시

**전체 조회**
```kotlin
// MVC
fun getAllUsers(): List<UserResponse>

// WebFlux
fun getAllUsers(): Flow<UserResponse>
```

**단건 조회**
```kotlin
// MVC
fun getUserById(id: Long): UserResponse  // Optional 사용

// WebFlux
suspend fun getUserById(id: Long): UserResponse  // nullable 사용
```

**저장**
```kotlin
// MVC
fun createUser(request: CreateUserRequest): UserResponse

// WebFlux
suspend fun createUser(request: CreateUserRequest): UserResponse
```

---

## 실행 방법

### 사전 요구사항
- JDK 17+
- Docker & Docker Compose

### 1. Docker로 전체 실행

```bash
# 전체 서비스 실행 (PostgreSQL + api-mvc + api-webflux)
docker-compose -f docker/docker-compose.yml up -d

# 컨테이너 상태 확인
docker ps
```

### 2. 개발 환경 (Hot Reload 지원)

Docker 컨테이너 내에서 코드 변경 시 자동 재시작됩니다.

```bash
# 전체 개발 환경 실행
docker-compose -f docker/docker-compose.dev.yml up -d

# 특정 서비스만 실행
docker-compose -f docker/docker-compose.dev.yml up -d postgres api-mvc

# 로그 확인
docker-compose -f docker/docker-compose.dev.yml logs -f api-mvc

# 재시작 (의존성 변경 등)
docker-compose -f docker/docker-compose.dev.yml restart api-mvc

# 종료
docker-compose -f docker/docker-compose.dev.yml down
```

**Hot Reload 동작 방식:**
- 소스 코드가 볼륨으로 마운트됨
- Spring Boot DevTools가 파일 변경 감지
- 자동으로 애플리케이션 재시작

**Hot Reload 가능/불가능:**
| 변경 사항 | Hot Reload | 재시작 필요 |
|----------|:----------:|:----------:|
| 메서드 내부 로직 수정 | ✓ | |
| 새 클래스 추가 | ✓ | |
| 메서드 시그니처 변경 | | ✓ |
| build.gradle.kts 수정 | | ✓ |
| application.yml 변경 | | ✓ |
| 새 Bean 추가 | | ✓ |

### 3. 로컬 직접 실행 (IDE 디버깅용)

```bash
# PostgreSQL만 Docker로 실행
docker-compose -f docker/docker-compose.yml up -d postgres

# 동기 API 실행 (터미널 1)
./gradlew :api-mvc:bootRun --args='--spring.profiles.active=local'

# 비동기 API 실행 (터미널 2)
./gradlew :api-webflux:bootRun --args='--spring.profiles.active=local'
```

### 4. 빌드 & 테스트

```bash
# 전체 빌드
./gradlew clean build

# 테스트만 실행
./gradlew test

# 특정 모듈 테스트
./gradlew :api-mvc:test
./gradlew :api-webflux:test
```

---

## API 문서 (Swagger UI)

| 모듈 | URL |
|------|-----|
| api-mvc | http://localhost:8080/swagger-ui.html |
| api-webflux | http://localhost:8081/swagger-ui.html |

### API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/users` | 전체 사용자 조회 |
| GET | `/api/v1/users/{id}` | ID로 사용자 조회 |
| GET | `/api/v1/users/email/{email}` | 이메일로 사용자 조회 |
| POST | `/api/v1/users` | 사용자 생성 |
| PUT | `/api/v1/users/{id}` | 사용자 수정 |
| DELETE | `/api/v1/users/{id}` | 사용자 삭제 |

---

## 환경별 설정

| 환경 | api-mvc | api-webflux | PostgreSQL |
|------|---------|-------------|------------|
| local | 8080 | 8081 | 5432 |
| qa | 8080 | 8081 | 5432 |
| prod | 8080 | 8081 | 5432 |

### 프로파일 설정 파일
- `application.yml` - 공통 설정
- `application-local.yml` - 로컬 개발 환경
- `application-qa.yml` - QA 환경
- `application-prod.yml` - 프로덕션 환경

---

## 학습 가이드

### 1단계: 동기 API 이해 (api-mvc)

Spring MVC + JPA 기반의 전통적인 웹 애플리케이션 구조를 학습합니다.

**주요 파일:**
```
api-mvc/src/main/kotlin/com/playground/mvc/
├── controller/UserController.kt   ← REST API 진입점
├── service/UserService.kt         ← 비즈니스 로직
├── repository/UserRepository.kt   ← DB 접근 (JPA)
└── entity/User.kt                 ← JPA 엔티티
```

**학습 포인트:**
- `@RestController`, `@GetMapping`, `@PostMapping` 등 어노테이션
- `JpaRepository` 인터페이스와 메서드 네이밍 규칙
- `@Transactional` 트랜잭션 관리
- `@Valid`를 통한 요청 검증

### 2단계: 비동기 API 이해 (api-webflux)

WebFlux + R2DBC + Coroutines 기반의 리액티브 프로그래밍을 학습합니다.

**주요 파일:**
```
api-webflux/src/main/kotlin/com/playground/webflux/
├── router/UserRouter.kt           ← 라우팅 정의
├── handler/UserHandler.kt         ← 요청 처리
├── service/UserService.kt         ← suspend 함수로 작성
└── repository/UserRepository.kt   ← CoroutineCrudRepository
```

**학습 포인트:**
- `suspend` 키워드와 코루틴 기초
- `Flow<T>` vs `List<T>` 차이점
- Functional Endpoints (Router + Handler) 패턴
- R2DBC의 논블로킹 DB 접근

### 3단계: 테스트 코드 분석

**JUnit5 스타일:**
```kotlin
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
```

**Kotest 스타일:**
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

### 4단계: 실습 과제

1. **새 엔티티 추가**
   - `Post` 도메인을 api-mvc와 api-webflux 양쪽에 구현
   - CRUD API 작성

2. **관계 매핑**
   - User와 Post의 1:N 관계 구현
   - JPA: `@OneToMany`, `@ManyToOne`
   - R2DBC: 수동 조인 또는 별도 쿼리

3. **테스트 작성**
   - 새로 추가한 기능에 대한 Kotest 테스트 작성
   - MockK를 활용한 모킹

4. **인프라 연동**
   - infra 모듈에 Redis 캐시 기능 구현
   - Kafka 이벤트 발행/구독 구현

---

## IDE 설정 (Cursor / VS Code)

### 추천 익스텐션

**필수:**
| 익스텐션 | 설명 |
|----------|------|
| Kotlin Language | Kotlin 언어 지원 (JetBrains 공식) |
| Spring Boot Extension Pack | Spring Boot 개발 지원 |
| Gradle for Java | Gradle 빌드 지원 |

**권장:**
| 익스텐션 | 설명 |
|----------|------|
| Docker | Docker/Compose 파일 지원 |
| YAML | application.yml 편집 지원 |
| GitLens | Git 히스토리/blame 보기 |
| Database Client | DB 조회 (PostgreSQL 연결) |

### 설치 방법
`Cmd+Shift+X` (macOS) 또는 `Ctrl+Shift+X` (Windows/Linux) → 익스텐션 검색

---

## 문제 해결

### Docker 빌드 실패 (Apple Silicon)
```
no match for platform in manifest: not found
```
→ Dockerfile에서 `eclipse-temurin:17-jre` 이미지 사용 (alpine 제외)

### Swagger UI 500 에러
→ `GlobalExceptionHandler`가 정적 리소스 요청을 가로채는 문제
→ `NoResourceFoundException` 핸들러 추가로 해결

### WebFlux Swagger UI 경로
- 올바른 경로: `/swagger-ui.html` 또는 `/webjars/swagger-ui/index.html`
- `/swagger-ui/index.html`은 WebFlux에서 지원하지 않음

---

## 참고 자료

- [Spring Boot 공식 문서](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Kotlin Coroutines 가이드](https://kotlinlang.org/docs/coroutines-guide.html)
- [Spring WebFlux 레퍼런스](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [R2DBC 공식 문서](https://r2dbc.io/)
- [Kotest 공식 문서](https://kotest.io/docs/framework/framework.html)
