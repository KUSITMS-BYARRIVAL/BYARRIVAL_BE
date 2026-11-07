package com.byarrival.domain.term.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.term.dto.request.TermsAgreementRequest.Agreement;
import com.byarrival.domain.term.dto.response.TermsAgreementResponse;
import com.byarrival.domain.term.entity.Terms;
import com.byarrival.domain.term.entity.TermsType;
import com.byarrival.domain.term.entity.UserTermsAgreement;
import com.byarrival.domain.term.exception.TermErrorCode;
import com.byarrival.domain.term.repository.TermsRepository;
import com.byarrival.domain.term.repository.UserTermsAgreementRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TermServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 9, 0);
    private static final Long USER_ID = 1L;

    private TermsRepository termsRepository;
    private UserTermsAgreementRepository agreementRepository;
    private TermService termService;

    @BeforeEach
    void setUp() {
        termsRepository = mock(TermsRepository.class);
        agreementRepository = mock(UserTermsAgreementRepository.class);
        ZoneId kst = ZoneId.of("Asia/Seoul");
        Clock clock = Clock.fixed(ZonedDateTime.of(NOW, kst).toInstant(), kst);
        termService = new TermService(termsRepository, agreementRepository, clock);
        given(termsRepository.findLatestOfEachType()).willReturn(List.of(
            terms(1L, TermsType.SERVICE, true), terms(2L, TermsType.PRIVACY, true)));
        given(agreementRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void 필수_약관에_모두_동의하면_동의_이력을_쌓는다() {
        List<TermsAgreementResponse> responses = termService.agree(USER_ID,
            List.of(new Agreement(1L, true), new Agreement(2L, true)));

        assertThat(responses).extracting(TermsAgreementResponse::termsId).containsExactly(1L, 2L);
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.agreed()).isTrue();
            assertThat(response.agreedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void 필수_약관이_빠지면_진행할_수_없다() {
        assertAgreeError(List.of(new Agreement(1L, true)), TermErrorCode.REQUIRED_TERMS_NOT_AGREED);
    }

    @Test
    void 필수_약관에_동의하지_않으면_진행할_수_없다() {
        assertAgreeError(List.of(new Agreement(1L, true), new Agreement(2L, false)),
            TermErrorCode.REQUIRED_TERMS_NOT_AGREED);
    }

    @Test
    void 최신_버전이_아니거나_없는_약관이면_동의할_수_없다() {
        assertAgreeError(List.of(new Agreement(1L, true), new Agreement(2L, true), new Agreement(99L, true)),
            TermErrorCode.INVALID_TERMS);
    }

    @Test
    void 같은_약관을_중복해서_보내면_동의할_수_없다() {
        assertAgreeError(List.of(new Agreement(1L, true), new Agreement(1L, true), new Agreement(2L, true)),
            TermErrorCode.INVALID_TERMS);
    }

    @Test
    void 필수_약관마다_가장_최근_이력이_동의면_동의한_상태다() {
        given(agreementRepository.findByUserIdAndTermsIdInOrderByAgreedAtDescIdDesc(eq(USER_ID), anyCollection()))
            .willReturn(List.of(agreement(1L, true), agreement(2L, true), agreement(2L, false)));

        assertThat(termService.hasAgreedRequiredTerms(USER_ID)).isTrue();
    }

    @Test
    void 필수_약관의_가장_최근_이력이_미동의거나_이력이_없으면_동의하지_않은_상태다() {
        given(agreementRepository.findByUserIdAndTermsIdInOrderByAgreedAtDescIdDesc(eq(USER_ID), anyCollection()))
            .willReturn(List.of(agreement(1L, false), agreement(1L, true)));

        assertThat(termService.hasAgreedRequiredTerms(USER_ID)).isFalse();
    }

    private void assertAgreeError(List<Agreement> agreements, TermErrorCode errorCode) {
        assertThatThrownBy(() -> termService.agree(USER_ID, agreements))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(errorCode);
        verify(agreementRepository, never()).saveAll(any());
    }

    private Terms terms(Long id, TermsType type, boolean required) {
        Terms terms = instantiate(Terms.class);
        ReflectionTestUtils.setField(terms, "id", id);
        ReflectionTestUtils.setField(terms, "termsType", type);
        ReflectionTestUtils.setField(terms, "required", required);
        return terms;
    }

    private UserTermsAgreement agreement(Long termsId, boolean agreed) {
        return UserTermsAgreement.record(USER_ID, termsId, agreed, NOW);
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
