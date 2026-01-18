package com.playground.webflux.config

import com.playground.core.exception.ApiException
import com.playground.core.exception.ErrorResponse
import com.playground.core.util.logger
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
