package com.playground.core.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T> T.logger(): Logger = LoggerFactory.getLogger(T::class.java)

inline fun <T> T?.orThrow(lazyMessage: () -> String): T {
    return this ?: throw IllegalArgumentException(lazyMessage())
}

fun String?.isNotNullOrBlank(): Boolean = !this.isNullOrBlank()

fun <T : Any> T?.toOptional(): java.util.Optional<T> = java.util.Optional.ofNullable(this)
