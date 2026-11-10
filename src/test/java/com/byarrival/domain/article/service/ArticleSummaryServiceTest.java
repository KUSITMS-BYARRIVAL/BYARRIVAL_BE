package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.ai.exception.AiErrorCode;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import com.byarrival.domain.article.dto.ArticleSummaryOutput;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.entity.ArticleContent;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.entity.ArticleProcessStatus;
import com.byarrival.domain.article.repository.ArticleContentRepository;
import com.byarrival.domain.article.repository.ArticleRepository;
import com.byarrival.domain.article.service.ArticleSummaryService.SummaryResult;
import com.byarrival.domain.article.service.ArticleSummaryService.SummaryTarget;
import com.byarrival.domain.article.service.ArticleSummaryService.UsageTotal;
import com.byarrival.domain.feed.entity.RssFeed;
import com.byarrival.domain.feed.repository.RssFeedRepository;
import com.byarrival.support.TestcontainersConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfig.class)
class ArticleSummaryServiceTest {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 11, 9);
    private static final String BODY = "기사 본문입니다. ".repeat(50);

    @Autowired
    private ArticleSummaryService articleSummaryService;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleContentRepository articleContentRepository;

    @Autowired
    private RssFeedRepository rssFeedRepository;

    @MockitoBean
    private ArticleSummaryGenerator generator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long feedId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        feedId = rssFeedRepository.save(RssFeed.create("한국경제", "https://news.example.com/rss.xml", "경제")).getId();
        categoryId = jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES ('경제', 1) RETURNING id", Long.class);
    }

    @AfterEach
    void tearDown() {
        articleContentRepository.deleteAll();
        articleRepository.deleteAll();
        rssFeedRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM category");
    }

    @Test
    void 검증을_통과한_요약과_난이도를_저장하고_요약_완료로_바꾼다() {
        Article article = cleanedArticle("1", "한은, 기준금리 동결");
        given(generator.generate("한은, 기준금리 동결", BODY, ArticleDifficulty.HARD))
            .willReturn(result(output("한은, 기준금리 동결", ArticleDifficulty.HARD)));

        SummaryResult result = articleSummaryService.summarize(
            List.of(new SummaryTarget(article.getId(), ArticleDifficulty.HARD)));

        Article saved = articleRepository.findById(article.getId()).orElseThrow();
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failedArticleIds()).isEmpty();
        // 카테고리와 읽기시간이 있으므로 바로 추천 후보가 되고, 원문 본문은 지운다
        assertThat(saved.getProcessStatus()).isEqualTo(ArticleProcessStatus.READY);
        assertThat(saved.isRecommendable()).isTrue();
        assertThat(articleContentRepository.existsById(article.getId())).isFalse();
        assertThat(saved.getDifficulty()).isEqualTo(ArticleDifficulty.HARD);
        assertThat(List.of(saved.getSummarySentence1(), saved.getSummarySentence2(), saved.getSummarySentence3()))
            .containsExactly("첫째 문장입니다.", "둘째 문장입니다.", "셋째 문장입니다.");
        assertThat(saved.getSummaryOneLine()).isEqualTo("한 줄 요약입니다.");
        assertThat(saved.getSummaryKeywords()).containsExactly("기준금리", "한국은행", "물가");
    }

    @Test
    void 검증에_실패하면_저장하지_않고_실패로_돌려준다() {
        Article valid = cleanedArticle("1", "첫째 기사");
        Article tooLong = cleanedArticle("2", "둘째 기사");
        given(generator.generate("첫째 기사", BODY, ArticleDifficulty.EASY))
            .willReturn(result(output("첫째 기사", ArticleDifficulty.EASY)));
        given(generator.generate("둘째 기사", BODY, ArticleDifficulty.EASY))
            .willReturn(result(new ArticleSummaryOutput("둘째 기사", ArticleDifficulty.EASY, "한 줄 요약입니다.",
                List.of("가".repeat(31) + "니다.", "둘째 문장입니다.", "셋째 문장입니다."), List.of("기준금리", "한국은행", "물가"))));

        SummaryResult result = articleSummaryService.summarize(List.of(
            new SummaryTarget(valid.getId(), ArticleDifficulty.EASY),
            new SummaryTarget(tooLong.getId(), ArticleDifficulty.EASY)));

        Article failed = articleRepository.findById(tooLong.getId()).orElseThrow();
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failedArticleIds()).containsExactly(tooLong.getId());
        assertThat(failed.getProcessStatus()).isEqualTo(ArticleProcessStatus.CLEANED);
        assertThat(failed.getSummarySentence1()).isNull();
        assertThat(failed.getSummaryOneLine()).isNull();
        assertThat(failed.getSummaryKeywords()).isNull();
        assertThat(failed.getDifficulty()).isNull();
        assertThat(failed.isRecommendable()).isFalse();
        assertThat(articleContentRepository.existsById(tooLong.getId())).isTrue();
    }

    @Test
    void 검증에_실패하면_어긴_규칙을_알려주고_다시_요청해_통과하면_저장한다() {
        Article article = cleanedArticle("1", "첫째 기사");
        ArticleSummaryOutput tooLong = new ArticleSummaryOutput("첫째 기사", ArticleDifficulty.HARD, "한 줄 요약입니다.",
            List.of("가".repeat(31) + "니다.", "둘째 문장입니다.", "셋째 문장입니다."), List.of("기준금리", "한국은행", "물가"));
        given(generator.generate("첫째 기사", BODY, ArticleDifficulty.HARD)).willReturn(result(tooLong));
        given(generator.regenerate(eq("첫째 기사"), eq(BODY), eq(ArticleDifficulty.HARD), eq(tooLong),
            contains("summary 1번 문장이 34자입니다"))).willReturn(result(output("첫째 기사", ArticleDifficulty.HARD)));

        SummaryResult result = articleSummaryService.summarize(
            List.of(new SummaryTarget(article.getId(), ArticleDifficulty.HARD)));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(articleRepository.findById(article.getId()).orElseThrow().getSummarySentence1())
            .isEqualTo("첫째 문장입니다.");
        assertThat(result.usage()).isEqualTo(new UsageTotal(2, 1, 2000, 400));
    }

    @Test
    void 다시_요청해도_3회까지_통과하지_못하면_실패로_남긴다() {
        Article article = cleanedArticle("1", "첫째 기사");
        ArticleSummaryOutput tooLong = new ArticleSummaryOutput("첫째 기사", ArticleDifficulty.EASY, "한 줄 요약입니다.",
            List.of("가".repeat(31) + "니다.", "둘째 문장입니다.", "셋째 문장입니다."), List.of("기준금리", "한국은행", "물가"));
        given(generator.generate("첫째 기사", BODY, ArticleDifficulty.EASY)).willReturn(result(tooLong));
        given(generator.regenerate(anyString(), anyString(), any(), any(), anyString())).willReturn(result(tooLong));

        SummaryResult result = articleSummaryService.summarize(
            List.of(new SummaryTarget(article.getId(), ArticleDifficulty.EASY)));

        assertThat(result.failedArticleIds()).containsExactly(article.getId());
        verify(generator, times(3)).regenerate(anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void AI_호출이_실패하면_실패로_돌려주고_다른_기사는_계속_처리한다() {
        Article failing = cleanedArticle("1", "첫째 기사");
        Article ok = cleanedArticle("2", "둘째 기사");
        given(generator.generate(eq("첫째 기사"), anyString(), any()))
            .willThrow(new BusinessException(AiErrorCode.AI_CALL_FAILED, "RateLimitException status=429"));
        given(generator.generate("둘째 기사", BODY, ArticleDifficulty.EASY))
            .willReturn(result(output("둘째 기사", ArticleDifficulty.EASY)));

        SummaryResult result = articleSummaryService.summarize(List.of(
            new SummaryTarget(failing.getId(), ArticleDifficulty.HARD),
            new SummaryTarget(ok.getId(), ArticleDifficulty.EASY)));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failedArticleIds()).containsExactly(failing.getId());
    }

    @Test
    void 이미_요약한_기사는_다시_호출하지_않는다() {
        Article article = cleanedArticle("1", "첫째 기사");
        given(generator.generate("첫째 기사", BODY, ArticleDifficulty.EASY))
            .willReturn(result(output("첫째 기사", ArticleDifficulty.EASY)));
        articleSummaryService.summarize(List.of(new SummaryTarget(article.getId(), ArticleDifficulty.EASY)));

        SummaryResult rerun = articleSummaryService.summarize(
            List.of(new SummaryTarget(article.getId(), ArticleDifficulty.HARD)));

        assertThat(rerun.succeeded()).isZero();
        assertThat(rerun.skipped()).isEqualTo(1);
        verify(generator, never()).generate(anyString(), anyString(), eq(ArticleDifficulty.HARD));
        assertThat(articleRepository.findById(article.getId()).orElseThrow().getDifficulty())
            .isEqualTo(ArticleDifficulty.EASY);
    }

    private Article cleanedArticle(String key, String title) {
        Article article = Article.collected()
            .feedId(feedId)
            .categoryId(categoryId)
            .guid("guid-" + key)
            .originalUrl("https://news.example.com/" + key)
            .title(title)
            .pressName("한국경제")
            .publishedAt(LocalDateTime.of(2026, 11, 8, 10, 0))
            .serviceDate(SERVICE_DATE)
            .build();
        article.markCleaned("https://press.example.com/" + key, new BigDecimal("1.000"));
        Article saved = articleRepository.save(article);
        articleContentRepository.save(ArticleContent.of(saved, BODY));
        return saved;
    }

    private static ClaudeResult<ArticleSummaryOutput> result(ArticleSummaryOutput output) {
        return new ClaudeResult<>(output, new TokenUsage(1000, 200));
    }

    private static ArticleSummaryOutput output(String title, ArticleDifficulty difficulty) {
        return new ArticleSummaryOutput(title, difficulty, "한 줄 요약입니다.",
            List.of("첫째 문장입니다.", "둘째 문장입니다.", "셋째 문장입니다."), List.of("기준금리", "한국은행", "물가"));
    }
}
