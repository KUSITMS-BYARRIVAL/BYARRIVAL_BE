package com.byarrival.domain.term.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.term.dto.request.TermsAgreementRequest.Agreement;
import com.byarrival.domain.term.dto.response.TermsAgreementResponse;
import com.byarrival.domain.term.dto.response.TermsResponse;
import com.byarrival.domain.term.entity.Terms;
import com.byarrival.domain.term.entity.TermsType;
import com.byarrival.domain.term.entity.UserTermsAgreement;
import com.byarrival.domain.term.exception.TermErrorCode;
import com.byarrival.domain.term.repository.TermsRepository;
import com.byarrival.domain.term.repository.UserTermsAgreementRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 약관 조회와 동의 이력 저장. 필수 약관에 모두 동의해야 서비스를 이용할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class TermService {

    private final TermsRepository termsRepository;
    private final UserTermsAgreementRepository userTermsAgreementRepository;
    private final MarketingConsentService marketingConsentService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TermsResponse> getLatestTerms() {
        return termsRepository.findLatestOfEachType().stream()
            .map(TermsResponse::from)
            .toList();
    }

    /**
     * 최신 버전 약관에만 동의할 수 있고, 필수 약관은 모두 동의해야 한다. 동의 결과는 이력으로 쌓는다.
     */
    @Transactional
    public List<TermsAgreementResponse> agree(Long userId, List<Agreement> agreements) {
        Map<Long, Terms> latestTerms = termsRepository.findLatestOfEachType().stream()
            .collect(Collectors.toMap(Terms::getId, Function.identity()));
        validateTermsIds(agreements, latestTerms);
        validateRequiredTermsAgreed(agreements, latestTerms);
        Map<TermsType, Boolean> marketing = marketingConsentOf(agreements, latestTerms);
        marketingConsentService.validateSignupConsent(marketing);

        LocalDateTime now = LocalDateTime.now(clock);
        // 저장하기 전에 지금 상태와 비교해 바뀐 마케팅·야간 동의만 안내한다
        marketingConsentService.announceIfChanged(userId, marketing, now);
        List<UserTermsAgreement> saved = userTermsAgreementRepository.saveAll(agreements.stream()
            .map(agreement -> UserTermsAgreement.record(userId, agreement.termsId(), agreement.agreed(), now))
            .toList());
        return saved.stream()
            .map(TermsAgreementResponse::from)
            .toList();
    }

    /**
     * 최신 필수 약관마다 가장 최근 이력이 동의 상태인지 확인한다.
     */
    @Transactional(readOnly = true)
    public boolean hasAgreedRequiredTerms(Long userId) {
        List<Long> requiredTermsIds = termsRepository.findLatestOfEachType().stream()
            .filter(Terms::isRequired)
            .map(Terms::getId)
            .toList();
        if (requiredTermsIds.isEmpty()) {
            return true;
        }

        Map<Long, Boolean> currentState = new HashMap<>();
        userTermsAgreementRepository.findByUserIdAndTermsIdInOrderByAgreedAtDescIdDesc(userId, requiredTermsIds)
            .forEach(agreement -> currentState.putIfAbsent(agreement.getTermsId(), agreement.isAgreed()));
        return requiredTermsIds.stream()
            .allMatch(termsId -> Boolean.TRUE.equals(currentState.get(termsId)));
    }

    private static Map<TermsType, Boolean> marketingConsentOf(List<Agreement> agreements,
        Map<Long, Terms> latestTerms) {
        Map<TermsType, Boolean> consent = new EnumMap<>(TermsType.class);
        for (Agreement agreement : agreements) {
            TermsType type = latestTerms.get(agreement.termsId()).getTermsType();
            if (type == TermsType.MARKETING || type == TermsType.MARKETING_NIGHT) {
                consent.put(type, agreement.agreed());
            }
        }
        return consent;
    }

    private void validateTermsIds(List<Agreement> agreements, Map<Long, Terms> latestTerms) {
        Set<Long> seen = new HashSet<>();
        for (Agreement agreement : agreements) {
            if (!latestTerms.containsKey(agreement.termsId()) || !seen.add(agreement.termsId())) {
                throw new BusinessException(TermErrorCode.INVALID_TERMS);
            }
        }
    }

    private void validateRequiredTermsAgreed(List<Agreement> agreements, Map<Long, Terms> latestTerms) {
        Map<Long, Boolean> agreedByTermsId = agreements.stream()
            .collect(Collectors.toMap(Agreement::termsId, Agreement::agreed));
        boolean allRequiredAgreed = latestTerms.values().stream()
            .filter(Terms::isRequired)
            .allMatch(terms -> Boolean.TRUE.equals(agreedByTermsId.get(terms.getId())));
        if (!allRequiredAgreed) {
            throw new BusinessException(TermErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }
    }
}
