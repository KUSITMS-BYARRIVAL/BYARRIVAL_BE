package com.byarrival.domain.user.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.domain.term.service.TermService;
import com.byarrival.domain.user.dto.response.NotificationPermissionResponse;
import com.byarrival.domain.user.dto.response.UserMeResponse;
import com.byarrival.domain.user.entity.JobGroup;
import com.byarrival.domain.user.entity.NotificationPermission;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.entity.UserInterestCategory;
import com.byarrival.domain.user.entity.UserJobGroup;
import com.byarrival.domain.user.entity.UserStatus;
import com.byarrival.domain.user.repository.UserInterestCategoryRepository;
import com.byarrival.domain.user.repository.UserJobGroupRepository;
import com.byarrival.domain.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
    private final UserInterestCategoryRepository userInterestCategoryRepository;
    private final UserJobGroupRepository userJobGroupRepository;
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
        User user = getActiveUser(userId);
        return UserMeResponse.of(user, termService.hasAgreedRequiredTerms(userId));
    }

    @Transactional
    public NotificationPermissionResponse updateNotificationPermission(Long userId, NotificationPermission permission) {
        User user = getActiveUser(userId);
        user.updateNotificationPermission(permission);
        return NotificationPermissionResponse.from(user.getNotificationPermission());
    }

    /**
     * OS 알림 권한을 아직 묻지 않았으면 비어 있다.
     */
    @Transactional(readOnly = true)
    public Optional<NotificationPermission> findNotificationPermission(Long userId) {
        return userRepository.findById(userId).map(User::getNotificationPermission);
    }

    /**
     * 같은 소셜 계정의 활성 회원이 이미 있으면 유니크 인덱스 위반(DataIntegrityViolationException)이 발생한다.
     */
    @Transactional
    public User signUp(SocialProvider provider, String providerUserId) {
        return userRepository.saveAndFlush(User.signUp(provider, providerUserId));
    }

    @Transactional(readOnly = true)
    public List<Long> findInterestCategoryIds(Long userId) {
        return userInterestCategoryRepository.findCategoryIdsByUserId(userId);
    }

    /**
     * 관심 분야를 통째로 바꾼다. 카테고리 존재 여부는 호출하는 쪽에서 확인한다.
     */
    @Transactional
    public void replaceInterestCategories(Long userId, Collection<Long> categoryIds) {
        userInterestCategoryRepository.deleteAllByUserIdInBatch(userId);
        userInterestCategoryRepository.saveAll(categoryIds.stream()
            .distinct()
            .map(categoryId -> UserInterestCategory.of(userId, categoryId))
            .toList());
    }

    /**
     * 고른 순서대로 돌려준다.
     */
    @Transactional(readOnly = true)
    public List<JobGroup> findJobGroups(Long userId) {
        return userJobGroupRepository.findJobGroupsByUserId(userId);
    }

    /**
     * 직군을 통째로 바꾸고 목록 순서를 고른 순서로 저장한다. 개수·중복 검증은 호출하는 쪽에서 한다.
     */
    @Transactional
    public void replaceJobGroups(Long userId, List<JobGroup> jobGroups) {
        userJobGroupRepository.deleteAllByUserIdInBatch(userId);
        List<UserJobGroup> rows = new ArrayList<>();
        for (int i = 0; i < jobGroups.size(); i++) {
            rows.add(UserJobGroup.of(userId, jobGroups.get(i), i + 1));
        }
        userJobGroupRepository.saveAll(rows);
    }

    /**
     * 탈퇴했거나 없는 회원이면 UNAUTHORIZED로 응답해 앱이 로그인 화면으로 이동하게 한다.
     * 엔티티를 바꾸려면 호출하는 쪽의 트랜잭션 안에서 부른다.
     */
    public User getActiveUser(Long userId) {
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
