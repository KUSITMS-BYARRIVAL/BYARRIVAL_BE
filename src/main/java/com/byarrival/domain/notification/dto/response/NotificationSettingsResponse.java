package com.byarrival.domain.notification.dto.response;

import com.byarrival.domain.notification.entity.NotificationCategory;
import com.byarrival.domain.user.entity.NotificationPermission;
import java.util.List;
import java.util.Map;

/**
 * 수신 설정. permission이 DENIED면 앱은 OS 설정으로 이동하라는 안내를 보여 준다. 아직 묻지 않았으면 null이다.
 */
public record NotificationSettingsResponse(
    NotificationPermission permission,
    List<Setting> settings
) {

    public static NotificationSettingsResponse of(
        NotificationPermission permission, Map<NotificationCategory, Boolean> enabled) {
        List<Setting> settings = enabled.entrySet().stream()
            .map(entry -> new Setting(entry.getKey(), entry.getValue()))
            .toList();
        return new NotificationSettingsResponse(permission, settings);
    }

    public record Setting(
        NotificationCategory category,
        boolean enabled
    ) {
    }
}
