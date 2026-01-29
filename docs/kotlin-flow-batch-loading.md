# Kotlin Flow Batch Loading (Map 매칭) 가이드

이 문서는 N+1 문제를 해결하기 위한 가장 효율적인 기술인 **Batch Loading** 패턴을 설명합니다.

## 1. 개념 (The Concept)

개별 조회 방식(Sequential/Parallel)은 게시글의 개수만큼 DB 쿼리가 발생할 가능성이 높습니다. Batch Loading은 연관된 데이터를 **한 번의 쿼리로 뭉텅이(Batch)로 가져와서 메모리에서 합치는** 방식입니다.

### 처리 프로세스
1. **게시글 조회**: 전체 게시글 리스트를 가져옵니다.
2. **ID 추출**: 게시글들에 포함된 유니크한 `userId` 리스트를 추출합니다.
3. **Batch 조회**: 추출된 ID들로 `WHERE id IN (...)` 쿼리를 한 번만 실행합니다.
4. **Map 변환**: 조회된 사용자 리스트를 `Map<ID, User>` 형태로 변환하여 빠른 검색 환경을 만듭니다.
5. **결합**: 게시글 리스트를 순회하며 Map에서 사용자 정보를 찾아 DTO로 조립합니다.

## 2. 왜 Map을 사용하는가?

리스트에서 데이터를 찾으면 매번 처음부터 끝까지 검색해야 하므로 시간 복잡도가 **O(N)**입니다. 하지만 Map은 주소를 알고 바로 찾아가므로 **O(1)**의 성능을 보입니다. 1000개의 게시글과 100명의 사용자가 있을 때:
- 리스트 검색: 최대 100,000번 비교
- Map 검색: 딱 1000번 확인

## 3. 핵심 코드 패턴 (Kotlin)

```kotlin
suspend fun getAllPostsBatch(): List<PostResponse> {
    // 1. 게시글 가져오기
    val posts = postRepository.findAll().toList()
    
    // 2. ID 추출
    val userIds = posts.map { it.userId }.distinct()
    
    // 3. 사용자 뭉텅이 조회
    val users = userRepository.findAllById(userIds).toList()
    
    // 4. Map으로 변환 (O(1) 검색을 위해)
    val userMap = users.associateBy { it.id }
    
    // 5. 메모리 조립
    return posts.map { post ->
        PostResponse.from(post, userMap[post.userId]!!)
    }
}
```

## 4. 장단점

### 장점
- **DB 최적화**: 쿼리 횟수를 획기적으로 줄입니다 (N+1 -> 1+1).
- **예측 가능성**: 데이터 양에 상관없이 쿼리 횟수가 일정합니다.
- **성능**: 대량 데이터 처리 시 병렬 개별 조회보다 훨씬 빠릅니다.

### 단점
- **메모리 사용**: 데이터를 리스트로 변환하여 메모리에 올려야 하므로, 너무 큰 데이터(수백만 건)의 경우 페이지네이션과 함께 사용해야 합니다.
- **스트리밍 손실**: 전체 데이터를 다 가져온 뒤 조립을 시작하므로, 첫 번째 데이터가 나가는 시간(TTFB)은 약간 느려질 수 있습니다.
