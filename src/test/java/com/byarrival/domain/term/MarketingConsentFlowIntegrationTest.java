package com.byarrival.domain.term;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.domain.auth.service.TokenService;
import com.byarrival.domain.term.service.MarketingConsentService;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.repository.UserRepository;
import com.byarrival.support.TestcontainersConfig;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 광고성 정보 수신 동의(마케팅·야간)와 정보통신망법 안내. 안내는 커밋 뒤에 남으므로 롤백하지 않고 회원마다 새로 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class MarketingConsentFlowIntegrationTest {

    private static final String CONSENT = "/api/v1/users/me/marketing-consent";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MarketingConsentService marketingConsentService;

    @Autowired
    private Clock clock;

    private Long userId;
    private String bearer;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();
        bearer = "Bearer " + tokenService.issue(userId).accessToken();
    }

    @Test
    void 동의한_적이_없으면_모두_동의하지_않은_상태다() throws Exception {
        perform(get(CONSENT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.marketing.agreed").value(false))
            .andExpect(jsonPath("$.data.marketing.changedAt").doesNotExist())
            .andExpect(jsonPath("$.data.night.agreed").value(false));
    }

    @Test
    void 동의를_바꾸면_이력을_남기고_처리_결과를_알림함에_안내한다() throws Exception {
        change(true, true)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.marketing.agreed").value(true))
            .andExpect(jsonPath("$.data.marketing.changedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.night.agreed").value(true));

        assertThat(agreementCount()).isEqualTo(2);
        assertThat(noticeBodies()).singleElement().asString()
            .contains("광고성 정보 수신 동의").contains("야간(21~08시) 광고성 정보 수신 동의").contains("처리됐어요");

        // 광고성 정보 수신을 철회하면 야간 동의도 함께 끈다
        change(false, false).andExpect(jsonPath("$.data.night.agreed").value(false));
        assertThat(agreementCount()).isEqualTo(4);
        assertThat(noticeBodies()).hasSize(2);
        assertThat(noticeBodies().get(1)).contains("광고성 정보 수신 철회").contains("야간(21~08시) 광고성 정보 수신 철회");
    }

    @Test
    void 바뀐_값만_이력과_안내를_남긴다() throws Exception {
        change(true, false);
        change(true, false);
        change(true, true);

        assertThat(agreementCount()).isEqualTo(2);
        assertThat(noticeBodies()).hasSize(2);
        assertThat(noticeBodies().get(1)).doesNotContain("광고성 정보 수신 동의,").contains("야간(21~08시) 광고성 정보 수신 동의");
    }

    @Test
    void 광고성_정보_수신에_동의하지_않으면_야간_동의를_할_수_없다() throws Exception {
        change(false, true)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_MARKETING_CONSENT"));
        assertThat(agreementCount()).isZero();
    }

    @Test
    void 회원가입_약관_동의에서도_같은_규칙으로_받고_동의하면_안내한다() throws Exception {
        String required = "{\"termsId\":%d,\"agreed\":true},{\"termsId\":%d,\"agreed\":true}"
            .formatted(latestTermsId("SERVICE"), latestTermsId("PRIVACY"));

        agreeTerms(required + ",{\"termsId\":%d,\"agreed\":true}".formatted(latestTermsId("MARKETING_NIGHT")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_MARKETING_CONSENT"));

        // 마케팅·야간을 보내지 않으면 동의하지 않은 것으로 두고 안내하지 않는다
        agreeTerms(required).andExpect(status().isOk());
        assertThat(noticeBodies()).isEmpty();

        agreeTerms(required + ",{\"termsId\":%d,\"agreed\":true}".formatted(latestTermsId("MARKETING")))
            .andExpect(status().isOk());
        perform(get(CONSENT)).andExpect(jsonPath("$.data.marketing.agreed").value(true));
        assertThat(noticeBodies()).singleElement().asString().contains("광고성 정보 수신 동의");
    }

    @Test
    void 동의한_지_2년이_지나면_동의_여부를_한_번_다시_안내한다() {
        LocalDateTime agreedAt = LocalDateTime.now(clock).minusYears(2).minusDays(1);
        jdbcTemplate.update("INSERT INTO user_terms_agreement (user_id, terms_id, agreed, agreed_at) VALUES (?, ?, TRUE, ?)",
            userId, latestTermsId("MARKETING"), agreedAt);

        marketingConsentService.reconfirmDue();
        marketingConsentService.reconfirmDue();

        assertThat(noticeBodies()).singleElement().asString().contains("광고성 정보 수신에 동의하셨어요");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM marketing_consent_reconfirm WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void 철회한_회원에게는_재확인_안내를_보내지_않는다() {
        LocalDateTime longAgo = LocalDateTime.now(clock).minusYears(3);
        Long marketing = latestTermsId("MARKETING");
        jdbcTemplate.update("INSERT INTO user_terms_agreement (user_id, terms_id, agreed, agreed_at) VALUES (?, ?, TRUE, ?)",
            userId, marketing, longAgo);
        jdbcTemplate.update("INSERT INTO user_terms_agreement (user_id, terms_id, agreed, agreed_at) VALUES (?, ?, FALSE, ?)",
            userId, marketing, longAgo.plusDays(1));

        marketingConsentService.reconfirmDue();

        assertThat(noticeBodies()).isEmpty();
    }

    private ResultActions change(boolean marketing, boolean night) throws Exception {
        return perform(put(CONSENT).contentType(MediaType.APPLICATION_JSON)
            .content("{\"marketing\":%s,\"night\":%s}".formatted(marketing, night)));
    }

    private ResultActions agreeTerms(String agreements) throws Exception {
        return perform(post("/api/v1/terms/agreements").contentType(MediaType.APPLICATION_JSON)
            .content("{\"agreements\":[" + agreements + "]}"));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private Long latestTermsId(String type) {
        return jdbcTemplate.queryForObject("SELECT max(id) FROM terms WHERE terms_type = ?", Long.class, type);
    }

    private int agreementCount() {
        return jdbcTemplate.queryForObject("""
            SELECT count(*) FROM user_terms_agreement a JOIN terms t ON t.id = a.terms_id
            WHERE a.user_id = ? AND t.terms_type IN ('MARKETING', 'MARKETING_NIGHT')
            """, Integer.class, userId);
    }

    private List<String> noticeBodies() {
        return jdbcTemplate.queryForList(
            "SELECT body FROM notification WHERE user_id = ? AND type = 'MARKETING_CONSENT' ORDER BY id", String.class,
            userId);
    }
}
