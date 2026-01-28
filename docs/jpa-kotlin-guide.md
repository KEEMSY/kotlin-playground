# Kotlin JPA 학습 가이드

이 문서는 Kotlin 환경에서 Spring Data JPA를 사용할 때의 모범 사례와 주의 사항을 설명합니다.

## 1. 엔티티 설계 (Entity Design)

### 1.1 `data class` 사용 자제
Kotlin의 `data class`는 유용하지만, JPA 엔티티로 사용할 때는 다음과 같은 이유로 권장되지 않습니다:
- **equals/hashCode**: JPA 엔티티의 식별자(ID)가 생성되기 전(영속화 전)과 후의 해시값이 달라질 수 있어, `Set` 등에서 예기치 못한 동작을 유발할 수 있습니다.
- **toString**: 양방향 연관 관계가 있을 경우 무한 루프에 빠질 위험이 있습니다.
- **Lazy Loading**: 프록시 생성을 방해할 수 있습니다.

**권장 패턴:** 일반 `class`를 사용하고 필요한 경우에만 `equals`, `hashCode`, `toString`을 직접 구현하거나 IDE의 도움을 받으세요.

### 1.2 `all-open` 및 `no-arg` 플러그인
JPA는 프록시 생성을 위해 클래스가 `open`이어야 하며, 기본 생성자가 필요합니다. Kotlin은 기본적으로 클래스가 `final`이며 기본 생성자가 없으므로 다음 플러그인을 사용합니다.
- `kotlin-spring`: `@Entity`, `@Service` 등이 붙은 클래스를 자동으로 `open`으로 만듭니다.
- `kotlin-jpa`: `@Entity`, `@Embeddable` 등이 붙은 클래스에 인자 없는 생성자를 자동으로 생성합니다.

### 1.3 기본 생성자와 프로퍼티
```kotlin
@Entity
class Post(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null, // ID는 JPA가 관리하므로 불변(val)과 null 기본값 권장

    @Column(nullable = false)
    var title: String, // 변경 가능한 필드는 var 사용

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User // 지연 로딩을 위해 FetchType.LAZY 설정
)
```

## 2. 연관 관계 매핑 (Relationship Mapping)

### 2.1 지연 로딩 (Lazy Loading)
성능을 위해 모든 연관 관계는 기본적으로 지연 로딩(`FetchType.LAZY`)으로 설정하는 것이 좋습니다. Kotlin에서는 지연 로딩된 필드에 접근할 때 해당 시점에 쿼리가 발생합니다.

### 2.2 양방향 매핑과 편의 메서드
양방향 매핑 시 양쪽 엔티티의 상태를 일치시키기 위한 편의 메서드를 작성합니다.

```kotlin
// User.kt
fun addPost(post: Post) {
    posts.add(post)
    post.user = this
}
```

## 3. N+1 문제와 해결 (N+1 Problem)

### 3.1 Fetch Join
연관된 엔티티를 한 번의 쿼리로 가져오고 싶을 때 사용합니다.

```kotlin
@Repository
interface PostRepository : JpaRepository<Post, Long> {
    @Query("select p from Post p join fetch p.user where p.user.id = :userId")
    fun findAllByUserIdWithUser(userId: Long): List<Post>
}
```

### 3.2 EntityGraph
어노테이션을 통해 특정 연관 관계를 Fetch Join 하도록 설정할 수 있습니다.

## 4. 트랜잭션과 변경 감지 (Dirty Checking)

Kotlin에서도 `@Transactional`을 사용하여 트랜잭션 범위를 지정합니다. 트랜잭션 내에서 조회한 엔티티의 프로퍼티를 수정하면, 트랜잭션이 끝나는 시점에 자동으로 `UPDATE` 쿼리가 실행됩니다 (Dirty Checking).

```kotlin
@Transactional
fun updatePost(id: Long, request: UpdatePostRequest) {
    val post = postRepository.findById(id).orElseThrow()
    post.update(request.title, request.content)
    // 별도의 save() 호출 없이도 변경 사항이 DB에 반영됨
}
```

## 5. 불변성과 가변성

- **ID**: 생성 후 변경되지 않으므로 `val`을 사용합니다.
- **생성일/수정일**: 자동 관리되므로 `val` 또는 `var`를 적절히 사용합니다.
- **일반 필드**: 비즈니스 로직상 변경이 필요한 경우 `var`를 사용하고, `update` 메서드를 통해 캡슐화하는 것이 좋습니다.

## 6. Null 안정성 (Null Safety)

JPA는 조회 결과가 없을 경우 `null`을 반환할 수 있습니다. `Optional`을 사용하거나 Kotlin의 `?`를 활용하여 안전하게 처리하세요.
- Repository에서 `findById`는 `Optional`을 반환합니다.
- 단건 조회 쿼리 메서드 정의 시 반환 타입을 `Post?`로 지정할 수 있습니다.

## 7. JPA vs R2DBC 관계 처리 비교

| 항목 | JPA (MVC) | R2DBC (WebFlux) |
|------|-----------|-----------------|
| **관계 매핑** | `@ManyToOne`, `@OneToMany` 등 어노테이션 기반 자동 처리 | 지원 안 함. FK 필드(`userId`)만 정의 |
| **지연 로딩** | 프록시 객체를 통한 투명한 지연 로딩 지원 | 지원 안 함. 필요 시 별도 쿼리로 조회 |
| **객체 그래프** | 연관 객체가 엔티티에 직접 포함됨 (`post.user`) | 연관 객체 포함 불가. DTO에서 수동 결합 |
| **N+1 문제** | Fetch Join, EntityGraph 등으로 해결 | `JOIN` 쿼리 작성 또는 `flatMap`/`map`으로 수동 해결 |
| **상태 관리** | 영속성 컨텍스트가 엔티티 상태 관리 (Dirty Checking) | 상태 관리 없음. 명시적인 `save()` 호출 필요 |

### 7.1 R2DBC에서의 관계 처리 예시 (Service 계층)

R2DBC에서는 연관된 데이터를 가져오기 위해 비동기적으로 여러 번의 쿼리를 수행하고 이를 DTO에서 결합합니다.

```kotlin
// PostService.kt in api-webflux
suspend fun getPostById(id: Long): PostResponse {
    val post = postRepository.findById(id) ?: throw NotFoundException(...)
    // 수동으로 연관된 User 조회
    val user = userRepository.findById(post.userId) ?: throw NotFoundException(...)
    
    // DTO에서 결합
    return PostResponse.from(post, user)
}
```

## 8. 실무적 고민: JOIN vs. 별도 조회 (Separate Queries)

"모든 데이터를 한 번에 JOIN으로 가져오는 것이 항상 최선인가?"에 대한 고찰입니다.

### 8.1 JPA (MVC)의 선택: JOIN FETCH
JPA 환경에서는 **JOIN FETCH**가 가장 강력한 도구입니다.
- **장점**: 하이버네이트가 복잡한 객체 그래프 매핑(Post 안에 User 객체 주입)을 자동으로 수행합니다.
- **적용**: `PostRepository`에서 `@Query("select p from Post p join fetch p.user ...")`를 사용 중입니다.

### 8.2 R2DBC (WebFlux)의 선택: 별도 조회 및 결합
WebFlux 환경에서는 JOIN보다 **별도 조회 후 서비스 계층 결합**을 선호하는 경우가 많습니다.

| 비교 항목 | JOIN 사용 (R2DBC) | 별도 조회 및 결합 (WebFlux + Flow) |
|------|-----------|-----------------|
| **매핑 편의성** | 매우 낮음. 수동 RowMapper 작성 필요 | 높음. 각각의 엔티티를 DTO에서 결합 |
| **데이터 중복** | 1:N 관계에서 부모 데이터가 중복 전송됨 | 필요한 데이터만 전송되어 네트워크 효율적 |
| **확장성** | DB가 분리되면 사용 불가 | Microservices(DB 분리) 환경에서도 적용 가능 |
| **성능 (TTFB)** | 조인 연산 완료까지 응답 대기 | 첫 번째 데이터가 준비되는 대로 즉시 방출 가능 |

### 8.3 최적의 절충안: Batch Loading (IN 절 사용)
JOIN의 성능과 WebFlux의 유연함을 모두 챙기기 위해 실무에서 가장 많이 사용하는 패턴입니다.

1.  **Step 1**: 게시글 목록 조회 (`findAll`)
2.  **Step 2**: 게시글들로부터 작성자 ID 리스트 추출 (`userIds = posts.map { it.userId }.distinct()`)
3.  **Step 3**: `IN` 절을 사용하여 작성자들을 한 번에 조회 (`userRepository.findAllById(userIds)`)
4.  **Step 4**: 메모리에서 Map을 이용해 게시글과 작성자 매칭

이 방식은 **쿼리 횟수를 최소화(N+1 -> 1+1)**하면서도, R2DBC의 매핑 한계를 우회할 수 있는 가장 효율적인 방법입니다.
