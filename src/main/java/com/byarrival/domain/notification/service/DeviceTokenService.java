package com.byarrival.domain.notification.service;

import com.byarrival.domain.notification.entity.DevicePlatform;
import com.byarrival.domain.notification.entity.DeviceToken;
import com.byarrival.domain.notification.repository.DeviceTokenRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 푸시를 받을 기기 토큰 등록·삭제·조회
 */
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final Clock clock;

    /**
     * 같은 토큰을 다시 보내도 한 행만 유지한다. 다른 계정에 있던 토큰이면 지금 계정으로 옮긴다.
     */
    @Transactional
    public void register(Long userId, String token, DevicePlatform platform) {
        deviceTokenRepository.upsert(userId, token, platform.name(), LocalDateTime.now(clock));
    }

    /**
     * 내 토큰만 지운다. 없거나 다른 계정의 토큰이어도 성공으로 처리한다.
     */
    @Transactional
    public void unregister(Long userId, String token) {
        deviceTokenRepository.deleteByTokenAndUserId(token, userId);
    }

    @Transactional(readOnly = true)
    public List<String> findTokens(Long userId) {
        return deviceTokenRepository.findByUserId(userId).stream()
            .map(DeviceToken::getToken)
            .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasDevice(Long userId) {
        return deviceTokenRepository.existsByUserId(userId);
    }

    @Transactional
    public void deleteTokens(Collection<String> tokens) {
        if (!tokens.isEmpty()) {
            deviceTokenRepository.deleteByTokenIn(tokens);
        }
    }
}
