package com.byarrival.domain.term.scheduler;

import com.byarrival.common.config.ClockConfig;
import com.byarrival.domain.term.service.MarketingConsentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 10:00(KST)에 광고성 정보 수신 동의 후 2년이 지난 회원에게 동의 여부를 다시 안내한다(정보통신망법).
 * 안내한 시각을 남겨 다음 안내는 그로부터 2년 뒤에 한다. 블루/그린 전환 중 두 번 실행돼도 먼저 남긴 기록 때문에 다시 보내지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingConsentReconfirmScheduler {

    private final MarketingConsentService marketingConsentService;

    @Scheduled(cron = "0 0 10 * * *", zone = ClockConfig.KST)
    public void reconfirm() {
        try {
            int notified = marketingConsentService.reconfirmDue();
            log.info("광고성 정보 수신 동의 재확인 안내: {}명", notified);
        } catch (RuntimeException e) {
            log.error("광고성 정보 수신 동의 재확인 안내 실패", e);
        }
    }
}
