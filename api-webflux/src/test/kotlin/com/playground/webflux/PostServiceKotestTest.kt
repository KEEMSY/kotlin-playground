package com.playground.webflux

import com.playground.core.exception.NotFoundException
import com.playground.webflux.dto.CreatePostRequest
import com.playground.webflux.entity.Post
import com.playground.webflux.entity.User
import com.playground.webflux.repository.PostRepository
import com.playground.webflux.repository.UserRepository
import com.playground.webflux.service.PostService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.komapper.r2dbc.R2dbcDatabase
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.LocalDateTime

class PostServiceKotestTest : DescribeSpec({

    val postRepository = mockk<PostRepository>()
    val userRepository = mockk<UserRepository>()
    val db = mockk<R2dbcDatabase>()
    val redisTemplate = mockk<ReactiveRedisTemplate<String, Any>>()
    val valueOps = mockk<ReactiveValueOperations<String, Any>>()
    val postService = PostService(postRepository, userRepository, db, redisTemplate)

    val testUser = User(
        id = 1L,
        email = "test@example.com",
        name = "Test User"
    )

    val testPost = Post(
        id = 1L,
        title = "Test Title",
        content = "Test Content",
        userId = 1L
    )

    beforeEach {
        clearAllMocks()
        coEvery { redisTemplate.opsForValue() } returns valueOps
    }

    describe("createPost") {
        context("when user exists") {
            it("should create post successfully") {
                val request = CreatePostRequest("New Title", "New Content", 1L)

                coEvery { valueOps.get(any()) } returns Mono.empty()
                coEvery { valueOps.set(any<String>(), any<Any>(), any<Duration>()) } returns Mono.just(true)
                coEvery { userRepository.findById(1L) } returns testUser
                coEvery { postRepository.save(any()) } returns testPost

                val result = postService.createPost(request)

                result shouldNotBe null
                result.title shouldBe "Test Title"
                result.authorName shouldBe "Test User"

                coVerify { postRepository.save(any()) }
            }
        }

        context("when user does not exist") {
            it("should throw NotFoundException") {
                val request = CreatePostRequest("Title", "Content", 1L)
                coEvery { valueOps.get(any()) } returns Mono.empty()
                coEvery { userRepository.findById(1L) } returns null

                shouldThrow<NotFoundException> {
                    postService.createPost(request)
                }
            }
        }
    }

    describe("getPostById") {
        context("when post exists") {
            it("should return post response with author name") {
                coEvery { postRepository.findById(1L) } returns testPost
                coEvery { valueOps.get(any()) } returns Mono.empty()
                coEvery { valueOps.set(any<String>(), any<Any>(), any<Duration>()) } returns Mono.just(true)
                coEvery { userRepository.findById(1L) } returns testUser

                val result = postService.getPostById(1L)

                result.id shouldBe 1L
                result.authorName shouldBe "Test User"
            }
        }
    }
})
