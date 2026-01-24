package com.playground.mvc.dto

import com.playground.mvc.entity.Post
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

@Schema(description = "Post Creation Request")
data class CreatePostRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val title: String,

    @field:NotBlank
    val content: String,

    @field:Schema(description = "Author User ID")
    val userId: Long
)

@Schema(description = "Post Update Request")
data class UpdatePostRequest(
    @field:Size(max = 255)
    val title: String?,

    val content: String?
)

@Schema(description = "Post Response")
data class PostResponse(
    val id: Long,
    val title: String,
    val content: String,
    val authorName: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(post: Post): PostResponse {
            return PostResponse(
                id = post.id!!,
                title = post.title,
                content = post.content,
                authorName = post.user.name,
                createdAt = post.createdAt,
                updatedAt = post.updatedAt
            )
        }
    }
}
