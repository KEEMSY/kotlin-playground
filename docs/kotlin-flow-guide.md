# Kotlin Coroutine Flow 학습 가이드

이 문서는 Kotlin Coroutines의 `Flow`를 활용하여 비동기 데이터 스트림을 처리하는 방법과 주요 연산자, 에러 처리 패턴을 설명합니다.

## 1. Flow 기초 (The Basics)

`Flow`는 비동기적으로 계산된 여러 값을 스트림으로 전달할 수 있는 차가운(Cold) 스트림입니다. 값이 요청될 때(collect) 비로소 실행을 시작합니다.

### 1.1 Flow 빌더
```kotlin
// 기본 빌더
val newsFlow = flow {
    for (i in 1..3) {
        delay(100) // 비동기 작업 시뮬레이션
        emit("뉴스 $i") // 값 방출
    }
}

// 기존 데이터로부터 생성
val simpleFlow = listOf(1, 2, 3).asFlow()
```

## 2. 주요 연산자 (Intermediate Operators)

Flow 연산자는 스트림을 변형하며, 새로운 Flow를 반환합니다.

### 2.1 변환 및 필터링
- `map`: 데이터를 변형합니다.
- `filter`: 특정 조건에 맞는 데이터만 통과시킵니다.
- `take`: 지정된 개수의 값만 가져옵니다.

### 2.2 결합 (Combining)
- `zip`: 두 Flow의 값을 1:1로 결합합니다. (가장 느린 Flow 속도에 맞춤)
- `combine`: 두 Flow 중 어느 하나라도 값이 방출되면 가장 최근의 값들끼리 결합합니다.

### 2.3 평탄화 (Flattening)
- `flatMapConcat`: 첫 번째 요소의 처리가 끝나야 다음 요소의 처리를 시작합니다.
- `flatMapMerge`: 여러 내부 Flow를 동시에 실행하고 결과를 병렬로 합칩니다.

## 3. 실행 문맥과 flowOn (Context Preservation)

Flow는 기본적으로 수집기(Collector)의 코루틴 문맥에서 실행됩니다. 하지만 특정 연산자만 다른 스레드에서 실행하고 싶을 때 `flowOn`을 사용합니다.

```kotlin
flow {
    // 무거운 CPU 작업
    emit(compute())
}
.flowOn(Dispatchers.Default) // 윗부분은 Default에서 실행
.collect {
    // UI 업데이트 등은 Main/호출 스레드에서 실행
}
```

## 4. 에러 처리 (Error Handling)

### 4.1 catch 연산자
Flow 상단에서 발생한 예외를 잡아 처리하고, 필요한 경우 대체 값을 방출할 수 있습니다.

```kotlin
newsFlow
    .catch { e -> emit("에러 발생: ${e.message}") }
    .collect { println(it) }
```

### 4.2 retry 연산자
특정 예외 발생 시 지정된 횟수만큼 재시도합니다.

```kotlin
newsFlow
    .retry(3) { cause -> cause is IOException }
    .collect { ... }
```

## 5. 실전 활용: Server-Sent Events (SSE)

Spring WebFlux에서는 `Flow<T>`를 반환하는 것만으로 간단하게 SSE(Server-Sent Events)를 구현할 수 있습니다. 이를 통해 클라이언트에 실시간 데이터를 스트리밍합니다.

```kotlin
@GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
fun streamData(): Flow<DataResponse> {
    return streamingService.getRealtimeData()
}
```

## 6. 주의 사항

1. **Cold Stream**: `collect()`를 호출하지 않으면 Flow 내부의 코드는 실행되지 않습니다.
2. **Cancellation**: Flow를 실행 중인 코루틴이 취소되면 Flow도 함께 취소됩니다.
3. **Flow vs Sequence**: Sequence는 동기적이지만, Flow는 `delay` 같은 suspend 함수를 사용하여 비동기적으로 작동합니다.
