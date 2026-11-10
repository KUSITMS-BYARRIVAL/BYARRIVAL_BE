package com.byarrival.domain.user.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.domain.auth.service.TokenService;
import com.byarrival.domain.notification.service.DeviceTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴. 인증 정보(리프레시 토큰, 기기 푸시 토큰)는 바로 없애고, 프로필·설정·이용 이력은 보관 기간 동안 남긴다.
 * 하나라도 실패하면 모두 되돌려 계정을 유지한다.
 */
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final UserService userService;
    private final TokenService tokenService;
    private final DeviceTokenService deviceTokenService;

    /**
     * 이미 탈퇴했거나 없는 회원이면 UNAUTHORIZED로 응답한다.
     */
    @Transactional
    public void withdraw(Long userId) {
        if (!userService.isActiveUser(userId)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        // 토큰 일괄 처리 쿼리가 영속성 컨텍스트를 비우므로, 회원 상태는 그 뒤에 바꾼다.
        tokenService.revokeAll(userId);
        deviceTokenService.deleteAll(userId);
        userService.withdraw(userId);
    }
}
