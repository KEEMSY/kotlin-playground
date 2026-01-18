package com.playground.mvc

import com.playground.core.exception.ConflictException
import com.playground.core.exception.NotFoundException
import com.playground.mvc.dto.CreateUserRequest
import com.playground.mvc.dto.UpdateUserRequest
import com.playground.mvc.entity.User
import com.playground.mvc.repository.UserRepository
import com.playground.mvc.service.UserService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime
import java.util.*

@DisplayName("UserService JUnit5 Tests")
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
        fun `should return all users`() {
            // given
            every { userRepository.findAll() } returns listOf(testUser)

            // when
            val result = userService.getAllUsers()

            // then
            assertEquals(1, result.size)
            assertEquals(testUser.email, result[0].email)
            verify(exactly = 1) { userRepository.findAll() }
        }

        @Test
        @DisplayName("should return empty list when no users")
        fun `should return empty list when no users`() {
            // given
            every { userRepository.findAll() } returns emptyList()

            // when
            val result = userService.getAllUsers()

            // then
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("getUserById")
    inner class GetUserByIdTest {

        @Test
        @DisplayName("should return user when found")
        fun `should return user when found`() {
            // given
            every { userRepository.findById(1L) } returns Optional.of(testUser)

            // when
            val result = userService.getUserById(1L)

            // then
            assertEquals(testUser.id, result.id)
            assertEquals(testUser.email, result.email)
        }

        @Test
        @DisplayName("should throw NotFoundException when user not found")
        fun `should throw NotFoundException when user not found`() {
            // given
            every { userRepository.findById(1L) } returns Optional.empty()

            // when & then
            assertThrows<NotFoundException> {
                userService.getUserById(1L)
            }
        }
    }

    @Nested
    @DisplayName("createUser")
    inner class CreateUserTest {

        @Test
        @DisplayName("should create user successfully")
        fun `should create user successfully`() {
            // given
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

            // when
            val result = userService.createUser(request)

            // then
            assertNotNull(result)
            verify { userRepository.save(any()) }
        }

        @Test
        @DisplayName("should throw ConflictException when email exists")
        fun `should throw ConflictException when email exists`() {
            // given
            val request = CreateUserRequest("existing@example.com", "User")
            every { userRepository.existsByEmail(request.email) } returns true

            // when & then
            assertThrows<ConflictException> {
                userService.createUser(request)
            }
        }
    }

    @Nested
    @DisplayName("updateUser")
    inner class UpdateUserTest {

        @Test
        @DisplayName("should update user successfully")
        fun `should update user successfully`() {
            // given
            val request = UpdateUserRequest(name = "Updated Name")
            every { userRepository.findById(1L) } returns Optional.of(testUser)
            every { userRepository.save(any()) } returns testUser.apply { name = "Updated Name" }

            // when
            val result = userService.updateUser(1L, request)

            // then
            assertEquals("Updated Name", result.name)
        }

        @Test
        @DisplayName("should throw NotFoundException when user not found")
        fun `should throw NotFoundException when user not found`() {
            // given
            every { userRepository.findById(1L) } returns Optional.empty()

            // when & then
            assertThrows<NotFoundException> {
                userService.updateUser(1L, UpdateUserRequest(name = "Updated"))
            }
        }
    }

    @Nested
    @DisplayName("deleteUser")
    inner class DeleteUserTest {

        @Test
        @DisplayName("should delete user successfully")
        fun `should delete user successfully`() {
            // given
            every { userRepository.existsById(1L) } returns true
            every { userRepository.deleteById(1L) } just runs

            // when
            userService.deleteUser(1L)

            // then
            verify(exactly = 1) { userRepository.deleteById(1L) }
        }

        @Test
        @DisplayName("should throw NotFoundException when user not found")
        fun `should throw NotFoundException when user not found`() {
            // given
            every { userRepository.existsById(1L) } returns false

            // when & then
            assertThrows<NotFoundException> {
                userService.deleteUser(1L)
            }
        }
    }
}
