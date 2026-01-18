package com.playground.webflux.router

import com.playground.webflux.dto.CreateUserRequest
import com.playground.webflux.dto.UpdateUserRequest
import com.playground.webflux.dto.UserResponse
import com.playground.webflux.handler.UserHandler
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springdoc.core.annotations.RouterOperation
import org.springdoc.core.annotations.RouterOperations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class UserRouter(
    private val userHandler: UserHandler
) {

    @Bean
    @RouterOperations(
        RouterOperation(
            path = "/api/v1/users",
            method = [RequestMethod.GET],
            beanClass = UserHandler::class,
            beanMethod = "getAllUsers",
            operation = Operation(
                operationId = "getAllUsers",
                summary = "Get all users",
                tags = ["User"],
                responses = [
                    ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved users",
                        content = [Content(array = ArraySchema(schema = Schema(implementation = UserResponse::class)))]
                    )
                ]
            )
        ),
        RouterOperation(
            path = "/api/v1/users/{id}",
            method = [RequestMethod.GET],
            beanClass = UserHandler::class,
            beanMethod = "getUserById",
            operation = Operation(
                operationId = "getUserById",
                summary = "Get user by ID",
                tags = ["User"],
                parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, required = true)],
                responses = [
                    ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved user",
                        content = [Content(schema = Schema(implementation = UserResponse::class))]
                    ),
                    ApiResponse(responseCode = "404", description = "User not found")
                ]
            )
        ),
        RouterOperation(
            path = "/api/v1/users/email/{email}",
            method = [RequestMethod.GET],
            beanClass = UserHandler::class,
            beanMethod = "getUserByEmail",
            operation = Operation(
                operationId = "getUserByEmail",
                summary = "Get user by email",
                tags = ["User"],
                parameters = [Parameter(name = "email", `in` = ParameterIn.PATH, required = true)],
                responses = [
                    ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved user",
                        content = [Content(schema = Schema(implementation = UserResponse::class))]
                    ),
                    ApiResponse(responseCode = "404", description = "User not found")
                ]
            )
        ),
        RouterOperation(
            path = "/api/v1/users",
            method = [RequestMethod.POST],
            beanClass = UserHandler::class,
            beanMethod = "createUser",
            operation = Operation(
                operationId = "createUser",
                summary = "Create a new user",
                tags = ["User"],
                requestBody = RequestBody(
                    required = true,
                    content = [Content(schema = Schema(implementation = CreateUserRequest::class))]
                ),
                responses = [
                    ApiResponse(
                        responseCode = "201",
                        description = "Successfully created user",
                        content = [Content(schema = Schema(implementation = UserResponse::class))]
                    ),
                    ApiResponse(responseCode = "400", description = "Invalid input"),
                    ApiResponse(responseCode = "409", description = "User already exists")
                ]
            )
        ),
        RouterOperation(
            path = "/api/v1/users/{id}",
            method = [RequestMethod.PUT],
            beanClass = UserHandler::class,
            beanMethod = "updateUser",
            operation = Operation(
                operationId = "updateUser",
                summary = "Update user",
                tags = ["User"],
                parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, required = true)],
                requestBody = RequestBody(
                    required = true,
                    content = [Content(schema = Schema(implementation = UpdateUserRequest::class))]
                ),
                responses = [
                    ApiResponse(
                        responseCode = "200",
                        description = "Successfully updated user",
                        content = [Content(schema = Schema(implementation = UserResponse::class))]
                    ),
                    ApiResponse(responseCode = "404", description = "User not found"),
                    ApiResponse(responseCode = "409", description = "Email already in use")
                ]
            )
        ),
        RouterOperation(
            path = "/api/v1/users/{id}",
            method = [RequestMethod.DELETE],
            beanClass = UserHandler::class,
            beanMethod = "deleteUser",
            operation = Operation(
                operationId = "deleteUser",
                summary = "Delete user",
                tags = ["User"],
                parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, required = true)],
                responses = [
                    ApiResponse(responseCode = "204", description = "Successfully deleted user"),
                    ApiResponse(responseCode = "404", description = "User not found")
                ]
            )
        )
    )
    fun userRoutes(): RouterFunction<ServerResponse> = coRouter {
        "/api/v1/users".nest {
            accept(MediaType.APPLICATION_JSON).nest {
                GET("", userHandler::getAllUsers)
                GET("/{id}", userHandler::getUserById)
                GET("/email/{email}", userHandler::getUserByEmail)
                POST("", userHandler::createUser)
                PUT("/{id}", userHandler::updateUser)
                DELETE("/{id}", userHandler::deleteUser)
            }
        }
    }
}
