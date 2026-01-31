# Kotlin QueryDSL 학습 가이드 (JPA)

이 문서는 Kotlin 환경에서 QueryDSL을 설정하고 동적 쿼리를 작성하는 방법을 설명합니다.

## 1. 설정 (Setup) - Spring Boot 3

Spring Boot 3 (Jakarta Persistence) 버전에서는 설정이 이전 버전과 다릅니다.

### 1.1 build.gradle.kts 설정
`kapt` 플러그인을 사용하여 엔티티로부터 Q-Class를 생성합니다.

```kotlin
plugins {
    kotlin("kapt") version "1.9.23"
}

dependencies {
    // QueryDSL (Jakarta 버전 명시 필수)
    implementation("com.querydsl:querydsl-jpa:5.0.0:jakarta")
    kapt("com.querydsl:querydsl-apt:5.0.0:jakarta")
    kapt("jakarta.annotation:jakarta.annotation-api")
    kapt("jakarta.persistence:jakarta.persistence-api")
}
```

### 1.2 JPAQueryFactory 빈 설정
QueryDSL 쿼리를 작성할 때 핵심이 되는 `JPAQueryFactory`를 빈으로 등록합니다.

```kotlin
@Configuration
class QueryDslConfig(private val entityManager: EntityManager) {
    @Bean
    fun jpaQueryFactory() = JPAQueryFactory(entityManager)
}
```

## 2. 사용자 정의 리포지토리 패턴

Spring Data JPA와 QueryDSL을 함께 사용할 때 권장되는 구조입니다.

1.  **Custom 인터페이스**: QueryDSL을 사용할 메서드 정의.
2.  **Impl 클래스**: QueryDSL을 사용하여 실제 로직 구현 (이름 규칙: `인터페이스명` + `Impl`).
3.  **Repository 인터페이스**: 기존 JPA Repository에 Custom 인터페이스를 다중 상속.

## 3. 동적 쿼리 작성 (BooleanExpression)

`BooleanBuilder`보다 `BooleanExpression`을 반환하는 메서드 방식이 가독성이 높고 재사용이 가능하여 권장됩니다.

```kotlin
override fun search(condition: PostSearchCondition): List<Post> {
    return queryFactory
        .selectFrom(post)
        .where(
            titleContains(condition.title),
            authorNameEq(condition.authorName)
        )
        .fetch()
}

// null을 반환하면 where 절에서 자동으로 무시됨
private fun titleContains(title: String?): BooleanExpression? {
    return title?.let { post.title.contains(it) }
}
```

## 4. 조인 및 페치 조인 (Join & Fetch Join)

N+1 문제를 해결하기 위해 QueryDSL에서도 페치 조인을 지원합니다.

```kotlin
queryFactory
    .selectFrom(post)
    .leftJoin(post.user, user).fetchJoin() // 연관된 엔티티를 한 번에 조회
    .fetch()
```

## 5. 프로젝션 (Projection)

엔티티 전체가 아닌 필요한 필드만 DTO로 조회할 때 사용합니다.
- `Projections.constructor()`
- `Projections.fields()`
- **@QueryProjection**: DTO 생성자에 어노테이션을 붙여 Q-Class 생성 (가장 타입 안정적).

## 💡 학습 포인트
1.  **Q-Class 생성 확인**: 빌드 후 `build/generated/source/kapt` 폴더에 Q-Class가 생성되었는지 확인하세요.
2.  **컴파일 시점 오류**: QueryDSL은 쿼리 오류를 런타임이 아닌 컴파일 시점에 잡아줍니다.
3.  **동적 쿼리 분리**: 각 조건을 별도 메서드로 분리하여 쿼리 로직을 깔끔하게 유지하세요.
