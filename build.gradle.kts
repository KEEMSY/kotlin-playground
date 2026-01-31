import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.spring") version "1.9.23" apply false
    kotlin("plugin.jpa") version "1.9.23" apply false
    kotlin("kapt") version "1.9.23" apply false
}

allprojects {
    group = "com.playground"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "17"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    dependencies {
        // Kotlin
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

        // Coroutines
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("coroutinesVersion")}")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${property("coroutinesVersion")}")

        // Test
        testImplementation("org.junit.jupiter:junit-jupiter:${property("junitVersion")}")
        testImplementation("io.kotest:kotest-runner-junit5:${property("kotestVersion")}")
        testImplementation("io.kotest:kotest-assertions-core:${property("kotestVersion")}")
        testImplementation("io.kotest:kotest-property:${property("kotestVersion")}")
        testImplementation("io.mockk:mockk:${property("mockkVersion")}")
    }
}
