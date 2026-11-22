package com.byarrival.domain.admin.security;

import com.byarrival.domain.admin.entity.AdminAccount;
import com.byarrival.domain.admin.entity.AdminRole;

/**
 * 인증된 어드민. 컨트롤러에서 {@code @AuthenticationPrincipal AdminPrincipal}로 받는다.
 */
public record AdminPrincipal(Long id, String email, AdminRole role) {

    public static AdminPrincipal from(AdminAccount account) {
        return new AdminPrincipal(account.getId(), account.getEmail(), account.getRole());
    }
}
