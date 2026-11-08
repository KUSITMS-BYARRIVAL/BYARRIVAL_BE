package com.byarrival.domain.notification.dto.request;

import com.byarrival.domain.notification.entity.NotificationCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record NotificationSettingUpdateRequest(
    @NotEmpty List<@Valid @NotNull Setting> settings
) {

    public record Setting(
        @NotNull NotificationCategory category,
        @NotNull Boolean enabled
    ) {
    }
}
