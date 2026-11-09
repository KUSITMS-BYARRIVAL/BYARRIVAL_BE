package com.byarrival.domain.user.dto.response;

import com.byarrival.domain.user.entity.NotificationPermission;

public record NotificationPermissionResponse(
    NotificationPermission permission
) {

    public static NotificationPermissionResponse from(NotificationPermission permission) {
        return new NotificationPermissionResponse(permission);
    }
}
