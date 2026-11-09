package com.byarrival.domain.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceUnregisterRequest(
    @NotBlank @Size(max = 512) String fcmToken
) {
}
