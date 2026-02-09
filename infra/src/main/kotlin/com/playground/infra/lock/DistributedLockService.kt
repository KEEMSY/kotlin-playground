package com.playground.infra.lock

import java.time.Duration

/**
 * 동기 방식 분산 락 서비스 인터페이스
 * api-mvc (Spring MVC, 블로킹 I/O) 모듈에서 사용
 */
interface DistributedLockService {

    /**
     * 분산 락을 획득하고 action을 실행한 뒤 자동으로 락을 해제합니다.
     *
     * @param lockKey 락 키 (예: "user:email:test@example.com")
     * @param waitTime 락 획득을 위한 최대 대기 시간 (기본값: 3초)
     * @param leaseTime 락 보유 최대 시간, 이 시간이 지나면 자동 해제 (기본값: 10초)
     * @param action 락 획득 후 실행할 로직
     * @return action의 결과
     * @throws LockAcquisitionException 락 획득 실패 시
     */
    fun <T> executeWithLock(
        lockKey: String,
        waitTime: Duration = Duration.ofSeconds(3),
        leaseTime: Duration = Duration.ofSeconds(10),
        action: () -> T
    ): T

    /**
     * 락 획득을 시도합니다.
     *
     * @param lockKey 락 키
     * @param leaseTime 락 보유 최대 시간
     * @return 락 획득 성공 시 lockValue (해제 시 필요), 실패 시 null
     */
    fun tryLock(
        lockKey: String,
        leaseTime: Duration
    ): String?

    /**
     * 락을 해제합니다.
     * 본인이 획득한 락만 해제할 수 있습니다 (lockValue 검증).
     *
     * @param lockKey 락 키
     * @param lockValue 락 획득 시 반환받은 값
     * @return 해제 성공 여부
     */
    fun unlock(
        lockKey: String,
        lockValue: String
    ): Boolean
}
