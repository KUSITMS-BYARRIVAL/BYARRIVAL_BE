package com.byarrival.domain.notification.dto.request;

import com.byarrival.domain.notification.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeviceRegisterRequest(
    @NotBlank @Size(max = 512) String fcmToken,
    @NotNull DevicePlatform platform
) {
}
