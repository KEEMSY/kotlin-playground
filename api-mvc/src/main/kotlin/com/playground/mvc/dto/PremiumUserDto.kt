package com.playground.mvc.dto

import com.playground.infra.external.PaymentStatus
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class CreatePremiumUserRequest(
    @field:NotNull
    val userId: Long,

    @field:NotNull
    val plan: PremiumPlan,

    @field:Min(1000)
    val amount: Long = 9900
)

data class PremiumUserResponse(
    val userId: Long,
    val plan: PremiumPlan,
    val transactionId: String,
    val paymentStatus: PaymentStatus,
    val message: String
)

enum class PremiumPlan(val price: Long) {
    BASIC(9900),
    STANDARD(19900),
    PREMIUM(29900)
}
