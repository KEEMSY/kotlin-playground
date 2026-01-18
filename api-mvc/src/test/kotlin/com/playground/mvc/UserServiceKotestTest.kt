package com.playground.mvc

import com.playground.core.exception.ConflictException
import com.playground.core.exception.NotFoundException
import com.playground.mvc.dto.CreateUserRequest
import com.playground.mvc.dto.UpdateUserRequest
import com.playground.mvc.entity.User
import com.playground.mvc.repository.UserRepository
import com.playground.mvc.service.UserService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import java.time.LocalDateTime
import java.util.*

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
                every { userRepository.findAll() } returns listOf(testUser)

                val result = userService.getAllUsers()

                result shouldHaveSize 1
                result[0].email shouldBe testUser.email
                verify(exactly = 1) { userRepository.findAll() }
            }
        }

        context("when no users exist") {
            it("should return empty list") {
                every { userRepository.findAll() } returns emptyList()

                val result = userService.getAllUsers()

                result.shouldBeEmpty()
            }
        }
    }

    describe("getUserById") {
        context("when user exists") {
            it("should return user") {
                every { userRepository.findById(1L) } returns Optional.of(testUser)

                val result = userService.getUserById(1L)

                result.id shouldBe testUser.id
                result.email shouldBe testUser.email
            }
        }

        context("when user does not exist") {
            it("should throw NotFoundException") {
                every { userRepository.findById(1L) } returns Optional.empty()

                shouldThrow<NotFoundException> {
                    userService.getUserById(1L)
                }
            }
        }
    }

    describe("getUserByEmail") {
        context("when user exists") {
            it("should return user") {
                every { userRepository.findByEmail("test@example.com") } returns Optional.of(testUser)

                val result = userService.getUserByEmail("test@example.com")

                result.email shouldBe "test@example.com"
            }
        }

        context("when user does not exist") {
            it("should throw NotFoundException") {
                every { userRepository.findByEmail("notfound@example.com") } returns Optional.empty()

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

                every { userRepository.existsByEmail(request.email) } returns false
                every { userRepository.save(any()) } returns newUser

                val result = userService.createUser(request)

                result shouldNotBe null
                result.email shouldBe request.email
                verify { userRepository.save(any()) }
            }
        }

        context("when email already exists") {
            it("should throw ConflictException") {
                val request = CreateUserRequest("existing@example.com", "User")
                every { userRepository.existsByEmail(request.email) } returns true

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
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { userRepository.save(any()) } answers {
                    firstArg<User>().apply { name = "Updated Name" }
                }

                val result = userService.updateUser(1L, request)

                result.name shouldBe "Updated Name"
            }

            it("should update user email when new email is unique") {
                val request = UpdateUserRequest(email = "newemail@example.com")
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { userRepository.existsByEmail("newemail@example.com") } returns false
                every { userRepository.save(any()) } answers {
                    firstArg<User>().apply { email = "newemail@example.com" }
                }

                val result = userService.updateUser(1L, request)

                result.email shouldBe "newemail@example.com"
            }
        }

        context("when user does not exist") {
            it("should throw NotFoundException") {
                every { userRepository.findById(1L) } returns Optional.empty()

                shouldThrow<NotFoundException> {
                    userService.updateUser(1L, UpdateUserRequest(name = "Updated"))
                }
            }
        }

        context("when new email already exists") {
            it("should throw ConflictException") {
                val request = UpdateUserRequest(email = "existing@example.com")
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { userRepository.existsByEmail("existing@example.com") } returns true

                shouldThrow<ConflictException> {
                    userService.updateUser(1L, request)
                }
            }
        }
    }

    describe("deleteUser") {
        context("when user exists") {
            it("should delete user successfully") {
                every { userRepository.existsById(1L) } returns true
                every { userRepository.deleteById(1L) } just runs

                userService.deleteUser(1L)

                verify(exactly = 1) { userRepository.deleteById(1L) }
            }
        }

        context("when user does not exist") {
            it("should throw NotFoundException") {
                every { userRepository.existsById(1L) } returns false

                shouldThrow<NotFoundException> {
                    userService.deleteUser(1L)
                }
            }
        }
    }
})
