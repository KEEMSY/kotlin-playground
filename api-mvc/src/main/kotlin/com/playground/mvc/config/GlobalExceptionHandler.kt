package com.playground.mvc.config

import com.playground.core.exception.ApiException
import com.playground.core.exception.ErrorResponse
import com.playground.core.util.logger
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = logger()

    @ExceptionHandler(ApiException::class)
    fun handleApiException(
        ex: ApiException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("API Exception: ${ex.message}", ex)
        return ResponseEntity
            .status(ex.status)
            .body(
                ErrorResponse(
                    status = ex.status.value(),
                    error = ex.status.reasonPhrase,
                    message = ex.message,
                    errorCode = ex.errorCode,
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    status = HttpStatus.BAD_REQUEST.value(),
                    error = HttpStatus.BAD_REQUEST.reasonPhrase,
                    message = message,
                    errorCode = "VALIDATION_ERROR",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(
        ex: NoResourceFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(
                ErrorResponse(
                    status = HttpStatus.NOT_FOUND.value(),
                    error = HttpStatus.NOT_FOUND.reasonPhrase,
                    message = ex.message ?: "Resource not found",
                    errorCode = "NOT_FOUND",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(CallNotPermittedException::class)
    fun handleCircuitBreakerOpen(
        ex: CallNotPermittedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Circuit breaker is OPEN: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                ErrorResponse(
                    status = HttpStatus.SERVICE_UNAVAILABLE.value(),
                    error = HttpStatus.SERVICE_UNAVAILABLE.reasonPhrase,
                    message = "Service is temporarily unavailable. Please try again later.",
                    errorCode = "CIRCUIT_BREAKER_OPEN",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(RequestNotPermitted::class)
    fun handleRateLimitExceeded(
        ex: RequestNotPermitted,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Rate limit exceeded: ${ex.message}")
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(
                ErrorResponse(
                    status = HttpStatus.TOO_MANY_REQUESTS.value(),
                    error = HttpStatus.TOO_MANY_REQUESTS.reasonPhrase,
                    message = "Too many requests. Please slow down.",
                    errorCode = "RATE_LIMIT_EXCEEDED",
                    path = request.requestURI
                )
            )
    }

    @ExceptionHandler(Exception::class)
    fun handleException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error: ${ex.message}", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ErrorResponse(
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
                    message = "An unexpected error occurred",
                    errorCode = "INTERNAL_SERVER_ERROR",
                    path = request.requestURI
                )
            )
    }
}
