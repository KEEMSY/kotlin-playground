package com.playground.infra.external

import java.time.LocalDateTime

data class PaymentResult(
    val transactionId: String,
    val status: PaymentStatus = PaymentStatus.COMPLETED,
    val userId: Long? = null,
    val amount: Long? = null,
    val processedAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun pending(userId: Long, amount: Long): PaymentResult {
            return PaymentResult(
                transactionId = "PENDING-${System.currentTimeMillis()}",
                status = PaymentStatus.PENDING,
                userId = userId,
                amount = amount
            )
        }
    }
}

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED
}
