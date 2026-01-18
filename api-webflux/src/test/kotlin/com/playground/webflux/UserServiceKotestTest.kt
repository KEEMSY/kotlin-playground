package com.playground.webflux

import com.playground.core.exception.ConflictException
import com.playground.core.exception.NotFoundException
import com.playground.webflux.dto.CreateUserRequest
import com.playground.webflux.dto.UpdateUserRequest
import com.playground.webflux.entity.User
import com.playground.webflux.repository.UserRepository
import com.playground.webflux.service.UserService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import java.time.LocalDateTime

class UserServiceKotestTest : DescribeSpec({

    val userRepository = mockk<UserRepository>()
    val userService = UserService(userRepository)

    val testUser = User(
        id = 1L,
        email = "test@example.com",
        name = "Test User",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    beforeEach {
        clearAllMocks()
    }

    describe("getAllUsers") {
        context("when users exist") {
            it("should return all users") {
                coEvery { userRepository.findAll() } returns flowOf(testUser)

                val result = userService.getAllUsers().toList()

                result shouldHaveSize 1
                result[0].email shouldBe testUser.email
                coVerify(exactly = 1) { userRepository.findAll() }
            }
        }

        context("when no users exist") {
            it("should return empty flow") {
                coEvery { userRepository.findAll() } returns flowOf()

                val result = userService.getAllUsers().toList()

                result.shouldBeEmpty()
            }
        }
    }

    describe("getUserById") {
        context("when user exists") {
            it("should return user") {
                coEvery { userRepository.findById(1L) } returns testUser

                val result = userService.getUserById(1L)

                result.id shouldBe testUser.id
                result.email shouldBe testUser.email
            }
        }

        context("when user does not exist") {
            it("should throw NotFoundException") {
                coEvery { userRepository.findById(1L) } returns null

                shouldThrow<NotFoundException> {
                    userService.getUserById(1L)
                }
            }
        }
    }

    describe("getUserByEmail") {
        context("when user exists") {
            it("should return user") {
                coEvery { userRepository.findByEmail("test@example.com") } returns testUser

                val result = userService.getUserByEmail("test@example.com")

                result.email shouldBe "test@example.com"
            }
        }

        context("when user does not exist") {
            it("should throw NotFoundException") {
                coEvery { userRepository.findByEmail("notfound@example.com") } returns null

                shouldThrow<NotFoundException> {
                    userService.getUserByEmail("notfound@example.com")
                }
            }
        }
    }

    describe("createUser") {
        context("when email is unique") {
            it("should create user successfully") {
                val request = CreateUserRequest("new@example.com", "New User")
                val newUser = User(
                    id = 2L,
                    email = request.email,
                    name = request.name,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )

                coEvery { userRepository.existsByEmail(request.email) } returns false
                coEvery { userRepository.save(any()) } returns newUser

                val result = userService.createUser(request)

                result shouldNotBe null
                result.email shouldBe request.email
                coVerify { userRepository.save(any()) }
            }
        }

        context("when email already exists") {
            it("should throw ConflictException") {
                val request = CreateUserRequest("existing@example.com", "User")
                coEvery { userRepository.existsByEmail(request.email) } returns true

                shouldThrow<ConflictException> {
                    userService.createUser(request)
                }
            }
        }
    }

    describe("updateUser") {
        context("when user exists") {
            it("should update user name") {
                val request = UpdateUserRequest(name = "Updated Name")
                val updatedUser = testUser.copy(name = "Updated Name")

                coEvery { userRepository.findById(1L) } returns testUser
                coEvery { userRepository.save(any()) } returns updatedUser

                val result = userService.updateUser(1L, request)

                result.name shouldBe "Updated Name"
            }

            it("should update user email when new email is unique") {
                val request = UpdateUserRequest(email = "newemail@example.com")
                val updatedUser = testUser.copy(email = "newemail@example.com")

                coEvery { userRepository.findById(1L) } returns testUser
                coEvery { userRepository.existsByEmail("newemail@example.com") } returns false
                coEvery { userRepository.save(any()) } returns updatedUser

                val result = userService.updateUser(1L, request)

                result.email shouldBe "newemail@example.com"
            }
        }

        context("when user does not exist") {
            it("should throw NotFoundException") {
                coEvery { userRepository.findById(1L) } returns null

                shouldThrow<NotFoundException> {
                    userService.updateUser(1L, UpdateUserRequest(name = "Updated"))
                }
            }
        }

        context("when new email already exists") {
            it("should throw ConflictException") {
                val request = UpdateUserRequest(email = "existing@example.com")
                coEvery { userRepository.findById(1L) } returns testUser
                coEvery { userRepository.existsByEmail("existing@example.com") } returns true

                shouldThrow<ConflictException> {
                    userService.updateUser(1L, request)
                }
            }
        }
    }

    describe("deleteUser") {
        context("when user exists") {
            it("should delete user successfully") {
                coEvery { userRepository.existsById(1L) } returns true
                coEvery { userRepository.deleteById(1L) } just runs

                userService.deleteUser(1L)

                coVerify(exactly = 1) { userRepository.deleteById(1L) }
            }
        }

        context("when user does not exist") {
            it("should throw NotFoundException") {
                coEvery { userRepository.existsById(1L) } returns false

                shouldThrow<NotFoundException> {
                    userService.deleteUser(1L)
                }
            }
        }
    }
})
