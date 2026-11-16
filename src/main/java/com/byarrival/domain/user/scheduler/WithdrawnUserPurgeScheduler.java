package com.byarrival.domain.user.scheduler;

import com.byarrival.common.config.ClockConfig;
import com.byarrival.domain.user.service.ProfileImageService;
import com.byarrival.domain.user.service.UserService;
import com.byarrival.domain.user.service.UserService.PurgedUsers;
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
    private final ProfileImageService profileImageService;

    @Scheduled(cron = "0 0 5 * * *", zone = ClockConfig.KST)
    public void purge() {
        PurgedUsers purged = userService.deleteExpiredWithdrawnUsers();
        // 파일 삭제는 외부 호출이라 DB 파기를 커밋한 뒤에 한다
        purged.profileImageUrls().forEach(profileImageService::deleteQuietly);
        log.info("보관 기간이 지난 탈퇴 회원 파기: {}명", purged.count());
    }
}
