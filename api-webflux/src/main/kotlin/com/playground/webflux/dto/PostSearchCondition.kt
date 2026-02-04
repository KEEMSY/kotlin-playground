package com.playground.webflux.dto

data class PostSearchCondition(
    val title: String? = null,
    val content: String? = null,
    val authorName: String? = null
)
