package com.playground.mvc

import com.playground.core.exception.NotFoundException
import com.playground.mvc.dto.CreatePostRequest
import com.playground.mvc.dto.UpdatePostRequest
import com.playground.mvc.entity.Post
import com.playground.mvc.entity.User
import com.playground.mvc.repository.PostRepository
import com.playground.mvc.repository.UserRepository
import com.playground.mvc.service.PostService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import java.time.LocalDateTime
import java.util.*

class PostServiceKotestTest : DescribeSpec({

    val postRepository = mockk<PostRepository>()
    val userRepository = mockk<UserRepository>()
    val postService = PostService(postRepository, userRepository)

    val testUser = User(
        id = 1L,
        email = "test@example.com",
        name = "Test User",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    val testPost = Post(
        id = 1L,
        title = "Test Title",
        content = "Test Content",
        user = testUser,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    beforeEach {
        clearAllMocks()
    }

    describe("createPost") {
        context("when user exists") {
            it("should create post successfully") {
                val request = CreatePostRequest("New Title", "New Content", 1L)
                
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { postRepository.save(any()) } answers {
                    firstArg<Post>().apply { 
                        val idField = Post::class.java.getDeclaredField("id")
                        idField.isAccessible = true
                        idField.set(this, 1L)
                    }
                }

                val result = postService.createPost(request)

                result shouldNotBe null
                result.title shouldBe "New Title"
                result.authorName shouldBe testUser.name
                
                testUser.posts shouldHaveSize 1
                verify { postRepository.save(any()) }
            }
        }

        context("when user does not exist") {
            it("should throw NotFoundException") {
                val request = CreatePostRequest("Title", "Content", 1L)
                every { userRepository.findById(1L) } returns Optional.empty()

                shouldThrow<NotFoundException> {
                    postService.createPost(request)
                }
            }
        }
    }

    describe("getPostById") {
        context("when post exists") {
            it("should return post response") {
                every { postRepository.findById(1L) } returns Optional.of(testPost)

                val result = postService.getPostById(1L)

                result.id shouldBe 1L
                result.title shouldBe "Test Title"
            }
        }

        context("when post does not exist") {
            it("should throw NotFoundException") {
                every { postRepository.findById(1L) } returns Optional.empty()

                shouldThrow<NotFoundException> {
                    postService.getPostById(1L)
                }
            }
        }
    }

    describe("getPostsByUserId") {
        it("should return list of post responses") {
            every { postRepository.findAllByUserIdWithUser(1L) } returns listOf(testPost)

            val result = postService.getPostsByUserId(1L)

            result shouldHaveSize 1
            result[0].title shouldBe "Test Title"
        }
    }

    describe("updatePost") {
        context("when post exists") {
            it("should update post title and content") {
                val request = UpdatePostRequest("Updated Title", "Updated Content")
                every { postRepository.findById(1L) } returns Optional.of(testPost)

                val result = postService.updatePost(1L, request)

                result.title shouldBe "Updated Title"
                result.content shouldBe "Updated Content"
            }
        }
    }

    describe("deletePost") {
        context("when post exists") {
            it("should delete successfully") {
                every { postRepository.existsById(1L) } returns true
                every { postRepository.deleteById(1L) } just runs

                postService.deletePost(1L)

                verify { postRepository.deleteById(1L) }
            }
        }

        context("when post does not exist") {
            it("should throw NotFoundException") {
                every { postRepository.existsById(1L) } returns false

                shouldThrow<NotFoundException> {
                    postService.deletePost(1L)
                }
            }
        }
    }
})
