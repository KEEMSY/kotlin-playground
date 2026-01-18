package com.playground.mvc.service

import com.playground.core.exception.ConflictException
import com.playground.core.exception.NotFoundException
import com.playground.core.util.logger
import com.playground.mvc.dto.CreateUserRequest
import com.playground.mvc.dto.UpdateUserRequest
import com.playground.mvc.dto.UserResponse
import com.playground.mvc.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository
) {
    private val log = logger()

    fun getAllUsers(): List<UserResponse> {
        log.info("Fetching all users")
        return userRepository.findAll()
            .map { UserResponse.from(it) }
    }

    fun getUserById(id: Long): UserResponse {
        log.info("Fetching user with id: $id")
        val user = userRepository.findById(id)
            .orElseThrow { NotFoundException("User not found with id: $id") }
        return UserResponse.from(user)
    }

    fun getUserByEmail(email: String): UserResponse {
        log.info("Fetching user with email: $email")
        val user = userRepository.findByEmail(email)
            .orElseThrow { NotFoundException("User not found with email: $email") }
        return UserResponse.from(user)
    }

    @Transactional
    fun createUser(request: CreateUserRequest): UserResponse {
        log.info("Creating user with email: ${request.email}")

        if (userRepository.existsByEmail(request.email)) {
            throw ConflictException("User already exists with email: ${request.email}")
        }

        val user = userRepository.save(request.toEntity())
        log.info("Created user with id: ${user.id}")
        return UserResponse.from(user)
    }

    @Transactional
    fun updateUser(id: Long, request: UpdateUserRequest): UserResponse {
        log.info("Updating user with id: $id")

        val user = userRepository.findById(id)
            .orElseThrow { NotFoundException("User not found with id: $id") }

        request.email?.let { email ->
            if (email != user.email && userRepository.existsByEmail(email)) {
                throw ConflictException("User already exists with email: $email")
            }
        }

        user.update(request.email, request.name)
        val updatedUser = userRepository.save(user)
        log.info("Updated user with id: ${updatedUser.id}")
        return UserResponse.from(updatedUser)
    }

    @Transactional
    fun deleteUser(id: Long) {
        log.info("Deleting user with id: $id")

        if (!userRepository.existsById(id)) {
            throw NotFoundException("User not found with id: $id")
        }

        userRepository.deleteById(id)
        log.info("Deleted user with id: $id")
    }
}
