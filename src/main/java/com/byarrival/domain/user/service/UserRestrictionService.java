package com.byarrival.domain.user.service;

import com.byarrival.domain.user.entity.RestrictionPeriod;
import com.byarrival.domain.user.entity.UserRestriction;
import com.byarrival.domain.user.repository.UserRestrictionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 이용 제한(MEMBER-03). 제한 중인 회원은 로그인은 되지만 안내 화면 외에는 앱을 쓸 수 없고 알림도 받지 않는다.
 * 제한 기간이 지나면 따로 해제하지 않아도 풀린다.
 */
@Service
@RequiredArgsConstructor
public class UserRestrictionService {

    private final UserRestrictionRepository restrictionRepository;
    private final Clock clock;

    /**
     * 지금 적용 중인 제한. 없거나 기간이 지났거나 해제됐으면 비어 있다.
     */
    @Transactional(readOnly = true)
    public Optional<UserRestriction> findActive(Long userId) {
        return restrictionRepository.findActive(userId, LocalDateTime.now(clock)).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public boolean isRestricted(Long userId) {
        return restrictionRepository.existsActive(userId, LocalDateTime.now(clock));
    }

    /**
     * 지난 제한까지 최근 순으로 돌려준다.
     */
    @Transactional(readOnly = true)
    public List<UserRestriction> findHistory(Long userId) {
        return restrictionRepository.findAllByUserIdOrderByStartsAtDescIdDesc(userId);
    }

    /**
     * 지금부터 제한한다. 이미 제한 중인지는 호출하는 쪽이 회원 행을 잠근 뒤 확인한다.
     */
    @Transactional
    public UserRestriction restrict(Long userId, Long adminId, RestrictionPeriod period, String reason) {
        return restrictionRepository.save(
            UserRestriction.start(userId, adminId, period, reason.strip(), LocalDateTime.now(clock)));
    }

    /**
     * 적용 중인 제한을 지금 푼다.
     *
     * @return 푼 제한이 있었는지
     */
    @Transactional
    public boolean release(Long userId, Long adminId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<UserRestriction> active = restrictionRepository.findActive(userId, now);
        active.forEach(restriction -> restriction.release(adminId, now));
        return !active.isEmpty();
    }
}
