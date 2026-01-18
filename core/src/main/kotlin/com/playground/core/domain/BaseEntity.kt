package com.playground.core.domain

import java.time.LocalDateTime

interface BaseEntity {
    val id: Long?
    val createdAt: LocalDateTime
    val updatedAt: LocalDateTime
}

interface Identifiable<T> {
    val id: T?
}
