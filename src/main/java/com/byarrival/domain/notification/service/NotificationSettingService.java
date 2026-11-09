package com.byarrival.domain.notification.service;

import com.byarrival.domain.notification.dto.request.NotificationSettingUpdateRequest;
import com.byarrival.domain.notification.dto.response.NotificationSettingsResponse;
import com.byarrival.domain.notification.entity.NotificationCategory;
import com.byarrival.domain.notification.entity.UserNotificationSetting;
import com.byarrival.domain.notification.repository.UserNotificationSettingRepository;
import com.byarrival.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리별 수신 설정 조회·변경. 한 번도 바꾸지 않은 카테고리는 켜진 것으로 본다.
 */
@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private final UserNotificationSettingRepository settingRepository;
    private final UserService userService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public NotificationSettingsResponse getSettings(Long userId) {
        Map<NotificationCategory, Boolean> enabled = new EnumMap<>(NotificationCategory.class);
        Arrays.stream(NotificationCategory.values()).forEach(category -> enabled.put(category, true));
        settingRepository.findByUserId(userId)
            .forEach(setting -> enabled.put(setting.getCategory(), setting.isEnabled()));
        return NotificationSettingsResponse.of(userService.findNotificationPermission(userId).orElse(null), enabled);
    }

    @Transactional
    public NotificationSettingsResponse updateSettings(Long userId, NotificationSettingUpdateRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        // 같은 카테고리가 여러 번 오면 마지막 값을 쓴다. 카테고리 수만큼만 반복하므로 쿼리도 그 수를 넘지 않는다.
        Map<NotificationCategory, Boolean> changes = new EnumMap<>(NotificationCategory.class);
        request.settings().forEach(setting -> changes.put(setting.category(), setting.enabled()));
        changes.forEach((category, enabled) -> settingRepository.upsert(userId, category.name(), enabled, now));
        return getSettings(userId);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(Long userId, NotificationCategory category) {
        return settingRepository.findByUserIdAndCategory(userId, category)
            .map(UserNotificationSetting::isEnabled)
            .orElse(true);
    }
}
