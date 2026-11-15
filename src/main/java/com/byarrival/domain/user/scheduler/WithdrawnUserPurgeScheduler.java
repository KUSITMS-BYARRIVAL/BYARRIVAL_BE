package com.byarrival.domain.user.scheduler;

import com.byarrival.common.config.ClockConfig;
import com.byarrival.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보관 기간(6개월)이 지난 탈퇴 회원 데이터를 매일 05:00(KST)에 파기한다. 블루/그린 전환 중 두 번 실행돼도 결과는 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawnUserPurgeScheduler {

    private final UserService userService;

    @Scheduled(cron = "0 0 5 * * *", zone = ClockConfig.KST)
    public void purge() {
        int deleted = userService.deleteExpiredWithdrawnUsers();
        log.info("보관 기간이 지난 탈퇴 회원 파기: {}명", deleted);
    }
}
