package com.byarrival.domain.user.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.common.monitoring.ServerErrorNotifier;
import com.byarrival.domain.auth.client.KakaoUnlinkClient;
import com.byarrival.domain.auth.service.AppleTokenService;
import com.byarrival.domain.auth.service.TokenService;
import com.byarrival.domain.notification.service.DeviceTokenService;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원 탈퇴. 인증 정보(리프레시 토큰, 기기 푸시 토큰)는 바로 없애고, 프로필·설정·이용 이력은 보관 기간 동안 남긴다.
 * DB 처리는 한 트랜잭션으로 묶어 하나라도 실패하면 계정을 유지한다.
 * 소셜 연결 끊기는 외부 호출이라 커밋한 뒤에 하고, 실패해도 탈퇴는 그대로 두고 서버 에러 채널에 알린다.
 */
@Slf4j
@Service
public class UserWithdrawalService {

    static final String WITHDRAW_METHOD = "DELETE";
    static final String WITHDRAW_URI = "/api/v1/users/me";

    private final UserService userService;
    private final TokenService tokenService;
    private final DeviceTokenService deviceTokenService;
    private final KakaoUnlinkClient kakaoUnlinkClient;
    private final AppleTokenService appleTokenService;
    private final ServerErrorNotifier serverErrorNotifier;
    private final TransactionTemplate transactionTemplate;

    public UserWithdrawalService(UserService userService, TokenService tokenService,
        DeviceTokenService deviceTokenService, KakaoUnlinkClient kakaoUnlinkClient, AppleTokenService appleTokenService,
        ServerErrorNotifier serverErrorNotifier, PlatformTransactionManager transactionManager) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.deviceTokenService = deviceTokenService;
        this.kakaoUnlinkClient = kakaoUnlinkClient;
        this.appleTokenService = appleTokenService;
        this.serverErrorNotifier = serverErrorNotifier;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 이미 탈퇴했거나 없는 회원이면 UNAUTHORIZED로 응답한다.
     */
    public void withdraw(Long userId) {
        User withdrawn = transactionTemplate.execute(status -> withdrawInTransaction(userId));
        unlinkSocialAccount(withdrawn);
    }

    private User withdrawInTransaction(Long userId) {
        if (!userService.isActiveUser(userId)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        // 토큰 일괄 처리 쿼리가 영속성 컨텍스트를 비우므로, 회원 상태는 그 뒤에 바꾼다.
        tokenService.revokeAll(userId);
        deviceTokenService.deleteAll(userId);
        return userService.withdraw(userId);
    }

    // 실패한 회원은 운영자가 서버 로그의 회원 ID로 찾아 직접 연결을 끊는다. 알림에는 회원 정보를 넣지 않는다.
    private void unlinkSocialAccount(User user) {
        try {
            if (user.getProvider() == SocialProvider.KAKAO) {
                kakaoUnlinkClient.unlink(user.getProviderUserId());
            } else if (user.getProvider() == SocialProvider.APPLE) {
                appleTokenService.revoke(user);
            }
        } catch (RuntimeException e) {
            log.error("{} 연결 끊기 실패. 탈퇴는 완료했으니 직접 연결을 끊어야 합니다: userId={}, error={}",
                user.getProvider(), user.getId(), e.getClass().getSimpleName());
            serverErrorNotifier.notify(WITHDRAW_METHOD, WITHDRAW_URI, e);
        }
    }
}
