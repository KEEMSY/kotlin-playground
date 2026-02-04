package com.playground.webflux.controller

import com.playground.webflux.dto.*
import com.playground.webflux.service.PostService
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
@RequestMapping("/api/v1/posts")
@Tag(name = "Post", description = "Post management APIs")
class PostController(
    private val postService: PostService
) {

    @PostMapping
    @Operation(summary = "Create a new post")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Successfully created post"),
        ApiResponse(responseCode = "404", description = "User not found")
    )
    suspend fun createPost(
        @Valid @RequestBody request: CreatePostRequest
    ): ResponseEntity<PostResponse> {
        val post = postService.createPost(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(post)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get post by ID")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Successfully retrieved post"),
        ApiResponse(responseCode = "404", description = "Post or User not found")
    )
    suspend fun getPostById(
        @Parameter(description = "Post ID") @PathVariable id: Long
    ): ResponseEntity<PostResponse> {
        val post = postService.getPostById(id)
        return ResponseEntity.ok(post)
    }

    @GetMapping("/search")
    @Operation(summary = "Search posts (Dynamic Query with Komapper)")
    suspend fun searchPosts(condition: PostSearchCondition): ResponseEntity<List<PostResponse>> {
        val posts = postService.searchPosts(condition)
        return ResponseEntity.ok(posts)
    }

    @GetMapping
    @Operation(summary = "Get all posts with parallel author fetching")
    suspend fun getAllPosts(): ResponseEntity<List<PostResponse>> {
        val posts = postService.getAllPosts().toList()
        return ResponseEntity.ok(posts)
    }

    @GetMapping("/batch")
    @Operation(summary = "Get all posts with Batch Loading (Map matching)")
    suspend fun getAllPostsBatch(): ResponseEntity<List<PostResponse>> {
        val posts = postService.getAllPostsBatch()
        return ResponseEntity.ok(posts)
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get posts by user ID")
    suspend fun getPostsByUserId(
        @Parameter(description = "User ID") @PathVariable userId: Long
    ): ResponseEntity<List<PostResponse>> {
        val posts = postService.getPostsByUserId(userId).toList()
        return ResponseEntity.ok(posts)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update post")
    suspend fun updatePost(
        @Parameter(description = "Post ID") @PathVariable id: Long,
        @Valid @RequestBody request: UpdatePostRequest
    ): ResponseEntity<PostResponse> {
        val post = postService.updatePost(id, request)
        return ResponseEntity.ok(post)
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete post")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deletePost(
        @Parameter(description = "Post ID") @PathVariable id: Long
    ): ResponseEntity<Void> {
        postService.deletePost(id)
        return ResponseEntity.noContent().build()
    }
}
