package com.playground.webflux.dto

import com.playground.webflux.entity.Post
import com.playground.webflux.entity.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CreatePostRequest(
    @field:NotBlank(message = "Title is required")
    @field:Size(max = 255)
    val title: String,

    @field:NotBlank(message = "Content is required")
    val content: String,

    val userId: Long
) {
    fun toEntity(): Post = Post(
        title = title,
        content = content,
        userId = userId
    )
}

data class UpdatePostRequest(
    @field:Size(max = 255)
    val title: String? = null,

    val content: String? = null
)

data class PostResponse(
    val id: Long,
    val title: String,
    val content: String,
    val authorName: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(post: Post, user: User): PostResponse = PostResponse(
            id = post.id!!,
            title = post.title,
            content = post.content,
            authorName = user.name,
            createdAt = post.createdAt,
            updatedAt = post.updatedAt
        )
    }
}
