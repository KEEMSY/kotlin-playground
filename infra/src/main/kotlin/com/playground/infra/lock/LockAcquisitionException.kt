package com.playground.infra.lock

import com.playground.core.exception.ApiException
import org.springframework.http.HttpStatus

class LockAcquisitionException(
    lockKey: String,
    waitTimeMillis: Long
) : ApiException(
    status = HttpStatus.SERVICE_UNAVAILABLE,
    message = "Failed to acquire lock for key '$lockKey' within ${waitTimeMillis}ms",
    errorCode = "LOCK_ACQUISITION_FAILED"
)
