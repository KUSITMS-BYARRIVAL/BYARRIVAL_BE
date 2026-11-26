package com.byarrival.domain.admin.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.Restriction;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.RestrictionHistory;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.RestrictionResult;
import com.byarrival.domain.admin.entity.AdminAccount;
import com.byarrival.domain.admin.entity.AdminMemberStatus;
import com.byarrival.domain.admin.exception.AdminErrorCode;
import com.byarrival.domain.admin.repository.AdminAccountRepository;
import com.byarrival.domain.user.entity.RestrictionPeriod;
import com.byarrival.domain.user.entity.UserRestriction;
import com.byarrival.domain.user.service.UserRestrictionService;
import com.byarrival.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어드민 회원 이용 제한·해제와 조치 이력(MEMBER-03).
 * 같은 회원의 제한·해제는 회원 행을 잠가 줄 세운다. 그래서 적용 중인 제한은 한 번에 하나뿐이다.
 */
@Service
@RequiredArgsConstructor
public class AdminMemberRestrictionService {

    private final UserService userService;
    private final UserRestrictionService userRestrictionService;
    private final AdminAccountRepository adminAccountRepository;
    private final Clock clock;

    /**
     * 이미 제한 중이면 기간을 덮어쓰지 않고 거절한다. 기간을 바꾸려면 해제한 뒤 다시 제한한다.
     */
    @Transactional
    public RestrictionResult restrict(Long adminId, Long memberId, RestrictionPeriod period, String reason) {
        lockActiveMember(memberId);
        if (userRestrictionService.findActive(memberId).isPresent()) {
            throw new BusinessException(AdminErrorCode.ADMIN_MEMBER_ALREADY_RESTRICTED);
        }
        userRestrictionService.restrict(memberId, adminId, period, reason);
        return getRestriction(memberId);
    }

    /**
     * 적용 중인 제한을 지금 푼다. 제한 중이 아니어도 성공으로 응답한다.
     */
    @Transactional
    public RestrictionResult release(Long adminId, Long memberId) {
        lockActiveMember(memberId);
        userRestrictionService.release(memberId, adminId);
        return getRestriction(memberId);
    }

    /**
     * 회원 상세에 넣는 현재 상태와 조치 이력. 회원이 있는지는 호출하는 쪽에서 확인한다.
     */
    @Transactional(readOnly = true)
    public RestrictionResult getRestriction(Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<UserRestriction> history = userRestrictionService.findHistory(memberId);
        UserRestriction active = history.stream()
            .filter(restriction -> restriction.isActiveAt(now))
            .findFirst()
            .orElse(null);
        Map<Long, String> adminEmails = findAdminEmails(history);
        List<RestrictionHistory> items = history.stream()
            .map(restriction -> new RestrictionHistory(restriction.getId(), restriction.getPeriod(),
                restriction.getReason(), restriction.getStartsAt(), restriction.getEndsAt(),
                adminEmails.get(restriction.getAdminId()), restriction.getReleasedAt(),
                adminEmails.get(restriction.getReleasedByAdminId()), restriction == active))
            .toList();
        AdminMemberStatus status = active == null ? AdminMemberStatus.NORMAL : AdminMemberStatus.RESTRICTED;
        return new RestrictionResult(memberId, status, active == null ? null : Restriction.from(active), items);
    }

    private void lockActiveMember(Long memberId) {
        userService.findActiveByIdForUpdate(memberId)
            .orElseThrow(() -> new BusinessException(AdminErrorCode.ADMIN_MEMBER_NOT_FOUND));
    }

    private Map<Long, String> findAdminEmails(List<UserRestriction> history) {
        Set<Long> adminIds = history.stream()
            .flatMap(restriction -> Stream.of(restriction.getAdminId(), restriction.getReleasedByAdminId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        return adminAccountRepository.findAllById(adminIds).stream()
            .collect(Collectors.toMap(AdminAccount::getId, AdminAccount::getEmail));
    }
}
