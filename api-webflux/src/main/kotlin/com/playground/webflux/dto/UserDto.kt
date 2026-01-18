package com.playground.webflux.dto

import com.playground.webflux.entity.User
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CreateUserRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Name is required")
    @field:Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    val name: String
) {
    fun toEntity(): User = User(
        email = email,
        name = name
    )
}

data class UpdateUserRequest(
    @field:Email(message = "Invalid email format")
    val email: String? = null,

    @field:Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    val name: String? = null
)

data class UserResponse(
    val id: Long,
    val email: String,
    val name: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(
            id = user.id!!,
            email = user.email,
            name = user.name,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }
}
