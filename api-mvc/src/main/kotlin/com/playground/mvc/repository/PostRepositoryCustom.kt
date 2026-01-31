package com.playground.mvc.repository

import com.playground.mvc.entity.Post

data class PostSearchCondition(
    val title: String? = null,
    val content: String? = null,
    val authorName: String? = null
)

interface PostRepositoryCustom {
    fun search(condition: PostSearchCondition): List<Post>
}
