package com.playground.webflux.handler

import com.playground.webflux.dto.CreateUserRequest
import com.playground.webflux.dto.UpdateUserRequest
import com.playground.webflux.dto.UserResponse
import com.playground.webflux.service.UserService
import kotlinx.coroutines.flow.toList
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.*

@Component
class UserHandler(
    private val userService: UserService
) {

    suspend fun getAllUsers(request: ServerRequest): ServerResponse {
        val users = userService.getAllUsers().toList()
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValueAndAwait(users)
    }

    suspend fun getUserById(request: ServerRequest): ServerResponse {
        val id = request.pathVariable("id").toLong()
        val user = userService.getUserById(id)
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValueAndAwait(user)
    }

    suspend fun getUserByEmail(request: ServerRequest): ServerResponse {
        val email = request.pathVariable("email")
        val user = userService.getUserByEmail(email)
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValueAndAwait(user)
    }

    suspend fun createUser(request: ServerRequest): ServerResponse {
        val createRequest = request.awaitBody<CreateUserRequest>()
        val user = userService.createUser(createRequest)
        return ServerResponse.status(HttpStatus.CREATED)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValueAndAwait(user)
    }

    suspend fun updateUser(request: ServerRequest): ServerResponse {
        val id = request.pathVariable("id").toLong()
        val updateRequest = request.awaitBody<UpdateUserRequest>()
        val user = userService.updateUser(id, updateRequest)
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValueAndAwait(user)
    }

    suspend fun deleteUser(request: ServerRequest): ServerResponse {
        val id = request.pathVariable("id").toLong()
        userService.deleteUser(id)
        return ServerResponse.noContent().buildAndAwait()
    }
}
