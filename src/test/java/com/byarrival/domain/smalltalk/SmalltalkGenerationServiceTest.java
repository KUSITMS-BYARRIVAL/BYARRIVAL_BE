package com.byarrival.domain.smalltalk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import com.byarrival.domain.smalltalk.dto.SmalltalkOutput;
import com.byarrival.domain.smalltalk.service.SmalltalkGenerationService;
import com.byarrival.domain.smalltalk.service.SmalltalkGenerator;
import com.byarrival.domain.smalltalk.service.SmalltalkGenerator.SmalltalkSource;
import com.byarrival.support.TestcontainersConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 배치의 스몰토크 생성(TALK-01). AI 호출은 대신하고 인기 순서·대체 생성·저장을 실제 DB로 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class SmalltalkGenerationServiceTest {

    private static final TokenUsage USAGE = new TokenUsage(100, 20);

    @Autowired
    private SmalltalkGenerationService smalltalkGenerationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SmalltalkGenerator generator;

    private Long feedId;
    private LocalDate serviceDate;

    @BeforeEach
    void setUp() {
        feedId = jdbcTemplate.queryForObject(
            "INSERT INTO rss_feed (press_name, feed_url) VALUES ('테스트', ?) RETURNING id", Long.class,
            "https://example.com/" + UUID.randomUUID());
        serviceDate = LocalDate.of(2031, 1, 1).plusDays(Math.abs(UUID.randomUUID().getMostSignificantBits() % 3000));
    }

    @Test
    void 카테고리별_인기_순서대로_2건을_채우고_실패하거나_알맞지_않은_기사는_다음_기사로_대체한다() {
        Long economy = newCategory();
        Long sports = newCategory();
        // 경제: 1위 실패(검증 4회 실패) → 2위 정치 갈등(알맞지 않음) → 3위·4위 성공 → 5위는 부르지 않는다
        Long failed = newArticle(economy, "경제 1위");
        Long unsuitable = newArticle(economy, "경제 2위");
        Long third = newArticle(economy, "경제 3위");
        Long fourth = newArticle(economy, "경제 4위");
        Long fifth = newArticle(economy, "경제 5위");
        // 스포츠: 1건뿐이라 1건만 만든다
        Long only = newArticle(sports, "스포츠 1위");
        given(generator.generate(source(failed))).willReturn(result("경제 1위", true, "가".repeat(31) + "요"));
        given(generator.regenerate(source(failed), any(), anyString()))
            .willReturn(result("경제 1위", true, "해요체가 아닌 문장이다."));
        given(generator.generate(source(unsuitable))).willReturn(result("경제 2위", false, ""));
        given(generator.generate(source(third))).willReturn(result("경제 3위", true, "경제 3위 소식 들으셨어요?"));
        given(generator.generate(source(fourth))).willReturn(result("경제 4위", true, "경제 4위가 화제라고 해요."));
        given(generator.generate(source(only))).willReturn(result("스포츠 1위", true, "어제 경기 보셨어요?"));

        Map<Long, Integer> counts = smalltalkGenerationService.generate(serviceDate);

        assertThat(counts).containsEntry(economy, 2).containsEntry(sports, 1);
        verify(generator, times(3)).regenerate(source(failed), any(), anyString());
        verify(generator, never()).regenerate(source(unsuitable), any(), anyString());
        verify(generator, never()).generate(source(fifth));
        List<Map<String, Object>> saved = jdbcTemplate.queryForList(
            "SELECT article_id, hook, followup, visibility FROM smalltalk WHERE service_date = ? ORDER BY article_id",
            serviceDate);
        assertThat(saved).extracting(row -> ((Number) row.get("article_id")).longValue())
            .containsExactly(third, fourth, only);
        assertThat(saved.getFirst().get("hook")).isEqualTo("경제 3위 소식 들으셨어요?");
        assertThat(saved.getFirst().get("followup")).isNull();
        assertThat(saved.getFirst().get("visibility")).isEqualTo("VISIBLE");

        // 다시 돌려도 이미 채운 카테고리는 부르지 않는다
        clearInvocations(generator);
        smalltalkGenerationService.generate(serviceDate);
        verify(generator, never()).generate(source(fifth));
        verify(generator, never()).generate(source(third));
    }

    @Test
    void 생성하는_사이_같은_기사의_스몰토크가_저장되면_확보한_건수에_넣는다() {
        Long category = newCategory();
        Long first = newArticle(category, "1위");
        Long second = newArticle(category, "2위");
        Long third = newArticle(category, "3위");
        // AI 응답을 기다리는 사이 다른 배치 실행이 1위 기사의 스몰토크를 먼저 저장했다
        given(generator.generate(source(first))).willAnswer(invocation -> {
            jdbcTemplate.update("INSERT INTO smalltalk (article_id, service_date, hook) VALUES (?, ?, '먼저 저장됐어요.')",
                first, serviceDate);
            return result("1위", true, "1위 소식 들으셨어요?");
        });
        given(generator.generate(source(second))).willReturn(result("2위", true, "2위 소식 들으셨어요?"));

        Map<Long, Integer> counts = smalltalkGenerationService.generate(serviceDate);

        assertThat(counts).containsEntry(category, 2);
        verify(generator, never()).generate(source(third));
        assertThat(jdbcTemplate.queryForObject("SELECT hook FROM smalltalk WHERE article_id = ?", String.class, first))
            .isEqualTo("먼저 저장됐어요.");
    }

    private static SmalltalkSource source(Long articleId) {
        return argThat(source -> source != null && source.articleId().equals(articleId));
    }

    private static ClaudeResult<SmalltalkOutput> result(String title, boolean suitable, String hook) {
        return new ClaudeResult<>(new SmalltalkOutput(title, suitable, hook), USAGE);
    }

    private Long newCategory() {
        return jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, 99) RETURNING id", Long.class,
            "테스트-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private Long newArticle(Long categoryId, String title) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date,
                difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3,
                summary_one_line, process_status, recommendable, visibility)
            VALUES (?, ?, ?, ?, '테스트일보', ?, ?, 'EASY', 2.0, '첫 문장', '둘째 문장', '셋째 문장', '한 줄 요약',
                'READY', TRUE, 'VISIBLE')
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(), title,
            serviceDate.atTime(1, 0), serviceDate);
    }
}
