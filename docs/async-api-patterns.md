# 비동기 API 서버 개발 실전 패턴

실제 API 서버 개발에서 자주 마주치는 시나리오와 비동기 패턴을 다룹니다.

---

## 목차

1. [다중 서비스 호출 패턴](#1-다중-서비스-호출-패턴)
2. [캐싱 전략](#2-캐싱-전략)
3. [외부 API 연동](#3-외부-api-연동)
4. [파일 업로드/다운로드](#4-파일-업로드다운로드)
5. [실시간 통신 (SSE, WebSocket)](#5-실시간-통신)
6. [배치 처리](#6-배치-처리)
7. [에러 처리와 복원력](#7-에러-처리와-복원력)
8. [로깅과 모니터링](#8-로깅과-모니터링)
9. [테스트 전략](#9-테스트-전략)
10. [성능 최적화](#10-성능-최적화)

---

## 1. 다중 서비스 호출 패턴

### 시나리오: 사용자 대시보드 API

사용자 대시보드에 여러 정보를 한 번에 보여줘야 합니다.

```kotlin
data class DashboardResponse(
    val user: UserInfo,
    val recentOrders: List<Order>,
    val notifications: List<Notification>,
    val recommendations: List<Product>,
    val stats: UserStats
)
```

### 패턴 1: 순차 호출 (느림)

```kotlin
// ❌ 각 호출이 순차적으로 실행 - 총 5초
suspend fun getDashboard(userId: Long): DashboardResponse {
    val user = userService.getUser(userId)              // 1초
    val orders = orderService.getRecentOrders(userId)   // 1초
    val notifications = notificationService.get(userId) // 1초
    val recommendations = recommendService.get(userId)  // 1초
    val stats = statsService.getUserStats(userId)       // 1초

    return DashboardResponse(user, orders, notifications, recommendations, stats)
}
```

### 패턴 2: 완전 병렬 호출 (빠름)

```kotlin
// ✅ 모든 호출이 동시에 실행 - 총 1초
suspend fun getDashboard(userId: Long): DashboardResponse = coroutineScope {
    val user = async { userService.getUser(userId) }
    val orders = async { orderService.getRecentOrders(userId) }
    val notifications = async { notificationService.get(userId) }
    val recommendations = async { recommendService.get(userId) }
    val stats = async { statsService.getUserStats(userId) }

    DashboardResponse(
        user = user.await(),
        orders = orders.await(),
        notifications = notifications.await(),
        recommendations = recommendations.await(),
        stats = stats.await()
    )
}
```

### 패턴 3: 부분 병렬 (의존성 있는 경우)

```kotlin
// user 정보가 있어야 다른 정보를 조회할 수 있는 경우
suspend fun getDashboard(userId: Long): DashboardResponse = coroutineScope {
    // 1단계: 사용자 정보 먼저 조회
    val user = userService.getUser(userId)

    // 2단계: 사용자 정보 기반으로 병렬 조회
    val orders = async { orderService.getRecentOrders(user.id) }
    val notifications = async { notificationService.get(user.id, user.preferences) }
    val recommendations = async { recommendService.get(user.id, user.interests) }
    val stats = async { statsService.getUserStats(user.id) }

    DashboardResponse(
        user = user,
        orders = orders.await(),
        notifications = notifications.await(),
        recommendations = recommendations.await(),
        stats = stats.await()
    )
}
```

### 패턴 4: 선택적 실패 허용

```kotlin
// 일부 서비스 실패해도 응답 반환
suspend fun getDashboard(userId: Long): DashboardResponse = coroutineScope {
    val user = userService.getUser(userId)  // 필수

    // 선택적 데이터 - 실패해도 기본값 사용
    val orders = async {
        runCatching { orderService.getRecentOrders(userId) }
            .getOrDefault(emptyList())
    }
    val notifications = async {
        runCatching { notificationService.get(userId) }
            .getOrDefault(emptyList())
    }
    val recommendations = async {
        runCatching { recommendService.get(userId) }
            .getOrDefault(emptyList())
    }
    val stats = async {
        runCatching { statsService.getUserStats(userId) }
            .getOrNull()
    }

    DashboardResponse(
        user = user,
        orders = orders.await(),
        notifications = notifications.await(),
        recommendations = recommendations.await(),
        stats = stats.await()
    )
}
```

### 패턴 5: 타임아웃 적용

```kotlin
suspend fun getDashboard(userId: Long): DashboardResponse = coroutineScope {
    val user = userService.getUser(userId)

    // 각 서비스에 개별 타임아웃
    val orders = async {
        withTimeoutOrNull(500) {
            orderService.getRecentOrders(userId)
        } ?: emptyList()
    }

    // 전체 타임아웃
    withTimeout(3000) {
        DashboardResponse(
            user = user,
            orders = orders.await(),
            // ...
        )
    }
}
```

---

## 2. 캐싱 전략

### 시나리오: 자주 조회되는 데이터 캐싱

### 인메모리 캐시 (Caffeine)

```kotlin
@Service
class CachedProductService(
    private val productRepository: ProductRepository
) {
    private val cache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofMinutes(10))
        .buildSuspending<Long, Product>()

    suspend fun getProduct(id: Long): Product {
        return cache.get(id) {
            productRepository.findById(id)
                ?: throw NotFoundException("Product not found: $id")
        }
    }

    suspend fun invalidate(id: Long) {
        cache.invalidate(id)
    }
}
```

### Redis 캐시 (논블로킹)

```kotlin
@Service
class RedisCachedService(
    private val redisTemplate: ReactiveRedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository
) {
    private val cacheTtl = Duration.ofMinutes(30)

    suspend fun getUser(id: Long): User {
        val cacheKey = "user:$id"

        // 캐시 조회
        val cached = redisTemplate.opsForValue()
            .get(cacheKey)
            .awaitSingleOrNull()

        if (cached != null) {
            return objectMapper.readValue(cached, User::class.java)
        }

        // DB 조회 및 캐시 저장
        val user = userRepository.findById(id)
            ?: throw NotFoundException("User not found")

        redisTemplate.opsForValue()
            .set(cacheKey, objectMapper.writeValueAsString(user), cacheTtl)
            .awaitSingle()

        return user
    }

    suspend fun invalidateUser(id: Long) {
        redisTemplate.delete("user:$id").awaitSingle()
    }
}
```

### Cache-Aside 패턴과 Write-Through 조합

```kotlin
@Service
class UserService(
    private val repository: UserRepository,
    private val cache: UserCacheService
) {
    // Read: Cache-Aside
    suspend fun getUser(id: Long): User {
        return cache.get(id) ?: run {
            val user = repository.findById(id)
                ?: throw NotFoundException("User not found")
            cache.put(id, user)
            user
        }
    }

    // Write: Write-Through
    suspend fun updateUser(id: Long, request: UpdateUserRequest): User {
        val user = repository.findById(id)
            ?: throw NotFoundException("User not found")

        val updated = user.copy(
            name = request.name ?: user.name,
            email = request.email ?: user.email
        )

        val saved = repository.save(updated)
        cache.put(id, saved)  // 캐시도 함께 업데이트

        return saved
    }

    // Delete: Cache Invalidation
    suspend fun deleteUser(id: Long) {
        repository.deleteById(id)
        cache.invalidate(id)
    }
}
```

---

## 3. 외부 API 연동

### 시나리오: 결제 시스템 연동

### 기본 WebClient 설정

```kotlin
@Configuration
class PaymentClientConfig {

    @Bean
    fun paymentWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl("https://api.payment.com/v1")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("X-API-Key", "\${payment.api.key}")
            .filter(logRequest())
            .filter(logResponse())
            .build()
    }

    private fun logRequest() = ExchangeFilterFunction.ofRequestProcessor { request ->
        logger.info("Request: ${request.method()} ${request.url()}")
        Mono.just(request)
    }

    private fun logResponse() = ExchangeFilterFunction.ofResponseProcessor { response ->
        logger.info("Response: ${response.statusCode()}")
        Mono.just(response)
    }
}
```

### 재시도와 타임아웃 적용

```kotlin
@Service
class PaymentService(
    private val webClient: WebClient
) {
    suspend fun processPayment(request: PaymentRequest): PaymentResponse {
        return withRetry(maxRetries = 3, delayMs = 1000) {
            withTimeout(5000) {
                webClient.post()
                    .uri("/payments")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus({ it.isError }) { response ->
                        handlePaymentError(response)
                    }
                    .awaitBody<PaymentResponse>()
            }
        }
    }

    private suspend fun handlePaymentError(response: ClientResponse): Mono<Throwable> {
        val body = response.awaitBody<PaymentErrorResponse>()
        return when (response.statusCode()) {
            HttpStatus.BAD_REQUEST -> Mono.error(InvalidPaymentException(body.message))
            HttpStatus.CONFLICT -> Mono.error(DuplicatePaymentException(body.message))
            HttpStatus.TOO_MANY_REQUESTS -> Mono.error(RateLimitException(body.message))
            else -> Mono.error(PaymentException("Payment failed: ${body.message}"))
        }
    }
}

// 재시도 유틸리티
suspend fun <T> withRetry(
    maxRetries: Int,
    delayMs: Long,
    shouldRetry: (Throwable) -> Boolean = { it is IOException },
    block: suspend () -> T
): T {
    var lastException: Throwable? = null
    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: Throwable) {
            lastException = e
            if (!shouldRetry(e) || attempt == maxRetries - 1) throw e
            delay(delayMs * (attempt + 1))  // Exponential backoff
        }
    }
    throw lastException!!
}
```

### 여러 외부 API 병렬 호출

```kotlin
@Service
class OrderEnrichmentService(
    private val paymentClient: PaymentClient,
    private val shippingClient: ShippingClient,
    private val inventoryClient: InventoryClient
) {
    suspend fun enrichOrder(order: Order): EnrichedOrder = coroutineScope {
        val paymentInfo = async {
            paymentClient.getPaymentInfo(order.paymentId)
        }
        val shippingInfo = async {
            shippingClient.getShippingStatus(order.shippingId)
        }
        val stockInfo = async {
            inventoryClient.checkStock(order.productIds)
        }

        EnrichedOrder(
            order = order,
            payment = paymentInfo.await(),
            shipping = shippingInfo.await(),
            stock = stockInfo.await()
        )
    }
}
```

---

## 4. 파일 업로드/다운로드

### 시나리오: S3에 파일 업로드

### 비동기 파일 업로드

```kotlin
@Service
class FileUploadService(
    private val s3Client: S3AsyncClient,
    private val bucketName: String
) {
    suspend fun uploadFile(
        fileName: String,
        contentType: String,
        data: ByteArray
    ): String {
        val key = "${UUID.randomUUID()}/$fileName"

        val request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(contentType)
            .build()

        s3Client.putObject(request, AsyncRequestBody.fromBytes(data))
            .await()

        return "https://$bucketName.s3.amazonaws.com/$key"
    }
}
```

### 스트리밍 다운로드

```kotlin
@RestController
class FileDownloadController(
    private val fileService: FileService
) {
    @GetMapping("/files/{id}/download")
    suspend fun downloadFile(
        @PathVariable id: Long,
        response: ServerHttpResponse
    ): Flow<DataBuffer> {
        val file = fileService.getFile(id)

        response.headers.contentType = MediaType.parseMediaType(file.contentType)
        response.headers.contentDisposition = ContentDisposition.builder("attachment")
            .filename(file.name)
            .build()

        return fileService.streamFileContent(file.path)
    }
}

@Service
class FileService {
    fun streamFileContent(path: String): Flow<DataBuffer> = flow {
        val channel = AsynchronousFileChannel.open(Path.of(path), StandardOpenOption.READ)
        val buffer = ByteBuffer.allocate(8192)
        var position = 0L

        try {
            while (true) {
                buffer.clear()
                val bytesRead = channel.readSuspend(buffer, position)
                if (bytesRead == -1) break

                buffer.flip()
                emit(DefaultDataBufferFactory().wrap(buffer.array().copyOf(bytesRead)))
                position += bytesRead
            }
        } finally {
            channel.close()
        }
    }
}
```

### 멀티파트 업로드 처리

```kotlin
@RestController
class UploadController(
    private val uploadService: FileUploadService
) {
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun upload(
        @RequestPart("file") filePart: FilePart,
        @RequestPart("metadata") metadata: FileMetadata
    ): UploadResponse {
        val bytes = filePart.content()
            .reduce { buffer1, buffer2 ->
                buffer1.write(buffer2)
                DataBufferUtils.release(buffer2)
                buffer1
            }
            .awaitSingle()
            .let { buffer ->
                ByteArray(buffer.readableByteCount()).also {
                    buffer.read(it)
                    DataBufferUtils.release(buffer)
                }
            }

        val url = uploadService.uploadFile(
            fileName = filePart.filename(),
            contentType = filePart.headers().contentType?.toString() ?: "application/octet-stream",
            data = bytes
        )

        return UploadResponse(url = url, size = bytes.size)
    }
}
```

---

## 5. 실시간 통신

### 시나리오: 실시간 알림

### Server-Sent Events (SSE)

```kotlin
@RestController
class NotificationController(
    private val notificationService: NotificationService
) {
    @GetMapping("/notifications/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamNotifications(
        @RequestParam userId: Long
    ): Flow<ServerSentEvent<Notification>> {
        return notificationService.subscribeToNotifications(userId)
            .map { notification ->
                ServerSentEvent.builder(notification)
                    .id(notification.id.toString())
                    .event("notification")
                    .build()
            }
    }
}

@Service
class NotificationService {
    private val notificationChannels = ConcurrentHashMap<Long, MutableSharedFlow<Notification>>()

    fun subscribeToNotifications(userId: Long): Flow<Notification> {
        val channel = notificationChannels.getOrPut(userId) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 100)
        }
        return channel.asSharedFlow()
    }

    suspend fun sendNotification(userId: Long, notification: Notification) {
        notificationChannels[userId]?.emit(notification)
    }
}
```

### WebSocket

```kotlin
@Configuration
class WebSocketConfig {
    @Bean
    fun webSocketHandler(chatService: ChatService): WebSocketHandler {
        return ChatWebSocketHandler(chatService)
    }

    @Bean
    fun handlerMapping(handler: WebSocketHandler): HandlerMapping {
        val map = mapOf("/ws/chat" to handler)
        return SimpleUrlHandlerMapping(map, 1)
    }
}

class ChatWebSocketHandler(
    private val chatService: ChatService
) : WebSocketHandler {

    override fun handle(session: WebSocketSession): Mono<Void> {
        val roomId = session.handshakeInfo.uri.query?.let {
            parseQueryParam(it, "roomId")
        } ?: return Mono.error(IllegalArgumentException("roomId required"))

        val input = session.receive()
            .map { it.payloadAsText }
            .map { objectMapper.readValue(it, ChatMessage::class.java) }
            .doOnNext { message ->
                runBlocking {
                    chatService.broadcast(roomId, message)
                }
            }
            .then()

        val output = session.send(
            chatService.subscribe(roomId)
                .map { message ->
                    session.textMessage(objectMapper.writeValueAsString(message))
                }
                .asFlux()
        )

        return Mono.zip(input, output).then()
    }
}
```

### 실시간 대시보드 (주기적 업데이트)

```kotlin
@RestController
class DashboardController(
    private val metricsService: MetricsService
) {
    @GetMapping("/dashboard/metrics/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamMetrics(): Flow<ServerSentEvent<DashboardMetrics>> = flow {
        while (true) {
            val metrics = metricsService.getCurrentMetrics()
            emit(
                ServerSentEvent.builder(metrics)
                    .event("metrics")
                    .build()
            )
            delay(5000)  // 5초마다 업데이트
        }
    }
}
```

---

## 6. 배치 처리

### 시나리오: 대량 데이터 처리

### Flow를 이용한 배치 처리

```kotlin
@Service
class BatchProcessingService(
    private val userRepository: UserRepository,
    private val emailService: EmailService
) {
    suspend fun sendNewsletterToAllUsers() {
        var processedCount = 0
        var errorCount = 0

        userRepository.findAllSubscribedUsers()
            .buffer(100)  // 버퍼링으로 배치 처리
            .collect { user ->
                try {
                    emailService.sendNewsletter(user)
                    processedCount++
                } catch (e: Exception) {
                    logger.error("Failed to send to ${user.email}", e)
                    errorCount++
                }

                if (processedCount % 1000 == 0) {
                    logger.info("Progress: $processedCount processed, $errorCount errors")
                }
            }

        logger.info("Completed: $processedCount processed, $errorCount errors")
    }
}
```

### 청크 단위 병렬 처리

```kotlin
@Service
class ParallelBatchService(
    private val orderRepository: OrderRepository,
    private val reportService: ReportService
) {
    suspend fun generateMonthlyReports(yearMonth: YearMonth) = coroutineScope {
        val orders = orderRepository.findByMonth(yearMonth)

        orders
            .chunked(100)  // 100개씩 청크
            .map { chunk ->
                async(Dispatchers.IO) {
                    chunk.map { order ->
                        reportService.generateReport(order)
                    }
                }
            }
            .awaitAll()
            .flatten()
    }
}

// Flow 확장 함수
fun <T> Flow<T>.chunked(size: Int): Flow<List<T>> = flow {
    val chunk = mutableListOf<T>()
    collect { value ->
        chunk.add(value)
        if (chunk.size >= size) {
            emit(chunk.toList())
            chunk.clear()
        }
    }
    if (chunk.isNotEmpty()) {
        emit(chunk.toList())
    }
}
```

### 스케줄링된 배치 작업

```kotlin
@Component
class ScheduledBatchJobs(
    private val batchService: BatchProcessingService
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Scheduled(cron = "0 0 2 * * *")  // 매일 새벽 2시
    fun runDailyCleanup() {
        scope.launch {
            try {
                batchService.cleanupExpiredData()
            } catch (e: Exception) {
                logger.error("Daily cleanup failed", e)
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        scope.cancel()
    }
}
```

---

## 7. 에러 처리와 복원력

### Circuit Breaker 패턴

```kotlin
@Service
class ResilientExternalService(
    private val webClient: WebClient
) {
    private val circuitBreaker = CircuitBreaker(
        failureThreshold = 5,
        resetTimeoutMs = 30000
    )

    suspend fun callExternalApi(): Result {
        return circuitBreaker.execute {
            webClient.get()
                .uri("/api/data")
                .retrieve()
                .awaitBody<Result>()
        }
    }
}

class CircuitBreaker(
    private val failureThreshold: Int,
    private val resetTimeoutMs: Long
) {
    private var state: State = State.CLOSED
    private var failureCount = 0
    private var lastFailureTime: Long = 0

    enum class State { CLOSED, OPEN, HALF_OPEN }

    suspend fun <T> execute(block: suspend () -> T): T {
        when (state) {
            State.OPEN -> {
                if (System.currentTimeMillis() - lastFailureTime > resetTimeoutMs) {
                    state = State.HALF_OPEN
                } else {
                    throw CircuitBreakerOpenException()
                }
            }
            else -> {}
        }

        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }

    private fun onSuccess() {
        failureCount = 0
        state = State.CLOSED
    }

    private fun onFailure() {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        if (failureCount >= failureThreshold) {
            state = State.OPEN
        }
    }
}
```

### Fallback 패턴

```kotlin
@Service
class ProductService(
    private val primaryApi: ProductApi,
    private val fallbackApi: ProductApi,
    private val cache: ProductCache
) {
    suspend fun getProduct(id: Long): Product {
        // 1차: Primary API
        return runCatching { primaryApi.getProduct(id) }
            .recoverCatching {
                // 2차: Fallback API
                logger.warn("Primary API failed, trying fallback")
                fallbackApi.getProduct(id)
            }
            .recoverCatching {
                // 3차: Cache
                logger.warn("Fallback API failed, using cache")
                cache.get(id) ?: throw ProductNotFoundException(id)
            }
            .getOrThrow()
    }
}
```

### 전역 예외 처리

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @ExceptionHandler(NotFoundException::class)
    suspend fun handleNotFound(e: NotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                code = "NOT_FOUND",
                message = e.message ?: "Resource not found"
            ))
    }

    @ExceptionHandler(TimeoutCancellationException::class)
    suspend fun handleTimeout(e: TimeoutCancellationException): ResponseEntity<ErrorResponse> {
        logger.error("Request timeout", e)
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
            .body(ErrorResponse(
                code = "TIMEOUT",
                message = "Request timed out"
            ))
    }

    @ExceptionHandler(CircuitBreakerOpenException::class)
    suspend fun handleCircuitBreaker(e: CircuitBreakerOpenException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse(
                code = "SERVICE_UNAVAILABLE",
                message = "Service temporarily unavailable"
            ))
    }

    @ExceptionHandler(Exception::class)
    suspend fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                code = "INTERNAL_ERROR",
                message = "An unexpected error occurred"
            ))
    }
}
```

---

## 8. 로깅과 모니터링

### 코루틴 컨텍스트에서 요청 추적

```kotlin
// MDC 컨텍스트 전파
class MdcContext(
    private val contextMap: Map<String, String>
) : ThreadContextElement<Map<String, String>?> {

    companion object Key : CoroutineContext.Key<MdcContext>

    override val key: CoroutineContext.Key<MdcContext> = Key

    override fun updateThreadContext(context: CoroutineContext): Map<String, String>? {
        val oldState = MDC.getCopyOfContextMap()
        MDC.setContextMap(contextMap)
        return oldState
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: Map<String, String>?) {
        if (oldState == null) {
            MDC.clear()
        } else {
            MDC.setContextMap(oldState)
        }
    }
}

// 사용
suspend fun processWithTracing(requestId: String) {
    withContext(MdcContext(mapOf("requestId" to requestId))) {
        logger.info("Processing request")  // requestId가 MDC에 포함됨
        doSomething()
    }
}
```

### 요청/응답 로깅 필터

```kotlin
@Component
class LoggingFilter : WebFilter {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val requestId = UUID.randomUUID().toString().substring(0, 8)
        val startTime = System.currentTimeMillis()

        val request = exchange.request
        logger.info("[$requestId] --> ${request.method} ${request.uri}")

        return chain.filter(exchange)
            .doFinally { signal ->
                val duration = System.currentTimeMillis() - startTime
                val status = exchange.response.statusCode?.value() ?: 0
                logger.info("[$requestId] <-- $status (${duration}ms)")
            }
    }
}
```

### 메트릭 수집

```kotlin
@Service
class MetricsService(
    private val meterRegistry: MeterRegistry
) {
    private val requestCounter = meterRegistry.counter("api.requests.total")
    private val requestTimer = meterRegistry.timer("api.requests.duration")
    private val activeRequests = meterRegistry.gauge("api.requests.active", AtomicInteger(0))

    suspend fun <T> recordRequest(block: suspend () -> T): T {
        requestCounter.increment()
        activeRequests?.incrementAndGet()

        val startTime = System.nanoTime()
        return try {
            block()
        } finally {
            val duration = System.nanoTime() - startTime
            requestTimer.record(duration, TimeUnit.NANOSECONDS)
            activeRequests?.decrementAndGet()
        }
    }
}
```

---

## 9. 테스트 전략

### 단위 테스트

```kotlin
class UserServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val userService = UserService(userRepository)

    @Test
    fun `getUser should return user when exists`() = runTest {
        // given
        val user = User(id = 1, name = "Test", email = "test@test.com")
        coEvery { userRepository.findById(1) } returns user

        // when
        val result = userService.getUser(1)

        // then
        result shouldBe user
        coVerify(exactly = 1) { userRepository.findById(1) }
    }

    @Test
    fun `getUser should throw when not exists`() = runTest {
        // given
        coEvery { userRepository.findById(any()) } returns null

        // when/then
        shouldThrow<NotFoundException> {
            userService.getUser(999)
        }
    }
}
```

### 통합 테스트

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test")
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `POST users should create user`() {
        val request = CreateUserRequest(
            email = "new@test.com",
            name = "New User"
        )

        webTestClient.post()
            .uri("/api/v1/users")
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.email").isEqualTo("new@test.com")
            .jsonPath("$.name").isEqualTo("New User")
    }
}
```

### 비동기 테스트 유틸리티

```kotlin
// 가상 시간 테스트
@Test
fun `should timeout after 5 seconds`() = runTest {
    val result = withTimeoutOrNull(5000) {
        delay(10000)  // 가상 시간으로 즉시 진행
        "completed"
    }

    result shouldBe null
}

// Flow 테스트
@Test
fun `flow should emit values`() = runTest {
    val flow = flowOf(1, 2, 3)
        .map { it * 2 }

    flow.toList() shouldBe listOf(2, 4, 6)
}

// Turbine을 사용한 Flow 테스트
@Test
fun `notifications should be emitted`() = runTest {
    notificationService.subscribe(userId).test {
        notificationService.send(userId, notification1)
        awaitItem() shouldBe notification1

        notificationService.send(userId, notification2)
        awaitItem() shouldBe notification2

        cancelAndConsumeRemainingEvents()
    }
}
```

---

## 10. 성능 최적화

### 커넥션 풀 최적화

```yaml
# application.yml
spring:
  r2dbc:
    pool:
      initial-size: 10
      max-size: 50
      max-idle-time: 30m
      validation-query: SELECT 1
```

### N+1 문제 해결

```kotlin
// ❌ N+1 문제
suspend fun getUsersWithOrders(): List<UserWithOrders> {
    val users = userRepository.findAll().toList()
    return users.map { user ->
        val orders = orderRepository.findByUserId(user.id).toList()  // N번 호출!
        UserWithOrders(user, orders)
    }
}

// ✅ 배치 조회
suspend fun getUsersWithOrders(): List<UserWithOrders> {
    val users = userRepository.findAll().toList()
    val userIds = users.map { it.id }
    val ordersByUser = orderRepository.findByUserIdIn(userIds)
        .toList()
        .groupBy { it.userId }

    return users.map { user ->
        UserWithOrders(user, ordersByUser[user.id] ?: emptyList())
    }
}
```

### 응답 압축

```yaml
server:
  compression:
    enabled: true
    mime-types: application/json,application/xml,text/html,text/plain
    min-response-size: 1024
```

### 페이지네이션

```kotlin
@Service
class PaginatedService(
    private val repository: ProductRepository
) {
    fun getProducts(page: Int, size: Int): Flow<Product> {
        return repository.findAllBy(PageRequest.of(page, size))
    }

    suspend fun getProductsPage(page: Int, size: Int): Page<Product> {
        val pageable = PageRequest.of(page, size)
        val content = repository.findAllBy(pageable).toList()
        val total = repository.count()
        return PageImpl(content, pageable, total)
    }
}
```

### Rate Limiting

```kotlin
@Component
class RateLimitFilter(
    private val rateLimiter: RateLimiter
) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val clientIp = exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"

        return mono {
            if (!rateLimiter.tryAcquire(clientIp)) {
                throw RateLimitExceededException()
            }
        }.then(chain.filter(exchange))
    }
}

class RateLimiter(
    private val requestsPerMinute: Int = 100
) {
    private val requests = ConcurrentHashMap<String, MutableList<Long>>()

    fun tryAcquire(clientId: String): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - 60_000

        val clientRequests = requests.getOrPut(clientId) { mutableListOf() }
        clientRequests.removeIf { it < windowStart }

        return if (clientRequests.size < requestsPerMinute) {
            clientRequests.add(now)
            true
        } else {
            false
        }
    }
}
```

---

## 마무리

이 문서에서 다룬 패턴들은 실제 프로덕션 환경에서 자주 사용되는 것들입니다.

### 핵심 포인트

1. **병렬화**: 독립적인 작업은 항상 async로 병렬 처리
2. **복원력**: Circuit Breaker, Retry, Fallback으로 장애 대응
3. **캐싱**: 적절한 캐시 전략으로 성능 향상
4. **스트리밍**: 대용량 데이터는 Flow로 처리
5. **모니터링**: 요청 추적과 메트릭 수집 필수

### 다음 단계

1. 이 프로젝트에서 패턴들을 직접 구현해보기
2. 성능 테스트로 개선 효과 측정
3. 프로덕션 환경에 점진적으로 적용
