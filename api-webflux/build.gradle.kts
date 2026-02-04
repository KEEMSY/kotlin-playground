plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("plugin.spring")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(project(":core"))

    // Spring Boot WebFlux
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // R2DBC PostgreSQL
    runtimeOnly("org.postgresql:r2dbc-postgresql:${property("r2dbcPostgresqlVersion")}")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${property("coroutinesVersion")}")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:${property("springdocVersion")}")

    // Komapper (Type-safe SQL for Kotlin)
    implementation("org.komapper:komapper-spring-boot-starter-r2dbc:2.1.0")
    implementation("org.komapper:komapper-dialect-postgresql-r2dbc:2.1.0")
    ksp("org.komapper:komapper-processor:2.1.0")

    // Jackson

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:postgresql:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:r2dbc:${property("testcontainersVersion")}")
}
