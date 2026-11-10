package com.byarrival.domain.user.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.domain.user.dto.UserTravelSetting;
import com.byarrival.domain.user.repository.UserTravelSettingRepository;
import com.byarrival.domain.user.repository.UserTravelSettingRepository.TravelMinutes;
import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노선 생성에 쓰는 사용자 이동시간·관심 분야 조회
 */
@Service
@RequiredArgsConstructor
public class UserTravelSettingService {

    private final UserTravelSettingRepository userTravelSettingRepository;

    /**
     * 탈퇴했거나 없는 회원이면 UNAUTHORIZED로 응답해 앱이 로그인 화면으로 이동하게 한다.
     */
    @Transactional(readOnly = true)
    public UserTravelSetting getTravelSetting(Long userId) {
        TravelMinutes minutes = userTravelSettingRepository.findTravelMinutes(userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        Map<DayOfWeek, Integer> weekdayMinutes = new EnumMap<>(DayOfWeek.class);
        putIfPresent(weekdayMinutes, DayOfWeek.MONDAY, minutes.getMonMinutes());
        putIfPresent(weekdayMinutes, DayOfWeek.TUESDAY, minutes.getTueMinutes());
        putIfPresent(weekdayMinutes, DayOfWeek.WEDNESDAY, minutes.getWedMinutes());
        putIfPresent(weekdayMinutes, DayOfWeek.THURSDAY, minutes.getThuMinutes());
        putIfPresent(weekdayMinutes, DayOfWeek.FRIDAY, minutes.getFriMinutes());
        putIfPresent(weekdayMinutes, DayOfWeek.SATURDAY, minutes.getSatMinutes());
        putIfPresent(weekdayMinutes, DayOfWeek.SUNDAY, minutes.getSunMinutes());
        return new UserTravelSetting(minutes.getDefaultMinutes(), weekdayMinutes,
            userTravelSettingRepository.findInterestCategoryIds(userId));
    }

    private void putIfPresent(Map<DayOfWeek, Integer> weekdayMinutes, DayOfWeek dayOfWeek, Integer minutes) {
        if (minutes != null) {
            weekdayMinutes.put(dayOfWeek, minutes);
        }
    }
}
