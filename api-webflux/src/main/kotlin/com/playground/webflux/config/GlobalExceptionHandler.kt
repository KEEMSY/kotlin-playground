package com.playground.webflux.config

import com.playground.core.exception.ApiException
import com.playground.core.exception.ErrorResponse
import com.playground.core.util.logger
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.resource.NoResourceFoundException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Configuration
@Order(-2)
class GlobalExceptionHandler : ErrorWebExceptionHandler {

    private val log = logger()

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        val response = exchange.response
        val path = exchange.request.path.value()

        val errorResponse = when (ex) {
            is NoResourceFoundException -> {
                response.statusCode = HttpStatus.NOT_FOUND
                ErrorResponse(
                    status = HttpStatus.NOT_FOUND.value(),
                    error = HttpStatus.NOT_FOUND.reasonPhrase,
                    message = ex.message ?: "Resource not found",
                    errorCode = "NOT_FOUND",
                    path = path
                )
            }
            is CallNotPermittedException -> {
                log.warn("Circuit breaker is OPEN: ${ex.message}")
                response.statusCode = HttpStatus.SERVICE_UNAVAILABLE
                ErrorResponse(
                    status = HttpStatus.SERVICE_UNAVAILABLE.value(),
                    error = HttpStatus.SERVICE_UNAVAILABLE.reasonPhrase,
                    message = "Service is temporarily unavailable. Please try again later.",
                    errorCode = "CIRCUIT_BREAKER_OPEN",
                    path = path
                )
            }
            is RequestNotPermitted -> {
                log.warn("Rate limit exceeded: ${ex.message}")
                response.statusCode = HttpStatus.TOO_MANY_REQUESTS
                ErrorResponse(
                    status = HttpStatus.TOO_MANY_REQUESTS.value(),
                    error = HttpStatus.TOO_MANY_REQUESTS.reasonPhrase,
                    message = "Too many requests. Please slow down.",
                    errorCode = "RATE_LIMIT_EXCEEDED",
                    path = path
                )
            }
            is ResponseStatusException -> {
                response.statusCode = ex.statusCode
                ErrorResponse(
                    status = ex.statusCode.value(),
                    error = ex.statusCode.toString(),
                    message = ex.reason ?: ex.message,
                    errorCode = ex.statusCode.value().toString(),
                    path = path
                )
            }
            is ApiException -> {
                log.warn("API Exception: ${ex.message}", ex)
                response.statusCode = ex.status
                ErrorResponse(
                    status = ex.status.value(),
                    error = ex.status.reasonPhrase,
                    message = ex.message,
                    errorCode = ex.errorCode,
                    path = path
                )
            }
            else -> {
                log.error("Unexpected error: ${ex.message}", ex)
                response.statusCode = HttpStatus.INTERNAL_SERVER_ERROR
                ErrorResponse(
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
                    message = "An unexpected error occurred",
                    errorCode = "INTERNAL_SERVER_ERROR",
                    path = path
                )
            }
        }

        response.headers.contentType = MediaType.APPLICATION_JSON

        val buffer = response.bufferFactory().wrap(
            com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules()
                .writeValueAsBytes(errorResponse)
        )

        return response.writeWith(Mono.just(buffer))
    }
}
