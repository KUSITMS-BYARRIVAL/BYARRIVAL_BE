package com.byarrival.domain.user.dto.request;

import com.byarrival.domain.user.entity.NotificationPermission;
import jakarta.validation.constraints.NotNull;

public record NotificationPermissionRequest(
    @NotNull NotificationPermission permission
) {
}
