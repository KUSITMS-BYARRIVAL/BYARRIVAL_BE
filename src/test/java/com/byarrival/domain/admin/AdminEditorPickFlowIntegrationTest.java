package com.byarrival.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.domain.admin.security.AdminCookieFactory;
import com.byarrival.domain.auth.service.TokenService;
import com.byarrival.domain.smalltalk.service.SmalltalkStore;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.repository.UserRepository;
import com.byarrival.support.TestcontainersConfig;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 DB와 보안 필터로 어드민 에디터 픽 관리와 앱 반영을 확인한다(EDITOR-01·03·04·05·07).
 * 다른 테스트의 데이터와 겹치지 않도록 날짜를 고정하고, 테스트마다 롤백한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, AdminEditorPickFlowIntegrationTest.FixedClockConfig.class})
@Transactional
class AdminEditorPickFlowIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2034, 6, 14);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final String PICKS = "/api/admin/editor-picks";
    private static final String PASSWORD = "correct-password";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TODAY.atTime(10, 0).atZone(KST).toInstant(), KST);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SmalltalkStore smalltalkStore;

    private Cookie cookie;
    private String adminEmail;
    private Long feedId;
    private Long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        adminEmail = "ops-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("INSERT INTO admin_account (email, password_hash) VALUES (?, ?)", adminEmail,
            PASSWORD_HASH);
        cookie = login(adminEmail);
        feedId = jdbcTemplate.queryForObject("INSERT INTO rss_feed (press_name, feed_url) VALUES (?, ?) RETURNING id",
            Long.class, "언론", "https://example.com/" + UUID.randomUUID());
        categoryId = jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, 99) RETURNING id", Long.class,
            "테스트-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Test
    void 목록은_기본으로_내일_에디터_픽을_보여_주고_배치가_만든_것은_노출_중이다() throws Exception {
        Long article = article("내일 기사", TOMORROW);
        Long pick = batchPick(article, TOMORROW, "어제 경기 보셨어요?");

        mockMvc.perform(get(PICKS).cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.serviceDate").value(TOMORROW.toString()))
            .andExpect(jsonPath("$.data.editable").value(true))
            .andExpect(jsonPath("$.data.picks.length()").value(1))
            .andExpect(jsonPath("$.data.picks[0].editorPickId").value(pick))
            .andExpect(jsonPath("$.data.picks[0].order").value(1))
            .andExpect(jsonPath("$.data.picks[0].status").value("EXPOSED"))
            .andExpect(jsonPath("$.data.picks[0].createdBy").doesNotExist())
            .andExpect(jsonPath("$.data.picks[0].article.title").value("내일 기사"))
            .andExpect(jsonPath("$.data.picks[0].article.available").value(true));
    }

    @Test
    void 오늘_노출_중인_기사로_내일_에디터_픽을_직접_추가한다() throws Exception {
        Long article = article("오늘 기사", TODAY);

        create(null, article, "  출근길에 이 얘기 어때요?  ")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.serviceDate").value(TOMORROW.toString()))
            .andExpect(jsonPath("$.data.hook").value("출근길에 이 얘기 어때요?"))
            .andExpect(jsonPath("$.data.status").value("EDITED"))
            .andExpect(jsonPath("$.data.createdBy").value(adminEmail));
    }

    @Test
    void 연결할_수_없는_기사와_이미_쓴_기사와_지난_노출일은_추가하지_않는다() throws Exception {
        Long later = article("모레 기사", TOMORROW.plusDays(1));
        Long hidden = article("숨긴 기사", TODAY);
        jdbcTemplate.update("UPDATE article SET visibility = 'HIDDEN' WHERE id = ?", hidden);
        Long taken = article("쓴 기사", TODAY);
        batchPick(taken, TODAY, "이미 있음");

        create(TOMORROW, later, "한마디")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ADMIN_EDITOR_PICK_ARTICLE_UNAVAILABLE"));
        create(TOMORROW, hidden, "한마디").andExpect(status().isBadRequest());
        create(TOMORROW, taken, "한마디")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ADMIN_EDITOR_PICK_ARTICLE_TAKEN"));
        create(TODAY.minusDays(1), article("어제 기사", TODAY), "한마디")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ADMIN_EDITOR_PICK_PAST"));
        create(TOMORROW, article("긴 기사", TODAY), "가".repeat(41)).andExpect(status().isBadRequest());
        create(TOMORROW, article("빈 기사", TODAY), " ").andExpect(status().isBadRequest());
    }

    @Test
    void 연결할_수_있는_기사는_오늘과_노출일_기사_중_아직_쓰지_않은_것이다() throws Exception {
        Long today = article("오늘-후보", TODAY);
        Long tomorrow = article("내일-후보", TOMORROW);
        Long taken = article("쓴-후보", TODAY);
        batchPick(taken, TODAY, "이미 있음");
        article("모레-후보", TOMORROW.plusDays(1));

        mockMvc.perform(get(PICKS + "/articles").param("keyword", "후보").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].articleId").value(tomorrow))
            .andExpect(jsonPath("$.data[1].articleId").value(today));
    }

    @Test
    void 수정하면_수정함이_되고_수정한_운영자를_남긴다() throws Exception {
        Long article = article("내일 기사", TOMORROW);
        Long other = article("다른 기사", TODAY);
        Long pick = batchPick(article, TOMORROW, "배치 한마디");

        update(pick, TOMORROW, other, "고친 한마디")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.hook").value("고친 한마디"))
            .andExpect(jsonPath("$.data.status").value("EDITED"))
            .andExpect(jsonPath("$.data.article.articleId").value(other))
            .andExpect(jsonPath("$.data.editedBy").value(adminEmail))
            .andExpect(jsonPath("$.data.editedAt").isNotEmpty());
        // 원래 기사는 비었으므로 다른 에디터 픽에 쓸 수 있다
        create(TOMORROW, article, "다시 씀").andExpect(status().isOk());
    }

    @Test
    void 지난_노출일의_에디터_픽은_바꿀_수_없다() throws Exception {
        LocalDate yesterday = TODAY.minusDays(1);
        Long pick = batchPick(article("어제 기사", yesterday), yesterday, "어제 한마디");

        mockMvc.perform(get(PICKS).param("serviceDate", yesterday.toString()).cookie(cookie))
            .andExpect(jsonPath("$.data.editable").value(false));
        update(pick, TOMORROW, article("오늘 기사", TODAY), "고침").andExpect(status().isConflict());
        mockMvc.perform(post(PICKS + "/" + pick + "/hide").cookie(cookie)).andExpect(status().isConflict());
        mockMvc.perform(delete(PICKS + "/" + pick).cookie(cookie)).andExpect(status().isConflict());
    }

    @Test
    void 숨기면_앱에서_빠지고_연결_기사를_숨겨도_숨김으로_보인다() throws Exception {
        Long first = article("첫 기사", TODAY);
        Long second = article("둘째 기사", TODAY);
        Long hiddenPick = batchPick(first, TODAY, "숨길 한마디");
        Long articleHiddenPick = batchPick(second, TODAY, "기사 숨김 한마디");
        jdbcTemplate.update("UPDATE article SET visibility = 'HIDDEN' WHERE id = ?", second);

        mockMvc.perform(post(PICKS + "/" + hiddenPick + "/hide").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("HIDDEN"))
            .andExpect(jsonPath("$.data.hidden").value(true));
        list(TODAY)
            .andExpect(jsonPath("$.data.picks[?(@.editorPickId == %d)].status".formatted(articleHiddenPick))
                .value("HIDDEN"))
            .andExpect(jsonPath("$.data.picks[?(@.editorPickId == %d)].hidden".formatted(articleHiddenPick))
                .value(false));
        appSmalltalks().andExpect(jsonPath("$.data.smalltalks.length()").value(0));

        mockMvc.perform(post(PICKS + "/" + hiddenPick + "/restore").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("EXPOSED"));
        appSmalltalks()
            .andExpect(jsonPath("$.data.smalltalks.length()").value(1))
            .andExpect(jsonPath("$.data.smalltalks[0].smalltalkId").value(hiddenPick));
    }

    @Test
    void 삭제하면_목록과_앱에서_빠지고_배치가_그_자리를_다시_채우지_않는다() throws Exception {
        Long article = article("오늘 기사", TODAY);
        Long pick = batchPick(article, TODAY, "지울 한마디");

        mockMvc.perform(delete(PICKS + "/" + pick).cookie(cookie)).andExpect(status().isOk());

        list(TODAY).andExpect(jsonPath("$.data.picks.length()").value(0));
        appSmalltalks().andExpect(jsonPath("$.data.smalltalks.length()").value(0));
        mockMvc.perform(get("/api/v1/smalltalks/" + pick).header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete(PICKS + "/" + pick).cookie(cookie)).andExpect(status().isNotFound());
        // 배치는 이미 스몰토크가 있는 기사로 보고 건너뛴다
        assertThat(smalltalkStore.findArticleIds(TODAY)).contains(article);
    }

    @Test
    void 순서를_바꾸면_목록과_앱이_그_순서를_따른다() throws Exception {
        Long a = batchPick(article("A 기사", TODAY), TODAY, "A");
        Long b = batchPick(article("B 기사", TODAY), TODAY, "B");
        Long c = batchPick(article("C 기사", TODAY), TODAY, "C");

        reorder(TODAY, c, a, b)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.picks[0].editorPickId").value(c))
            .andExpect(jsonPath("$.data.picks[0].order").value(1))
            .andExpect(jsonPath("$.data.picks[1].editorPickId").value(a))
            .andExpect(jsonPath("$.data.picks[2].editorPickId").value(b));
        appSmalltalks()
            .andExpect(jsonPath("$.data.smalltalks[0].smalltalkId").value(c))
            .andExpect(jsonPath("$.data.smalltalks[1].smalltalkId").value(a))
            .andExpect(jsonPath("$.data.smalltalks[2].smalltalkId").value(b));

        reorder(TODAY, c, a).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ADMIN_EDITOR_PICK_ORDER_MISMATCH"));
        reorder(TODAY, c, a, a).andExpect(status().isBadRequest());
    }

    @Test
    void 노출일을_옮기면_옮긴_날의_순서_뒤에_붙는다() throws Exception {
        Long first = batchPick(article("내일 첫 기사", TOMORROW), TOMORROW, "첫");
        Long moved = batchPick(article("오늘 기사", TODAY), TODAY, "옮길");
        reorder(TOMORROW, first).andExpect(status().isOk());
        reorder(TODAY, moved).andExpect(status().isOk());

        Long movedArticle = jdbcTemplate.queryForObject("SELECT article_id FROM smalltalk WHERE id = ?", Long.class,
            moved);
        update(moved, TOMORROW, movedArticle, "옮김").andExpect(status().isOk());

        list(TOMORROW)
            .andExpect(jsonPath("$.data.picks[0].editorPickId").value(first))
            .andExpect(jsonPath("$.data.picks[1].editorPickId").value(moved));
        list(TODAY).andExpect(jsonPath("$.data.picks.length()").value(0));
    }

    @Test
    void 에디터도_에디터_픽을_관리할_수_있고_쿠키_없이는_쓸_수_없다() throws Exception {
        String email = "editor-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("INSERT INTO admin_account (email, password_hash, role) VALUES (?, ?, 'EDITOR')", email,
            PASSWORD_HASH);
        Cookie editor = login(email);

        mockMvc.perform(get(PICKS).cookie(editor)).andExpect(status().isOk());
        mockMvc.perform(get(PICKS)).andExpect(status().isUnauthorized());
    }

    private ResultActions list(LocalDate serviceDate) throws Exception {
        return mockMvc.perform(get(PICKS).param("serviceDate", serviceDate.toString()).cookie(cookie));
    }

    private ResultActions create(LocalDate serviceDate, Long articleId, String hook) throws Exception {
        String date = serviceDate == null ? "null" : "\"" + serviceDate + "\"";
        return mockMvc.perform(post(PICKS).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content("{\"serviceDate\":%s,\"articleId\":%d,\"hook\":\"%s\"}".formatted(date, articleId, hook)));
    }

    private ResultActions update(Long pick, LocalDate serviceDate, Long articleId, String hook) throws Exception {
        return mockMvc.perform(put(PICKS + "/" + pick).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content("{\"serviceDate\":\"%s\",\"articleId\":%d,\"hook\":\"%s\"}".formatted(serviceDate, articleId,
                hook)));
    }

    private ResultActions reorder(LocalDate serviceDate, Long... ids) throws Exception {
        String list = String.join(",", java.util.Arrays.stream(ids).map(String::valueOf).toList());
        return mockMvc.perform(put(PICKS + "/order").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content("{\"serviceDate\":\"%s\",\"editorPickIds\":[%s]}".formatted(serviceDate, list)));
    }

    private ResultActions appSmalltalks() throws Exception {
        return mockMvc.perform(get("/api/v1/smalltalks").header(HttpHeaders.AUTHORIZATION, bearer()));
    }

    private String bearer() {
        Long userId = userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();
        return "Bearer " + tokenService.issue(userId).accessToken();
    }

    private Cookie login(String email) throws Exception {
        String setCookie = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String pair = setCookie.substring(0, setCookie.indexOf(';'));
        return new Cookie(AdminCookieFactory.COOKIE_NAME, pair.substring(pair.indexOf('=') + 1));
    }

    private Long article(String title, LocalDate serviceDate) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date,
                difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3, summary_one_line,
                process_status, recommendable)
            VALUES (?, ?, ?, ?, '언론', ?, ?, 'EASY', 1.5, '첫 문장', '둘째 문장', '셋째 문장', '한 줄 요약', 'READY', TRUE)
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(), title,
            serviceDate.atTime(3, 0), serviceDate);
    }

    private Long batchPick(Long articleId, LocalDate serviceDate, String hook) {
        return jdbcTemplate.queryForObject(
            "INSERT INTO smalltalk (article_id, service_date, hook) VALUES (?, ?, ?) RETURNING id", Long.class,
            articleId, serviceDate, hook);
    }
}
