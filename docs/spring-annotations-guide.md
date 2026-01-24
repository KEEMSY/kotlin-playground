# Spring 어노테이션 가이드

Spring MVC와 WebFlux에서 사용하는 주요 어노테이션을 학습합니다.

---

## 목차

1. [컨트롤러 어노테이션](#1-컨트롤러-어노테이션)
2. [HTTP 메서드 매핑](#2-http-메서드-매핑)
3. [요청 데이터 바인딩](#3-요청-데이터-바인딩)
4. [응답 관련 어노테이션](#4-응답-관련-어노테이션)
5. [Swagger/OpenAPI 문서화](#5-swaggeropenapi-문서화)
6. [검증 어노테이션](#6-검증-어노테이션)
7. [서비스/컴포넌트 어노테이션](#7-서비스컴포넌트-어노테이션)
8. [트랜잭션 어노테이션](#8-트랜잭션-어노테이션)
9. [MVC vs WebFlux 비교](#9-mvc-vs-webflux-비교)

---

## 1. 컨트롤러 어노테이션

### @RestController

REST API 컨트롤러임을 선언합니다. `@Controller` + `@ResponseBody`의 조합입니다.

```kotlin
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService
) {
    // 모든 메서드의 반환값이 자동으로 JSON 변환됨
}
```

### @Controller vs @RestController

| 어노테이션 | 용도 | 반환값 처리 |
|-----------|------|------------|
| `@Controller` | 전통적인 MVC (View 반환) | 뷰 이름을 반환 |
| `@RestController` | REST API | 객체를 JSON으로 변환 |

```kotlin
// @Controller - 뷰 반환
@Controller
class WebController {
    @GetMapping("/home")
    fun home(): String = "home"  // templates/home.html 렌더링
}

// @RestController - JSON 반환
@RestController
class ApiController {
    @GetMapping("/users")
    fun getUsers(): List<User> = listOf(...)  // JSON 응답
}
```

### @RequestMapping

클래스 또는 메서드 레벨에서 URL 경로를 매핑합니다.

```kotlin
@RestController
@RequestMapping("/api/v1/users")  // 기본 경로 설정
class UserController {

    @RequestMapping(method = [RequestMethod.GET])  // GET /api/v1/users
    fun getAllUsers(): List<User> = ...

    @RequestMapping("/{id}", method = [RequestMethod.GET])  // GET /api/v1/users/{id}
    fun getUser(@PathVariable id: Long): User = ...
}
```

---

## 2. HTTP 메서드 매핑

### @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping

HTTP 메서드별 축약 어노테이션입니다.

```kotlin
@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

    // GET /api/v1/users
    @GetMapping
    suspend fun getAllUsers(): ResponseEntity<List<UserResponse>> {
        return ResponseEntity.ok(userService.getAllUsers())
    }

    // GET /api/v1/users/123
    @GetMapping("/{id}")
    suspend fun getUserById(@PathVariable id: Long): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.getUserById(id))
    }

    // POST /api/v1/users
    @PostMapping
    suspend fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<UserResponse> {
        val user = userService.createUser(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(user)
    }

    // PUT /api/v1/users/123
    @PutMapping("/{id}")
    suspend fun updateUser(
        @PathVariable id: Long,
        @RequestBody request: UpdateUserRequest
    ): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.updateUser(id, request))
    }

    // DELETE /api/v1/users/123
    @DeleteMapping("/{id}")
    suspend fun deleteUser(@PathVariable id: Long): ResponseEntity<Void> {
        userService.deleteUser(id)
        return ResponseEntity.noContent().build()
    }

    // PATCH /api/v1/users/123
    @PatchMapping("/{id}")
    suspend fun patchUser(
        @PathVariable id: Long,
        @RequestBody request: PatchUserRequest
    ): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.patchUser(id, request))
    }
}
```

### 메서드 어노테이션 속성

```kotlin
@GetMapping(
    value = ["/search"],           // 경로 (여러 개 지정 가능)
    params = ["name"],             // 필수 쿼리 파라미터
    headers = ["X-API-Version=1"], // 필수 헤더
    produces = ["application/json"], // 응답 Content-Type
    consumes = ["application/json"]  // 요청 Content-Type (GET에서는 보통 안 씀)
)
suspend fun search(@RequestParam name: String): List<User> = ...
```

---

## 3. 요청 데이터 바인딩

### @PathVariable

URL 경로의 변수를 바인딩합니다.

```kotlin
// GET /api/v1/users/123
@GetMapping("/{id}")
suspend fun getUser(@PathVariable id: Long): User = ...

// GET /api/v1/users/123/orders/456
@GetMapping("/{userId}/orders/{orderId}")
suspend fun getOrder(
    @PathVariable userId: Long,
    @PathVariable orderId: Long
): Order = ...

// 변수명이 다른 경우
@GetMapping("/{user_id}")
suspend fun getUser(@PathVariable("user_id") userId: Long): User = ...

// 선택적 PathVariable (required = false, 기본값 설정)
@GetMapping(value = ["/users", "/users/{id}"])
suspend fun getUsers(@PathVariable(required = false) id: Long?): Any {
    return if (id != null) userService.getById(id) else userService.getAll()
}
```

### @RequestParam

쿼리 파라미터를 바인딩합니다.

```kotlin
// GET /api/v1/users?name=john&age=25
@GetMapping
suspend fun searchUsers(
    @RequestParam name: String,           // 필수
    @RequestParam(required = false) age: Int?,  // 선택
    @RequestParam(defaultValue = "0") page: Int,  // 기본값
    @RequestParam(defaultValue = "10") size: Int
): List<User> = ...

// 여러 값 받기: GET /api/v1/users?ids=1&ids=2&ids=3
@GetMapping
suspend fun getUsersByIds(@RequestParam ids: List<Long>): List<User> = ...

// 모든 파라미터를 Map으로 받기
@GetMapping("/search")
suspend fun search(@RequestParam params: Map<String, String>): List<User> = ...
```

### @RequestBody

HTTP 요청 본문을 객체로 변환합니다.

```kotlin
// POST /api/v1/users
// Body: {"email": "test@test.com", "name": "John"}
@PostMapping
suspend fun createUser(@RequestBody request: CreateUserRequest): UserResponse = ...

// 검증과 함께 사용
@PostMapping
suspend fun createUser(@Valid @RequestBody request: CreateUserRequest): UserResponse = ...
```

```kotlin
// DTO 정의
data class CreateUserRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Name is required")
    @field:Size(min = 2, max = 50, message = "Name must be 2-50 characters")
    val name: String
)
```

### @RequestHeader

HTTP 헤더 값을 바인딩합니다.

```kotlin
@GetMapping
suspend fun getUsers(
    @RequestHeader("Authorization") token: String,
    @RequestHeader(value = "X-Request-Id", required = false) requestId: String?,
    @RequestHeader(value = "Accept-Language", defaultValue = "en") lang: String
): List<User> = ...

// 모든 헤더를 Map으로 받기
@GetMapping
suspend fun getUsers(@RequestHeader headers: Map<String, String>): List<User> = ...
```

### @CookieValue

쿠키 값을 바인딩합니다.

```kotlin
@GetMapping
suspend fun getUsers(
    @CookieValue("session_id") sessionId: String,
    @CookieValue(value = "theme", defaultValue = "light") theme: String
): List<User> = ...
```

### @ModelAttribute

폼 데이터나 쿼리 파라미터를 객체로 바인딩합니다.

```kotlin
// GET /api/v1/users/search?name=john&minAge=20&maxAge=30
@GetMapping("/search")
suspend fun searchUsers(@ModelAttribute criteria: UserSearchCriteria): List<User> = ...

data class UserSearchCriteria(
    val name: String?,
    val minAge: Int?,
    val maxAge: Int?,
    val page: Int = 0,
    val size: Int = 10
)
```

---

## 4. 응답 관련 어노테이션

### @ResponseStatus

기본 HTTP 상태 코드를 지정합니다.

```kotlin
@PostMapping
@ResponseStatus(HttpStatus.CREATED)  // 201 Created
suspend fun createUser(@RequestBody request: CreateUserRequest): UserResponse = ...

@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)  // 204 No Content
suspend fun deleteUser(@PathVariable id: Long) {
    userService.deleteUser(id)
}
```

### ResponseEntity 사용

상태 코드, 헤더, 본문을 세밀하게 제어합니다.

```kotlin
@PostMapping
suspend fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<UserResponse> {
    val user = userService.createUser(request)
    
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .header("X-User-Id", user.id.toString())
        .body(user)
}

@GetMapping("/{id}")
suspend fun getUser(@PathVariable id: Long): ResponseEntity<UserResponse> {
    val user = userService.findById(id)
    
    return if (user != null) {
        ResponseEntity.ok(user)
    } else {
        ResponseEntity.notFound().build()
    }
}

// 다양한 ResponseEntity 팩토리 메서드
ResponseEntity.ok(body)                    // 200 OK
ResponseEntity.created(uri).body(body)     // 201 Created
ResponseEntity.accepted().body(body)       // 202 Accepted
ResponseEntity.noContent().build()         // 204 No Content
ResponseEntity.badRequest().body(error)    // 400 Bad Request
ResponseEntity.notFound().build()          // 404 Not Found
ResponseEntity.status(HttpStatus.CONFLICT).body(error)  // 409 Conflict
```

---

## 5. Swagger/OpenAPI 문서화

### @Tag

컨트롤러 그룹을 정의합니다.

```kotlin
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "User management APIs")
class UserController { ... }
```

### @Operation

개별 API 엔드포인트를 설명합니다.

```kotlin
@GetMapping("/{id}")
@Operation(
    summary = "Get user by ID",
    description = "Retrieve a user by their unique identifier"
)
suspend fun getUserById(@PathVariable id: Long): ResponseEntity<UserResponse> = ...
```

### @ApiResponse / @ApiResponses

응답 코드별 설명을 추가합니다.

```kotlin
@GetMapping("/{id}")
@Operation(summary = "Get user by ID")
@ApiResponses(
    ApiResponse(responseCode = "200", description = "Successfully retrieved user"),
    ApiResponse(responseCode = "404", description = "User not found"),
    ApiResponse(responseCode = "500", description = "Internal server error")
)
suspend fun getUserById(@PathVariable id: Long): ResponseEntity<UserResponse> = ...
```

### @Parameter

파라미터에 대한 설명을 추가합니다.

```kotlin
@GetMapping("/{id}")
suspend fun getUserById(
    @Parameter(description = "User ID", required = true, example = "123")
    @PathVariable id: Long
): ResponseEntity<UserResponse> = ...

@GetMapping
suspend fun searchUsers(
    @Parameter(description = "Search keyword")
    @RequestParam(required = false) keyword: String?,
    
    @Parameter(description = "Page number", example = "0")
    @RequestParam(defaultValue = "0") page: Int,
    
    @Parameter(description = "Page size", example = "10")
    @RequestParam(defaultValue = "10") size: Int
): ResponseEntity<List<UserResponse>> = ...
```

### @Schema

DTO 필드에 대한 설명을 추가합니다.

```kotlin
data class CreateUserRequest(
    @field:Schema(description = "User email address", example = "user@example.com")
    val email: String,

    @field:Schema(description = "User display name", example = "John Doe", minLength = 2, maxLength = 50)
    val name: String,

    @field:Schema(description = "User age", minimum = "0", maximum = "150")
    val age: Int?
)
```

### 전체 예시

```kotlin
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "User management APIs")
class UserController(private val userService: UserService) {

    @GetMapping("/{id}")
    @Operation(
        summary = "Get user by ID",
        description = "Retrieve a user by their unique identifier"
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved user",
            content = [Content(schema = Schema(implementation = UserResponse::class))]
        ),
        ApiResponse(responseCode = "404", description = "User not found")
    )
    suspend fun getUserById(
        @Parameter(description = "User ID", required = true)
        @PathVariable id: Long
    ): ResponseEntity<UserResponse> {
        val user = userService.getUserById(id)
        return ResponseEntity.ok(user)
    }

    @PostMapping
    @Operation(summary = "Create a new user")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Successfully created user"),
        ApiResponse(responseCode = "400", description = "Invalid input"),
        ApiResponse(responseCode = "409", description = "User already exists")
    )
    suspend fun createUser(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "User creation request",
            required = true,
            content = [Content(schema = Schema(implementation = CreateUserRequest::class))]
        )
        @Valid @RequestBody request: CreateUserRequest
    ): ResponseEntity<UserResponse> {
        val user = userService.createUser(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(user)
    }
}
```

---

## 6. 검증 어노테이션

Jakarta Bean Validation (이전 javax.validation) 어노테이션입니다.

### 기본 검증 어노테이션

```kotlin
data class CreateUserRequest(
    // 문자열 검증
    @field:NotNull(message = "Email cannot be null")
    @field:NotBlank(message = "Email cannot be blank")
    @field:NotEmpty(message = "Email cannot be empty")
    @field:Email(message = "Invalid email format")
    @field:Size(min = 5, max = 100, message = "Email must be 5-100 characters")
    val email: String,

    @field:Pattern(regexp = "^[a-zA-Z0-9]+$", message = "Username must be alphanumeric")
    val username: String,

    // 숫자 검증
    @field:Min(value = 0, message = "Age must be at least 0")
    @field:Max(value = 150, message = "Age must be at most 150")
    val age: Int,

    @field:Positive(message = "Price must be positive")
    @field:PositiveOrZero(message = "Count must be zero or positive")
    val price: BigDecimal,

    @field:DecimalMin(value = "0.0", message = "Rating must be at least 0")
    @field:DecimalMax(value = "5.0", message = "Rating must be at most 5")
    val rating: Double,

    // 시간 검증
    @field:Past(message = "Birth date must be in the past")
    val birthDate: LocalDate,

    @field:Future(message = "Expiry date must be in the future")
    val expiryDate: LocalDateTime,

    @field:PastOrPresent(message = "Created date must be past or present")
    val createdAt: Instant,

    // 컬렉션 검증
    @field:Size(min = 1, max = 10, message = "Tags must have 1-10 items")
    val tags: List<String>,

    // Boolean 검증
    @field:AssertTrue(message = "Must accept terms")
    val acceptTerms: Boolean
)
```

### 중첩 객체 검증

```kotlin
data class OrderRequest(
    @field:NotNull
    @field:Valid  // 중첩 객체도 검증
    val shippingAddress: AddressRequest,

    @field:NotEmpty
    @field:Valid  // 리스트의 각 요소도 검증
    val items: List<OrderItemRequest>
)

data class AddressRequest(
    @field:NotBlank
    val street: String,

    @field:NotBlank
    val city: String,

    @field:Pattern(regexp = "^\\d{5}$")
    val zipCode: String
)
```

### 컨트롤러에서 검증 적용

```kotlin
@PostMapping
suspend fun createUser(
    @Valid @RequestBody request: CreateUserRequest  // @Valid로 검증 활성화
): ResponseEntity<UserResponse> {
    // 검증 실패 시 MethodArgumentNotValidException 발생
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(userService.createUser(request))
}

// 경로 변수 검증 (@Validated 클래스 레벨에 필요)
@Validated
@RestController
@RequestMapping("/api/v1/users")
class UserController {

    @GetMapping("/{id}")
    suspend fun getUser(
        @PathVariable @Min(1) id: Long  // id는 1 이상이어야 함
    ): ResponseEntity<UserResponse> = ...
}
```

### 검증 예외 처리

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.map { error ->
            FieldError(
                field = error.field,
                message = error.defaultMessage ?: "Validation failed"
            )
        }

        return ResponseEntity.badRequest().body(
            ErrorResponse(
                code = "VALIDATION_ERROR",
                message = "Validation failed",
                errors = errors
            )
        )
    }
}

data class ErrorResponse(
    val code: String,
    val message: String,
    val errors: List<FieldError> = emptyList()
)

data class FieldError(
    val field: String,
    val message: String
)
```

---

## 7. 서비스/컴포넌트 어노테이션

### 스테레오타입 어노테이션

| 어노테이션 | 용도 | 설명 |
|-----------|------|------|
| `@Component` | 일반 컴포넌트 | 스프링 빈으로 등록 |
| `@Service` | 서비스 레이어 | 비즈니스 로직 담당 |
| `@Repository` | 데이터 접근 레이어 | DB 접근 담당, 예외 변환 |
| `@Controller` | 프레젠테이션 레이어 | 웹 요청 처리 |
| `@RestController` | REST API | @Controller + @ResponseBody |
| `@Configuration` | 설정 클래스 | @Bean 메서드 포함 |

```kotlin
// 서비스 레이어
@Service
class UserService(
    private val userRepository: UserRepository
) {
    suspend fun getUserById(id: Long): UserResponse { ... }
}

// 리포지토리 레이어
@Repository
interface UserRepository : CoroutineCrudRepository<User, Long> {
    suspend fun findByEmail(email: String): User?
}

// 일반 컴포넌트
@Component
class EmailValidator {
    fun isValid(email: String): Boolean = ...
}

// 설정 클래스
@Configuration
class SwaggerConfig {
    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(Info().title("API").version("1.0"))
}
```

### 의존성 주입 어노테이션

```kotlin
// 생성자 주입 (권장)
@Service
class UserService(
    private val userRepository: UserRepository,  // 자동 주입
    private val emailService: EmailService
)

// 필드 주입 (비권장)
@Service
class UserService {
    @Autowired
    private lateinit var userRepository: UserRepository
}

// 특정 빈 지정
@Service
class NotificationService(
    @Qualifier("smsNotifier") private val notifier: Notifier
)

// 선택적 의존성
@Service
class CacheService(
    @Autowired(required = false)
    private val redisTemplate: RedisTemplate<String, Any>?
)
```

---

## 8. 트랜잭션 어노테이션

### @Transactional

```kotlin
@Service
@Transactional(readOnly = true)  // 클래스 레벨: 기본 읽기 전용
class UserService(private val userRepository: UserRepository) {

    // 읽기 전용 트랜잭션 (클래스 레벨 설정 상속)
    suspend fun getUserById(id: Long): UserResponse {
        return userRepository.findById(id)?.let { UserResponse.from(it) }
            ?: throw NotFoundException("User not found")
    }

    // 쓰기 트랜잭션 (메서드 레벨에서 오버라이드)
    @Transactional
    suspend fun createUser(request: CreateUserRequest): UserResponse {
        val user = userRepository.save(request.toEntity())
        return UserResponse.from(user)
    }

    // 롤백 조건 지정
    @Transactional(
        rollbackFor = [Exception::class],
        noRollbackFor = [BusinessException::class]
    )
    suspend fun processOrder(orderId: Long) { ... }

    // 전파 레벨
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    suspend fun auditLog(action: String) { ... }

    // 격리 수준
    @Transactional(isolation = Isolation.SERIALIZABLE)
    suspend fun transferMoney(from: Long, to: Long, amount: BigDecimal) { ... }

    // 타임아웃
    @Transactional(timeout = 30)  // 30초
    suspend fun longRunningOperation() { ... }
}
```

### 트랜잭션 전파 레벨

| 전파 레벨 | 설명 |
|----------|------|
| `REQUIRED` (기본) | 기존 트랜잭션 사용, 없으면 새로 생성 |
| `REQUIRES_NEW` | 항상 새 트랜잭션 생성 |
| `SUPPORTS` | 기존 트랜잭션 있으면 사용, 없으면 트랜잭션 없이 실행 |
| `NOT_SUPPORTED` | 트랜잭션 없이 실행 |
| `MANDATORY` | 기존 트랜잭션 필수, 없으면 예외 |
| `NEVER` | 트랜잭션 있으면 예외 |
| `NESTED` | 중첩 트랜잭션 (세이브포인트) |

---

## 9. MVC vs WebFlux 비교

### 어노테이션 사용 차이

| 구분 | MVC | WebFlux |
|------|-----|---------|
| 컨트롤러 | `@RestController` | `@RestController` |
| 매핑 | `@GetMapping` 등 | `@GetMapping` 등 |
| 반환 타입 | `ResponseEntity<T>` | `ResponseEntity<T>` |
| 함수 타입 | 일반 함수 | `suspend` 함수 |
| 스레드 모델 | Thread per Request | Event Loop |

### 코드 비교

```kotlin
// MVC
@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Long): ResponseEntity<UserResponse> {
        val user = userService.getUserById(id)
        return ResponseEntity.ok(user)
    }

    @GetMapping
    fun getAllUsers(): ResponseEntity<List<UserResponse>> {
        return ResponseEntity.ok(userService.getAllUsers())
    }
}
```

```kotlin
// WebFlux (어노테이션 기반)
@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

    @GetMapping("/{id}")
    suspend fun getUserById(@PathVariable id: Long): ResponseEntity<UserResponse> {
        val user = userService.getUserById(id)
        return ResponseEntity.ok(user)
    }

    @GetMapping
    suspend fun getAllUsers(): ResponseEntity<List<UserResponse>> {
        val users = userService.getAllUsers().toList()
        return ResponseEntity.ok(users)
    }
}
```

### 주요 차이점

1. **suspend 키워드**: WebFlux는 코루틴 지원으로 `suspend` 함수 사용
2. **Flow 처리**: WebFlux에서 스트리밍 데이터는 `Flow<T>` 사용
3. **Repository**: MVC는 `JpaRepository`, WebFlux는 `CoroutineCrudRepository`

```kotlin
// MVC Repository
interface UserRepository : JpaRepository<User, Long>

// WebFlux Repository
interface UserRepository : CoroutineCrudRepository<User, Long>
```

---

## 빠른 참조표

### HTTP 메서드 → 어노테이션

| HTTP Method | 어노테이션 | 용도 |
|-------------|-----------|------|
| GET | `@GetMapping` | 조회 |
| POST | `@PostMapping` | 생성 |
| PUT | `@PutMapping` | 전체 수정 |
| PATCH | `@PatchMapping` | 부분 수정 |
| DELETE | `@DeleteMapping` | 삭제 |

### 요청 데이터 → 어노테이션

| 데이터 위치 | 어노테이션 | 예시 |
|------------|-----------|------|
| URL 경로 | `@PathVariable` | `/users/{id}` |
| 쿼리 파라미터 | `@RequestParam` | `/users?name=john` |
| 요청 본문 | `@RequestBody` | JSON body |
| 헤더 | `@RequestHeader` | `Authorization` |
| 쿠키 | `@CookieValue` | `session_id` |
| 폼 데이터 | `@ModelAttribute` | form fields |

### 자주 사용하는 검증

| 검증 내용 | 어노테이션 |
|----------|-----------|
| null 불가 | `@NotNull` |
| 빈 문자열 불가 | `@NotBlank` |
| 이메일 형식 | `@Email` |
| 길이 제한 | `@Size(min, max)` |
| 숫자 범위 | `@Min`, `@Max` |
| 패턴 매칭 | `@Pattern(regexp)` |
| 과거 날짜 | `@Past` |
| 미래 날짜 | `@Future` |
