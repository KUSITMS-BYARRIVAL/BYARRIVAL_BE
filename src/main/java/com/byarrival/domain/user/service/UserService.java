package com.byarrival.domain.user.service;

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

    @Transactional(readOnly = true)
    public Optional<User> findActiveUser(SocialProvider provider, String providerUserId) {
        return userRepository.findByProviderAndProviderUserIdAndStatus(provider, providerUserId, UserStatus.ACTIVE);
    }

    /**
     * 같은 소셜 계정의 활성 회원이 이미 있으면 유니크 인덱스 위반(DataIntegrityViolationException)이 발생한다.
     */
    @Transactional
    public User signUp(SocialProvider provider, String providerUserId) {
        return userRepository.saveAndFlush(User.signUp(provider, providerUserId));
    }
}
