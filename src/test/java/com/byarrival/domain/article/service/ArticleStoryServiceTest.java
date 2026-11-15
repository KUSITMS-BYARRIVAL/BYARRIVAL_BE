package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.ai.exception.AiErrorCode;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import com.byarrival.domain.article.service.ArticleStoryGrouper.GroupResult;
import com.byarrival.domain.article.service.ArticleStoryGrouper.StoryGroupingException;
import com.byarrival.domain.article.service.ArticleStoryGrouper.StoryInput;
import com.byarrival.domain.article.service.ArticleStoryService.StoryResult;
import com.byarrival.support.TestcontainersConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 실제 DB로 같은 사건 정리를 확인한다. AI 호출(ArticleStoryGrouper)만 가짜로 바꾼다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class ArticleStoryServiceTest {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 11, 12);

    @Autowired
    private ArticleStoryService articleStoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ArticleStoryGrouper grouper;

    @MockitoSpyBean
    private ArticleStoryStore store;

    private Long feedId;
    private Long economy;
    private Long it;

    @BeforeEach
    void setUp() {
        feedId = jdbcTemplate.queryForObject(
            "INSERT INTO rss_feed (press_name, feed_url) VALUES ('테스트', ?) RETURNING id", Long.class,
            "https://example.com/" + UUID.randomUUID());
        economy = newCategory();
        it = newCategory();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM article WHERE feed_id = ?", feedId);
        jdbcTemplate.update("DELETE FROM rss_feed WHERE id = ?", feedId);
        jdbcTemplate.update("DELETE FROM category WHERE id IN (?, ?)", economy, it);
    }

    @Test
    void 묶음마다_먼저_고른_기사만_남기고_나머지는_추천에서_뺀다() {
        Long first = newArticle(economy);
        Long economyOther = newArticle(economy);
        Long second = newArticle(it);
        Long itOther = newArticle(it);
        given(grouper.group(any())).willReturn(grouped(Set.of(second, first)));

        StoryResult result = articleStoryService.dedupe(SERVICE_DATE);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.groupCount()).isEqualTo(1);
        assertThat(result.removedCount()).isEqualTo(1);
        assertThat(recommendable(first)).isTrue();
        assertThat(recommendable(second)).isFalse();
        assertThat(duplicateOf(second)).isEqualTo(first);
        assertThat(recommendable(economyOther)).isTrue();
        assertThat(recommendable(itOther)).isTrue();
    }

    @Test
    void 빼면_카테고리_추천_후보가_0건이_되는_기사는_남긴다() {
        Long first = newArticle(economy);
        Long onlyIt = newArticle(it);
        given(grouper.group(any())).willReturn(grouped(Set.of(first, onlyIt)));

        StoryResult result = articleStoryService.dedupe(SERVICE_DATE);

        assertThat(result.removedCount()).isZero();
        assertThat(recommendable(first)).isTrue();
        assertThat(recommendable(onlyIt)).isTrue();
    }

    @Test
    void 묶기에_실패하면_아무_기사도_빼지_않고_넘어간다() {
        Long first = newArticle(economy);
        Long second = newArticle(it);
        given(grouper.group(any())).willThrow(new StoryGroupingException(
            new BusinessException(AiErrorCode.AI_RESPONSE_INVALID), 3, new TokenUsage(45000, 30)));

        StoryResult result = articleStoryService.dedupe(SERVICE_DATE);

        assertThat(result.succeeded()).isFalse();
        assertThat(recommendable(first)).isTrue();
        assertThat(recommendable(second)).isTrue();
    }

    @Test
    void 추천_후보가_2건_미만이면_AI를_부르지_않는다() {
        newArticle(economy);

        StoryResult result = articleStoryService.dedupe(SERVICE_DATE);

        assertThat(result.succeeded()).isTrue();
        verify(grouper, never()).group(any());
    }

    @Test
    void 이미_추천에서_뺀_기사는_다시_정리_대상에_넣지_않는다() {
        Long first = newArticle(economy);
        Long economyOther = newArticle(economy);
        Long second = newArticle(it);
        Long itOther = newArticle(it);
        given(grouper.group(any())).willReturn(grouped(Set.of(first, second)));
        articleStoryService.dedupe(SERVICE_DATE);

        given(grouper.group(any())).willReturn(grouped());
        articleStoryService.dedupe(SERVICE_DATE);

        ArgumentCaptor<List<StoryInput>> inputs = ArgumentCaptor.captor();
        verify(grouper, times(2)).group(inputs.capture());
        assertThat(inputs.getAllValues().get(1)).extracting(StoryInput::articleId)
            .containsExactly(first, economyOther, itOther);
        assertThat(recommendable(second)).isFalse();
        assertThat(recommendable(economyOther)).isTrue();
        assertThat(recommendable(itOther)).isTrue();
    }

    @Test
    void 재실행으로_남길_기사가_바뀌면_기존_중복_기록도_새로_남긴_기사로_옮긴다() {
        // 첫 실행에서 요약에 실패해 후보가 아니던 기사가 재실행에서 후보가 된 경우
        Long latecomer = newArticle(economy);
        jdbcTemplate.update("UPDATE article SET recommendable = FALSE, process_status = 'CLEANED' WHERE id = ?",
            latecomer);
        Long kept = newArticle(economy);
        Long economyOther = newArticle(economy);
        Long removed = newArticle(it);
        Long itOther = newArticle(it);
        given(grouper.group(any())).willReturn(grouped(Set.of(kept, removed)));
        articleStoryService.dedupe(SERVICE_DATE);

        jdbcTemplate.update("UPDATE article SET recommendable = TRUE, process_status = 'READY' WHERE id = ?",
            latecomer);
        given(grouper.group(any())).willReturn(grouped(Set.of(latecomer, kept)));
        articleStoryService.dedupe(SERVICE_DATE);

        assertThat(recommendable(latecomer)).isTrue();
        assertThat(recommendable(kept)).isFalse();
        assertThat(duplicateOf(kept)).isEqualTo(latecomer);
        assertThat(duplicateOf(removed)).isEqualTo(latecomer);
        assertThat(recommendable(economyOther)).isTrue();
        assertThat(recommendable(itOther)).isTrue();
    }

    @Test
    void 묶기_중_예상하지_못한_오류가_나도_아무_기사도_빼지_않고_넘어간다() {
        Long first = newArticle(economy);
        Long second = newArticle(it);
        given(grouper.group(any())).willThrow(new IllegalStateException("예상 못 한 오류"));

        StoryResult result = articleStoryService.dedupe(SERVICE_DATE);

        assertThat(result.succeeded()).isFalse();
        assertThat(recommendable(first)).isTrue();
        assertThat(recommendable(second)).isTrue();
    }

    @Test
    void 숨긴_기사는_정리_대상에_넣지_않아_대표로_남지_않는다() {
        Long hidden = newArticle(economy);
        jdbcTemplate.update("UPDATE article SET visibility = 'HIDDEN' WHERE id = ?", hidden);
        Long visible = newArticle(economy);
        Long other = newArticle(it);
        given(grouper.group(any())).willReturn(grouped());

        articleStoryService.dedupe(SERVICE_DATE);

        ArgumentCaptor<List<StoryInput>> inputs = ArgumentCaptor.captor();
        verify(grouper).group(inputs.capture());
        assertThat(inputs.getValue()).extracting(StoryInput::articleId).containsExactly(visible, other);
    }

    @Test
    void 중복_저장에_실패하면_아무_기사도_빼지_않고_쓴_토큰과_함께_넘어간다() {
        Long first = newArticle(economy);
        Long economyOther = newArticle(economy);
        Long second = newArticle(it);
        Long itOther = newArticle(it);
        given(grouper.group(any())).willReturn(grouped(Set.of(first, second)));
        willThrow(new IllegalStateException("잠금 시간 초과")).given(store).markDuplicates(any());

        StoryResult result = articleStoryService.dedupe(SERVICE_DATE);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.calls()).isEqualTo(1);
        assertThat(result.usage()).isEqualTo(new TokenUsage(15000, 2500));
        assertThat(recommendable(second)).isTrue();
        assertThat(recommendable(economyOther)).isTrue();
        assertThat(recommendable(itOther)).isTrue();
    }

    @SafeVarargs
    private GroupResult grouped(Set<Long>... groups) {
        return new GroupResult(List.of(groups), 1, new TokenUsage(15000, 2500));
    }

    private boolean recommendable(Long articleId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT recommendable FROM article WHERE id = ?", Boolean.class, articleId));
    }

    private Long duplicateOf(Long articleId) {
        return jdbcTemplate.queryForObject(
            "SELECT duplicate_of_article_id FROM article WHERE id = ?", Long.class, articleId);
    }

    private Long newCategory() {
        return jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, 99) RETURNING id", Long.class,
            "테스트-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private Long newArticle(Long categoryId) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date,
                difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3,
                summary_one_line, process_status, recommendable)
            VALUES (?, ?, ?, '테스트 기사', '테스트일보', now(), ?, 'EASY', 2.0, '첫 문장', '둘째 문장', '셋째 문장',
                '한 줄 요약', 'READY', TRUE)
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(), SERVICE_DATE);
    }
}
