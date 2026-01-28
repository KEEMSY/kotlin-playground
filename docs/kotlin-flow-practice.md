# Kotlin Flow 실전 실습 가이드

이 문서는 `api-webflux` 모듈에서 진행한 Flow 최적화 및 스트리밍 구현 내용을 복습하기 위해 작성되었습니다.

## 1. 실시간 스트리밍 (SSE) 구현

WebFlux에서는 `Flow`를 반환하는 것만으로 간단하게 서버 전송 이벤트(SSE)를 구현할 수 있습니다.

### 핵심 코드 패턴
```kotlin
// StreamingController.kt
@GetMapping("/news", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
fun getNewsStream(): Flow<News> {
    return streamingService.getNewsStream()
}
```
- **특징**: 클라이언트가 연결을 끊기 전까지 서버가 데이터를 지속적으로 밀어넣습니다(Push).
- **응용**: 주식 시세, 알림 서비스, 대시보드.

## 2. 스트림 최적화: N+1 문제 해결

### 문제 상황 (개선 전)
데이터 한 줄을 읽을 때마다 연관된 데이터를 조회하면 성능이 급격히 저하됩니다.
```kotlin
// 비효율적: 게시글 N개당 사용자 조회 N번 발생
postRepository.findAllByUserId(userId)
    .map { post ->
        val user = userRepository.findById(post.userId) 
        PostResponse.from(post, user)
    }
```

### 해결 방법 (개선 후)
연관된 부모 데이터를 **미리 한 번만 조회**하고 스트림 내부에서 재사용합니다.
```kotlin
fun getPostsByUserId(userId: Long): Flow<PostResponse> = flow {
    val user = userRepository.findById(userId) ?: throw ... // 1회 조회
    postRepository.findAllByUserId(userId).collect { post ->
        emit(PostResponse.from(post, user)) // 재사용
    }
}
```

## 3. 병렬 처리 연산자: `flatMapMerge`

여러 개의 비동기 작업을 동시에 처리해야 할 때 사용합니다.

### 핵심 코드
```kotlin
@OptIn(kotlinx.coroutines.FlowPreview::class)
fun getAllPosts(): Flow<PostResponse> = postRepository.findAll()
    .flatMapMerge(concurrency = 16) { post ->
        flow {
            val user = userRepository.findById(post.userId) ?: throw ...
            emit(PostResponse.from(post, user))
        }
    }
```
- **flatMapMerge**: 여러 내부 Flow를 동시에 실행하고 결과를 합칩니다.
- **concurrency**: 동시에 실행할 최대 작업 수입니다. (예: 16개씩 병렬 처리)
- **@OptIn**: 이 연산자가 아직 실험적(Experimental) 기능임을 인지하고 사용하겠다는 선언입니다.

## 4. 스트림 결합: `merge`

서로 다른 성격의 데이터 스트림을 하나로 합쳐서 클라이언트에 전달할 때 유용합니다.

```kotlin
fun getCombinedStream(): Flow<String> {
    val news = getNewsStream().map { "[뉴스] ..." }
    val stocks = getStockPriceStream().map { "[주식] ..." }

    return merge(news, stocks) // 먼저 도착하는 순서대로 방출
}
```

---

## 💡 복습 포인트
1. **차가운 스트림(Cold Stream)**: Flow는 수집(collect)하기 전까지는 아무런 동작도 하지 않습니다.
2. **비블로킹(Non-blocking)**: 모든 작업은 코루틴 위에서 비블로킹으로 동작하므로, 많은 요청도 적은 스레드로 처리할 수 있습니다.
3. **연산자 체이닝**: `map`, `filter`, `take`, `catch` 등을 연결하여 데이터 파이프라인을 구축하는 연습이 필요합니다.
