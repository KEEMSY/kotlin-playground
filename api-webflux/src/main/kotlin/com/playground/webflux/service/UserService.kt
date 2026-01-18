package com.playground.webflux.service

import com.playground.core.exception.ConflictException
import com.playground.core.exception.NotFoundException
import com.playground.core.util.logger
import com.playground.webflux.dto.CreateUserRequest
import com.playground.webflux.dto.UpdateUserRequest
import com.playground.webflux.dto.UserResponse
import com.playground.webflux.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository
) {
    private val log = logger()

    fun getAllUsers(): Flow<UserResponse> {
        log.info("Fetching all users")
        return userRepository.findAll()
            .map { UserResponse.from(it) }
    }

    suspend fun getUserById(id: Long): UserResponse {
        log.info("Fetching user with id: $id")
        val user = userRepository.findById(id)
            ?: throw NotFoundException("User not found with id: $id")
        return UserResponse.from(user)
    }

    suspend fun getUserByEmail(email: String): UserResponse {
        log.info("Fetching user with email: $email")
        val user = userRepository.findByEmail(email)
            ?: throw NotFoundException("User not found with email: $email")
        return UserResponse.from(user)
    }

    @Transactional
    suspend fun createUser(request: CreateUserRequest): UserResponse {
        log.info("Creating user with email: ${request.email}")

        if (userRepository.existsByEmail(request.email)) {
            throw ConflictException("User already exists with email: ${request.email}")
        }

        val user = userRepository.save(request.toEntity())
        log.info("Created user with id: ${user.id}")
        return UserResponse.from(user)
    }

    @Transactional
    suspend fun updateUser(id: Long, request: UpdateUserRequest): UserResponse {
        log.info("Updating user with id: $id")

        val user = userRepository.findById(id)
            ?: throw NotFoundException("User not found with id: $id")

        request.email?.let { email ->
            if (email != user.email && userRepository.existsByEmail(email)) {
                throw ConflictException("User already exists with email: $email")
            }
        }

        val updatedUser = userRepository.save(user.update(request.email, request.name))
        log.info("Updated user with id: ${updatedUser.id}")
        return UserResponse.from(updatedUser)
    }

    @Transactional
    suspend fun deleteUser(id: Long) {
        log.info("Deleting user with id: $id")

        if (!userRepository.existsById(id)) {
            throw NotFoundException("User not found with id: $id")
        }

        userRepository.deleteById(id)
        log.info("Deleted user with id: $id")
    }
}
