package com.playground.webflux

import com.playground.core.exception.ConflictException
import com.playground.core.exception.NotFoundException
import com.playground.webflux.dto.CreateUserRequest
import com.playground.webflux.dto.UpdateUserRequest
import com.playground.webflux.entity.User
import com.playground.webflux.repository.UserRepository
import com.playground.webflux.service.UserService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime

@DisplayName("UserService JUnit5 Tests (Coroutines)")
class UserServiceJUnit5Test {

    @MockK
    private lateinit var userRepository: UserRepository

    @InjectMockKs
    private lateinit var userService: UserService

    private val testUser = User(
        id = 1L,
        email = "test@example.com",
        name = "Test User",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    @DisplayName("getAllUsers")
    inner class GetAllUsersTest {

        @Test
        @DisplayName("should return all users")
        fun `should return all users`() = runTest {
            // given
            coEvery { userRepository.findAll() } returns flowOf(testUser)

            // when
            val result = userService.getAllUsers().toList()

            // then
            assertEquals(1, result.size)
            assertEquals(testUser.email, result[0].email)
            coVerify(exactly = 1) { userRepository.findAll() }
        }

        @Test
        @DisplayName("should return empty flow when no users")
        fun `should return empty flow when no users`() = runTest {
            // given
            coEvery { userRepository.findAll() } returns flowOf()

            // when
            val result = userService.getAllUsers().toList()

            // then
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("getUserById")
    inner class GetUserByIdTest {

        @Test
        @DisplayName("should return user when found")
        fun `should return user when found`() = runTest {
            // given
            coEvery { userRepository.findById(1L) } returns testUser

            // when
            val result = userService.getUserById(1L)

            // then
            assertEquals(testUser.id, result.id)
            assertEquals(testUser.email, result.email)
        }

        @Test
        @DisplayName("should throw NotFoundException when user not found")
        fun `should throw NotFoundException when user not found`() = runTest {
            // given
            coEvery { userRepository.findById(1L) } returns null

            // when & then
            assertThrows<NotFoundException> {
                kotlinx.coroutines.runBlocking {
                    userService.getUserById(1L)
                }
            }
        }
    }

    @Nested
    @DisplayName("createUser")
    inner class CreateUserTest {

        @Test
        @DisplayName("should create user successfully")
        fun `should create user successfully`() = runTest {
            // given
            val request = CreateUserRequest("new@example.com", "New User")
            val newUser = testUser.copy(email = request.email, name = request.name)

            coEvery { userRepository.existsByEmail(request.email) } returns false
            coEvery { userRepository.save(any()) } returns newUser

            // when
            val result = userService.createUser(request)

            // then
            assertNotNull(result)
            assertEquals(request.email, result.email)
            coVerify { userRepository.save(any()) }
        }

        @Test
        @DisplayName("should throw ConflictException when email exists")
        fun `should throw ConflictException when email exists`() = runTest {
            // given
            val request = CreateUserRequest("existing@example.com", "User")
            coEvery { userRepository.existsByEmail(request.email) } returns true

            // when & then
            assertThrows<ConflictException> {
                kotlinx.coroutines.runBlocking {
                    userService.createUser(request)
                }
            }
        }
    }

    @Nested
    @DisplayName("updateUser")
    inner class UpdateUserTest {

        @Test
        @DisplayName("should update user successfully")
        fun `should update user successfully`() = runTest {
            // given
            val request = UpdateUserRequest(name = "Updated Name")
            val updatedUser = testUser.copy(name = "Updated Name")

            coEvery { userRepository.findById(1L) } returns testUser
            coEvery { userRepository.save(any()) } returns updatedUser

            // when
            val result = userService.updateUser(1L, request)

            // then
            assertEquals("Updated Name", result.name)
        }

        @Test
        @DisplayName("should throw NotFoundException when user not found")
        fun `should throw NotFoundException when user not found`() = runTest {
            // given
            coEvery { userRepository.findById(1L) } returns null

            // when & then
            assertThrows<NotFoundException> {
                kotlinx.coroutines.runBlocking {
                    userService.updateUser(1L, UpdateUserRequest(name = "Updated"))
                }
            }
        }
    }

    @Nested
    @DisplayName("deleteUser")
    inner class DeleteUserTest {

        @Test
        @DisplayName("should delete user successfully")
        fun `should delete user successfully`() = runTest {
            // given
            coEvery { userRepository.existsById(1L) } returns true
            coEvery { userRepository.deleteById(1L) } just runs

            // when
            userService.deleteUser(1L)

            // then
            coVerify(exactly = 1) { userRepository.deleteById(1L) }
        }

        @Test
        @DisplayName("should throw NotFoundException when user not found")
        fun `should throw NotFoundException when user not found`() = runTest {
            // given
            coEvery { userRepository.existsById(1L) } returns false

            // when & then
            assertThrows<NotFoundException> {
                kotlinx.coroutines.runBlocking {
                    userService.deleteUser(1L)
                }
            }
        }
    }
}
