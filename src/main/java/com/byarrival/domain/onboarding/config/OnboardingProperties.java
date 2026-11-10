package com.byarrival.domain.onboarding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 온보딩 입력 규칙. 기획 확정 전 가정값이라 설정으로 뺐다.
 *
 * @param nicknameMinLength     닉네임 최소 글자 수
 * @param nicknameMaxLength     닉네임 최대 글자 수
 * @param commuteMinutesMin     기본 이동시간 최솟값(분)
 * @param commuteMinutesMax     기본 이동시간 최댓값(분)
 * @param commuteMinutesStep    기본 이동시간 단위(분)
 */
@ConfigurationProperties(prefix = "onboarding")
public record OnboardingProperties(
    int nicknameMinLength,
    int nicknameMaxLength,
    int commuteMinutesMin,
    int commuteMinutesMax,
    int commuteMinutesStep
) {

    public OnboardingProperties {
        if (nicknameMinLength <= 0 || nicknameMaxLength < nicknameMinLength) {
            throw new IllegalArgumentException("onboarding 닉네임 글자 수 범위가 올바르지 않습니다.");
        }
        if (commuteMinutesStep <= 0 || commuteMinutesMin <= 0 || commuteMinutesMax < commuteMinutesMin) {
            throw new IllegalArgumentException("onboarding 기본 이동시간 범위가 올바르지 않습니다.");
        }
    }
}
