package com.byarrival.domain.admin.dto.response;

import com.byarrival.domain.admin.entity.AdminRole;
import com.byarrival.domain.admin.security.AdminPrincipal;

/**
 * 로그인한 어드민 정보. 토큰은 HttpOnly 쿠키로만 보내고 응답 본문에 넣지 않는다.
 */
public record AdminResponse(Long adminId, String email, AdminRole role) {

    public static AdminResponse from(AdminPrincipal principal) {
        return new AdminResponse(principal.id(), principal.email(), principal.role());
    }
}
