package com.byarrival.domain.admin.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.admin.entity.AdminAccount;
import com.byarrival.domain.admin.exception.AdminErrorCode;
import com.byarrival.domain.admin.repository.AdminAccountRepository;
import com.byarrival.domain.admin.security.AdminPrincipal;
import com.byarrival.domain.admin.security.AdminTokenProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * 어드민 로그인(ADM-01). 비밀번호 해시 비교는 느리므로 트랜잭션 없이 한다. 로그인 실패 횟수는 제한하지 않는다.
 */
@Service
public class AdminAuthService {

    private static final int BCRYPT_STRENGTH = 12;

    private final AdminAccountRepository repository;
    private final AdminTokenProvider tokenProvider;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    // 없는 이메일도 비슷한 시간이 걸리게 해, 응답 시간으로 계정 존재를 알 수 없게 한다
    private final String dummyHash = passwordEncoder.encode("byarrival-admin-dummy-password");

    public AdminAuthService(AdminAccountRepository repository, AdminTokenProvider tokenProvider, Clock clock) {
        this.repository = repository;
        this.tokenProvider = tokenProvider;
        this.clock = clock;
    }

    public LoginResult login(String email, String password) {
        Optional<AdminAccount> found = repository.findByEmail(email.strip().toLowerCase(Locale.ROOT));
        if (found.isEmpty() || !found.get().isActive()) {
            passwordEncoder.matches(password, dummyHash);
            throw new BusinessException(AdminErrorCode.ADMIN_LOGIN_FAILED);
        }
        AdminAccount account = found.get();
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new BusinessException(AdminErrorCode.ADMIN_LOGIN_FAILED);
        }
        return new LoginResult(AdminPrincipal.from(account), tokenProvider.create(account));
    }

    /**
     * 토큰의 서명·만료와 계정 상태를 확인한다. 비활성 계정이나 로그아웃한 토큰은 바로 막는다.
     *
     * @throws org.springframework.security.oauth2.jwt.JwtException 쓸 수 없는 토큰
     */
    public AdminPrincipal authenticate(String token) {
        Jwt jwt = tokenProvider.decode(token);
        Long id = Long.valueOf(jwt.getSubject());
        Number version = jwt.getClaim(AdminTokenProvider.TOKEN_VERSION_CLAIM);
        return repository.findByIdAndActiveTrue(id)
            .filter(account -> version != null && account.getTokenVersion() == version.intValue())
            .map(AdminPrincipal::from)
            .orElseThrow(() -> new BadJwtException("사용할 수 없는 어드민 토큰입니다."));
    }

    /**
     * 이 계정에 발급한 토큰을 모두 무효로 만든다.
     */
    public void logout(Long adminId) {
        repository.increaseTokenVersion(adminId, LocalDateTime.now(clock));
    }

    public record LoginResult(AdminPrincipal admin, String token) {
    }
}
