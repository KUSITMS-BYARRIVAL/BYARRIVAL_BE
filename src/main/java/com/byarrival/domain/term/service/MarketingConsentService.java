package com.byarrival.domain.term.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.term.dto.response.MarketingConsentResponse;
import com.byarrival.domain.term.dto.response.MarketingConsentResponse.Consent;
import com.byarrival.domain.term.entity.Terms;
import com.byarrival.domain.term.entity.TermsType;
import com.byarrival.domain.term.entity.UserTermsAgreement;
import com.byarrival.domain.term.event.MarketingConsentChangedEvent;
import com.byarrival.domain.term.event.MarketingConsentReconfirmEvent;
import com.byarrival.domain.term.exception.TermErrorCode;
import com.byarrival.domain.term.repository.MarketingConsentRepository;
import com.byarrival.domain.term.repository.MarketingConsentRepository.ConsentRow;
import com.byarrival.domain.term.repository.MarketingConsentRepository.ReconfirmTarget;
import com.byarrival.domain.term.repository.TermsRepository;
import com.byarrival.domain.term.repository.UserTermsAgreementRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 광고성 정보 수신 동의(마케팅·야간). 정보통신망법에 따라 동의·철회 결과를 안내하고, 동의 후 2년마다 동의 여부를 다시 안내한다.
 * <ul>
 *     <li>동의하거나 철회한 적이 없으면 동의하지 않은 것(false)이다.</li>
 *     <li>야간(21~08시) 광고성 정보 수신은 광고성 정보 수신에 동의한 사람만 동의할 수 있다.</li>
 *     <li>광고성 정보 수신을 철회하면 야간 동의도 함께 철회한다.</li>
 *     <li>동의·철회는 이력으로 쌓는다(약관 동의와 같은 user_terms_agreement).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MarketingConsentService {

    static final List<TermsType> TYPES = List.of(TermsType.MARKETING, TermsType.MARKETING_NIGHT);
    static final Period RECONFIRM_PERIOD = Period.ofYears(2);
    static final int RECONFIRM_BATCH_SIZE = 500;

    private final MarketingConsentRepository consentRepository;
    private final TermsRepository termsRepository;
    private final UserTermsAgreementRepository agreementRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MarketingConsentResponse getConsent(Long userId) {
        Map<TermsType, ConsentRow> current = currentOf(userId);
        return new MarketingConsentResponse(consentOf(current.get(TermsType.MARKETING)),
            consentOf(current.get(TermsType.MARKETING_NIGHT)));
    }

    /**
     * 바뀐 값만 이력으로 남기고, 바뀐 것이 있으면 커밋 뒤 결과를 안내한다.
     */
    @Transactional
    public MarketingConsentResponse change(Long userId, boolean marketing, boolean night) {
        validate(marketing, night);
        Map<TermsType, ConsentRow> current = currentOf(userId);
        Map<TermsType, Boolean> changes = changesOf(current, Map.of(TermsType.MARKETING, marketing,
            TermsType.MARKETING_NIGHT, night));
        record(userId, changes, LocalDateTime.now(clock));
        return getConsent(userId);
    }

    /**
     * 회원가입 약관 동의(ONB-03)에서 마케팅·야간 동의가 함께 들어오면 같은 규칙으로 확인한다.
     *
     * @param requested 약관 동의 요청에 들어 있는 마케팅·야간 동의 값
     */
    public void validateSignupConsent(Map<TermsType, Boolean> requested) {
        boolean marketing = Boolean.TRUE.equals(requested.get(TermsType.MARKETING));
        boolean night = Boolean.TRUE.equals(requested.get(TermsType.MARKETING_NIGHT));
        validate(marketing, night);
    }

    /**
     * 약관 동의로 마케팅·야간 동의가 저장되기 직전에 부른다. 바뀐 것이 있으면 커밋 뒤 결과를 안내한다.
     *
     * @param requested 약관 동의 요청에 들어 있는 마케팅·야간 동의 값
     */
    @Transactional(readOnly = true)
    public void announceIfChanged(Long userId, Map<TermsType, Boolean> requested, LocalDateTime changedAt) {
        Map<TermsType, Boolean> changes = changesOf(currentOf(userId), requested);
        publishChanged(userId, changes, changedAt);
    }

    /**
     * 광고성 정보 수신에 동의한 지(또는 마지막 재확인 안내를 받은 지) 2년이 지난 회원에게 동의 여부를 다시 안내한다.
     *
     * @return 안내한 회원 수
     */
    @Transactional
    public int reconfirmDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime before = now.minus(RECONFIRM_PERIOD);
        int notified = 0;
        List<ReconfirmTarget> targets;
        do {
            targets = consentRepository.findReconfirmTargets(before, RECONFIRM_BATCH_SIZE);
            for (ReconfirmTarget target : targets) {
                if (consentRepository.markReconfirmed(target.userId(), now, before)) {
                    eventPublisher.publishEvent(new MarketingConsentReconfirmEvent(target.userId(), target.agreedAt()));
                    notified++;
                }
            }
        } while (targets.size() == RECONFIRM_BATCH_SIZE);
        return notified;
    }

    private static void validate(boolean marketing, boolean night) {
        if (night && !marketing) {
            throw new BusinessException(TermErrorCode.INVALID_MARKETING_CONSENT);
        }
    }

    private Map<TermsType, ConsentRow> currentOf(Long userId) {
        return consentRepository.findLatest(userId, TYPES).stream()
            .collect(Collectors.toMap(ConsentRow::type, Function.identity()));
    }

    // 지금 상태와 다른 값만 남긴다. 기록이 없으면 동의하지 않은 상태다
    private static Map<TermsType, Boolean> changesOf(Map<TermsType, ConsentRow> current,
        Map<TermsType, Boolean> requested) {
        Map<TermsType, Boolean> changes = new EnumMap<>(TermsType.class);
        requested.forEach((type, agreed) -> {
            ConsentRow row = current.get(type);
            boolean now = row != null && row.agreed();
            if (agreed != null && agreed != now) {
                changes.put(type, agreed);
            }
        });
        return changes;
    }

    private void record(Long userId, Map<TermsType, Boolean> changes, LocalDateTime now) {
        if (changes.isEmpty()) {
            return;
        }
        Map<TermsType, Terms> latest = termsRepository.findLatestOfEachType().stream()
            .filter(terms -> TYPES.contains(terms.getTermsType()))
            .collect(Collectors.toMap(Terms::getTermsType, Function.identity()));
        List<UserTermsAgreement> rows = new ArrayList<>();
        changes.forEach((type, agreed) -> rows.add(UserTermsAgreement.record(userId,
            Objects.requireNonNull(latest.get(type), "약관이 없습니다: " + type).getId(), agreed, now)));
        agreementRepository.saveAll(rows);
        publishChanged(userId, changes, now);
    }

    private void publishChanged(Long userId, Map<TermsType, Boolean> changes, LocalDateTime changedAt) {
        if (changes.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new MarketingConsentChangedEvent(userId, changedAt,
            changes.get(TermsType.MARKETING), changes.get(TermsType.MARKETING_NIGHT)));
    }

    private static Consent consentOf(ConsentRow row) {
        return row == null ? new Consent(false, null) : new Consent(row.agreed(), row.agreedAt());
    }
}
