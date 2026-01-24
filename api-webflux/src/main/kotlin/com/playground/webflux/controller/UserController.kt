package com.playground.webflux.controller

import com.playground.webflux.dto.CreateUserRequest
import com.playground.webflux.dto.UpdateUserRequest
import com.playground.webflux.dto.UserResponse
import com.playground.webflux.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kotlinx.coroutines.flow.toList
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "User management APIs")
class UserController(
    private val userService: UserService
) {

    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieve a list of all users")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved users")
    suspend fun getAllUsers(): ResponseEntity<List<UserResponse>> {
        val users = userService.getAllUsers().toList()
        return ResponseEntity.ok(users)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieve a user by their ID")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Successfully retrieved user"),
        ApiResponse(responseCode = "404", description = "User not found")
    )
    suspend fun getUserById(
        @Parameter(description = "User ID") @PathVariable id: Long
    ): ResponseEntity<UserResponse> {
        val user = userService.getUserById(id)
        return ResponseEntity.ok(user)
    }

    @GetMapping("/email/{email}")
    @Operation(summary = "Get user by email", description = "Retrieve a user by their email")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Successfully retrieved user"),
        ApiResponse(responseCode = "404", description = "User not found")
    )
    suspend fun getUserByEmail(
        @Parameter(description = "User email") @PathVariable email: String
    ): ResponseEntity<UserResponse> {
        val user = userService.getUserByEmail(email)
        return ResponseEntity.ok(user)
    }

    @PostMapping
    @Operation(summary = "Create a new user", description = "Create a new user with the provided data")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Successfully created user"),
        ApiResponse(responseCode = "400", description = "Invalid input"),
        ApiResponse(responseCode = "409", description = "User already exists")
    )
    suspend fun createUser(
        @Valid @RequestBody request: CreateUserRequest
    ): ResponseEntity<UserResponse> {
        val user = userService.createUser(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(user)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Update an existing user")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Successfully updated user"),
        ApiResponse(responseCode = "404", description = "User not found"),
        ApiResponse(responseCode = "409", description = "Email already in use")
    )
    suspend fun updateUser(
        @Parameter(description = "User ID") @PathVariable id: Long,
        @Valid @RequestBody request: UpdateUserRequest
    ): ResponseEntity<UserResponse> {
        val user = userService.updateUser(id, request)
        return ResponseEntity.ok(user)
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Delete a user by their ID")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Successfully deleted user"),
        ApiResponse(responseCode = "404", description = "User not found")
    )
    suspend fun deleteUser(
        @Parameter(description = "User ID") @PathVariable id: Long
    ): ResponseEntity<Void> {
        userService.deleteUser(id)
        return ResponseEntity.noContent().build()
    }
}
