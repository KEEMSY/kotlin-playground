package com.playground.infra.external

/**
 * 외부 결제 API 클라이언트 인터페이스
 *
 * 실제 환경에서는 Stripe, Toss Payments 등의 외부 API를 호출합니다.
 * 학습 목적으로 FakePaymentClient로 실패 상황을 시뮬레이션합니다.
 */
interface PaymentClient {

    /**
     * 결제를 처리합니다.
     *
     * @param userId 사용자 ID
     * @param amount 결제 금액 (원 단위)
     * @return 결제 결과
     * @throws PaymentException 결제 실패 시
     */
    suspend fun processPayment(userId: Long, amount: Long): PaymentResult

    /**
     * 결제를 취소합니다.
     *
     * @param transactionId 거래 ID
     * @return 취소 결과
     * @throws PaymentException 취소 실패 시
     */
    suspend fun cancelPayment(transactionId: String): PaymentResult
}
