package com.playground.webflux.router

import com.playground.webflux.handler.DelayHandler
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springdoc.core.annotations.RouterOperation
import org.springdoc.core.annotations.RouterOperations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class DelayRouter(private val delayHandler: DelayHandler) {

    @Bean
    @RouterOperations(
            RouterOperation(
                    path = "/api/v1/delay/{ms}",
                    method = [RequestMethod.GET],
                    beanClass = DelayHandler::class,
                    beanMethod = "delay",
                    operation =
                            Operation(
                                    operationId = "delay",
                                    summary = "Simulate I/O delay (non-blocking)",
                                    description =
                                            "Simulates I/O operation using Kotlin coroutine delay(). " +
                                                    "This is NON-BLOCKING - the thread is released during the delay.",
                                    parameters =
                                            [
                                                    Parameter(
                                                            name = "ms",
                                                            `in` = ParameterIn.PATH,
                                                            description = "Delay in milliseconds"
                                                    )
                                            ],
                                    responses =
                                            [
                                                    ApiResponse(
                                                            responseCode = "200",
                                                            description = "Delay completed"
                                                    )
                                            ]
                            )
            ),
            RouterOperation(
                    path = "/api/v1/delay/cpu/{iterations}",
                    method = [RequestMethod.GET],
                    beanClass = DelayHandler::class,
                    beanMethod = "cpuWork",
                    operation =
                            Operation(
                                    operationId = "cpuWork",
                                    summary = "Simulate CPU-intensive work",
                                    description =
                                            "Simulates CPU-bound operation. CPU work is inherently blocking.",
                                    parameters =
                                            [
                                                    Parameter(
                                                            name = "iterations",
                                                            `in` = ParameterIn.PATH,
                                                            description = "Number of iterations"
                                                    )
                                            ],
                                    responses =
                                            [
                                                    ApiResponse(
                                                            responseCode = "200",
                                                            description = "CPU work completed"
                                                    )
                                            ]
                            )
            ),
            RouterOperation(
                    path = "/api/v1/delay/blocking/{ms}",
                    method = [RequestMethod.GET],
                    beanClass = DelayHandler::class,
                    beanMethod = "blockingDelay",
                    operation =
                            Operation(
                                    operationId = "blockingDelay",
                                    summary = "Simulate blocking delay (ANTI-PATTERN)",
                                    description =
                                            "Demonstrates what happens when you use Thread.sleep() in WebFlux. " +
                                                    "WARNING: This is an anti-pattern and will degrade performance!",
                                    parameters =
                                            [
                                                    Parameter(
                                                            name = "ms",
                                                            `in` = ParameterIn.PATH,
                                                            description = "Delay in milliseconds"
                                                    )
                                            ],
                                    responses =
                                            [
                                                    ApiResponse(
                                                            responseCode = "200",
                                                            description = "Blocking delay completed"
                                                    )
                                            ]
                            )
            )
    )
    fun delayRoutes() = coRouter {
        "/api/v1/delay".nest {
            accept(MediaType.APPLICATION_JSON).nest {
                GET("/cpu/{iterations}", delayHandler::cpuWork)
                GET("/blocking/{ms}", delayHandler::blockingDelay)
                GET("/{ms}", delayHandler::delay)
            }
        }
    }
}
