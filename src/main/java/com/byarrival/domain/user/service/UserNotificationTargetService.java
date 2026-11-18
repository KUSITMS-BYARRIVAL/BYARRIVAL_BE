package com.byarrival.domain.user.service;

import com.byarrival.domain.region.dto.response.RegionResponse;
import com.byarrival.domain.region.service.RegionService;
import com.byarrival.domain.user.entity.JobGroup;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.entity.UserStatus;
import com.byarrival.domain.user.repository.CommuteScheduleRepository;
import com.byarrival.domain.user.repository.UserJobGroupRepository;
import com.byarrival.domain.user.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자동 알림을 받을 회원과 알림에 쓰는 회원 정보(근무 지역·직군)를 찾는다.
 */
@Service
@RequiredArgsConstructor
public class UserNotificationTargetService {

    private final UserRepository userRepository;
    private final CommuteScheduleRepository commuteScheduleRepository;
    private final UserJobGroupRepository userJobGroupRepository;
    private final RegionService regionService;

    /**
     * 그 요일 출근 시각이 from~to(양 끝 포함)인 회원. 쉬는 날로 둔 회원은 빠진다.
     */
    @Transactional(readOnly = true)
    public List<Long> findCommuters(DayOfWeek day, LocalTime from, LocalTime to) {
        return commuteScheduleRepository.findUserIdsByCommuteStart(day, from, to);
    }

    @Transactional(readOnly = true)
    public List<Long> findNotifiableUserIds() {
        return userRepository.findNotifiableUserIds();
    }

    /**
     * 활동 중인 회원의 근무 지역과 직군. 직군은 처음 고른 하나를 쓴다. 탈퇴했거나 없는 회원이면 비어 있다.
     */
    @Transactional(readOnly = true)
    public Optional<Profile> findProfile(Long userId) {
        Optional<User> user = userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        RegionResponse region = regionService.findRegion(user.get().getRegionSigunguId()).orElse(null);
        JobGroup jobGroup = userJobGroupRepository.findJobGroupsByUserId(userId).stream().findFirst().orElse(null);
        return Optional.of(new Profile(
            region == null ? null : region.sigunguId(),
            region == null ? null : region.sigunguName(),
            jobGroup));
    }

    /**
     * @param sigunguId   근무 지역(시/군/구). 고르지 않았으면 null
     * @param sigunguName 근무 지역 이름(예: 강남구)
     * @param jobGroup    직군. 고르지 않았으면 null
     */
    public record Profile(Integer sigunguId, String sigunguName, JobGroup jobGroup) {
    }
}
