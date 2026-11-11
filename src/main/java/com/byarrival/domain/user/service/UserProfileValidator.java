package com.byarrival.domain.user.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.onboarding.config.OnboardingProperties;
import com.byarrival.domain.user.entity.DailyCommute;
import com.byarrival.domain.user.exception.UserErrorCode;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 온보딩과 마이 설정이 함께 쓰는 회원 정보 입력 규칙.
 */
@Component
@RequiredArgsConstructor
public class UserProfileValidator {

    private static final Pattern NICKNAME_CHARACTERS = Pattern.compile("^[가-힣a-zA-Z0-9]+$");

    private final CategoryMappingService categoryMappingService;
    private final OnboardingProperties properties;

    /**
     * @return 앞뒤 공백을 지운 닉네임
     */
    public String validNickname(String nickname) {
        String stripped = nickname.strip();
        int length = stripped.codePointCount(0, stripped.length());
        if (length < properties.nicknameMinLength() || length > properties.nicknameMaxLength()
            || !NICKNAME_CHARACTERS.matcher(stripped).matches()) {
            throw new BusinessException(UserErrorCode.INVALID_NICKNAME);
        }
        return stripped;
    }

    public int validCommuteMinutes(int minutes) {
        boolean inRange = minutes >= properties.commuteMinutesMin() && minutes <= properties.commuteMinutesMax();
        if (!inRange || minutes % properties.commuteMinutesStep() != 0) {
            throw new BusinessException(UserErrorCode.INVALID_COMMUTE_MINUTES);
        }
        return minutes;
    }

    public void validateCategories(Collection<Long> categoryIds) {
        Set<Long> activeIds = categoryMappingService.getActiveCategoryIds();
        if (!activeIds.containsAll(categoryIds)) {
            throw new BusinessException(UserErrorCode.INVALID_CATEGORY);
        }
    }

    public void validateDailyCommuteMinutes(int minutes) {
        if (!DailyCommute.isValidMinutes(minutes)) {
            throw new BusinessException(UserErrorCode.INVALID_WEEKLY_COMMUTE);
        }
    }
}
