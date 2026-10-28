package com.byarrival.domain.auth.dto.request;

import com.byarrival.domain.user.entity.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param token 카카오는 access token, 애플은 identity token
 */
public record SocialLoginRequest(
    @NotNull SocialProvider provider,
    @NotBlank String token
) {
}
