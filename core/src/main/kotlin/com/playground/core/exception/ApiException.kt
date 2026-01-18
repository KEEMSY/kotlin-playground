package com.playground.core.exception

import org.springframework.http.HttpStatus

open class ApiException(
    val status: HttpStatus,
    override val message: String,
    val errorCode: String? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class NotFoundException(
    message: String,
    errorCode: String? = "NOT_FOUND"
) : ApiException(HttpStatus.NOT_FOUND, message, errorCode)

class BadRequestException(
    message: String,
    errorCode: String? = "BAD_REQUEST"
) : ApiException(HttpStatus.BAD_REQUEST, message, errorCode)

class ConflictException(
    message: String,
    errorCode: String? = "CONFLICT"
) : ApiException(HttpStatus.CONFLICT, message, errorCode)

class UnauthorizedException(
    message: String,
    errorCode: String? = "UNAUTHORIZED"
) : ApiException(HttpStatus.UNAUTHORIZED, message, errorCode)

class ForbiddenException(
    message: String,
    errorCode: String? = "FORBIDDEN"
) : ApiException(HttpStatus.FORBIDDEN, message, errorCode)

class InternalServerException(
    message: String,
    errorCode: String? = "INTERNAL_SERVER_ERROR",
    cause: Throwable? = null
) : ApiException(HttpStatus.INTERNAL_SERVER_ERROR, message, errorCode, cause)
