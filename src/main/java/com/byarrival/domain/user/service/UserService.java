package com.byarrival.domain.user.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.domain.term.service.TermService;
import com.byarrival.domain.user.dto.response.UserMeResponse;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.entity.UserStatus;
import com.byarrival.domain.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 조회·가입
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TermService termService;

    @Transactional(readOnly = true)
    public Optional<User> findActiveUser(SocialProvider provider, String providerUserId) {
        return userRepository.findByProviderAndProviderUserIdAndStatus(provider, providerUserId, UserStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public boolean isActiveUser(Long userId) {
        return userRepository.existsByIdAndStatus(userId, UserStatus.ACTIVE);
    }

    /**
     * 탈퇴했거나 없는 회원이면 UNAUTHORIZED로 응답해 앱이 로그인 화면으로 이동하게 한다.
     */
    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        User user = userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        return UserMeResponse.of(user, termService.hasAgreedRequiredTerms(userId));
    }

    /**
     * 같은 소셜 계정의 활성 회원이 이미 있으면 유니크 인덱스 위반(DataIntegrityViolationException)이 발생한다.
     */
    @Transactional
    public User signUp(SocialProvider provider, String providerUserId) {
        return userRepository.saveAndFlush(User.signUp(provider, providerUserId));
    }
}
