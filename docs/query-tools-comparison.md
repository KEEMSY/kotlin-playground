# QueryDSL vs Komapper 비교 가이드 (Kotlin)

이 문서는 동기(JPA) 환경과 비동기(R2DBC) 환경에서 동적 쿼리를 처리하는 두 가지 대표적인 도구를 비교합니다.

## 1. 개요 (Overview)

| 비교 항목 | QueryDSL (JPA) | Komapper (R2DBC) |
|------|-----------|-----------------|
| **기반 기술** | JPA (Hibernate) | R2DBC, JDBC |
| **코드 생성** | **필요 (Q-Class)** | **필요 (Metadata)** |
| **분석 도구** | kapt / annotationProcessor | **KSP (Kotlin Symbol Processing)** |
| **동작 방식** | 동기 / 블로킹 | 비동기 / 논블로킹 (suspend) |
| **DSL 스타일** | Java-style | Kotlin-idiomatic |

## 2. 코드 스타일 비교

### 2.1 QueryDSL (api-mvc)
Q-Class를 기반으로 유연한 쿼리를 작성합니다.

```kotlin
queryFactory
    .selectFrom(post)
    .where(
        post.title.contains(condition.title),
        user.name.eq(condition.authorName)
    )
    .fetch()
```

### 2.2 Komapper (api-webflux)
코틀린의 확장 함수와 DSL을 극한으로 활용합니다.

```kotlin
db.runQuery {
    QueryDsl.from(p)
        .innerJoin(u) { p.userId eq u.id }
        .where {
            and(
                p.title contains condition.title,
                u.name eq condition.authorName
            )
        }
}
```

## 3. 핵심 차이점

### 3.1 설정 및 분석
- **QueryDSL**: `kapt` 설정을 통해 `Entity`를 스캔하여 `QClass`를 생성합니다. 설정이 다소 무겁고 컴파일 속도에 영향을 줄 수 있습니다.
- **Komapper**: 현대적인 `KSP`를 사용하여 메타데이터를 생성합니다. 코틀린 버전에 민감하지만 속도가 빠르고 코틀린 언어적 특성을 더 잘 이해합니다.

### 3.2 비동기 지원
- **QueryDSL**: JPA 자체가 블로킹 방식이므로 WebFlux에서 사용 시 별도의 스레드 풀 격리가 필요합니다.
- **Komapper**: 처음부터 **R2DBC와 Coroutines를 위해 설계**되었습니다. `suspend` 함수를 기본 지원하며 WebFlux 환경에서 최상의 성능을 냅니다.

### 3.3 타입 안정성
- 두 도구 모두 컴파일 시점에 컬럼명 오류 등을 잡아주는 강력한 타입 안정성을 제공합니다.

## 4. 결론: 무엇을 선택해야 하나요?

- **JPA 환경**: 고민 없이 **QueryDSL**을 선택하세요. 생태계가 가장 넓고 검증되었습니다.
- **WebFlux/R2DBC 환경**: **Komapper**나 **Kotlin-JDSL**이 최고의 선택입니다. 순수 코틀린 기반의 DSL을 통해 비동기 쿼리를 우아하게 작성할 수 있습니다.
