# Kotlin Playground

Spring Boot + Kotlin 기반의 학습용 프로젝트입니다.
- 동기(MVC)와 비동기(WebFlux + Coroutines) 방식을 비교하며 학습할 수 있도록 구성

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
## 성능 테스트 (Performance Testing)

### 테스트 엔드포인트

| 엔드포인트 | MVC (8080) | WebFlux (8081) | 설명 |
|-----------|------------|----------------|------|
| `/api/v1/delay/{ms}` | Thread.sleep | delay() | I/O 지연 시뮬레이션 |
| `/api/v1/delay/cpu/{iterations}` | CPU 작업 | CPU 작업 | CPU 바운드 작업 |
| `/api/v1/delay/blocking/{ms}` | - | Thread.sleep | 안티패턴 데모 |

### k6 부하 테스트

#### 사전 요구사항
```bash
# k6 설치 (macOS)
brew install k6

# 또는 Docker로 실행
docker run -i grafana/k6 run - <script.js
```

#### 테스트 실행
```bash
# 서비스 실행 (터미널 1, 2)
./gradlew :api-mvc:bootRun --args='--spring.profiles.active=local'
./gradlew :api-webflux:bootRun --args='--spring.profiles.active=local'

# 비교 테스트 실행
cd tests/k6
./run-comparison.sh

# 개별 테스트 실행
k6 run delay-test-mvc.js
k6 run delay-test-webflux.js
k6 run delay-test-webflux-blocking.js
```

### 스레드 모델 비교
```
MVC (Thread per Request)
========================
Request 1 ──▶ [Thread-1] ████████████░░░░░░░░░░░░ (blocking)
Request 2 ──▶ [Thread-2] ████████████░░░░░░░░░░░░ (blocking)
Request 3 ──▶ [Thread-3] ████████████░░░░░░░░░░░░ (blocking)
...
Request 200 ─▶ [Thread-200] ████████████░░░░░░░░░░ (blocking)
Request 201 ─▶ [대기열] ░░░░░░░░░░░░░░░░░░░░░░░░░ (waiting)

WebFlux (Event Loop)
====================
Request 1 ──▶ [reactor-1] ██░░░░░░░░██  (non-blocking, thread released)
Request 2 ──▶ [reactor-1] ██░░░░░░░░██  (same thread, different time)
Request 3 ──▶ [reactor-2] ██░░░░░░░░██
...
Request 1000+ ─▶ 모두 소수의 스레드로 처리
```

### Actuator 메트릭 확인

```bash
# MVC 메트릭
curl http://localhost:8080/actuator/metrics/jvm.threads.live

# WebFlux 메트릭
curl http://localhost:8081/actuator/metrics/jvm.threads.live

# 특정 메트릭 상세
curl http://localhost:8080/actuator/metrics/http.server.requests
```
---

## 참고 자료

- [Spring Boot 공식 문서](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Kotlin Coroutines 가이드](https://kotlinlang.org/docs/coroutines-guide.html)
- [Spring WebFlux 레퍼런스](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [R2DBC 공식 문서](https://r2dbc.io/)
- [Kotest 공식 문서](https://kotest.io/docs/framework/framework.html)
- [k6 부하 테스트 도구](https://k6.io/docs/)
